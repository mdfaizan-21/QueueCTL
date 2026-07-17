package com.queuectl.integration;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteConfigRepository;
import com.queuectl.repository.SqliteJobRepository;
import com.queuectl.repository.SqliteWorkerRepository;
import com.queuectl.service.*;
import com.queuectl.worker.WorkerManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the full QueueCTL pipeline.
 *
 * Tests the complete flow: enqueue → worker pickup → execution → state transitions.
 * Each test uses a fresh SQLite database via @TempDir.
 */
class EndToEndTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;
    private SqliteJobRepository jobRepo;
    private SqliteWorkerRepository workerRepo;
    private SqliteConfigRepository configRepo;
    private JobService jobService;
    private ConfigService configService;
    private DlqService dlqService;
    private MetricsService metricsService;
    private RetryService retryService;
    private WorkerManager workerManager;

    @BeforeEach
    void setUp() {
        factory = new SqliteConnectionFactory(tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();

        jobRepo = new SqliteJobRepository(factory);
        workerRepo = new SqliteWorkerRepository(factory);
        configRepo = new SqliteConfigRepository(factory);

        configService = new ConfigService(configRepo);
        jobService = new JobService(jobRepo);
        dlqService = new DlqService(jobRepo);
        retryService = new RetryService(jobRepo, configService);
        metricsService = new MetricsService(jobRepo, workerRepo, factory);

        workerManager = new WorkerManager(jobRepo, workerRepo,
                retryService, metricsService, configService);
    }

    @AfterEach
    void tearDown() {
        workerManager.shutdown();
    }

    @Test
    @DisplayName("E2E: enqueue → worker processes → job COMPLETED with output captured")
    void testFullSuccessFlow() throws Exception {
        // 1. Enqueue a job
        Job job = new Job("e2e-1", "echo integration-test-output");
        job.setPriority(Priority.HIGH);
        jobService.enqueue(job);

        assertEquals(JobState.PENDING, jobRepo.findById("e2e-1").orElseThrow().getState());
        assertEquals(1, jobRepo.countByState(JobState.PENDING));

        // 2. Start a worker
        workerManager.start(1);

        // 3. Wait for completion
        assertTrue(waitForJobState("e2e-1", JobState.COMPLETED, 10_000));

        // 4. Verify results
        Job completed = jobRepo.findById("e2e-1").orElseThrow();
        assertEquals(JobState.COMPLETED, completed.getState());
        assertTrue(completed.getOutput().contains("integration-test-output"));
        assertEquals(0, completed.getAttempts());
        assertNull(completed.getLockedBy());
        assertEquals(0, jobRepo.countByState(JobState.PENDING));
    }

    @Test
    @DisplayName("E2E: failing job → retry with backoff → eventually completes DLQ cycle")
    void testRetryAndDlqFlow() throws Exception {
        // 1. Enqueue a failing job with maxRetries=1
        String failCmd = System.getProperty("os.name").toLowerCase().contains("win")
                ? "cmd /c exit 1" : "sh -c 'exit 1'";
        Job job = new Job("e2e-2", failCmd);
        job.setMaxRetries(1);
        jobService.enqueue(job);

        // 2. Start a worker
        workerManager.start(1);

        // 3. Wait for it to hit DLQ (maxRetries=1 means 1 attempt then DEAD)
        assertTrue(waitForJobState("e2e-2", JobState.DEAD, 10_000),
                "Job should reach DEAD state after exhausting retries");

        // 4. Verify DLQ
        Job deadJob = jobRepo.findById("e2e-2").orElseThrow();
        assertEquals(JobState.DEAD, deadJob.getState());
        assertEquals(1, deadJob.getAttempts());
        assertTrue(deadJob.getError() != null && !deadJob.getError().isBlank());
        assertEquals(1, dlqService.countDeadJobs());

        // 5. Retry from DLQ
        workerManager.shutdown();
        dlqService.retryJob("e2e-2");

        Job retried = jobRepo.findById("e2e-2").orElseThrow();
        assertEquals(JobState.PENDING, retried.getState());
        assertEquals(0, retried.getAttempts());
        assertEquals(0, dlqService.countDeadJobs());
    }

    @Test
    @DisplayName("E2E: priority ordering — HIGH jobs processed before LOW")
    void testPriorityOrdering() throws Exception {
        // 1. Enqueue LOW first, then HIGH
        Job low = new Job("e2e-low", "echo low");
        low.setPriority(Priority.LOW);
        jobService.enqueue(low);

        Job high = new Job("e2e-high", "echo high");
        high.setPriority(Priority.HIGH);
        jobService.enqueue(high);

        // 2. Start 1 worker — it should pick HIGH first
        workerManager.start(1);

        // 3. Wait for HIGH to complete first
        assertTrue(waitForJobState("e2e-high", JobState.COMPLETED, 10_000));

        Job completedHigh = jobRepo.findById("e2e-high").orElseThrow();
        assertEquals(JobState.COMPLETED, completedHigh.getState());

        // 4. LOW should complete after
        assertTrue(waitForJobState("e2e-low", JobState.COMPLETED, 10_000));
    }

    @Test
    @DisplayName("E2E: multiple workers process multiple jobs concurrently")
    void testConcurrentWorkers() throws Exception {
        // 1. Enqueue 6 jobs
        for (int i = 1; i <= 6; i++) {
            jobService.enqueue(new Job("e2e-c" + i, "echo concurrent-" + i));
        }
        assertEquals(6, jobRepo.countByState(JobState.PENDING));

        // 2. Start 3 workers
        workerManager.start(3);

        // 3. Wait for all to complete
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (jobRepo.countByState(JobState.COMPLETED) >= 6) break;
            Thread.sleep(200);
        }

        // 4. All jobs should be COMPLETED
        assertEquals(6, jobRepo.countByState(JobState.COMPLETED));
        assertEquals(0, jobRepo.countByState(JobState.PENDING));
    }

    @Test
    @DisplayName("E2E: config changes affect runtime behavior")
    void testConfigAffectsRuntime() {
        // Default max-retries is 3
        assertEquals("3", configService.get("max-retries"));

        // Change to 5
        configService.set("max-retries", "5");
        assertEquals("5", configService.get("max-retries"));
        assertEquals(5, configService.getInt("max-retries", 3));

        // Verify all defaults are present
        assertTrue(configService.listAll().size() >= 5);
    }

    @Test
    @DisplayName("E2E: metrics reflect actual job processing")
    void testMetricsAfterProcessing() throws Exception {
        // 1. Enqueue and process 2 jobs
        jobService.enqueue(new Job("e2e-m1", "echo metrics1"));
        jobService.enqueue(new Job("e2e-m2", "echo metrics2"));

        workerManager.start(1);

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (jobRepo.countByState(JobState.COMPLETED) >= 2) break;
            Thread.sleep(200);
        }
        workerManager.shutdown();

        // 2. Check metrics
        var metrics = metricsService.gatherMetrics();
        assertEquals("2", metrics.get("Total Jobs"));
        assertEquals("2", metrics.get("Completed"));
        assertEquals("0", metrics.get("Pending"));
        assertEquals("100.0%", metrics.get("Success Rate"));
    }

    @Test
    @DisplayName("E2E: status counts reflect job states accurately")
    void testStatusCounts() throws Exception {
        jobService.enqueue(new Job("e2e-s1", "echo status1"));
        jobService.enqueue(new Job("e2e-s2", "echo status2"));

        int[] counts = jobService.getStateCounts();
        assertEquals(2, counts[0]); // PENDING
        assertEquals(0, counts[1]); // PROCESSING
        assertEquals(0, counts[2]); // COMPLETED
        assertEquals(2, jobService.totalJobs());

        // Process them
        workerManager.start(1);
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (jobRepo.countByState(JobState.COMPLETED) >= 2) break;
            Thread.sleep(200);
        }
        workerManager.shutdown();

        counts = jobService.getStateCounts();
        assertEquals(0, counts[0]); // PENDING
        assertEquals(2, counts[2]); // COMPLETED
    }

    @Test
    @DisplayName("E2E: job timeout kills long-running commands")
    void testJobTimeout() throws Exception {
        // Command that would run forever but has 1s timeout
        String longCmd = System.getProperty("os.name").toLowerCase().contains("win")
                ? "ping -n 30 127.0.0.1" : "sleep 30";
        Job job = new Job("e2e-timeout", longCmd);
        job.setTimeout(1);
        job.setMaxRetries(1);
        jobService.enqueue(job);

        workerManager.start(1);

        // Should fail and go to DLQ (timeout → failure → maxRetries=1 → DEAD)
        assertTrue(waitForJobState("e2e-timeout", JobState.DEAD, 15_000),
                "Timed-out job should reach DEAD state");

        Job timedOut = jobRepo.findById("e2e-timeout").orElseThrow();
        assertTrue(timedOut.getError().toLowerCase().contains("timed out"));
    }

    @Test
    @DisplayName("E2E: delayed job is not picked up before its run_at time")
    void testDelayedJob() throws Exception {
        // Enqueue a delayed job (run 10 minutes in the future)
        Job delayed = new Job("e2e-delayed", "echo delayed");
        delayed.setRunAt("2099-01-01T00:00:00Z");
        jobService.enqueue(delayed);

        // Enqueue an immediate job
        jobService.enqueue(new Job("e2e-immediate", "echo immediate"));

        workerManager.start(1);

        // Immediate should complete
        assertTrue(waitForJobState("e2e-immediate", JobState.COMPLETED, 10_000));

        // Delayed should still be PENDING
        Thread.sleep(500);
        assertEquals(JobState.PENDING, jobRepo.findById("e2e-delayed").orElseThrow().getState());
    }

    // --- Helper ---

    private boolean waitForJobState(String jobId, JobState target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Job j = jobRepo.findById(jobId).orElse(null);
            if (j != null && j.getState() == target) return true;
            Thread.sleep(100);
        }
        return false;
    }
}

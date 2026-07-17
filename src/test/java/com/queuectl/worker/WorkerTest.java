package com.queuectl.worker;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteConfigRepository;
import com.queuectl.repository.SqliteJobRepository;
import com.queuectl.repository.SqliteWorkerRepository;
import com.queuectl.service.ConfigService;
import com.queuectl.service.JobService;
import com.queuectl.service.MetricsService;
import com.queuectl.service.RetryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Worker poll loop and job processing.
 */
class WorkerTest {

    @TempDir
    Path tempDir;

    private SqliteJobRepository jobRepo;
    private SqliteWorkerRepository workerRepo;
    private JobService jobService;
    private Worker worker;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        jobRepo = new SqliteJobRepository(factory);
        workerRepo = new SqliteWorkerRepository(factory);
        SqliteConfigRepository configRepo = new SqliteConfigRepository(factory);
        ConfigService configService = new ConfigService(configRepo);
        MetricsService metricsService = new MetricsService(jobRepo, workerRepo, factory);
        RetryService retryService = new RetryService(jobRepo, configService);
        jobService = new JobService(jobRepo);

        worker = new Worker("test-worker", jobRepo, workerRepo,
                retryService, metricsService, configService);
    }

    @Test
    @DisplayName("worker processes a PENDING job to COMPLETED")
    void testWorkerProcessesJob() throws Exception {
        // Enqueue a simple echo command
        Job job = new Job("j1", "echo hello");
        jobService.enqueue(job);

        // Start worker in a thread, let it process one job, then stop it
        Thread workerThread = new Thread(worker);
        workerThread.start();

        // Wait for the job to be processed (with timeout)
        boolean processed = waitForJobState("j1", JobState.COMPLETED, 10_000);
        worker.shutdown();
        workerThread.join(5000);

        assertTrue(processed, "Job should reach COMPLETED state");
        Job completed = jobRepo.findById("j1").orElseThrow();
        assertEquals(JobState.COMPLETED, completed.getState());
        assertTrue(completed.getOutput().contains("hello"));
    }

    @Test
    @DisplayName("worker retries a failing job")
    void testWorkerRetriesFailingJob() throws Exception {
        // Enqueue a command that will fail
        String failCmd = System.getProperty("os.name").toLowerCase().contains("win")
                ? "cmd /c exit 1"
                : "sh -c 'exit 1'";
        Job job = new Job("j2", failCmd);
        job.setMaxRetries(2);
        jobService.enqueue(job);

        Thread workerThread = new Thread(worker);
        workerThread.start();

        // Wait for retry (state goes back to PENDING with attempts > 0)
        boolean retried = waitForCondition(() -> {
            Job j = jobRepo.findById("j2").orElse(null);
            return j != null && j.getAttempts() > 0;
        }, 10_000);

        worker.shutdown();
        workerThread.join(5000);

        assertTrue(retried, "Job should have been retried (attempts > 0)");
    }

    @Test
    @DisplayName("worker registers and deregisters itself")
    void testWorkerRegistration() throws Exception {
        Thread workerThread = new Thread(worker);
        workerThread.start();

        // Give it time to register
        Thread.sleep(500);

        assertTrue(workerRepo.findById("test-worker").isPresent(),
                "Worker should be registered");

        worker.shutdown();
        workerThread.join(5000);

        // After shutdown, worker should be deregistered
        assertTrue(workerRepo.findById("test-worker").isEmpty(),
                "Worker should be deregistered after shutdown");
    }

    @Test
    @DisplayName("worker shutdown stops the poll loop")
    void testGracefulShutdown() throws Exception {
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(300);

        assertTrue(worker.isRunning());

        worker.shutdown();
        workerThread.join(5000);

        assertFalse(worker.isRunning());
        assertFalse(workerThread.isAlive());
    }

    // --- Helpers ---

    private boolean waitForJobState(String jobId, JobState target, long timeoutMs)
            throws InterruptedException {
        return waitForCondition(() -> {
            Job j = jobRepo.findById(jobId).orElse(null);
            return j != null && j.getState() == target;
        }, timeoutMs);
    }

    private boolean waitForCondition(java.util.function.Supplier<Boolean> condition,
                                     long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) return true;
            Thread.sleep(100);
        }
        return false;
    }
}

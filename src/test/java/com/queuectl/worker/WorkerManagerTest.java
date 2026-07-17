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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkerManager.
 */
class WorkerManagerTest {

    @TempDir
    Path tempDir;

    private WorkerManager workerManager;
    private SqliteJobRepository jobRepo;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        jobRepo = new SqliteJobRepository(factory);
        SqliteWorkerRepository workerRepo = new SqliteWorkerRepository(factory);
        SqliteConfigRepository configRepo = new SqliteConfigRepository(factory);
        ConfigService configService = new ConfigService(configRepo);
        MetricsService metricsService = new MetricsService(jobRepo, workerRepo, factory);
        RetryService retryService = new RetryService(jobRepo, configService);
        jobService = new JobService(jobRepo);

        workerManager = new WorkerManager(jobRepo, workerRepo,
                retryService, metricsService, configService);
    }

    @AfterEach
    void tearDown() {
        workerManager.shutdown();
    }

    @Test
    @DisplayName("start creates the specified number of workers")
    void testStartMultipleWorkers() {
        workerManager.start(3);
        assertEquals(3, workerManager.getWorkers().size());
        assertEquals(3, workerManager.getActiveCount());
    }

    @Test
    @DisplayName("shutdown stops all workers")
    void testShutdownStopsAll() throws Exception {
        workerManager.start(2);
        Thread.sleep(300);

        workerManager.shutdown();
        Thread.sleep(500);

        assertEquals(0, workerManager.getWorkers().size());
    }

    @Test
    @DisplayName("start with invalid count throws exception")
    void testStartInvalidCount() {
        assertThrows(IllegalArgumentException.class, () -> workerManager.start(0));
        assertThrows(IllegalArgumentException.class, () -> workerManager.start(-1));
    }

    @Test
    @DisplayName("multiple workers process multiple jobs concurrently")
    void testConcurrentProcessing() throws Exception {
        // Enqueue 4 quick jobs
        for (int i = 1; i <= 4; i++) {
            jobService.enqueue(new Job("j" + i, "echo job" + i));
        }

        // Start 2 workers
        workerManager.start(2);

        // Wait for all jobs to complete
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            int completed = jobRepo.countByState(JobState.COMPLETED);
            if (completed >= 4) break;
            Thread.sleep(200);
        }

        workerManager.shutdown();

        assertEquals(4, jobRepo.countByState(JobState.COMPLETED),
                "All 4 jobs should be completed");
    }

    @Test
    @DisplayName("stalled jobs are recovered on startup")
    void testStalledJobRecovery() {
        // Simulate a stalled job (PROCESSING state from a crashed worker)
        Job job = new Job("stalled-1", "echo recovered");
        jobService.enqueue(job);
        job.setState(JobState.PROCESSING);
        job.setLockedBy("dead-worker");
        jobRepo.update(job);

        assertEquals(1, jobRepo.countByState(JobState.PROCESSING));

        // Starting the manager should reset stalled jobs
        workerManager.start(1);

        // Give the worker time to pick up and process the reset job
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                if (jobRepo.countByState(JobState.COMPLETED) >= 1) break;
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        workerManager.shutdown();

        assertEquals(JobState.COMPLETED, jobRepo.findById("stalled-1").orElseThrow().getState(),
                "Stalled job should be recovered and completed");
    }
}

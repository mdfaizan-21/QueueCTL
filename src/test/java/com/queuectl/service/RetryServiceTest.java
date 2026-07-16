package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteConfigRepository;
import com.queuectl.repository.SqliteJobRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RetryService.
 */
class RetryServiceTest {

    @TempDir
    Path tempDir;

    private RetryService retryService;
    private SqliteJobRepository jobRepo;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        jobRepo = new SqliteJobRepository(factory);
        jobService = new JobService(jobRepo);
        ConfigService configService = new ConfigService(new SqliteConfigRepository(factory));
        retryService = new RetryService(jobRepo, configService);
    }

    @Test
    @DisplayName("handleFailure retries when attempts < maxRetries")
    void testRetry() {
        Job job = new Job("j1", "echo fail");
        job.setMaxRetries(3);
        jobService.enqueue(job);

        // Simulate first failure (attempt will become 1, maxRetries=3)
        Job fetched = jobRepo.fetchAndLock("w1").orElseThrow();
        boolean retried = retryService.handleFailure(fetched, "Command failed");

        assertTrue(retried, "Should retry when attempts < maxRetries");
        Job updated = jobRepo.findById("j1").orElseThrow();
        assertEquals(JobState.PENDING, updated.getState());
        assertEquals(1, updated.getAttempts());
        assertNotNull(updated.getNextRetryAt(), "Should have next_retry_at set");
    }

    @Test
    @DisplayName("handleFailure moves to DLQ when retries exhausted")
    void testMoveToDlq() {
        Job job = new Job("j1", "echo fail");
        job.setMaxRetries(1);
        jobService.enqueue(job);

        Job fetched = jobRepo.fetchAndLock("w1").orElseThrow();
        boolean retried = retryService.handleFailure(fetched, "Final failure");

        assertFalse(retried, "Should not retry when attempts >= maxRetries");
        Job updated = jobRepo.findById("j1").orElseThrow();
        assertEquals(JobState.DEAD, updated.getState());
    }

    @Test
    @DisplayName("calculateBackoff uses exponential formula")
    void testCalculateBackoff() {
        // Default backoff-base=2, so: 2^1=2, 2^2=4, 2^3=8
        assertEquals(2, retryService.calculateBackoff(1));
        assertEquals(4, retryService.calculateBackoff(2));
        assertEquals(8, retryService.calculateBackoff(3));
    }
}

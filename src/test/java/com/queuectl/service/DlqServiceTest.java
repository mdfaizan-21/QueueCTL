package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteJobRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DlqService.
 */
class DlqServiceTest {

    @TempDir
    Path tempDir;

    private DlqService dlqService;
    private SqliteJobRepository jobRepo;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        jobRepo = new SqliteJobRepository(factory);
        jobService = new JobService(jobRepo);
        dlqService = new DlqService(jobRepo);
    }

    @Test
    @DisplayName("listDeadJobs returns only DEAD jobs")
    void testListDeadJobs() {
        // Create a dead job manually
        Job job = new Job("j1", "echo dead");
        jobService.enqueue(job);
        job.setState(JobState.DEAD);
        jobRepo.update(job);

        List<Job> dead = dlqService.listDeadJobs();
        assertEquals(1, dead.size());
        assertEquals("j1", dead.get(0).getId());
    }

    @Test
    @DisplayName("listDeadJobs returns empty when no dead jobs")
    void testListDeadJobsEmpty() {
        jobService.enqueue(new Job("j1", "echo alive"));
        assertTrue(dlqService.listDeadJobs().isEmpty());
    }

    @Test
    @DisplayName("retryJob resets DEAD job to PENDING")
    void testRetryJob() {
        Job job = new Job("j1", "echo retry");
        jobService.enqueue(job);
        job.setState(JobState.DEAD);
        job.setAttempts(3);
        job.setError("Failed 3 times");
        jobRepo.update(job);

        dlqService.retryJob("j1");

        Job retried = jobRepo.findById("j1").orElseThrow();
        assertEquals(JobState.PENDING, retried.getState());
        assertEquals(0, retried.getAttempts());
        assertNull(retried.getLockedBy());
    }

    @Test
    @DisplayName("retryJob throws for non-DEAD job")
    void testRetryNonDeadJob() {
        jobService.enqueue(new Job("j1", "echo alive"));
        assertThrows(IllegalArgumentException.class, () -> dlqService.retryJob("j1"));
    }

    @Test
    @DisplayName("retryJob throws for missing job")
    void testRetryMissingJob() {
        assertThrows(IllegalArgumentException.class, () -> dlqService.retryJob("nonexistent"));
    }

    @Test
    @DisplayName("countDeadJobs returns correct count")
    void testCountDeadJobs() {
        assertEquals(0, dlqService.countDeadJobs());

        Job job = new Job("j1", "echo dead");
        jobService.enqueue(job);
        job.setState(JobState.DEAD);
        jobRepo.update(job);

        assertEquals(1, dlqService.countDeadJobs());
    }
}

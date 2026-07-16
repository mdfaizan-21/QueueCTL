package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteJobRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JobService.
 */
class JobServiceTest {

    @TempDir
    Path tempDir;

    private JobService jobService;
    private SqliteJobRepository jobRepo;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        jobRepo = new SqliteJobRepository(factory);
        jobService = new JobService(jobRepo);
    }

    @Test
    @DisplayName("enqueue saves a valid job")
    void testEnqueue() {
        Job job = new Job("j1", "echo hello");
        jobService.enqueue(job);

        assertTrue(jobRepo.findById("j1").isPresent());
        assertEquals(JobState.PENDING, jobRepo.findById("j1").get().getState());
    }

    @Test
    @DisplayName("enqueue applies default priority MEDIUM")
    void testEnqueueDefaultPriority() {
        Job job = new Job("j1", "echo hello");
        job.setPriority(null);
        jobService.enqueue(job);

        assertEquals(Priority.MEDIUM, jobRepo.findById("j1").get().getPriority());
    }

    @Test
    @DisplayName("enqueue rejects blank command")
    void testEnqueueBlankCommand() {
        Job job = new Job("j1", "");
        assertThrows(IllegalArgumentException.class, () -> jobService.enqueue(job));
    }

    @Test
    @DisplayName("enqueue rejects blank ID")
    void testEnqueueBlankId() {
        Job job = new Job("", "echo hello");
        assertThrows(IllegalArgumentException.class, () -> jobService.enqueue(job));
    }

    @Test
    @DisplayName("enqueue rejects negative maxRetries")
    void testEnqueueNegativeRetries() {
        Job job = new Job("j1", "echo hello");
        job.setMaxRetries(-1);
        assertThrows(IllegalArgumentException.class, () -> jobService.enqueue(job));
    }

    @Test
    @DisplayName("enqueue rejects zero timeout")
    void testEnqueueZeroTimeout() {
        Job job = new Job("j1", "echo hello");
        job.setTimeout(0);
        assertThrows(IllegalArgumentException.class, () -> jobService.enqueue(job));
    }

    @Test
    @DisplayName("listJobs with null state returns all")
    void testListJobsAll() {
        jobService.enqueue(new Job("j1", "echo 1"));
        jobService.enqueue(new Job("j2", "echo 2"));
        assertEquals(2, jobService.listJobs(null).size());
    }

    @Test
    @DisplayName("listJobs with state filter returns matching")
    void testListJobsFiltered() {
        jobService.enqueue(new Job("j1", "echo 1"));
        List<Job> pending = jobService.listJobs(JobState.PENDING);
        assertEquals(1, pending.size());
        assertEquals(0, jobService.listJobs(JobState.COMPLETED).size());
    }

    @Test
    @DisplayName("getStateCounts returns correct counts")
    void testStateCounts() {
        jobService.enqueue(new Job("j1", "echo 1"));
        jobService.enqueue(new Job("j2", "echo 2"));
        int[] counts = jobService.getStateCounts();
        assertEquals(2, counts[0]); // pending
        assertEquals(0, counts[1]); // processing
    }

    @Test
    @DisplayName("totalJobs returns count")
    void testTotalJobs() {
        assertEquals(0, jobService.totalJobs());
        jobService.enqueue(new Job("j1", "echo 1"));
        assertEquals(1, jobService.totalJobs());
    }
}

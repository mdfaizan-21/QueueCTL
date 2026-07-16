package com.queuectl.repository;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import com.queuectl.exception.PersistenceException;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SqliteJobRepository covering:
 * - CRUD operations
 * - Atomic fetch-and-lock (duplicate prevention)
 * - Priority ordering
 * - Delayed job support
 * - Stalled job recovery
 * - Persistence across connections
 */
class SqliteJobRepositoryTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;
    private SqliteJobRepository repo;

    @BeforeEach
    void setUp() {
        factory = new SqliteConnectionFactory(tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        repo = new SqliteJobRepository(factory);
    }

    // ── Save & Find ──

    @Test
    @DisplayName("save and findById round-trip")
    void testSaveAndFind() {
        Job job = new Job("j1", "echo hello");
        repo.save(job);

        Optional<Job> found = repo.findById("j1");
        assertTrue(found.isPresent());
        assertEquals("j1", found.get().getId());
        assertEquals("echo hello", found.get().getCommand());
        assertEquals(JobState.PENDING, found.get().getState());
    }

    @Test
    @DisplayName("save rejects duplicate job ID")
    void testSaveDuplicate() {
        Job job1 = new Job("j1", "echo hello");
        repo.save(job1);

        Job job2 = new Job("j1", "echo world");
        assertThrows(PersistenceException.class, () -> repo.save(job2));
    }

    @Test
    @DisplayName("findById returns empty for missing job")
    void testFindByIdMissing() {
        assertTrue(repo.findById("nonexistent").isEmpty());
    }

    // ── Find by State ──

    @Test
    @DisplayName("findByState returns only matching jobs")
    void testFindByState() {
        Job j1 = new Job("j1", "echo 1");
        Job j2 = new Job("j2", "echo 2");
        j2.setState(JobState.COMPLETED);
        j2.setCreatedAt(j2.getCreatedAt());
        j2.setUpdatedAt(j2.getUpdatedAt());

        repo.save(j1);
        repo.save(j2);

        // Need to update j2's state in DB
        repo.update(j2);

        List<Job> pending = repo.findByState(JobState.PENDING);
        assertEquals(1, pending.size());
        assertEquals("j1", pending.get(0).getId());
    }

    @Test
    @DisplayName("findAll returns all jobs")
    void testFindAll() {
        repo.save(new Job("j1", "echo 1"));
        repo.save(new Job("j2", "echo 2"));
        repo.save(new Job("j3", "echo 3"));

        List<Job> all = repo.findAll();
        assertEquals(3, all.size());
    }

    // ── Atomic Fetch and Lock ──

    @Test
    @DisplayName("fetchAndLock returns PENDING job and sets PROCESSING")
    void testFetchAndLock() {
        repo.save(new Job("j1", "echo hello"));

        Optional<Job> locked = repo.fetchAndLock("worker-1");
        assertTrue(locked.isPresent());
        assertEquals("j1", locked.get().getId());
        assertEquals(JobState.PROCESSING, locked.get().getState());
        assertEquals("worker-1", locked.get().getLockedBy());
        assertNotNull(locked.get().getLockedAt());
    }

    @Test
    @DisplayName("fetchAndLock returns empty when no jobs available")
    void testFetchAndLockEmpty() {
        Optional<Job> locked = repo.fetchAndLock("worker-1");
        assertTrue(locked.isEmpty());
    }

    @Test
    @DisplayName("fetchAndLock does not return already locked jobs")
    void testFetchAndLockNoDoubleProcessing() {
        repo.save(new Job("j1", "echo hello"));

        // First worker grabs it
        Optional<Job> first = repo.fetchAndLock("worker-1");
        assertTrue(first.isPresent());

        // Second worker finds nothing
        Optional<Job> second = repo.fetchAndLock("worker-2");
        assertTrue(second.isEmpty());
    }

    @Test
    @DisplayName("fetchAndLock respects priority ordering (HIGH first)")
    void testFetchAndLockPriorityOrder() {
        Job low = new Job("j-low", "echo low");
        low.setPriority(Priority.LOW);
        repo.save(low);

        Job high = new Job("j-high", "echo high");
        high.setPriority(Priority.HIGH);
        repo.save(high);

        Job medium = new Job("j-med", "echo medium");
        medium.setPriority(Priority.MEDIUM);
        repo.save(medium);

        // Should pick HIGH first
        Optional<Job> first = repo.fetchAndLock("w1");
        assertTrue(first.isPresent());
        assertEquals("j-high", first.get().getId());

        // Then MEDIUM
        Optional<Job> second = repo.fetchAndLock("w2");
        assertTrue(second.isPresent());
        assertEquals("j-med", second.get().getId());

        // Then LOW
        Optional<Job> third = repo.fetchAndLock("w3");
        assertTrue(third.isPresent());
        assertEquals("j-low", third.get().getId());
    }

    @Test
    @DisplayName("fetchAndLock skips jobs with future next_retry_at")
    void testFetchAndLockSkipsFutureRetry() {
        Job job = new Job("j1", "echo hello");
        job.setNextRetryAt("2099-12-31T23:59:59Z");
        repo.save(job);

        Optional<Job> locked = repo.fetchAndLock("worker-1");
        assertTrue(locked.isEmpty(), "Should skip job with future retry time");
    }

    @Test
    @DisplayName("fetchAndLock skips delayed jobs with future run_at")
    void testFetchAndLockSkipsDelayedJobs() {
        Job job = new Job("j1", "echo hello");
        job.setRunAt("2099-12-31T23:59:59Z");
        repo.save(job);

        Optional<Job> locked = repo.fetchAndLock("worker-1");
        assertTrue(locked.isEmpty(), "Should skip job with future run_at");
    }

    @Test
    @DisplayName("fetchAndLock picks jobs with past next_retry_at")
    void testFetchAndLockPicksPastRetry() {
        Job job = new Job("j1", "echo hello");
        job.setNextRetryAt("2000-01-01T00:00:00Z");
        repo.save(job);

        Optional<Job> locked = repo.fetchAndLock("worker-1");
        assertTrue(locked.isPresent(), "Should pick job with past retry time");
    }

    // ── Update ──

    @Test
    @DisplayName("update modifies job fields")
    void testUpdate() {
        Job job = new Job("j1", "echo hello");
        repo.save(job);

        job.setState(JobState.COMPLETED);
        job.setOutput("hello\n");
        job.setAttempts(1);
        repo.update(job);

        Job updated = repo.findById("j1").orElseThrow();
        assertEquals(JobState.COMPLETED, updated.getState());
        assertEquals("hello\n", updated.getOutput());
        assertEquals(1, updated.getAttempts());
    }

    // ── Count & Delete ──

    @Test
    @DisplayName("countByState returns correct counts")
    void testCountByState() {
        repo.save(new Job("j1", "echo 1"));
        repo.save(new Job("j2", "echo 2"));
        assertEquals(2, repo.countByState(JobState.PENDING));
        assertEquals(0, repo.countByState(JobState.COMPLETED));
    }

    @Test
    @DisplayName("countAll returns total job count")
    void testCountAll() {
        assertEquals(0, repo.countAll());
        repo.save(new Job("j1", "echo 1"));
        repo.save(new Job("j2", "echo 2"));
        assertEquals(2, repo.countAll());
    }

    @Test
    @DisplayName("delete removes a job")
    void testDelete() {
        repo.save(new Job("j1", "echo hello"));
        assertTrue(repo.delete("j1"));
        assertTrue(repo.findById("j1").isEmpty());
    }

    @Test
    @DisplayName("delete returns false for missing job")
    void testDeleteMissing() {
        assertFalse(repo.delete("nonexistent"));
    }

    // ── Crash Recovery ──

    @Test
    @DisplayName("resetStalledJobs moves PROCESSING back to PENDING")
    void testResetStalledJobs() {
        Job job = new Job("j1", "echo hello");
        repo.save(job);

        // Simulate worker crash: lock the job then "crash"
        repo.fetchAndLock("crashed-worker");
        assertEquals(JobState.PROCESSING, repo.findById("j1").get().getState());

        // Reset
        int count = repo.resetStalledJobs();
        assertEquals(1, count);

        // Verify back to PENDING with cleared lock
        Job reset = repo.findById("j1").orElseThrow();
        assertEquals(JobState.PENDING, reset.getState());
        assertNull(reset.getLockedBy());
        assertNull(reset.getLockedAt());
    }

    // ── Persistence ──

    @Test
    @DisplayName("Jobs survive across separate repository instances (persistence)")
    void testPersistenceAcrossInstances() {
        // Save with first repo instance
        repo.save(new Job("j1", "echo persist"));

        // Create new repo instance pointing to same DB
        SqliteJobRepository repo2 = new SqliteJobRepository(factory);
        Optional<Job> found = repo2.findById("j1");
        assertTrue(found.isPresent(), "Job should survive across repository instances");
        assertEquals("echo persist", found.get().getCommand());
    }
}

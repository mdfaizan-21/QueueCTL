package com.queuectl.repository;

import com.queuectl.domain.WorkerInfo;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SqliteWorkerRepository.
 */
class SqliteWorkerRepositoryTest {

    @TempDir
    Path tempDir;

    private SqliteWorkerRepository repo;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        repo = new SqliteWorkerRepository(factory);
    }

    @Test
    @DisplayName("save and findById round-trip")
    void testSaveAndFind() {
        WorkerInfo worker = new WorkerInfo("w1");
        repo.save(worker);

        Optional<WorkerInfo> found = repo.findById("w1");
        assertTrue(found.isPresent());
        assertEquals("w1", found.get().getId());
        assertEquals("RUNNING", found.get().getStatus());
    }

    @Test
    @DisplayName("save with same ID replaces existing")
    void testSaveReplaces() {
        WorkerInfo w1 = new WorkerInfo("w1");
        repo.save(w1);

        WorkerInfo w1Updated = new WorkerInfo("w1");
        w1Updated.setStatus("STOPPED");
        repo.save(w1Updated);

        assertEquals("STOPPED", repo.findById("w1").get().getStatus());
    }

    @Test
    @DisplayName("findAll returns all workers")
    void testFindAll() {
        repo.save(new WorkerInfo("w1"));
        repo.save(new WorkerInfo("w2"));
        repo.save(new WorkerInfo("w3"));

        List<WorkerInfo> all = repo.findAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("updateHeartbeat changes heartbeat timestamp")
    void testUpdateHeartbeat() {
        repo.save(new WorkerInfo("w1"));
        repo.updateHeartbeat("w1", "2099-01-01T00:00:00Z");

        WorkerInfo updated = repo.findById("w1").orElseThrow();
        assertEquals("2099-01-01T00:00:00Z", updated.getHeartbeat());
    }

    @Test
    @DisplayName("updateStatus changes worker status")
    void testUpdateStatus() {
        repo.save(new WorkerInfo("w1"));
        repo.updateStatus("w1", "STOPPED");

        WorkerInfo updated = repo.findById("w1").orElseThrow();
        assertEquals("STOPPED", updated.getStatus());
    }

    @Test
    @DisplayName("delete removes a worker")
    void testDelete() {
        repo.save(new WorkerInfo("w1"));
        assertTrue(repo.delete("w1"));
        assertTrue(repo.findById("w1").isEmpty());
    }

    @Test
    @DisplayName("delete returns false for missing worker")
    void testDeleteMissing() {
        assertFalse(repo.delete("nonexistent"));
    }

    @Test
    @DisplayName("deleteAll removes all workers")
    void testDeleteAll() {
        repo.save(new WorkerInfo("w1"));
        repo.save(new WorkerInfo("w2"));
        assertEquals(2, repo.deleteAll());
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    @DisplayName("countActive counts RUNNING workers")
    void testCountActive() {
        repo.save(new WorkerInfo("w1"));
        repo.save(new WorkerInfo("w2"));
        repo.save(new WorkerInfo("w3"));
        repo.updateStatus("w3", "STOPPED");

        assertEquals(2, repo.countActive());
    }
}

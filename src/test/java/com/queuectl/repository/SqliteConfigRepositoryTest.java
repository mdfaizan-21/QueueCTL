package com.queuectl.repository;

import com.queuectl.domain.Config;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SqliteConfigRepository.
 */
class SqliteConfigRepositoryTest {

    @TempDir
    Path tempDir;

    private SqliteConfigRepository repo;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        repo = new SqliteConfigRepository(factory);
    }

    @Test
    @DisplayName("Default config values are present after migration")
    void testDefaultsPresent() {
        Optional<Config> maxRetries = repo.findByKey("max-retries");
        assertTrue(maxRetries.isPresent());
        assertEquals("3", maxRetries.get().getValue());

        Optional<Config> backoff = repo.findByKey("backoff-base");
        assertTrue(backoff.isPresent());
        assertEquals("2", backoff.get().getValue());
    }

    @Test
    @DisplayName("set creates new config entry")
    void testSetNew() {
        repo.set("custom-key", "custom-value");
        Optional<Config> found = repo.findByKey("custom-key");
        assertTrue(found.isPresent());
        assertEquals("custom-value", found.get().getValue());
    }

    @Test
    @DisplayName("set updates existing config entry")
    void testSetUpdate() {
        repo.set("max-retries", "5");
        assertEquals("5", repo.findByKey("max-retries").get().getValue());
    }

    @Test
    @DisplayName("findByKey returns empty for missing key")
    void testFindByKeyMissing() {
        assertTrue(repo.findByKey("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("findAll returns all config entries")
    void testFindAll() {
        List<Config> configs = repo.findAll();
        assertTrue(configs.size() >= 5, "Should have at least 5 default configs");
    }

    @Test
    @DisplayName("getOrDefault returns value when key exists")
    void testGetOrDefaultExists() {
        assertEquals("3", repo.getOrDefault("max-retries", "10"));
    }

    @Test
    @DisplayName("getOrDefault returns default when key is missing")
    void testGetOrDefaultMissing() {
        assertEquals("fallback", repo.getOrDefault("nonexistent", "fallback"));
    }
}

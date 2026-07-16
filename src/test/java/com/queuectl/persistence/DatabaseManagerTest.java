package com.queuectl.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DatabaseManager using temp file-based databases.
 */
class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SqliteConnectionFactory(
                tempDir.resolve("dbmgr_test.db").toString());
    }

    @Test
    @DisplayName("initialize creates all tables")
    void testInitialize() throws SQLException {
        DatabaseManager dbManager = new DatabaseManager(factory);
        dbManager.initialize();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                     "AND name IN ('jobs', 'workers', 'config', 'metrics')")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1), "All 4 tables should exist");
        }
    }

    @Test
    @DisplayName("initialize is idempotent")
    void testInitializeIdempotent() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        assertDoesNotThrow(dbManager::initialize);
        assertDoesNotThrow(dbManager::initialize);
        assertTrue(dbManager.isInitialized());
    }

    @Test
    @DisplayName("isHealthy returns true after initialization")
    void testIsHealthy() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        dbManager.initialize();
        assertTrue(dbManager.isHealthy());
    }

    @Test
    @DisplayName("isHealthy returns true even before initialization")
    void testIsHealthyBeforeInit() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        assertTrue(dbManager.isHealthy());
    }

    @Test
    @DisplayName("isInitialized returns false before initialize")
    void testIsInitializedBefore() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        assertFalse(dbManager.isInitialized());
    }

    @Test
    @DisplayName("getTableRowCount returns correct counts")
    void testGetTableRowCount() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        dbManager.initialize();

        assertEquals(5, dbManager.getTableRowCount("config"));
        assertEquals(0, dbManager.getTableRowCount("jobs"));
        assertEquals(0, dbManager.getTableRowCount("workers"));
        assertEquals(4, dbManager.getTableRowCount("metrics"));
    }

    @Test
    @DisplayName("getTableRowCount returns -1 for non-existent table")
    void testGetTableRowCountInvalidTable() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        dbManager.initialize();
        assertEquals(-1, dbManager.getTableRowCount("nonexistent"));
    }

    @Test
    @DisplayName("getConnectionFactory returns the factory")
    void testGetConnectionFactory() {
        DatabaseManager dbManager = new DatabaseManager(factory);
        assertSame(factory, dbManager.getConnectionFactory());
    }

    @Test
    @DisplayName("Data can be inserted and queried after initialization")
    void testDataOperationsAfterInit() throws SQLException {
        DatabaseManager dbManager = new DatabaseManager(factory);
        dbManager.initialize();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "INSERT INTO jobs (id, command, state, created_at, updated_at) " +
                "VALUES ('j1', 'echo hello', 'PENDING', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");

            try (ResultSet rs = stmt.executeQuery("SELECT command FROM jobs WHERE id='j1'")) {
                assertTrue(rs.next());
                assertEquals("echo hello", rs.getString(1));
            }
        }

        assertEquals(1, dbManager.getTableRowCount("jobs"));
    }
}

package com.queuectl.persistence;

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
 * Tests for SqliteConnectionFactory using temp file-based databases.
 */
class SqliteConnectionFactoryTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory createFactory(String name) {
        return new SqliteConnectionFactory(tempDir.resolve(name + ".db").toString());
    }

    @Test
    @DisplayName("Connection can be created and used")
    void testConnection() throws SQLException {
        SqliteConnectionFactory factory = createFactory("test_conn");
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("WAL mode is enabled on connection")
    void testWalMode() throws SQLException {
        SqliteConnectionFactory factory = createFactory("test_wal");
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1));
        }
    }

    @Test
    @DisplayName("Foreign keys are enabled")
    void testForeignKeysEnabled() throws SQLException {
        SqliteConnectionFactory factory = createFactory("test_fk");
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Foreign keys should be enabled");
        }
    }

    @Test
    @DisplayName("Data persists across separate connections")
    void testPersistenceAcrossConnections() throws SQLException {
        SqliteConnectionFactory factory = createFactory("test_persist");

        // Create table and insert on conn1
        try (Connection conn1 = factory.getConnection();
             Statement stmt = conn1.createStatement()) {
            stmt.execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY)");
            stmt.execute("INSERT INTO test_table (id) VALUES (42)");
        }

        // Query on conn2 — should see same data (file-based persistence)
        try (Connection conn2 = factory.getConnection();
             Statement stmt = conn2.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM test_table")) {
            assertTrue(rs.next(), "conn2 should see data written by conn1");
            assertEquals(42, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("getJdbcUrl returns the configured URL")
    void testGetJdbcUrl() {
        SqliteConnectionFactory factory = createFactory("test_url");
        assertTrue(factory.getJdbcUrl().contains("test_url.db"));
    }
}

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
 * Tests for MigrationRunner using temp file-based databases.
 */
class MigrationRunnerTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SqliteConnectionFactory(
                tempDir.resolve("migration_test.db").toString());
    }

    @Test
    @DisplayName("Migrations create the jobs table")
    void testJobsTableCreated() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='jobs'")) {
            assertTrue(rs.next(), "jobs table should exist");
        }
    }

    @Test
    @DisplayName("Migrations create the workers table")
    void testWorkersTableCreated() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='workers'")) {
            assertTrue(rs.next(), "workers table should exist");
        }
    }

    @Test
    @DisplayName("Migrations create the config table with defaults")
    void testConfigTableWithDefaults() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM config")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1), "Should have 5 default config entries");
        }
    }

    @Test
    @DisplayName("Migrations create the metrics table with defaults")
    void testMetricsTableWithDefaults() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM metrics")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1), "Should have 4 default metric entries");
        }
    }

    @Test
    @DisplayName("Migrations are idempotent — running twice is safe")
    void testIdempotent() {
        MigrationRunner runner = new MigrationRunner(factory);
        assertDoesNotThrow(runner::runMigrations, "First run should succeed");
        assertDoesNotThrow(runner::runMigrations, "Second run should also succeed");
    }

    @Test
    @DisplayName("Jobs table has correct columns")
    void testJobsTableColumns() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(jobs)")) {
            int columnCount = 0;
            while (rs.next()) {
                columnCount++;
            }
            assertEquals(15, columnCount, "jobs table should have 15 columns");
        }
    }

    @Test
    @DisplayName("Default config values are correct")
    void testDefaultConfigValues() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT value FROM config WHERE key='max-retries'")) {
                assertTrue(rs.next());
                assertEquals("3", rs.getString(1));
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT value FROM config WHERE key='backoff-base'")) {
                assertTrue(rs.next());
                assertEquals("2", rs.getString(1));
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT value FROM config WHERE key='poll-interval'")) {
                assertTrue(rs.next());
                assertEquals("1000", rs.getString(1));
            }
        }
    }

    @Test
    @DisplayName("Indexes are created for efficient polling")
    void testIndexesCreated() throws SQLException {
        MigrationRunner runner = new MigrationRunner(factory);
        runner.runMigrations();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND tbl_name='jobs'")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 2, "Should have at least 2 indexes on jobs table");
        }
    }

    @Test
    @DisplayName("getMigrationCount returns expected count")
    void testMigrationCount() {
        MigrationRunner runner = new MigrationRunner(factory);
        assertTrue(runner.getMigrationCount() > 0, "Should have at least 1 migration");
    }
}

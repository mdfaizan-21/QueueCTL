package com.queuectl.persistence;

import com.queuectl.exception.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Executes database schema migrations in order.
 *
 * Migrations are idempotent DDL statements executed sequentially.
 * Each migration uses "CREATE TABLE IF NOT EXISTS" for safety on re-runs.
 *
 * Tables created:
 * - **jobs** — background job queue with all lifecycle fields
 * - **workers** — registered worker instances with heartbeat
 * - **config** — key-value runtime configuration
 * - **metrics** — key-value metrics counters
 */
public class MigrationRunner {

    private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);

    /**
     * Ordered list of DDL migrations to execute.
     */
    private static final List<String> MIGRATIONS = List.of(
        // ─── Jobs table ───
        """
        CREATE TABLE IF NOT EXISTS jobs (
            id          TEXT PRIMARY KEY,
            command     TEXT NOT NULL,
            state       TEXT NOT NULL DEFAULT 'PENDING',
            attempts    INTEGER DEFAULT 0,
            max_retries INTEGER DEFAULT 3,
            priority    TEXT DEFAULT 'MEDIUM',
            timeout     INTEGER DEFAULT 60,
            run_at      TEXT,
            next_retry_at TEXT,
            locked_by   TEXT,
            locked_at   TEXT,
            output      TEXT DEFAULT '',
            error       TEXT DEFAULT '',
            created_at  TEXT NOT NULL,
            updated_at  TEXT NOT NULL
        )
        """,

        // ─── Index for efficient job polling ───
        """
        CREATE INDEX IF NOT EXISTS idx_jobs_state_priority
        ON jobs (state, priority DESC, created_at ASC)
        """,

        // ─── Index for retry scheduling ───
        """
        CREATE INDEX IF NOT EXISTS idx_jobs_next_retry
        ON jobs (state, next_retry_at)
        """,

        // ─── Workers table ───
        """
        CREATE TABLE IF NOT EXISTS workers (
            id         TEXT PRIMARY KEY,
            status     TEXT DEFAULT 'RUNNING',
            heartbeat  TEXT,
            started_at TEXT
        )
        """,

        // ─── Config table ───
        """
        CREATE TABLE IF NOT EXISTS config (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """,

        // ─── Metrics table ───
        """
        CREATE TABLE IF NOT EXISTS metrics (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL DEFAULT '0'
        )
        """,

        // ─── Default config values (INSERT OR IGNORE to preserve existing) ───
        "INSERT OR IGNORE INTO config (key, value) VALUES ('max-retries', '3')",
        "INSERT OR IGNORE INTO config (key, value) VALUES ('backoff-base', '2')",
        "INSERT OR IGNORE INTO config (key, value) VALUES ('poll-interval', '1000')",
        "INSERT OR IGNORE INTO config (key, value) VALUES ('job-timeout', '60')",
        "INSERT OR IGNORE INTO config (key, value) VALUES ('worker-heartbeat', '5')",

        // ─── Default metric counters ───
        "INSERT OR IGNORE INTO metrics (key, value) VALUES ('jobs_processed', '0')",
        "INSERT OR IGNORE INTO metrics (key, value) VALUES ('jobs_failed', '0')",
        "INSERT OR IGNORE INTO metrics (key, value) VALUES ('jobs_retried', '0')",
        "INSERT OR IGNORE INTO metrics (key, value) VALUES ('total_execution_time_ms', '0')"
    );

    private final SqliteConnectionFactory connectionFactory;

    /**
     * Creates a migration runner.
     *
     * @param connectionFactory factory for obtaining database connections
     */
    public MigrationRunner(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Executes all migrations in a single transaction.
     *
     * If any migration fails, the entire transaction is rolled back.
     *
     * @throws PersistenceException if migration fails
     */
    public void runMigrations() {
        logger.info("Running database migrations ({} statements)...", MIGRATIONS.size());

        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < MIGRATIONS.size(); i++) {
                    String sql = MIGRATIONS.get(i);
                    logger.debug("Executing migration {}/{}", i + 1, MIGRATIONS.size());
                    stmt.execute(sql);
                }
                conn.commit();
                logger.info("Database migrations completed successfully.");
            } catch (SQLException e) {
                conn.rollback();
                throw new PersistenceException("Migration failed, rolled back", e);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to run migrations", e);
        }
    }

    /**
     * Returns the number of migrations.
     *
     * @return migration count
     */
    public int getMigrationCount() {
        return MIGRATIONS.size();
    }
}

package com.queuectl.persistence;

import com.queuectl.exception.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Central manager for database lifecycle: initialization, migrations, and health checks.
 *
 * Usage:
 * <pre>
 *   SqliteConnectionFactory factory = new SqliteConnectionFactory("queuectl.db");
 *   DatabaseManager dbManager = new DatabaseManager(factory);
 *   dbManager.initialize();
 *   // Database is now ready for use
 * </pre>
 *
 * The manager ensures:
 * - Database file is created if it doesn't exist
 * - Schema migrations are applied
 * - Connection health is verifiable
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final SqliteConnectionFactory connectionFactory;
    private final MigrationRunner migrationRunner;
    private boolean initialized = false;

    /**
     * Creates a DatabaseManager.
     *
     * @param connectionFactory factory for obtaining database connections
     */
    public DatabaseManager(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        this.migrationRunner = new MigrationRunner(connectionFactory);
    }

    /**
     * Initializes the database by running all migrations.
     * Safe to call multiple times (idempotent).
     *
     * @throws PersistenceException if initialization fails
     */
    public void initialize() {
        if (initialized) {
            logger.debug("Database already initialized, skipping.");
            return;
        }

        logger.info("Initializing database...");
        migrationRunner.runMigrations();
        initialized = true;
        logger.info("Database initialization complete.");
    }

    /**
     * Checks if the database connection is healthy.
     *
     * @return true if a connection can be established and queried
     */
    public boolean isHealthy() {
        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            logger.error("Database health check failed", e);
            return false;
        }
    }

    /**
     * Returns the connection factory used by this manager.
     *
     * @return the connection factory
     */
    public SqliteConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * Returns whether the database has been initialized.
     *
     * @return true if initialize() has been called successfully
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns table row counts for diagnostics.
     *
     * @param tableName the table to count
     * @return row count, or -1 if the query fails
     */
    public int getTableRowCount(String tableName) {
        // Sanitize table name to prevent SQL injection
        String sanitized = tableName.replaceAll("[^a-zA-Z_]", "");
        String sql = "SELECT COUNT(*) FROM " + sanitized;

        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.warn("Failed to count rows in table '{}': {}", tableName, e.getMessage());
            return -1;
        }
    }
}

package com.queuectl.persistence;

import com.queuectl.exception.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Thread-safe factory for SQLite JDBC connections.
 *
 * <p>Configures connections with:
 * <ul>
 *   <li>WAL (Write-Ahead Logging) mode for concurrent read/write</li>
 *   <li>Busy timeout to handle lock contention</li>
 *   <li>Foreign key enforcement</li>
 * </ul>
 *
 * <p>Each call to {@link #getConnection()} returns a new connection.
 * Callers are responsible for closing connections after use.
 */
public class SqliteConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(SqliteConnectionFactory.class);

    private static final int BUSY_TIMEOUT_MS = 5000;

    private final String jdbcUrl;

    /**
     * Creates a factory for the given database file path.
     *
     * @param dbPath path to the SQLite database file (e.g., "queuectl.db")
     */
    public SqliteConnectionFactory(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        logger.info("SQLite connection factory initialized: {}", dbPath);
    }

    /**
     * Creates a factory with a pre-built JDBC URL.
     * Useful for in-memory databases (e.g., "jdbc:sqlite::memory:").
     *
     * @param jdbcUrl full JDBC URL
     * @param isRawUrl ignored, used to disambiguate from path constructor
     */
    public SqliteConnectionFactory(String jdbcUrl, boolean isRawUrl) {
        this.jdbcUrl = jdbcUrl;
        logger.info("SQLite connection factory initialized with URL: {}", jdbcUrl);
    }

    /**
     * Returns a new configured SQLite connection.
     *
     * <p>The connection has WAL mode, busy timeout, and foreign keys enabled.
     * Caller is responsible for closing the connection.
     *
     * @return a new JDBC Connection
     * @throws PersistenceException if connection cannot be established
     */
    public Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(jdbcUrl);
            configureConnection(conn);
            return conn;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to connect to SQLite: " + jdbcUrl, e);
        }
    }

    /**
     * Applies performance and safety pragmas to the connection.
     */
    private void configureConnection(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // WAL mode enables concurrent readers with a single writer
            stmt.execute("PRAGMA journal_mode=WAL");
            // Wait up to 5 seconds for locks to clear
            stmt.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
            // Enforce foreign key constraints
            stmt.execute("PRAGMA foreign_keys=ON");
            // Synchronous NORMAL for a balance of safety and speed
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        logger.debug("SQLite connection configured: WAL, busy_timeout={}, FK=ON", BUSY_TIMEOUT_MS);
    }

    /**
     * Returns the JDBC URL used by this factory.
     *
     * @return the JDBC URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }
}

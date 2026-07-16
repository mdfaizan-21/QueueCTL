package com.queuectl.repository;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import com.queuectl.exception.PersistenceException;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of {@link JobRepository}.
 *
 * Key design decisions:
 * - Uses optimistic locking via atomic UPDATE for {@link #fetchAndLock}
 * - Priority ordering uses CASE expression mapping HIGH/MEDIUM/LOW to 3/2/1
 * - All timestamps stored as ISO-8601 UTC strings
 * - Each method obtains and releases its own connection (no shared state)
 */
public class SqliteJobRepository implements JobRepository {

    private static final Logger logger = LoggerFactory.getLogger(SqliteJobRepository.class);

    private final SqliteConnectionFactory connectionFactory;

    public SqliteJobRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void save(Job job) {
        String sql = """
            INSERT INTO jobs (id, command, state, attempts, max_retries, priority,
                              timeout, run_at, next_retry_at, locked_by, locked_at,
                              output, error, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setJobParameters(ps, job);
            ps.executeUpdate();
            logger.info("Job saved: id={}, command='{}'", job.getId(), job.getCommand());

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                throw new PersistenceException("Job with ID '" + job.getId() + "' already exists", e);
            }
            throw new PersistenceException("Failed to save job: " + job.getId(), e);
        }
    }

    @Override
    public Optional<Job> findById(String id) {
        String sql = "SELECT * FROM jobs WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToJob(rs));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find job: " + id, e);
        }
    }

    @Override
    public List<Job> findByState(JobState state) {
        String sql = """
            SELECT * FROM jobs WHERE state = ?
            ORDER BY
                CASE priority
                    WHEN 'HIGH' THEN 3
                    WHEN 'MEDIUM' THEN 2
                    WHEN 'LOW' THEN 1
                    ELSE 2
                END DESC,
                created_at ASC
            """;

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, state.name());
            return executeJobListQuery(ps);

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find jobs by state: " + state, e);
        }
    }

    @Override
    public List<Job> findAll() {
        String sql = "SELECT * FROM jobs ORDER BY created_at DESC";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            return executeJobListQuery(ps);

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find all jobs", e);
        }
    }

    /**
     * Atomically fetches the next eligible PENDING job and locks it.
     *
     * This is the core of the duplicate-prevention strategy. The SQL:
     * <ol>
     * - Selects the highest-priority, oldest eligible PENDING job
     * - Updates it to PROCESSING with the worker's lock in a single statement
     * - Returns the updated row
     * </ol>
     *
     * Eligibility: state=PENDING AND (next_retry_at IS NULL OR next_retry_at &lt;= now)
     * AND (run_at IS NULL OR run_at &lt;= now).
     */
    @Override
    public Optional<Job> fetchAndLock(String workerId) {
        String now = TimeUtil.now();

        // Step 1: Find the next eligible job ID (under transaction)
        String selectSql = """
            SELECT id FROM jobs
            WHERE state = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= ?)
              AND (run_at IS NULL OR run_at <= ?)
            ORDER BY
                CASE priority
                    WHEN 'HIGH' THEN 3
                    WHEN 'MEDIUM' THEN 2
                    WHEN 'LOW' THEN 1
                    ELSE 2
                END DESC,
                created_at ASC
            LIMIT 1
            """;

        // Step 2: Atomically update the selected job
        String updateSql = """
            UPDATE jobs
            SET state = 'PROCESSING',
                locked_by = ?,
                locked_at = ?,
                updated_at = ?
            WHERE id = ?
              AND state = 'PENDING'
            """;

        String fetchSql = "SELECT * FROM jobs WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find eligible job
                String jobId = null;
                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, now);
                    selectPs.setString(2, now);
                    try (ResultSet rs = selectPs.executeQuery()) {
                        if (rs.next()) {
                            jobId = rs.getString("id");
                        }
                    }
                }

                if (jobId == null) {
                    conn.rollback();
                    return Optional.empty();
                }

                // Atomically lock it (the WHERE state='PENDING' guard prevents races)
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setString(1, workerId);
                    updatePs.setString(2, now);
                    updatePs.setString(3, now);
                    updatePs.setString(4, jobId);
                    int updated = updatePs.executeUpdate();

                    if (updated == 0) {
                        // Another worker grabbed it between SELECT and UPDATE
                        conn.rollback();
                        logger.debug("Job {} was grabbed by another worker", jobId);
                        return Optional.empty();
                    }
                }

                // Fetch the locked job
                Job lockedJob = null;
                try (PreparedStatement fetchPs = conn.prepareStatement(fetchSql)) {
                    fetchPs.setString(1, jobId);
                    try (ResultSet rs = fetchPs.executeQuery()) {
                        if (rs.next()) {
                            lockedJob = mapResultSetToJob(rs);
                        }
                    }
                }

                conn.commit();
                logger.debug("Job {} locked by worker {}", jobId, workerId);
                return Optional.ofNullable(lockedJob);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to fetch and lock job for worker: " + workerId, e);
        }
    }

    @Override
    public void update(Job job) {
        String sql = """
            UPDATE jobs SET
                command = ?, state = ?, attempts = ?, max_retries = ?,
                priority = ?, timeout = ?, run_at = ?, next_retry_at = ?,
                locked_by = ?, locked_at = ?, output = ?, error = ?,
                updated_at = ?
            WHERE id = ?
            """;

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, job.getCommand());
            ps.setString(2, job.getState().name());
            ps.setInt(3, job.getAttempts());
            ps.setInt(4, job.getMaxRetries());
            ps.setString(5, job.getPriority() != null ? job.getPriority().name() : "MEDIUM");
            ps.setInt(6, job.getTimeout());
            ps.setString(7, job.getRunAt());
            ps.setString(8, job.getNextRetryAt());
            ps.setString(9, job.getLockedBy());
            ps.setString(10, job.getLockedAt());
            ps.setString(11, job.getOutput());
            ps.setString(12, job.getError());
            ps.setString(13, TimeUtil.now());
            ps.setString(14, job.getId());

            int updated = ps.executeUpdate();
            if (updated == 0) {
                logger.warn("No job found to update: {}", job.getId());
            } else {
                logger.debug("Job updated: id={}, state={}", job.getId(), job.getState());
            }

        } catch (SQLException e) {
            throw new PersistenceException("Failed to update job: " + job.getId(), e);
        }
    }

    @Override
    public int countByState(JobState state) {
        String sql = "SELECT COUNT(*) FROM jobs WHERE state = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, state.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new PersistenceException("Failed to count jobs by state: " + state, e);
        }
    }

    @Override
    public boolean delete(String id) {
        String sql = "DELETE FROM jobs WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            int deleted = ps.executeUpdate();
            return deleted > 0;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete job: " + id, e);
        }
    }

    @Override
    public int resetStalledJobs() {
        String sql = """
            UPDATE jobs
            SET state = 'PENDING',
                locked_by = NULL,
                locked_at = NULL,
                updated_at = ?
            WHERE state = 'PROCESSING'
            """;

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, TimeUtil.now());
            int count = ps.executeUpdate();
            if (count > 0) {
                logger.info("Reset {} stalled PROCESSING jobs back to PENDING", count);
            }
            return count;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to reset stalled jobs", e);
        }
    }

    @Override
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM jobs";

        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to count all jobs", e);
        }
    }

    // ========== Private Helpers ==========

    private void setJobParameters(PreparedStatement ps, Job job) throws SQLException {
        ps.setString(1, job.getId());
        ps.setString(2, job.getCommand());
        ps.setString(3, job.getState().name());
        ps.setInt(4, job.getAttempts());
        ps.setInt(5, job.getMaxRetries());
        ps.setString(6, job.getPriority() != null ? job.getPriority().name() : "MEDIUM");
        ps.setInt(7, job.getTimeout());
        ps.setString(8, job.getRunAt());
        ps.setString(9, job.getNextRetryAt());
        ps.setString(10, job.getLockedBy());
        ps.setString(11, job.getLockedAt());
        ps.setString(12, job.getOutput());
        ps.setString(13, job.getError());
        ps.setString(14, job.getCreatedAt());
        ps.setString(15, job.getUpdatedAt());
    }

    private Job mapResultSetToJob(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getString("id"));
        job.setCommand(rs.getString("command"));
        job.setState(JobState.valueOf(rs.getString("state")));
        job.setAttempts(rs.getInt("attempts"));
        job.setMaxRetries(rs.getInt("max_retries"));
        job.setPriority(Priority.fromString(rs.getString("priority")));
        job.setTimeout(rs.getInt("timeout"));
        job.setRunAt(rs.getString("run_at"));
        job.setNextRetryAt(rs.getString("next_retry_at"));
        job.setLockedBy(rs.getString("locked_by"));
        job.setLockedAt(rs.getString("locked_at"));
        job.setOutput(rs.getString("output"));
        job.setError(rs.getString("error"));
        job.setCreatedAt(rs.getString("created_at"));
        job.setUpdatedAt(rs.getString("updated_at"));
        return job;
    }

    private List<Job> executeJobListQuery(PreparedStatement ps) throws SQLException {
        List<Job> jobs = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                jobs.add(mapResultSetToJob(rs));
            }
        }
        return jobs;
    }
}

package com.queuectl.service;

import com.queuectl.domain.JobState;
import com.queuectl.exception.PersistenceException;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.JobRepository;
import com.queuectl.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for computing and reporting job processing metrics.
 *
 * Provides aggregated statistics including:
 * - Jobs processed (completed count)
 * - Average runtime
 * - Success rate
 * - Retry count
 * - DLQ count
 * - Active worker count
 */
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final SqliteConnectionFactory connectionFactory;

    public MetricsService(JobRepository jobRepository, WorkerRepository workerRepository,
                          SqliteConnectionFactory connectionFactory) {
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Gathers all metrics as a key-value map for display.
     *
     * @return ordered map of metric name → value string
     */
    public Map<String, String> gatherMetrics() {
        Map<String, String> metrics = new LinkedHashMap<>();

        int completed = jobRepository.countByState(JobState.COMPLETED);
        int failed = jobRepository.countByState(JobState.FAILED);
        int dead = jobRepository.countByState(JobState.DEAD);
        int pending = jobRepository.countByState(JobState.PENDING);
        int processing = jobRepository.countByState(JobState.PROCESSING);
        int total = jobRepository.countAll();
        int activeWorkers = workerRepository.countActive();

        // Compute success rate
        int processed = completed + failed + dead;
        double successRate = processed > 0 ? (double) completed / processed * 100.0 : 0.0;

        // Get retry count and avg execution time from metrics table
        long totalRetries = getMetricValue("jobs_retried");
        long totalExecTimeMs = getMetricValue("total_execution_time_ms");
        long avgExecTimeMs = completed > 0 ? totalExecTimeMs / completed : 0;

        metrics.put("Total Jobs", String.valueOf(total));
        metrics.put("Pending", String.valueOf(pending));
        metrics.put("Processing", String.valueOf(processing));
        metrics.put("Completed", String.valueOf(completed));
        metrics.put("Failed", String.valueOf(failed));
        metrics.put("Dead (DLQ)", String.valueOf(dead));
        metrics.put("Success Rate", String.format("%.1f%%", successRate));
        metrics.put("Total Retries", String.valueOf(totalRetries));
        metrics.put("Avg Execution Time", avgExecTimeMs + " ms");
        metrics.put("Active Workers", String.valueOf(activeWorkers));

        return metrics;
    }

    /**
     * Increments a metric counter.
     *
     * @param key   the metric key
     * @param delta the amount to add
     */
    public void incrementMetric(String key, long delta) {
        String sql = "UPDATE metrics SET value = CAST((CAST(value AS INTEGER) + " + delta + ") AS TEXT) WHERE key = ?";

        try (Connection conn = connectionFactory.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to increment metric '{}': {}", key, e.getMessage());
        }
    }

    private long getMetricValue(String key) {
        String sql = "SELECT value FROM metrics WHERE key = ?";

        try (Connection conn = connectionFactory.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Long.parseLong(rs.getString("value"));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read metric '{}': {}", key, e.getMessage());
        }
        return 0;
    }
}

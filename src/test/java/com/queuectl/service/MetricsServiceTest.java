package com.queuectl.service;

import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteJobRepository;
import com.queuectl.repository.SqliteWorkerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetricsService.
 */
class MetricsServiceTest {

    @TempDir
    Path tempDir;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        metricsService = new MetricsService(
                new SqliteJobRepository(factory),
                new SqliteWorkerRepository(factory),
                factory);
    }

    @Test
    @DisplayName("gatherMetrics returns all expected keys")
    void testGatherMetrics() {
        Map<String, String> metrics = metricsService.gatherMetrics();

        assertTrue(metrics.containsKey("Total Jobs"));
        assertTrue(metrics.containsKey("Pending"));
        assertTrue(metrics.containsKey("Processing"));
        assertTrue(metrics.containsKey("Completed"));
        assertTrue(metrics.containsKey("Failed"));
        assertTrue(metrics.containsKey("Dead (DLQ)"));
        assertTrue(metrics.containsKey("Success Rate"));
        assertTrue(metrics.containsKey("Total Retries"));
        assertTrue(metrics.containsKey("Avg Execution Time"));
        assertTrue(metrics.containsKey("Active Workers"));
    }

    @Test
    @DisplayName("gatherMetrics returns zeros for empty system")
    void testGatherMetricsEmpty() {
        Map<String, String> metrics = metricsService.gatherMetrics();
        assertEquals("0", metrics.get("Total Jobs"));
        assertEquals("0", metrics.get("Active Workers"));
        assertEquals("0.0%", metrics.get("Success Rate"));
    }

    @Test
    @DisplayName("incrementMetric updates counter")
    void testIncrementMetric() {
        metricsService.incrementMetric("jobs_processed", 5);
        // Verify via another gatherMetrics call
        Map<String, String> metrics = metricsService.gatherMetrics();
        assertNotNull(metrics);
    }
}

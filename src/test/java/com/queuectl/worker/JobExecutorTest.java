package com.queuectl.worker;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JobExecutor.
 */
class JobExecutorTest {

    private JobExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JobExecutor();
    }

    @Test
    @DisplayName("successful command returns success result")
    void testSuccessfulCommand() {
        Job job = new Job("j1", "echo hello");
        job.setTimeout(10);

        JobExecutor.Result result = executor.execute(job);

        assertTrue(result.success());
        assertTrue(result.output().contains("hello"));
        assertTrue(result.error().isEmpty());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    @DisplayName("failing command returns failure result")
    void testFailingCommand() {
        // Use a command that exits with non-zero
        String cmd = System.getProperty("os.name").toLowerCase().contains("win")
                ? "cmd /c exit 1"
                : "sh -c 'exit 1'";
        Job job = new Job("j2", cmd);
        job.setTimeout(10);

        JobExecutor.Result result = executor.execute(job);

        assertFalse(result.success());
    }

    @Test
    @DisplayName("timed out command returns failure")
    void testTimeout() {
        // Command that sleeps longer than timeout
        String cmd = System.getProperty("os.name").toLowerCase().contains("win")
                ? "ping -n 10 127.0.0.1"
                : "sleep 10";
        Job job = new Job("j3", cmd);
        job.setTimeout(1);

        JobExecutor.Result result = executor.execute(job);

        assertFalse(result.success());
        assertTrue(result.error().contains("timed out") || result.error().contains("Timed out")
                || result.error().contains("timeout"));
    }

    @Test
    @DisplayName("captures stdout from multi-line output")
    void testMultiLineOutput() {
        String cmd = System.getProperty("os.name").toLowerCase().contains("win")
                ? "cmd /c echo line1 & echo line2"
                : "echo 'line1' && echo 'line2'";
        Job job = new Job("j4", cmd);
        job.setTimeout(10);

        JobExecutor.Result result = executor.execute(job);

        assertTrue(result.success());
        assertTrue(result.output().contains("line1"));
        assertTrue(result.output().contains("line2"));
    }
}

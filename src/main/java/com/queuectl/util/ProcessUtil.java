package com.queuectl.util;

import com.queuectl.exception.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility for executing system processes with timeout and output capture.
 */
public final class ProcessUtil {

    private static final Logger logger = LoggerFactory.getLogger(ProcessUtil.class);

    private ProcessUtil() {
        // Utility class
    }

    /**
     * Result of a process execution.
     */
    public record ExecutionResult(int exitCode, String stdout, String stderr, long durationMs) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * Executes a command with the given timeout.
     *
     * @param command        the shell command (e.g., "echo hello")
     * @param timeoutSeconds maximum execution time in seconds
     * @return the execution result
     * @throws JobExecutionException if process cannot be started or times out
     */
    public static ExecutionResult execute(String command, int timeoutSeconds) {
        String[] shellCommand = CommandUtil.wrapForShell(command);
        long startTime = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Wait for completion with timeout FIRST — reading stdout before
            // waitFor blocks the calling thread until the process ends, which
            // defeats the timeout.
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long durationMs = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS); // allow cleanup
                throw new JobExecutionException(
                        "Command timed out after " + timeoutSeconds + "s: " + command);
            }

            // Read output AFTER process completes (safe, won't block)
            String stdout;
            String stderr;
            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                stdout = stdoutReader.lines().collect(Collectors.joining("\n"));
                stderr = stderrReader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.exitValue();
            logger.debug("Command completed: exit={}, duration={}ms, command='{}'",
                    exitCode, durationMs, command);

            return new ExecutionResult(exitCode, stdout, stderr, durationMs);

        } catch (JobExecutionException e) {
            throw e; // Re-throw timeout
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            throw new JobExecutionException(
                    "Failed to execute command: " + command, e);
        }
    }
}


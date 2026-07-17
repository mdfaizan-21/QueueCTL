package com.queuectl.worker;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.util.ProcessUtil;
import com.queuectl.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a job's shell command via ProcessBuilder.
 *
 * Captures stdout, stderr, exit code, and duration.
 * Returns a structured result that the Worker uses to decide
 * whether to mark the job COMPLETED or hand it to RetryService.
 */
public class JobExecutor {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutor.class);

    /**
     * Result of executing a single job.
     *
     * @param success    true if exit code == 0
     * @param output     stdout from the command
     * @param error      stderr or exception message
     * @param durationMs wall-clock execution time in milliseconds
     */
    public record Result(boolean success, String output, String error, long durationMs) {}

    /**
     * Executes the job's command with its configured timeout.
     *
     * @param job the job to execute
     * @return execution result (never null)
     */
    public Result execute(Job job) {
        logger.info("Executing job {}: command='{}', timeout={}s",
                job.getId(), job.getCommand(), job.getTimeout());

        try {
            ProcessUtil.ExecutionResult procResult =
                    ProcessUtil.execute(job.getCommand(), job.getTimeout());

            if (procResult.isSuccess()) {
                logger.info("Job {} completed successfully in {}ms",
                        job.getId(), procResult.durationMs());
                return new Result(true, procResult.stdout(), "", procResult.durationMs());
            } else {
                String errorMsg = procResult.stderr().isBlank()
                        ? "Exit code: " + procResult.exitCode()
                        : procResult.stderr();
                logger.warn("Job {} failed: exit={}, stderr='{}'",
                        job.getId(), procResult.exitCode(), errorMsg);
                return new Result(false, procResult.stdout(), errorMsg, procResult.durationMs());
            }

        } catch (Exception e) {
            logger.error("Job {} execution error: {}", job.getId(), e.getMessage());
            return new Result(false, "", e.getMessage(), 0);
        }
    }
}

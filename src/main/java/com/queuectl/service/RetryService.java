package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.repository.JobRepository;
import com.queuectl.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing job retries with exponential backoff.
 *
 * Backoff formula: delay = backoffBase ^ attempts (in seconds).
 * Example with base=2: attempt 1 → 2s, attempt 2 → 4s, attempt 3 → 8s.
 */
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);

    private final JobRepository jobRepository;
    private final ConfigService configService;

    public RetryService(JobRepository jobRepository, ConfigService configService) {
        this.jobRepository = jobRepository;
        this.configService = configService;
    }

    /**
     * Handles a failed job: either schedules a retry or moves to DLQ.
     *
     * @param job   the failed job
     * @param error the error message from execution
     * @return true if job was retried, false if moved to DEAD
     */
    public boolean handleFailure(Job job, String error) {
        job.incrementAttempts();
        job.setError(error);

        // Job-level maxRetries takes precedence; global config is only used
        // if the job still has the default value (which matches the constructor default).
        int maxRetries = job.getMaxRetries();
        if (maxRetries <= 0) {
            maxRetries = configService.getInt("max-retries", 3);
        }

        if (job.getAttempts() >= maxRetries) {
            // Exhausted retries → Dead Letter Queue
            job.setState(JobState.DEAD);
            job.setLockedBy(null);
            job.setLockedAt(null);
            jobRepository.update(job);
            logger.warn("Job {} moved to DLQ after {} attempts", job.getId(), job.getAttempts());
            return false;
        }

        // Schedule retry with exponential backoff
        int backoffBase = configService.getInt("backoff-base", 2);
        long delaySeconds = (long) Math.pow(backoffBase, job.getAttempts());
        String nextRetryAt = TimeUtil.futureFromNow(delaySeconds);

        job.setState(JobState.PENDING);
        job.setNextRetryAt(nextRetryAt);
        job.setLockedBy(null);
        job.setLockedAt(null);
        jobRepository.update(job);

        logger.info("Job {} scheduled for retry #{} in {}s (next_retry_at: {})",
                job.getId(), job.getAttempts(), delaySeconds, nextRetryAt);
        return true;
    }

    /**
     * Calculates the backoff delay for a given attempt number.
     *
     * @param attempt the attempt number (1-based)
     * @return delay in seconds
     */
    public long calculateBackoff(int attempt) {
        int backoffBase = configService.getInt("backoff-base", 2);
        return (long) Math.pow(backoffBase, attempt);
    }
}

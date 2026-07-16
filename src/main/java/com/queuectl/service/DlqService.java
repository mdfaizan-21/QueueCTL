package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for Dead Letter Queue operations.
 *
 * <p>Jobs enter the DLQ when they exhaust all retry attempts.
 * This service allows listing and retrying dead jobs.
 */
public class DlqService {

    private static final Logger logger = LoggerFactory.getLogger(DlqService.class);

    private final JobRepository jobRepository;

    public DlqService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Lists all jobs in the Dead Letter Queue.
     *
     * @return list of DEAD jobs
     */
    public List<Job> listDeadJobs() {
        return jobRepository.findByState(JobState.DEAD);
    }

    /**
     * Retries a dead job by resetting it to PENDING.
     *
     * <p>Resets: state → PENDING, attempts → 0, clears error/lock/retry fields.
     *
     * @param jobId the job ID to retry
     * @throws IllegalArgumentException if job not found or not in DEAD state
     */
    public void retryJob(String jobId) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);

        if (jobOpt.isEmpty()) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        Job job = jobOpt.get();

        if (job.getState() != JobState.DEAD) {
            throw new IllegalArgumentException(
                    "Job '" + jobId + "' is not in DEAD state (current: " + job.getState() + ")");
        }

        // Reset for retry
        job.setState(JobState.PENDING);
        job.setAttempts(0);
        job.setError("");
        job.setOutput("");
        job.setLockedBy(null);
        job.setLockedAt(null);
        job.setNextRetryAt(null);

        jobRepository.update(job);
        logger.info("DLQ job retried: id={}, reset to PENDING", jobId);
    }

    /**
     * Counts jobs in the Dead Letter Queue.
     *
     * @return number of DEAD jobs
     */
    public int countDeadJobs() {
        return jobRepository.countByState(JobState.DEAD);
    }
}

package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import com.queuectl.exception.PersistenceException;
import com.queuectl.repository.JobRepository;
import com.queuectl.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for job operations.
 *
 * Handles business logic for enqueuing, querying, and managing jobs.
 * Validates input and delegates persistence to {@link JobRepository}.
 */
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Enqueues a new job. Validates and sets defaults before saving.
     *
     * @param job the job to enqueue
     * @throws IllegalArgumentException if job is invalid
     * @throws PersistenceException if a job with the same ID already exists
     */
    public void enqueue(Job job) {
        validateJob(job);
        applyDefaults(job);

        jobRepository.save(job);
        logger.info("Job enqueued: id={}, command='{}', priority={}", 
                job.getId(), job.getCommand(), job.getPriority());
    }

    /**
     * Finds a job by ID.
     *
     * @param id the job ID
     * @return the job, or empty if not found
     */
    public Optional<Job> findById(String id) {
        return jobRepository.findById(id);
    }

    /**
     * Lists all jobs, optionally filtered by state.
     *
     * @param state the state to filter by, or null for all jobs
     * @return list of matching jobs
     */
    public List<Job> listJobs(JobState state) {
        if (state != null) {
            return jobRepository.findByState(state);
        }
        return jobRepository.findAll();
    }

    /**
     * Returns the count of jobs in each state.
     *
     * @return array of counts: [pending, processing, completed, failed, dead]
     */
    public int[] getStateCounts() {
        return new int[]{
                jobRepository.countByState(JobState.PENDING),
                jobRepository.countByState(JobState.PROCESSING),
                jobRepository.countByState(JobState.COMPLETED),
                jobRepository.countByState(JobState.FAILED),
                jobRepository.countByState(JobState.DEAD)
        };
    }

    /**
     * Total number of jobs in the system.
     *
     * @return total job count
     */
    public int totalJobs() {
        return jobRepository.countAll();
    }

    /**
     * Resets stalled PROCESSING jobs back to PENDING (crash recovery).
     *
     * @return number of jobs reset
     */
    public int resetStalledJobs() {
        return jobRepository.resetStalledJobs();
    }

    private void validateJob(Job job) {
        if (job.getId() == null || job.getId().isBlank()) {
            throw new IllegalArgumentException("Job ID is required");
        }
        if (job.getCommand() == null || job.getCommand().isBlank()) {
            throw new IllegalArgumentException("Job command is required");
        }
        if (job.getMaxRetries() < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (job.getTimeout() <= 0) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
    }

    private void applyDefaults(Job job) {
        if (job.getState() == null) {
            job.setState(JobState.PENDING);
        }
        if (job.getPriority() == null) {
            job.setPriority(Priority.MEDIUM);
        }
        String now = TimeUtil.now();
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(now);
        }
        if (job.getUpdatedAt() == null) {
            job.setUpdatedAt(now);
        }
    }
}

package com.queuectl.repository;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Job persistence operations.
 *
 * Defines the contract for storing, retrieving, updating, and
 * atomically locking jobs. Implementations must ensure thread-safety
 * for concurrent worker access.
 */
public interface JobRepository {

    /**
     * Persists a new job. Fails if a job with the same ID already exists.
     *
     * @param job the job to save
     * @throws com.queuectl.exception.PersistenceException if save fails or ID already exists
     */
    void save(Job job);

    /**
     * Finds a job by its unique ID.
     *
     * @param id the job ID
     * @return the job, or empty if not found
     */
    Optional<Job> findById(String id);

    /**
     * Finds all jobs in the given state.
     *
     * @param state the job state to filter by
     * @return list of matching jobs, ordered by priority DESC, createdAt ASC
     */
    List<Job> findByState(JobState state);

    /**
     * Returns all jobs regardless of state.
     *
     * @return all jobs, ordered by createdAt DESC
     */
    List<Job> findAll();

    /**
     * Atomically fetches the next eligible job and locks it for a worker.
     *
     * Eligible jobs are PENDING with either:
         * - No scheduled retry (next_retry_at IS NULL)
     * - Retry time has passed (next_retry_at &lt;= now)
     * - No delayed start (run_at IS NULL) or run_at &lt;= now
         *
     * Jobs are ordered by priority DESC, created_at ASC.
     * The selected job is atomically set to PROCESSING with the given worker ID.
     *
     * @param workerId the ID of the worker acquiring the lock
     * @return the locked job, or empty if no eligible job is available
     */
    Optional<Job> fetchAndLock(String workerId);

    /**
     * Updates all fields of an existing job.
     *
     * @param job the job with updated fields
     * @throws com.queuectl.exception.PersistenceException if update fails
     */
    void update(Job job);

    /**
     * Counts jobs in a given state.
     *
     * @param state the state to count
     * @return number of jobs in that state
     */
    int countByState(JobState state);

    /**
     * Deletes a job by ID.
     *
     * @param id the job ID to delete
     * @return true if a job was deleted, false if not found
     */
    boolean delete(String id);

    /**
     * Resets all PROCESSING jobs back to PENDING.
     * Used for crash recovery on startup.
     *
     * @return number of jobs reset
     */
    int resetStalledJobs();

    /**
     * Counts total number of jobs.
     *
     * @return total job count
     */
    int countAll();
}

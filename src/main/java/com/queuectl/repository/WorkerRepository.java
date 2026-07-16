package com.queuectl.repository;

import com.queuectl.domain.WorkerInfo;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Worker persistence operations.
 */
public interface WorkerRepository {

    /**
     * Registers a new worker.
     *
     * @param worker the worker info to save
     */
    void save(WorkerInfo worker);

    /**
     * Finds a worker by ID.
     *
     * @param id the worker ID
     * @return the worker, or empty if not found
     */
    Optional<WorkerInfo> findById(String id);

    /**
     * Returns all registered workers.
     *
     * @return list of all workers
     */
    List<WorkerInfo> findAll();

    /**
     * Updates a worker's heartbeat timestamp.
     *
     * @param id        the worker ID
     * @param heartbeat the new heartbeat timestamp (ISO-8601)
     */
    void updateHeartbeat(String id, String heartbeat);

    /**
     * Updates a worker's status.
     *
     * @param id     the worker ID
     * @param status the new status
     */
    void updateStatus(String id, String status);

    /**
     * Removes a worker registration.
     *
     * @param id the worker ID to remove
     * @return true if removed, false if not found
     */
    boolean delete(String id);

    /**
     * Removes all worker registrations.
     *
     * @return number of workers removed
     */
    int deleteAll();

    /**
     * Counts active workers (status = RUNNING).
     *
     * @return number of active workers
     */
    int countActive();
}

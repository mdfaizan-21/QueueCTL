package com.queuectl.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents metadata about a running worker instance.
 *
 * Workers register themselves in the database to enable:
 * - Heartbeat monitoring (detect stale workers)
 * - Status reporting via CLI commands
 * - Crash recovery (release locks held by dead workers)
 */
public class WorkerInfo {

    /** Unique identifier for the worker (e.g., "worker-1", UUID). */
    private String id;

    /** Current status: RUNNING, STOPPED, STALE. */
    private String status;

    /** Last heartbeat timestamp (ISO-8601). */
    private String heartbeat;

    /** Timestamp when the worker started (ISO-8601). */
    private String startedAt;

    /** Default constructor. */
    public WorkerInfo() {
    }

    /**
     * Creates a new WorkerInfo with the given ID.
     * Sets status to RUNNING and initializes timestamps.
     *
     * @param id unique worker identifier
     */
    public WorkerInfo(String id) {
        this.id = Objects.requireNonNull(id, "Worker ID must not be null");
        this.status = "RUNNING";
        String now = Instant.now().toString();
        this.heartbeat = now;
        this.startedAt = now;
    }

    // ========== Getters and Setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(String heartbeat) {
        this.heartbeat = heartbeat;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Updates the heartbeat to current time.
     */
    public void updateHeartbeat() {
        this.heartbeat = Instant.now().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkerInfo that = (WorkerInfo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorkerInfo{" +
                "id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", heartbeat='" + heartbeat + '\'' +
                ", startedAt='" + startedAt + '\'' +
                '}';
    }
}

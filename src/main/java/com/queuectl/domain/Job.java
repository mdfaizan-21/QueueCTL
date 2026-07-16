package com.queuectl.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Core domain entity representing a background job in the queue.
 *
 * A Job encapsulates:
 * - Identity: unique id
 * - Work: the shell command to execute
 * - Lifecycle: state, attempts, retry metadata
 * - Scheduling: priority, runAt, nextRetryAt
 * - Locking: lockedBy worker id, lockedAt timestamp
 * - Results: output (stdout), error (stderr)
 * - Auditing: createdAt, updatedAt
 *
 * This class is designed for Jackson serialization and SQLite persistence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {

    /** Unique identifier for the job. */
    @JsonProperty("id")
    private String id;

    /** Shell command to execute (e.g., "echo hello", "sleep 5"). */
    @JsonProperty("command")
    private String command;

    /** Current lifecycle state. */
    @JsonProperty("state")
    private JobState state;

    /** Number of execution attempts made so far. */
    @JsonProperty("attempts")
    private int attempts;

    /** Maximum number of retries before moving to DLQ. */
    @JsonProperty("maxRetries")
    private int maxRetries;

    /** Job priority for queue ordering. */
    @JsonProperty("priority")
    private Priority priority;

    /** Execution timeout in seconds. */
    @JsonProperty("timeout")
    private int timeout;

    /** Scheduled execution time (ISO-8601). Null = execute immediately. */
    @JsonProperty("runAt")
    private String runAt;

    /** Next retry timestamp (ISO-8601). Set by retry engine. */
    @JsonProperty("nextRetryAt")
    private String nextRetryAt;

    /** ID of the worker that currently holds the lock. */
    @JsonProperty("lockedBy")
    private String lockedBy;

    /** Timestamp when the lock was acquired (ISO-8601). */
    @JsonProperty("lockedAt")
    private String lockedAt;

    /** Captured stdout from command execution. */
    @JsonProperty("output")
    private String output;

    /** Captured stderr from command execution. */
    @JsonProperty("error")
    private String error;

    /** Timestamp when the job was created (ISO-8601). */
    @JsonProperty("createdAt")
    private String createdAt;

    /** Timestamp when the job was last updated (ISO-8601). */
    @JsonProperty("updatedAt")
    private String updatedAt;

    /**
     * Default constructor for Jackson deserialization.
     * Sets sensible defaults for optional fields.
     */
    public Job() {
        this.state = JobState.PENDING;
        this.attempts = 0;
        this.maxRetries = 3;
        this.priority = Priority.MEDIUM;
        this.timeout = 60;
        this.output = "";
        this.error = "";
    }

    /**
     * Full constructor for programmatic creation.
     *
     * @param id       unique job identifier
     * @param command  shell command to execute
     */
    public Job(String id, String command) {
        this();
        this.id = Objects.requireNonNull(id, "Job ID must not be null");
        this.command = Objects.requireNonNull(command, "Job command must not be null");
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ========== Getters and Setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getRunAt() {
        return runAt;
    }

    public void setRunAt(String runAt) {
        this.runAt = runAt;
    }

    public String getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(String nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public String getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(String lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========== Utility Methods ==========

    /**
     * Increments the attempt counter and updates the timestamp.
     */
    public void incrementAttempts() {
        this.attempts++;
        this.updatedAt = Instant.now().toString();
    }

    /**
     * Checks if the job has exceeded its maximum retry count.
     *
     * @return true if attempts exceed maxRetries
     */
    public boolean isRetriesExhausted() {
        return this.attempts >= this.maxRetries;
    }

    /**
     * Returns true if this job is scheduled for future execution.
     *
     * @return true if runAt is set and is in the future
     */
    public boolean isDelayed() {
        if (runAt == null || runAt.isBlank()) {
            return false;
        }
        try {
            Instant scheduledTime = Instant.parse(runAt);
            return scheduledTime.isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(id, job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", command='" + command + '\'' +
                ", state=" + state +
                ", attempts=" + attempts +
                ", maxRetries=" + maxRetries +
                ", priority=" + priority +
                ", timeout=" + timeout +
                '}';
    }
}

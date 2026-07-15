package com.queuectl.domain;

/**
 * Represents the lifecycle states of a job in the queue system.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING → PROCESSING → COMPLETED
 *                        → FAILED → PENDING (retry)
 *                                 → DEAD (max retries exceeded)
 * </pre>
 */
public enum JobState {

    /** Job is queued and waiting to be picked up by a worker. */
    PENDING,

    /** Job is currently being executed by a worker. */
    PROCESSING,

    /** Job executed successfully. */
    COMPLETED,

    /** Job execution failed. May be retried or moved to DLQ. */
    FAILED,

    /** Job has exhausted all retries and is in the Dead Letter Queue. */
    DEAD
}

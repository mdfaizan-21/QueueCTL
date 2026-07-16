package com.queuectl.exception;

/**
 * Exception thrown for worker lifecycle errors.
 *
 * Covers scenarios such as:
 * - Worker start failure
 * - Worker already running
 * - Worker shutdown failure
 * - Heartbeat update failure
 */
public class WorkerException extends QueueCtlException {

    public WorkerException(String message) {
        super(message);
    }

    public WorkerException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.queuectl.exception;

/**
 * Exception thrown for worker lifecycle errors.
 *
 * <p>Covers scenarios such as:
 * <ul>
 *   <li>Worker start failure</li>
 *   <li>Worker already running</li>
 *   <li>Worker shutdown failure</li>
 *   <li>Heartbeat update failure</li>
 * </ul>
 */
public class WorkerException extends QueueCtlException {

    public WorkerException(String message) {
        super(message);
    }

    public WorkerException(String message, Throwable cause) {
        super(message, cause);
    }
}

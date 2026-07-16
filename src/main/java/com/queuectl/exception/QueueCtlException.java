package com.queuectl.exception;

/**
 * Base exception for all QueueCTL errors.
 *
 * All domain-specific exceptions extend this class to enable
 * catch-all handling at the CLI layer while preserving specific
 * exception types for targeted error handling deeper in the stack.
 */
public class QueueCtlException extends RuntimeException {

    public QueueCtlException(String message) {
        super(message);
    }

    public QueueCtlException(String message, Throwable cause) {
        super(message, cause);
    }
}

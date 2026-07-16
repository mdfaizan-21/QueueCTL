package com.queuectl.exception;

/**
 * Exception thrown for database/persistence layer errors.
 *
 * Covers scenarios such as:
 * - Database connection failure
 * - Schema migration failure
 * - SQL execution failure
 * - Transaction failure
 */
public class PersistenceException extends QueueCtlException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

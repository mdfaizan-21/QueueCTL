package com.queuectl.exception;

/**
 * Exception thrown for database/persistence layer errors.
 *
 * <p>Covers scenarios such as:
 * <ul>
 *   <li>Database connection failure</li>
 *   <li>Schema migration failure</li>
 *   <li>SQL execution failure</li>
 *   <li>Transaction failure</li>
 * </ul>
 */
public class PersistenceException extends QueueCtlException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

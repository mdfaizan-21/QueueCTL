package com.queuectl.exception;

/**
 * Exception thrown for configuration errors.
 *
 * Covers scenarios such as:
 * - Invalid configuration key
 * - Invalid configuration value
 * - Configuration read/write failure
 */
public class ConfigException extends QueueCtlException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

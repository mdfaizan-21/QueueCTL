package com.queuectl.exception;

/**
 * Exception thrown for configuration errors.
 *
 * <p>Covers scenarios such as:
 * <ul>
 *   <li>Invalid configuration key</li>
 *   <li>Invalid configuration value</li>
 *   <li>Configuration read/write failure</li>
 * </ul>
 */
public class ConfigException extends QueueCtlException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

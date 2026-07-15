package com.queuectl.domain;

import java.util.Objects;

/**
 * Represents a key-value configuration entry stored in the database.
 *
 * <p>Used for runtime-configurable settings such as:
 * <ul>
 *   <li>max-retries: maximum retry attempts before DLQ</li>
 *   <li>backoff-base: base for exponential backoff calculation</li>
 *   <li>poll-interval: worker polling interval in milliseconds</li>
 *   <li>job-timeout: default job execution timeout in seconds</li>
 *   <li>worker-heartbeat: heartbeat interval in seconds</li>
 * </ul>
 */
public class Config {

    /** Configuration key (e.g., "max-retries"). */
    private String key;

    /** Configuration value (stored as string, parsed by consumers). */
    private String value;

    /** Default constructor. */
    public Config() {
    }

    /**
     * Creates a configuration entry.
     *
     * @param key   the config key
     * @param value the config value
     */
    public Config(String key, String value) {
        this.key = Objects.requireNonNull(key, "Config key must not be null");
        this.value = Objects.requireNonNull(value, "Config value must not be null");
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the value as an integer.
     *
     * @return integer value
     * @throws NumberFormatException if value is not a valid integer
     */
    public int getValueAsInt() {
        return Integer.parseInt(value);
    }

    /**
     * Returns the value as a long.
     *
     * @return long value
     * @throws NumberFormatException if value is not a valid long
     */
    public long getValueAsLong() {
        return Long.parseLong(value);
    }

    /**
     * Returns the value as a double.
     *
     * @return double value
     * @throws NumberFormatException if value is not a valid double
     */
    public double getValueAsDouble() {
        return Double.parseDouble(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Config config = (Config) o;
        return Objects.equals(key, config.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "Config{key='" + key + "', value='" + value + "'}";
    }
}

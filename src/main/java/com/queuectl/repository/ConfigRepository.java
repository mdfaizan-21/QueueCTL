package com.queuectl.repository;

import com.queuectl.domain.Config;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for runtime configuration persistence.
 */
public interface ConfigRepository {

    /**
     * Gets a config value by key.
     *
     * @param key the config key
     * @return the config entry, or empty if not found
     */
    Optional<Config> findByKey(String key);

    /**
     * Sets a config value. Creates or updates the entry.
     *
     * @param key   the config key
     * @param value the config value
     */
    void set(String key, String value);

    /**
     * Returns all config entries.
     *
     * @return list of all config key-value pairs
     */
    List<Config> findAll();

    /**
     * Gets a config value as a string, with a default fallback.
     *
     * @param key          the config key
     * @param defaultValue fallback if key is not found
     * @return the config value, or the default
     */
    String getOrDefault(String key, String defaultValue);
}

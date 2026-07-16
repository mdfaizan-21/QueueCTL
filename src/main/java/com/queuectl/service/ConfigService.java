package com.queuectl.service;

import com.queuectl.domain.Config;
import com.queuectl.exception.ConfigException;
import com.queuectl.repository.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Service for managing runtime configuration.
 *
 * Validates configuration keys and values before persisting.
 */
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    /** Set of valid configuration keys. */
    private static final Set<String> VALID_KEYS = Set.of(
            "max-retries",
            "backoff-base",
            "poll-interval",
            "job-timeout",
            "worker-heartbeat"
    );

    private final ConfigRepository configRepository;

    public ConfigService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Sets a configuration value after validation.
     *
     * @param key   the config key
     * @param value the config value
     * @throws ConfigException if key is invalid or value is not a positive number
     */
    public void set(String key, String value) {
        if (!VALID_KEYS.contains(key)) {
            throw new ConfigException("Invalid config key: '" + key + "'. Valid keys: " + VALID_KEYS);
        }
        validateNumericValue(key, value);
        configRepository.set(key, value);
        logger.info("Config updated: {} = {}", key, value);
    }

    /**
     * Gets a configuration value.
     *
     * @param key the config key
     * @return the config value
     * @throws ConfigException if key is not found
     */
    public String get(String key) {
        return configRepository.findByKey(key)
                .map(Config::getValue)
                .orElseThrow(() -> new ConfigException("Config key not found: " + key));
    }

    /**
     * Gets a configuration value with a default fallback.
     *
     * @param key          the config key
     * @param defaultValue fallback value
     * @return the config value, or the default
     */
    public String getOrDefault(String key, String defaultValue) {
        return configRepository.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a config value as an integer.
     *
     * @param key          the config key
     * @param defaultValue fallback value
     * @return the integer config value
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(configRepository.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a config value as a long.
     *
     * @param key          the config key
     * @param defaultValue fallback value
     * @return the long config value
     */
    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(configRepository.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns all configuration entries.
     *
     * @return list of all config key-value pairs
     */
    public List<Config> listAll() {
        return configRepository.findAll();
    }

    /**
     * Returns the set of valid configuration keys.
     *
     * @return valid keys
     */
    public Set<String> getValidKeys() {
        return VALID_KEYS;
    }

    private void validateNumericValue(String key, String value) {
        try {
            double num = Double.parseDouble(value);
            if (num <= 0) {
                throw new ConfigException("Config value for '" + key + "' must be a positive number, got: " + value);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException("Config value for '" + key + "' must be a number, got: " + value);
        }
    }
}

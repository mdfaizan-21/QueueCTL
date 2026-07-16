package com.queuectl.repository;

import com.queuectl.domain.Config;
import com.queuectl.exception.PersistenceException;
import com.queuectl.persistence.SqliteConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of {@link ConfigRepository}.
 *
 * <p>Uses INSERT OR REPLACE (upsert) for set operations,
 * ensuring config values can be updated without checking for existence.
 */
public class SqliteConfigRepository implements ConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(SqliteConfigRepository.class);

    private final SqliteConnectionFactory connectionFactory;

    public SqliteConfigRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<Config> findByKey(String key) {
        String sql = "SELECT key, value FROM config WHERE key = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Config(rs.getString("key"), rs.getString("value")));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find config: " + key, e);
        }
    }

    @Override
    public void set(String key, String value) {
        String sql = "INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            logger.info("Config set: {} = {}", key, value);

        } catch (SQLException e) {
            throw new PersistenceException("Failed to set config: " + key, e);
        }
    }

    @Override
    public List<Config> findAll() {
        String sql = "SELECT key, value FROM config ORDER BY key";
        List<Config> configs = new ArrayList<>();

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                configs.add(new Config(rs.getString("key"), rs.getString("value")));
            }
            return configs;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find all configs", e);
        }
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        return findByKey(key)
                .map(Config::getValue)
                .orElse(defaultValue);
    }
}

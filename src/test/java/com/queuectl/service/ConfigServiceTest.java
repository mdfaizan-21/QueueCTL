package com.queuectl.service;

import com.queuectl.exception.ConfigException;
import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.SqliteConfigRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigService.
 */
class ConfigServiceTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                tempDir.resolve("test.db").toString());
        new DatabaseManager(factory).initialize();
        configService = new ConfigService(new SqliteConfigRepository(factory));
    }

    @Test
    @DisplayName("set valid key-value")
    void testSetValid() {
        assertDoesNotThrow(() -> configService.set("max-retries", "5"));
        assertEquals("5", configService.get("max-retries"));
    }

    @Test
    @DisplayName("set rejects invalid key")
    void testSetInvalidKey() {
        assertThrows(ConfigException.class, () -> configService.set("invalid-key", "5"));
    }

    @Test
    @DisplayName("set rejects non-numeric value")
    void testSetNonNumeric() {
        assertThrows(ConfigException.class, () -> configService.set("max-retries", "abc"));
    }

    @Test
    @DisplayName("set rejects zero value")
    void testSetZeroValue() {
        assertThrows(ConfigException.class, () -> configService.set("max-retries", "0"));
    }

    @Test
    @DisplayName("set rejects negative value")
    void testSetNegativeValue() {
        assertThrows(ConfigException.class, () -> configService.set("max-retries", "-1"));
    }

    @Test
    @DisplayName("get returns existing value")
    void testGetExists() {
        assertEquals("3", configService.get("max-retries"));
    }

    @Test
    @DisplayName("get throws for missing key")
    void testGetMissing() {
        assertThrows(ConfigException.class, () -> configService.get("nonexistent"));
    }

    @Test
    @DisplayName("getOrDefault returns value for existing key")
    void testGetOrDefaultExists() {
        assertEquals("3", configService.getOrDefault("max-retries", "99"));
    }

    @Test
    @DisplayName("getOrDefault returns default for missing key")
    void testGetOrDefaultMissing() {
        assertEquals("99", configService.getOrDefault("nonexistent", "99"));
    }

    @Test
    @DisplayName("getInt returns typed value")
    void testGetInt() {
        assertEquals(3, configService.getInt("max-retries", 10));
    }

    @Test
    @DisplayName("getInt returns default for missing key")
    void testGetIntMissing() {
        assertEquals(10, configService.getInt("nonexistent", 10));
    }

    @Test
    @DisplayName("listAll returns at least 5 defaults")
    void testListAll() {
        assertTrue(configService.listAll().size() >= 5);
    }
}

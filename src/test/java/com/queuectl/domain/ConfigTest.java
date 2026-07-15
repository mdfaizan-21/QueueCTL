package com.queuectl.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Config domain entity.
 */
class ConfigTest {

    @Test
    @DisplayName("Constructor sets key and value")
    void testConstructor() {
        Config config = new Config("max-retries", "5");
        assertEquals("max-retries", config.getKey());
        assertEquals("5", config.getValue());
    }

    @Test
    @DisplayName("Constructor rejects null key")
    void testNullKeyThrows() {
        assertThrows(NullPointerException.class, () -> new Config(null, "5"));
    }

    @Test
    @DisplayName("Constructor rejects null value")
    void testNullValueThrows() {
        assertThrows(NullPointerException.class, () -> new Config("key", null));
    }

    @Test
    @DisplayName("getValueAsInt parses integer values")
    void testGetValueAsInt() {
        Config config = new Config("max-retries", "5");
        assertEquals(5, config.getValueAsInt());
    }

    @Test
    @DisplayName("getValueAsInt throws for non-integer values")
    void testGetValueAsIntInvalid() {
        Config config = new Config("key", "not-a-number");
        assertThrows(NumberFormatException.class, config::getValueAsInt);
    }

    @Test
    @DisplayName("getValueAsLong parses long values")
    void testGetValueAsLong() {
        Config config = new Config("poll-interval", "1000");
        assertEquals(1000L, config.getValueAsLong());
    }

    @Test
    @DisplayName("getValueAsDouble parses double values")
    void testGetValueAsDouble() {
        Config config = new Config("backoff-base", "2.5");
        assertEquals(2.5, config.getValueAsDouble(), 0.001);
    }

    @Test
    @DisplayName("Equality is based on key only")
    void testEquality() {
        Config c1 = new Config("max-retries", "3");
        Config c2 = new Config("max-retries", "5");
        Config c3 = new Config("backoff-base", "3");
        assertEquals(c1, c2, "Configs with same key should be equal");
        assertNotEquals(c1, c3, "Configs with different keys should not be equal");
    }
}

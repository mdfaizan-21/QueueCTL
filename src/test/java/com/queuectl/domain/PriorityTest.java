package com.queuectl.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Priority enum.
 */
class PriorityTest {

    @Test
    @DisplayName("Priority weights are ordered: HIGH > MEDIUM > LOW")
    void testWeightOrdering() {
        assertTrue(Priority.HIGH.getWeight() > Priority.MEDIUM.getWeight());
        assertTrue(Priority.MEDIUM.getWeight() > Priority.LOW.getWeight());
    }

    @Test
    @DisplayName("fromString handles valid values case-insensitively")
    void testFromStringValid() {
        assertEquals(Priority.HIGH, Priority.fromString("HIGH"));
        assertEquals(Priority.HIGH, Priority.fromString("high"));
        assertEquals(Priority.HIGH, Priority.fromString("High"));
        assertEquals(Priority.MEDIUM, Priority.fromString("MEDIUM"));
        assertEquals(Priority.LOW, Priority.fromString("low"));
    }

    @Test
    @DisplayName("fromString returns MEDIUM for null or blank")
    void testFromStringNullOrBlank() {
        assertEquals(Priority.MEDIUM, Priority.fromString(null));
        assertEquals(Priority.MEDIUM, Priority.fromString(""));
        assertEquals(Priority.MEDIUM, Priority.fromString("   "));
    }

    @Test
    @DisplayName("fromString returns MEDIUM for unrecognized values")
    void testFromStringInvalid() {
        assertEquals(Priority.MEDIUM, Priority.fromString("CRITICAL"));
        assertEquals(Priority.MEDIUM, Priority.fromString("xyz"));
    }
}

package com.queuectl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimeUtil.
 */
class TimeUtilTest {

    @Test
    @DisplayName("now() returns a valid ISO-8601 timestamp")
    void testNow() {
        String now = TimeUtil.now();
        assertNotNull(now);
        assertTrue(now.contains("T"), "Should be ISO-8601 format");
        // Should be parseable
        assertDoesNotThrow(() -> java.time.Instant.parse(now));
    }

    @Test
    @DisplayName("formatForDisplay converts ISO-8601 to readable format")
    void testFormatForDisplay() {
        String result = TimeUtil.formatForDisplay("2026-07-15T17:30:00Z");
        assertNotNull(result);
        assertFalse(result.equals("-"));
        assertTrue(result.contains("2026"));
    }

    @Test
    @DisplayName("formatForDisplay returns '-' for null")
    void testFormatForDisplayNull() {
        assertEquals("-", TimeUtil.formatForDisplay(null));
    }

    @Test
    @DisplayName("formatForDisplay returns '-' for blank")
    void testFormatForDisplayBlank() {
        assertEquals("-", TimeUtil.formatForDisplay(""));
        assertEquals("-", TimeUtil.formatForDisplay("   "));
    }

    @Test
    @DisplayName("durationMs calculates correct duration")
    void testDurationMs() {
        long duration = TimeUtil.durationMs(
                "2026-07-15T17:30:00Z",
                "2026-07-15T17:30:05Z"
        );
        assertEquals(5000, duration);
    }

    @Test
    @DisplayName("durationMs returns -1 for null inputs")
    void testDurationMsNull() {
        assertEquals(-1, TimeUtil.durationMs(null, "2026-07-15T17:30:00Z"));
        assertEquals(-1, TimeUtil.durationMs("2026-07-15T17:30:00Z", null));
    }

    @Test
    @DisplayName("isPast returns true for past timestamps")
    void testIsPastTrue() {
        assertTrue(TimeUtil.isPast("2000-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("isPast returns false for future timestamps")
    void testIsPastFalse() {
        assertFalse(TimeUtil.isPast("2099-12-31T23:59:59Z"));
    }

    @Test
    @DisplayName("isPast returns true for null")
    void testIsPastNull() {
        assertTrue(TimeUtil.isPast(null));
    }

    @Test
    @DisplayName("futureFromNow returns a future timestamp")
    void testFutureFromNow() {
        String future = TimeUtil.futureFromNow(60);
        assertNotNull(future);
        assertFalse(TimeUtil.isPast(future));
    }
}

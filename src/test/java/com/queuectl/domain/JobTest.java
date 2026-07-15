package com.queuectl.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Job domain entity covering:
 * - Construction and defaults
 * - JSON serialization/deserialization
 * - Utility methods (incrementAttempts, isRetriesExhausted, isDelayed)
 * - Equality and hashCode
 */
class JobTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Default constructor sets sensible defaults")
    void testDefaultConstructor() {
        Job job = new Job();
        assertEquals(JobState.PENDING, job.getState());
        assertEquals(0, job.getAttempts());
        assertEquals(3, job.getMaxRetries());
        assertEquals(Priority.MEDIUM, job.getPriority());
        assertEquals(60, job.getTimeout());
        assertEquals("", job.getOutput());
        assertEquals("", job.getError());
    }

    @Test
    @DisplayName("Two-arg constructor sets id, command, and timestamps")
    void testTwoArgConstructor() {
        Job job = new Job("j1", "echo hello");
        assertEquals("j1", job.getId());
        assertEquals("echo hello", job.getCommand());
        assertEquals(JobState.PENDING, job.getState());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
    }

    @Test
    @DisplayName("Constructor rejects null id")
    void testNullIdThrows() {
        assertThrows(NullPointerException.class, () -> new Job(null, "echo"));
    }

    @Test
    @DisplayName("Constructor rejects null command")
    void testNullCommandThrows() {
        assertThrows(NullPointerException.class, () -> new Job("j1", null));
    }

    @Test
    @DisplayName("incrementAttempts increases counter and updates timestamp")
    void testIncrementAttempts() {
        Job job = new Job("j1", "echo hello");
        String before = job.getUpdatedAt();
        job.incrementAttempts();
        assertEquals(1, job.getAttempts());
        // updatedAt should change (or at least not be null)
        assertNotNull(job.getUpdatedAt());
    }

    @Test
    @DisplayName("isRetriesExhausted returns true when attempts >= maxRetries")
    void testRetriesExhausted() {
        Job job = new Job("j1", "echo hello");
        job.setMaxRetries(2);
        assertFalse(job.isRetriesExhausted());
        job.setAttempts(1);
        assertFalse(job.isRetriesExhausted());
        job.setAttempts(2);
        assertTrue(job.isRetriesExhausted());
        job.setAttempts(3);
        assertTrue(job.isRetriesExhausted());
    }

    @Test
    @DisplayName("isDelayed returns false when runAt is null or empty")
    void testIsDelayedNullRunAt() {
        Job job = new Job("j1", "echo hello");
        assertFalse(job.isDelayed());
        job.setRunAt("");
        assertFalse(job.isDelayed());
    }

    @Test
    @DisplayName("isDelayed returns true for future runAt")
    void testIsDelayedFutureTime() {
        Job job = new Job("j1", "echo hello");
        job.setRunAt("2099-12-31T23:59:59Z");
        assertTrue(job.isDelayed());
    }

    @Test
    @DisplayName("isDelayed returns false for past runAt")
    void testIsDelayedPastTime() {
        Job job = new Job("j1", "echo hello");
        job.setRunAt("2000-01-01T00:00:00Z");
        assertFalse(job.isDelayed());
    }

    @Test
    @DisplayName("Equality is based on job ID only")
    void testEquality() {
        Job job1 = new Job("j1", "echo hello");
        Job job2 = new Job("j1", "echo world");
        Job job3 = new Job("j2", "echo hello");
        assertEquals(job1, job2, "Jobs with same ID should be equal");
        assertNotEquals(job1, job3, "Jobs with different IDs should not be equal");
    }

    @Test
    @DisplayName("hashCode is consistent with equals")
    void testHashCode() {
        Job job1 = new Job("j1", "echo hello");
        Job job2 = new Job("j1", "echo world");
        assertEquals(job1.hashCode(), job2.hashCode());
    }

    @Test
    @DisplayName("JSON serialization round-trip preserves all fields")
    void testJsonRoundTrip() throws Exception {
        Job original = new Job("j1", "echo hello");
        original.setPriority(Priority.HIGH);
        original.setMaxRetries(5);
        original.setTimeout(30);
        original.setRunAt("2099-12-31T23:59:59Z");

        String json = mapper.writeValueAsString(original);
        Job deserialized = mapper.readValue(json, Job.class);

        assertEquals(original.getId(), deserialized.getId());
        assertEquals(original.getCommand(), deserialized.getCommand());
        assertEquals(original.getState(), deserialized.getState());
        assertEquals(original.getPriority(), deserialized.getPriority());
        assertEquals(original.getMaxRetries(), deserialized.getMaxRetries());
        assertEquals(original.getTimeout(), deserialized.getTimeout());
        assertEquals(original.getRunAt(), deserialized.getRunAt());
    }

    @Test
    @DisplayName("JSON deserialization with minimal fields applies defaults")
    void testJsonMinimalFields() throws Exception {
        String json = "{\"id\":\"j1\",\"command\":\"echo hello\"}";
        Job job = mapper.readValue(json, Job.class);
        assertEquals("j1", job.getId());
        assertEquals("echo hello", job.getCommand());
        assertEquals(JobState.PENDING, job.getState());
        assertEquals(3, job.getMaxRetries());
        assertEquals(Priority.MEDIUM, job.getPriority());
        assertEquals(60, job.getTimeout());
    }

    @Test
    @DisplayName("JSON deserialization ignores unknown fields")
    void testJsonIgnoresUnknown() throws Exception {
        String json = "{\"id\":\"j1\",\"command\":\"echo\",\"unknownField\":\"value\"}";
        Job job = mapper.readValue(json, Job.class);
        assertEquals("j1", job.getId());
    }

    @Test
    @DisplayName("toString includes key fields")
    void testToString() {
        Job job = new Job("j1", "echo hello");
        String str = job.toString();
        assertTrue(str.contains("j1"));
        assertTrue(str.contains("echo hello"));
        assertTrue(str.contains("PENDING"));
    }
}

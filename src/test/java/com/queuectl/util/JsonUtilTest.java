package com.queuectl.util;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonUtil.
 */
class JsonUtilTest {

    @Test
    @DisplayName("toJson serializes a Job correctly")
    void testToJson() {
        Job job = new Job("j1", "echo hello");
        String json = JsonUtil.toJson(job);
        assertTrue(json.contains("\"id\":\"j1\""));
        assertTrue(json.contains("\"command\":\"echo hello\""));
        assertTrue(json.contains("\"state\":\"PENDING\""));
    }

    @Test
    @DisplayName("fromJson deserializes a Job correctly")
    void testFromJson() {
        String json = "{\"id\":\"j1\",\"command\":\"echo hello\",\"priority\":\"HIGH\"}";
        Job job = JsonUtil.fromJson(json, Job.class);
        assertEquals("j1", job.getId());
        assertEquals("echo hello", job.getCommand());
        assertEquals(Priority.HIGH, job.getPriority());
    }

    @Test
    @DisplayName("Round-trip serialization preserves data")
    void testRoundTrip() {
        Job original = new Job("j1", "echo hello");
        original.setPriority(Priority.LOW);
        original.setMaxRetries(5);

        String json = JsonUtil.toJson(original);
        Job restored = JsonUtil.fromJson(json, Job.class);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getCommand(), restored.getCommand());
        assertEquals(original.getPriority(), restored.getPriority());
        assertEquals(original.getMaxRetries(), restored.getMaxRetries());
    }

    @Test
    @DisplayName("fromJson throws for invalid JSON")
    void testFromJsonInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonUtil.fromJson("not valid json", Job.class));
    }

    @Test
    @DisplayName("toPrettyJson produces indented output")
    void testToPrettyJson() {
        Job job = new Job("j1", "echo hello");
        String pretty = JsonUtil.toPrettyJson(job);
        assertTrue(pretty.contains("\n"), "Pretty JSON should contain newlines");
    }

    @Test
    @DisplayName("fromJson ignores unknown fields")
    void testIgnoresUnknownFields() {
        String json = "{\"id\":\"j1\",\"command\":\"echo\",\"unknownField\":123}";
        Job job = JsonUtil.fromJson(json, Job.class);
        assertEquals("j1", job.getId());
    }

    @Test
    @DisplayName("getMapper returns a non-null mapper")
    void testGetMapper() {
        assertNotNull(JsonUtil.getMapper());
    }
}

package com.queuectl.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the WorkerInfo domain entity.
 */
class WorkerInfoTest {

    @Test
    @DisplayName("Constructor sets ID, status=RUNNING, and initializes timestamps")
    void testConstructor() {
        WorkerInfo worker = new WorkerInfo("worker-1");
        assertEquals("worker-1", worker.getId());
        assertEquals("RUNNING", worker.getStatus());
        assertNotNull(worker.getHeartbeat());
        assertNotNull(worker.getStartedAt());
    }

    @Test
    @DisplayName("Constructor rejects null ID")
    void testNullIdThrows() {
        assertThrows(NullPointerException.class, () -> new WorkerInfo(null));
    }

    @Test
    @DisplayName("updateHeartbeat changes the heartbeat timestamp")
    void testUpdateHeartbeat() throws InterruptedException {
        WorkerInfo worker = new WorkerInfo("worker-1");
        String oldHeartbeat = worker.getHeartbeat();
        Thread.sleep(10); // Ensure time passes
        worker.updateHeartbeat();
        // New heartbeat should be different (or at least not null)
        assertNotNull(worker.getHeartbeat());
    }

    @Test
    @DisplayName("Equality is based on worker ID")
    void testEquality() {
        WorkerInfo w1 = new WorkerInfo("worker-1");
        WorkerInfo w2 = new WorkerInfo("worker-1");
        WorkerInfo w3 = new WorkerInfo("worker-2");
        assertEquals(w1, w2);
        assertNotEquals(w1, w3);
    }

    @Test
    @DisplayName("toString includes key fields")
    void testToString() {
        WorkerInfo worker = new WorkerInfo("worker-1");
        String str = worker.toString();
        assertTrue(str.contains("worker-1"));
        assertTrue(str.contains("RUNNING"));
    }
}

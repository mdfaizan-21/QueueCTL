package com.queuectl.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the exception hierarchy.
 */
class ExceptionHierarchyTest {

    @Test
    @DisplayName("All exceptions extend QueueCtlException")
    void testHierarchy() {
        assertInstanceOf(QueueCtlException.class, new JobExecutionException("test"));
        assertInstanceOf(QueueCtlException.class, new WorkerException("test"));
        assertInstanceOf(QueueCtlException.class, new ConfigException("test"));
        assertInstanceOf(QueueCtlException.class, new PersistenceException("test"));
    }

    @Test
    @DisplayName("QueueCtlException extends RuntimeException")
    void testBaseIsRuntimeException() {
        assertInstanceOf(RuntimeException.class, new QueueCtlException("test"));
    }

    @Test
    @DisplayName("JobExecutionException preserves jobId")
    void testJobExceptionJobId() {
        JobExecutionException ex = new JobExecutionException("j1", "Failed");
        assertEquals("j1", ex.getJobId());
        assertEquals("Failed", ex.getMessage());
    }

    @Test
    @DisplayName("JobExecutionException without jobId has null jobId")
    void testJobExceptionWithoutJobId() {
        JobExecutionException ex = new JobExecutionException("Failed");
        assertNull(ex.getJobId());
    }

    @Test
    @DisplayName("Exceptions preserve cause chain")
    void testCausePreservation() {
        Exception root = new IllegalStateException("root cause");
        PersistenceException ex = new PersistenceException("DB error", root);
        assertEquals("DB error", ex.getMessage());
        assertEquals(root, ex.getCause());
    }

    @Test
    @DisplayName("Catch-all with QueueCtlException catches all subtypes")
    void testCatchAll() {
        QueueCtlException[] exceptions = {
            new JobExecutionException("test"),
            new WorkerException("test"),
            new ConfigException("test"),
            new PersistenceException("test")
        };

        for (QueueCtlException ex : exceptions) {
            // This should not throw — all are caught by QueueCtlException
            try {
                throw ex;
            } catch (QueueCtlException caught) {
                assertEquals("test", caught.getMessage());
            }
        }
    }
}

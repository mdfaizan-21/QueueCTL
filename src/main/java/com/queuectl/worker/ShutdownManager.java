package com.queuectl.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles graceful shutdown of the worker system.
 *
 * Installs a JVM shutdown hook that triggers when:
 * - SIGTERM is sent (e.g., Ctrl+C)
 * - The JVM exits normally
 *
 * The hook ensures:
 * - All workers finish their current job
 * - Heartbeat scheduler stops
 * - Worker registrations are cleaned up
 * - PID lock is released
 */
public class ShutdownManager {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownManager.class);

    private final WorkerManager workerManager;
    private final WorkerHeartbeat workerHeartbeat;

    public ShutdownManager(WorkerManager workerManager, WorkerHeartbeat workerHeartbeat) {
        this.workerManager = workerManager;
        this.workerHeartbeat = workerHeartbeat;
    }

    /**
     * Registers a JVM shutdown hook for graceful teardown.
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, gracefully stopping...");

            // 1. Stop heartbeat first (no more DB writes)
            workerHeartbeat.stop();

            // 2. Shut down all workers (waits for current jobs to finish)
            workerManager.shutdown();

            // 3. Release PID lock
            com.queuectl.util.LockUtil.releaseLock();

            logger.info("Shutdown complete.");
        }, "shutdown-hook"));

        logger.info("Shutdown hook registered");
    }
}

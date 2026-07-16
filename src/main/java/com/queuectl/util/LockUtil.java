package com.queuectl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File-based PID lock to prevent multiple worker processes.
 *
 * <p>Creates a lock file containing the current PID. Used by:
 * <ul>
 *   <li>{@code worker start} — acquires lock</li>
 *   <li>{@code worker stop} — reads PID from lock, signals stop, removes lock</li>
 * </ul>
 */
public final class LockUtil {

    private static final Logger logger = LoggerFactory.getLogger(LockUtil.class);
    private static final String LOCK_FILE = "queuectl.pid";

    private LockUtil() {
        // Utility class
    }

    /**
     * Attempts to acquire the worker lock.
     *
     * @return true if lock was acquired, false if another worker is running
     */
    public static boolean acquireLock() {
        Path lockPath = Path.of(LOCK_FILE);
        if (Files.exists(lockPath)) {
            logger.warn("Lock file exists: {}. Another worker may be running.", lockPath);
            return false;
        }
        try {
            long pid = ProcessHandle.current().pid();
            Files.writeString(lockPath, String.valueOf(pid));
            logger.info("Worker lock acquired: PID={}", pid);
            return true;
        } catch (IOException e) {
            logger.error("Failed to create lock file", e);
            return false;
        }
    }

    /**
     * Releases the worker lock by deleting the PID file.
     */
    public static void releaseLock() {
        try {
            Path lockPath = Path.of(LOCK_FILE);
            if (Files.deleteIfExists(lockPath)) {
                logger.info("Worker lock released");
            }
        } catch (IOException e) {
            logger.error("Failed to delete lock file", e);
        }
    }

    /**
     * Checks if a worker lock exists.
     *
     * @return true if the lock file exists
     */
    public static boolean isLocked() {
        return Files.exists(Path.of(LOCK_FILE));
    }

    /**
     * Reads the PID from the lock file.
     *
     * @return the PID, or -1 if not readable
     */
    public static long readPid() {
        try {
            String content = Files.readString(Path.of(LOCK_FILE)).trim();
            return Long.parseLong(content);
        } catch (Exception e) {
            return -1;
        }
    }
}

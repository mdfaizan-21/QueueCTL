package com.queuectl.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for consistent timestamp formatting throughout QueueCTL.
 *
 * <p>All timestamps are stored as ISO-8601 UTC strings in the database.
 */
public final class TimeUtil {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private TimeUtil() {
        // Utility class — no instantiation
    }

    /**
     * Returns the current time as an ISO-8601 UTC string.
     *
     * @return current timestamp (e.g., "2026-07-15T17:30:00Z")
     */
    public static String now() {
        return Instant.now().toString();
    }

    /**
     * Formats an ISO-8601 timestamp for human-readable display.
     *
     * @param isoTimestamp ISO-8601 timestamp string
     * @return formatted string (e.g., "2026-07-15 17:30:00"), or the original if parsing fails
     */
    public static String formatForDisplay(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return "-";
        }
        try {
            Instant instant = Instant.parse(isoTimestamp);
            return DISPLAY_FORMATTER.format(instant);
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    /**
     * Calculates the duration in milliseconds between two ISO-8601 timestamps.
     *
     * @param start start timestamp
     * @param end   end timestamp
     * @return duration in milliseconds, or -1 if parsing fails
     */
    public static long durationMs(String start, String end) {
        if (start == null || end == null) {
            return -1;
        }
        try {
            Instant startInstant = Instant.parse(start);
            Instant endInstant = Instant.parse(end);
            return endInstant.toEpochMilli() - startInstant.toEpochMilli();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Checks if an ISO-8601 timestamp is in the past.
     *
     * @param isoTimestamp the timestamp to check
     * @return true if the timestamp is before now
     */
    public static boolean isPast(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return true;
        }
        try {
            return Instant.parse(isoTimestamp).isBefore(Instant.now());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Computes a future timestamp by adding seconds to now.
     *
     * @param seconds number of seconds to add
     * @return ISO-8601 timestamp in the future
     */
    public static String futureFromNow(long seconds) {
        return Instant.now().plusSeconds(seconds).toString();
    }
}

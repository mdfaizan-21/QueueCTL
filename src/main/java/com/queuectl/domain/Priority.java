package com.queuectl.domain;

/**
 * Job priority levels for queue ordering.
 *
 * Jobs with higher priority are dequeued first.
 * The numeric weight is used for database sorting (higher = processed first).
 */
public enum Priority {

    HIGH(3),
    MEDIUM(2),
    LOW(1);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    /**
     * Returns the numeric weight for sorting.
     * Higher weight = higher priority.
     *
     * @return the priority weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Converts a string to a Priority, case-insensitive.
     * Returns MEDIUM if the input is null or unrecognized.
     *
     * @param value the string representation
     * @return the corresponding Priority
     */
    public static Priority fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return Priority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}

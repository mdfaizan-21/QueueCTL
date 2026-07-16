package com.queuectl.util;

/**
 * Utility for building OS-appropriate shell commands.
 */
public final class CommandUtil {

    private CommandUtil() {
        // Utility class
    }

    /**
     * Wraps a command string into an OS-specific shell invocation.
     *
     * @param command the command to wrap (e.g., "echo hello")
     * @return array suitable for ProcessBuilder (e.g., ["cmd", "/c", "echo hello"])
     */
    public static String[] wrapForShell(String command) {
        if (isWindows()) {
            return new String[]{"cmd", "/c", command};
        } else {
            return new String[]{"sh", "-c", command};
        }
    }

    /**
     * Checks if the current OS is Windows.
     *
     * @return true if running on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

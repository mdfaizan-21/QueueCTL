package com.queuectl;

import com.queuectl.cli.QueueCtlCommand;
import picocli.CommandLine;

/**
 * Main entry point for the QueueCTL application.
 * Bootstraps Picocli and delegates to the root command.
 */
public class QueueCtlApp {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new QueueCtlCommand())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}

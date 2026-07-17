package com.queuectl.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Root command for QueueCTL.
 * Acts as a parent for all subcommands (enqueue, worker, status, list, dlq, config, metrics).
 * This class is a placeholder that will gain subcommands in later phases.
 */
@Command(
    name = "queuectl",
    mixinStandardHelpOptions = true,
    version = "QueueCTL 1.0.0",
    description = "A production-grade CLI background job queue system.",
    subcommands = {
        CommandLine.HelpCommand.class,
        EnqueueCommand.class,
        ShowCommand.class,
        ListCommand.class,
        StatusCommand.class,
        ConfigCommand.class,
        DlqCommand.class,
        MetricsCommand.class,
        WorkerCommand.class
    }
)
public class QueueCtlCommand implements Runnable {

    @Override
    public void run() {
        // When no subcommand is given, print usage help
        new CommandLine(this).usage(System.out);
    }
}

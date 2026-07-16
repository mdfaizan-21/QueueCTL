package com.queuectl.cli;

import picocli.CommandLine.Command;

/**
 * CLI command: queuectl worker start|stop
 *
 * Placeholder — full worker implementation comes in Phase 6.
 */
@Command(name = "worker", description = "Manage background workers.",
         subcommands = {
             WorkerCommand.StartCommand.class,
             WorkerCommand.StopCommand.class
         })
public class WorkerCommand implements Runnable {

    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }

    @Command(name = "start", description = "Start background workers.")
    static class StartCommand implements Runnable {

        @picocli.CommandLine.Option(names = "--count", defaultValue = "1",
                description = "Number of worker threads (default: 1)")
        private int count;

        @Override
        public void run() {
            System.out.println("Starting " + count + " worker(s)...");
            System.out.println("⚠ Worker engine will be implemented in Phase 6.");
        }
    }

    @Command(name = "stop", description = "Stop all running workers.")
    static class StopCommand implements Runnable {

        @Override
        public void run() {
            System.out.println("Stopping workers...");
            System.out.println("⚠ Worker engine will be implemented in Phase 6.");
        }
    }
}

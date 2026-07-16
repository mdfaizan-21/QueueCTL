package com.queuectl.cli;

import com.queuectl.config.AppContext;
import picocli.CommandLine.Command;

/**
 * CLI command: queuectl status
 */
@Command(name = "status", description = "Show system status overview.")
public class StatusCommand implements Runnable {

    @Override
    public void run() {
        try {
            AppContext ctx = AppContext.initialize();
            int[] counts = ctx.jobService().getStateCounts();
            int total = ctx.jobService().totalJobs();
            int activeWorkers = ctx.workerRepository().countActive();

            System.out.println("╔══════════════════════════════════╗");
            System.out.println("║       QueueCTL System Status     ║");
            System.out.println("╠══════════════════════════════════╣");
            System.out.printf("║  Pending:     %-18d ║%n", counts[0]);
            System.out.printf("║  Processing:  %-18d ║%n", counts[1]);
            System.out.printf("║  Completed:   %-18d ║%n", counts[2]);
            System.out.printf("║  Failed:      %-18d ║%n", counts[3]);
            System.out.printf("║  Dead (DLQ):  %-18d ║%n", counts[4]);
            System.out.println("╠══════════════════════════════════╣");
            System.out.printf("║  Total Jobs:  %-18d ║%n", total);
            System.out.printf("║  Workers:     %-18d ║%n", activeWorkers);
            System.out.println("╚══════════════════════════════════╝");

            boolean healthy = ctx.databaseManager().isHealthy();
            System.out.println("\nDatabase: " + (healthy ? "✓ Healthy" : "✗ Unhealthy"));

        } catch (Exception e) {
            System.err.println("✗ Failed to get status: " + e.getMessage());
        }
    }
}

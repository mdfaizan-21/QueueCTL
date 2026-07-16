package com.queuectl.cli;

import com.queuectl.config.AppContext;
import picocli.CommandLine.Command;

import java.util.Map;

/**
 * CLI command: queuectl metrics
 */
@Command(name = "metrics", description = "Show job processing metrics and statistics.")
public class MetricsCommand implements Runnable {

    @Override
    public void run() {
        try {
            AppContext ctx = AppContext.initialize();
            Map<String, String> metrics = ctx.metricsService().gatherMetrics();

            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║        QueueCTL Metrics              ║");
            System.out.println("╠══════════════════════════════════════╣");

            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                System.out.printf("║  %-20s %-15s ║%n", entry.getKey() + ":", entry.getValue());
            }

            System.out.println("╚══════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("✗ Failed to gather metrics: " + e.getMessage());
        }
    }
}

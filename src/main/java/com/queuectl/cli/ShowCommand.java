package com.queuectl.cli;

import com.queuectl.config.AppContext;
import com.queuectl.domain.Job;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Optional;

/**
 * CLI command: queuectl show <jobId>
 *
 * Displays the full details of a job including output, error, and timestamps.
 */
@Command(name = "show", description = "Show detailed information about a specific job.")
public class ShowCommand implements Runnable {

    @Parameters(index = "0", description = "The job ID to look up")
    private String jobId;

    @Override
    public void run() {
        try {
            AppContext ctx = AppContext.initialize();
            Optional<Job> jobOpt = ctx.jobService().findById(jobId);

            if (jobOpt.isEmpty()) {
                System.err.println("✗ Job not found: " + jobId);
                return;
            }

            Job job = jobOpt.get();

            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║                  Job Details                     ║");
            System.out.println("╠══════════════════════════════════════════════════╣");
            printField("ID", job.getId());
            printField("Command", job.getCommand());
            printField("State", job.getState().toString());
            printField("Priority", job.getPriority().toString());
            printField("Attempts", job.getAttempts() + " / " + job.getMaxRetries());
            printField("Timeout", job.getTimeout() + "s");
            printField("Created At", job.getCreatedAt());
            printField("Updated At", job.getUpdatedAt());

            if (job.getRunAt() != null && !job.getRunAt().isBlank()) {
                printField("Run At", job.getRunAt());
            }
            if (job.getLockedBy() != null && !job.getLockedBy().isBlank()) {
                printField("Locked By", job.getLockedBy());
                printField("Locked At", job.getLockedAt());
            }
            if (job.getNextRetryAt() != null && !job.getNextRetryAt().isBlank()) {
                printField("Next Retry", job.getNextRetryAt());
            }
            System.out.println("╠══════════════════════════════════════════════════╣");

            if (job.getOutput() != null && !job.getOutput().isBlank()) {
                System.out.println("║  Output:");
                for (String line : job.getOutput().split("\n")) {
                    System.out.println("║    " + line);
                }
            }
            if (job.getError() != null && !job.getError().isBlank()) {
                System.out.println("║  Error:");
                for (String line : job.getError().split("\n")) {
                    System.out.println("║    " + line);
                }
            }

            System.out.println("╚══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("✗ Failed to show job: " + e.getMessage());
        }
    }

    private void printField(String label, String value) {
        System.out.printf("║  %-14s %s%n", label + ":", value != null ? value : "-");
    }
}

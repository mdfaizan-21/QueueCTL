package com.queuectl.cli;

import com.queuectl.config.AppContext;
import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.util.TimeUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * CLI command: queuectl list [--state STATE]
 */
@Command(name = "list", description = "List jobs, optionally filtered by state.")
public class ListCommand implements Runnable {

    @Option(names = "--state", description = "Filter by state: PENDING, PROCESSING, COMPLETED, FAILED, DEAD")
    private String state;

    @Override
    public void run() {
        try {
            AppContext ctx = AppContext.initialize();

            JobState filter = null;
            if (state != null && !state.isBlank()) {
                try {
                    filter = JobState.valueOf(state.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("✗ Invalid state: " + state);
                    System.err.println("  Valid states: PENDING, PROCESSING, COMPLETED, FAILED, DEAD");
                    return;
                }
            }

            List<Job> jobs = ctx.jobService().listJobs(filter);

            if (jobs.isEmpty()) {
                System.out.println("No jobs found" + (filter != null ? " with state " + filter : "") + ".");
                return;
            }

            // Header
            System.out.printf("%-12s %-25s %-12s %-8s %-8s %-20s%n",
                    "ID", "COMMAND", "STATE", "PRIO", "ATTEMPTS", "CREATED");
            System.out.println("-".repeat(90));

            // Rows
            for (Job job : jobs) {
                String cmd = job.getCommand();
                if (cmd.length() > 23) cmd = cmd.substring(0, 20) + "...";
                System.out.printf("%-12s %-25s %-12s %-8s %d/%-6d %-20s%n",
                        job.getId(),
                        cmd,
                        job.getState(),
                        job.getPriority(),
                        job.getAttempts(), job.getMaxRetries(),
                        TimeUtil.formatForDisplay(job.getCreatedAt()));
            }

            System.out.println("\nTotal: " + jobs.size() + " job(s)");

        } catch (Exception e) {
            System.err.println("✗ Failed to list jobs: " + e.getMessage());
        }
    }
}

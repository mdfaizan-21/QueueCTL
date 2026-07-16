package com.queuectl.cli;

import com.queuectl.config.AppContext;
import com.queuectl.domain.Job;
import com.queuectl.util.TimeUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * CLI command: queuectl dlq list | queuectl dlq retry <jobId>
 */
@Command(name = "dlq", description = "Manage the Dead Letter Queue.",
         subcommands = {
             DlqCommand.ListDlqCommand.class,
             DlqCommand.RetryDlqCommand.class
         })
public class DlqCommand implements Runnable {

    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }

    @Command(name = "list", description = "List all jobs in the Dead Letter Queue.")
    static class ListDlqCommand implements Runnable {

        @Override
        public void run() {
            try {
                AppContext ctx = AppContext.initialize();
                List<Job> deadJobs = ctx.dlqService().listDeadJobs();

                if (deadJobs.isEmpty()) {
                    System.out.println("Dead Letter Queue is empty.");
                    return;
                }

                System.out.printf("%-12s %-25s %-8s %-30s %-20s%n",
                        "ID", "COMMAND", "ATTEMPTS", "ERROR", "CREATED");
                System.out.println("-".repeat(100));

                for (Job job : deadJobs) {
                    String cmd = job.getCommand();
                    if (cmd.length() > 23) cmd = cmd.substring(0, 20) + "...";
                    String err = job.getError() != null ? job.getError() : "";
                    if (err.length() > 28) err = err.substring(0, 25) + "...";
                    System.out.printf("%-12s %-25s %-8d %-30s %-20s%n",
                            job.getId(), cmd, job.getAttempts(), err,
                            TimeUtil.formatForDisplay(job.getCreatedAt()));
                }

                System.out.println("\nTotal: " + deadJobs.size() + " dead job(s)");

            } catch (Exception e) {
                System.err.println("✗ Failed to list DLQ: " + e.getMessage());
            }
        }
    }

    @Command(name = "retry", description = "Retry a dead job (reset to PENDING).")
    static class RetryDlqCommand implements Runnable {

        @Parameters(index = "0", description = "Job ID to retry")
        private String jobId;

        @Override
        public void run() {
            try {
                AppContext ctx = AppContext.initialize();
                ctx.dlqService().retryJob(jobId);
                System.out.println("✓ Job '" + jobId + "' retried — reset to PENDING");
            } catch (IllegalArgumentException e) {
                System.err.println("✗ " + e.getMessage());
            } catch (Exception e) {
                System.err.println("✗ Failed to retry job: " + e.getMessage());
            }
        }
    }
}

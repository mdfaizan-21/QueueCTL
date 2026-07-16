package com.queuectl.cli;

import com.queuectl.config.AppContext;
import com.queuectl.domain.Job;
import com.queuectl.util.JsonUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * CLI command: queuectl enqueue '{"id":"job1","command":"echo hello"}'
 */
@Command(name = "enqueue", description = "Enqueue a new job for processing.")
public class EnqueueCommand implements Runnable {

    @Parameters(index = "0", description = "Job JSON (e.g., '{\"id\":\"j1\",\"command\":\"echo hello\"}')")
    private String jobJson;

    @Override
    public void run() {
        try {
            AppContext ctx = AppContext.initialize();
            Job job = JsonUtil.fromJson(jobJson, Job.class);
            ctx.jobService().enqueue(job);
            System.out.println("✓ Job enqueued: " + job.getId());
            System.out.println("  Command:  " + job.getCommand());
            System.out.println("  Priority: " + job.getPriority());
            System.out.println("  Timeout:  " + job.getTimeout() + "s");
            if (job.getRunAt() != null && !job.getRunAt().isBlank()) {
                System.out.println("  Run At:   " + job.getRunAt());
            }
        } catch (IllegalArgumentException e) {
            System.err.println("✗ Invalid job: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("✗ Failed to enqueue: " + e.getMessage());
        }
    }
}

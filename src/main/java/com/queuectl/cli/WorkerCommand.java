package com.queuectl.cli;

import com.queuectl.config.AppContext;
import com.queuectl.util.LockUtil;
import com.queuectl.worker.ShutdownManager;
import com.queuectl.worker.WorkerHeartbeat;
import com.queuectl.worker.WorkerManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI command: queuectl worker start|stop
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

        @Option(names = "--count", defaultValue = "1",
                description = "Number of worker threads (default: 1)")
        private int count;

        @Override
        public void run() {
            // Check PID lock — prevent multiple worker processes
            if (!LockUtil.acquireLock()) {
                System.err.println("✗ Another worker process is already running.");
                System.err.println("  Use 'queuectl worker stop' to stop it first.");
                System.err.println("  If the previous process crashed, delete queuectl.pid manually.");
                return;
            }

            try {
                AppContext ctx = AppContext.initialize();

                // Create the worker manager
                WorkerManager workerManager = new WorkerManager(
                        ctx.jobRepository(),
                        ctx.workerRepository(),
                        ctx.retryService(),
                        ctx.metricsService(),
                        ctx.configService());

                // Create heartbeat
                WorkerHeartbeat heartbeat = new WorkerHeartbeat(
                        workerManager, ctx.workerRepository(), ctx.configService());

                // Register shutdown hook for graceful Ctrl+C handling
                ShutdownManager shutdownManager = new ShutdownManager(workerManager, heartbeat);
                shutdownManager.registerShutdownHook();

                // Start workers and heartbeat
                workerManager.start(count);
                heartbeat.start();

                System.out.println("✓ " + count + " worker(s) started. Press Ctrl+C to stop.");
                System.out.println("  Workers are polling for jobs...");

                // Block the main thread — workers run in the thread pool.
                // The shutdown hook handles cleanup when Ctrl+C is pressed.
                Thread.currentThread().join();

            } catch (InterruptedException e) {
                // Normal exit via Ctrl+C — shutdown hook handles cleanup
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("✗ Failed to start workers: " + e.getMessage());
                LockUtil.releaseLock();
            }
        }
    }

    @Command(name = "stop", description = "Stop all running workers (sends signal).")
    static class StopCommand implements Runnable {

        @Override
        public void run() {
            if (!LockUtil.isLocked()) {
                System.err.println("✗ No worker process is currently running.");
                return;
            }

            long pid = LockUtil.readPid();
            if (pid <= 0) {
                System.err.println("✗ Could not read PID from lock file.");
                return;
            }

            System.out.println("Stopping worker process (PID: " + pid + ")...");

            // Send a graceful termination signal to the worker process
            ProcessHandle.of(pid).ifPresentOrElse(
                    handle -> {
                        handle.destroy(); // sends SIGTERM
                        System.out.println("✓ Termination signal sent to PID " + pid);
                        System.out.println("  Workers will finish current jobs before stopping.");
                    },
                    () -> {
                        System.out.println("Process " + pid + " not found (may have already stopped).");
                        LockUtil.releaseLock();
                        System.out.println("✓ Lock file cleaned up.");
                    }
            );
        }
    }
}

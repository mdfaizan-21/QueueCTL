package com.queuectl.worker;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.domain.WorkerInfo;
import com.queuectl.repository.JobRepository;
import com.queuectl.repository.WorkerRepository;
import com.queuectl.service.ConfigService;
import com.queuectl.service.MetricsService;
import com.queuectl.service.RetryService;
import com.queuectl.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single worker thread that polls for and executes jobs.
 *
 * Lifecycle:
 *   1. Register itself in the workers table
 *   2. Poll loop: fetchAndLock → execute → handle result
 *   3. On shutdown: mark itself STOPPED, deregister
 *
 * Each Worker runs on its own thread and is managed by WorkerManager.
 */
public class Worker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    private final String workerId;
    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final RetryService retryService;
    private final MetricsService metricsService;
    private final ConfigService configService;
    private final JobExecutor jobExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Thread workerThread;

    public Worker(String workerId,
                  JobRepository jobRepository,
                  WorkerRepository workerRepository,
                  RetryService retryService,
                  MetricsService metricsService,
                  ConfigService configService) {
        this.workerId = workerId;
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.retryService = retryService;
        this.metricsService = metricsService;
        this.configService = configService;
        this.jobExecutor = new JobExecutor();
    }

    @Override
    public void run() {
        this.workerThread = Thread.currentThread();
        logger.info("Worker {} started", workerId);
        register();

        long pollIntervalMs = configService.getLong("poll-interval", 1) * 1000;

        while (running.get()) {
            try {
                Optional<Job> jobOpt = jobRepository.fetchAndLock(workerId);

                if (jobOpt.isPresent()) {
                    processJob(jobOpt.get());
                } else {
                    // No work available — sleep before next poll
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Worker {} interrupted, shutting down", workerId);
                break;
            } catch (Exception e) {
                logger.error("Worker {} encountered error: {}", workerId, e.getMessage(), e);
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        deregister();
        logger.info("Worker {} stopped", workerId);
    }

    /**
     * Processes a single job: execute, then mark completed or handle failure.
     */
    private void processJob(Job job) {
        logger.info("Worker {} processing job {}", workerId, job.getId());

        JobExecutor.Result result = jobExecutor.execute(job);

        if (result.success()) {
            // Mark job COMPLETED
            job.setState(JobState.COMPLETED);
            job.setOutput(result.output());
            job.setUpdatedAt(TimeUtil.now());
            job.setLockedBy(null);
            job.setLockedAt(null);
            jobRepository.update(job);

            metricsService.incrementMetric("jobs_processed", 1);
            metricsService.incrementMetric("total_execution_time_ms", result.durationMs());

            logger.info("Job {} completed by worker {} in {}ms",
                    job.getId(), workerId, result.durationMs());
        } else {
            // Hand off to retry service (retry or DLQ)
            boolean retried = retryService.handleFailure(job, result.error());
            if (retried) {
                metricsService.incrementMetric("jobs_retried", 1);
            }
        }
    }

    /**
     * Registers this worker in the database.
     */
    private void register() {
        WorkerInfo info = new WorkerInfo(workerId);
        info.setStatus("RUNNING");
        info.setHeartbeat(TimeUtil.now());
        workerRepository.save(info);
    }

    /**
     * Marks this worker as STOPPED and removes from database.
     */
    private void deregister() {
        workerRepository.updateStatus(workerId, "STOPPED");
        workerRepository.delete(workerId);
    }

    /**
     * Signals this worker to stop after current job completes.
     * Interrupts the thread to wake it from poll-interval sleep.
     */
    public void shutdown() {
        running.set(false);
        Thread t = workerThread;
        if (t != null) {
            t.interrupt();
        }
    }

    /**
     * Checks if this worker is still running.
     */
    public boolean isRunning() {
        return running.get();
    }

    public String getWorkerId() {
        return workerId;
    }
}

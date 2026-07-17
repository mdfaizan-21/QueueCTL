package com.queuectl.worker;

import com.queuectl.repository.JobRepository;
import com.queuectl.repository.WorkerRepository;
import com.queuectl.service.ConfigService;
import com.queuectl.service.MetricsService;
import com.queuectl.service.RetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages a pool of Worker threads.
 *
 * Responsible for:
 * - Starting N worker threads
 * - Graceful shutdown of all workers
 * - Tracking active worker instances
 */
public class WorkerManager {

    private static final Logger logger = LoggerFactory.getLogger(WorkerManager.class);

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final RetryService retryService;
    private final MetricsService metricsService;
    private final ConfigService configService;

    private final List<Worker> workers = new ArrayList<>();
    private ExecutorService executorService;

    public WorkerManager(JobRepository jobRepository,
                         WorkerRepository workerRepository,
                         RetryService retryService,
                         MetricsService metricsService,
                         ConfigService configService) {
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.retryService = retryService;
        this.metricsService = metricsService;
        this.configService = configService;
    }

    /**
     * Starts the specified number of worker threads.
     *
     * @param count number of workers to start
     */
    public void start(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Worker count must be > 0");
        }

        // Reset stalled jobs from previous crashes
        int reset = jobRepository.resetStalledJobs();
        if (reset > 0) {
            logger.info("Recovered {} stalled jobs from previous run", reset);
        }

        executorService = Executors.newFixedThreadPool(count);
        logger.info("Starting {} worker(s)...", count);

        for (int i = 0; i < count; i++) {
            String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
            Worker worker = new Worker(workerId, jobRepository, workerRepository,
                    retryService, metricsService, configService);
            workers.add(worker);
            executorService.submit(worker);
            logger.info("Worker {} submitted to thread pool", workerId);
        }

        logger.info("All {} worker(s) started successfully", count);
    }

    /**
     * Gracefully shuts down all workers.
     *
     * Signals each worker to stop, then waits up to 30 seconds
     * for current jobs to finish before force-killing.
     */
    public void shutdown() {
        logger.info("Shutting down {} worker(s)...", workers.size());

        // Signal all workers to stop
        for (Worker worker : workers) {
            worker.shutdown();
        }

        // Shut down the thread pool
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("Workers did not terminate in 30s, forcing shutdown...");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        workers.clear();
        logger.info("All workers shut down");
    }

    /**
     * Returns the number of active workers.
     */
    public int getActiveCount() {
        return (int) workers.stream().filter(Worker::isRunning).count();
    }

    /**
     * Returns the list of worker instances.
     */
    public List<Worker> getWorkers() {
        return List.copyOf(workers);
    }
}

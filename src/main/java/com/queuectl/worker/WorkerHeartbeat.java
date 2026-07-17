package com.queuectl.worker;

import com.queuectl.repository.WorkerRepository;
import com.queuectl.service.ConfigService;
import com.queuectl.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically updates the heartbeat timestamp for all active workers.
 *
 * This lets other processes (or a future monitor) detect stale workers
 * that may have crashed without proper deregistration.
 */
public class WorkerHeartbeat {

    private static final Logger logger = LoggerFactory.getLogger(WorkerHeartbeat.class);

    private final WorkerManager workerManager;
    private final WorkerRepository workerRepository;
    private final ConfigService configService;
    private ScheduledExecutorService scheduler;

    public WorkerHeartbeat(WorkerManager workerManager,
                           WorkerRepository workerRepository,
                           ConfigService configService) {
        this.workerManager = workerManager;
        this.workerRepository = workerRepository;
        this.configService = configService;
    }

    /**
     * Starts the heartbeat scheduler.
     * Interval is read from config key "worker-heartbeat" (in seconds).
     */
    public void start() {
        long intervalSeconds = configService.getLong("worker-heartbeat", 5);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::beat, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        logger.info("Heartbeat scheduler started (interval={}s)", intervalSeconds);
    }

    /**
     * Sends a heartbeat for every running worker.
     */
    private void beat() {
        try {
            String now = TimeUtil.now();
            List<Worker> activeWorkers = workerManager.getWorkers();
            for (Worker worker : activeWorkers) {
                if (worker.isRunning()) {
                    workerRepository.updateHeartbeat(worker.getWorkerId(), now);
                }
            }
            logger.debug("Heartbeat sent for {} worker(s)", activeWorkers.size());
        } catch (Exception e) {
            logger.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    /**
     * Stops the heartbeat scheduler.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            logger.info("Heartbeat scheduler stopped");
        }
    }
}

package com.queuectl.config;

import com.queuectl.persistence.DatabaseManager;
import com.queuectl.persistence.SqliteConnectionFactory;
import com.queuectl.repository.*;
import com.queuectl.service.*;

/**
 * Manual dependency injection container.
 *
 * <p>Wires all components together without a framework.
 * Acts as a simple service locator for CLI commands.
 *
 * <p>Usage:
 * <pre>
 *   AppContext ctx = AppContext.initialize();
 *   ctx.jobService().enqueue(job);
 * </pre>
 */
public class AppContext {

    private static final String DB_PATH = "queuectl.db";

    private final SqliteConnectionFactory connectionFactory;
    private final DatabaseManager databaseManager;
    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final ConfigRepository configRepository;
    private final JobService jobService;
    private final ConfigService configService;
    private final DlqService dlqService;
    private final MetricsService metricsService;
    private final RetryService retryService;

    private AppContext() {
        // 1. Persistence
        this.connectionFactory = new SqliteConnectionFactory(DB_PATH);
        this.databaseManager = new DatabaseManager(connectionFactory);
        databaseManager.initialize();

        // 2. Repositories
        this.jobRepository = new SqliteJobRepository(connectionFactory);
        this.workerRepository = new SqliteWorkerRepository(connectionFactory);
        this.configRepository = new SqliteConfigRepository(connectionFactory);

        // 3. Services
        this.configService = new ConfigService(configRepository);
        this.jobService = new JobService(jobRepository);
        this.dlqService = new DlqService(jobRepository);
        this.retryService = new RetryService(jobRepository, configService);
        this.metricsService = new MetricsService(jobRepository, workerRepository, connectionFactory);
    }

    /**
     * Creates and initializes the application context.
     * Runs database migrations on first call.
     *
     * @return initialized context
     */
    public static AppContext initialize() {
        return new AppContext();
    }

    // ── Accessors ──

    public SqliteConnectionFactory connectionFactory() { return connectionFactory; }
    public DatabaseManager databaseManager() { return databaseManager; }
    public JobRepository jobRepository() { return jobRepository; }
    public WorkerRepository workerRepository() { return workerRepository; }
    public ConfigRepository configRepository() { return configRepository; }
    public JobService jobService() { return jobService; }
    public ConfigService configService() { return configService; }
    public DlqService dlqService() { return dlqService; }
    public MetricsService metricsService() { return metricsService; }
    public RetryService retryService() { return retryService; }
}

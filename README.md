# QueueCTL

[🎥 Watch the CLI Demo Video](https://drive.google.com/file/d/1s-BlyXOnrlXTQWJd_GOcNayZyJV273Nw/view?usp=sharing)

A production-grade CLI background job queue system built in Java 21 — a simplified version of Sidekiq/Celery/BullMQ demonstrating strong backend engineering, concurrency handling, reliability, persistence, and clean architecture.

---

## Features

| Feature | Description |
|---------|-------------|
| **Job Enqueuing** | Submit shell commands as jobs via JSON with priority, timeout, and delayed execution |
| **Multi-Worker Processing** | Run N concurrent worker threads polling for jobs via atomic fetch-and-lock |
| **Priority Queues** | HIGH > MEDIUM > LOW — workers always pick the highest-priority job first |
| **Automatic Retries** | Failed jobs are retried with configurable exponential backoff (base^attempts) |
| **Dead Letter Queue** | Jobs that exhaust all retries move to DLQ for inspection and manual retry |
| **Delayed Execution** | Schedule jobs to run at a future time via `run_at` |
| **Job Timeouts** | Commands are killed if they exceed their configured timeout |
| **Metrics & Status** | Real-time dashboard: success rate, avg execution time, retry count, worker count |
| **Crash Recovery** | Stalled PROCESSING jobs are automatically reset to PENDING on worker startup |
| **Graceful Shutdown** | Ctrl+C triggers orderly shutdown — current jobs finish, workers deregister, locks release |
| **Persistent Storage** | SQLite with WAL mode for concurrent read/write performance |
| **Runtime Configuration** | Tune max-retries, backoff-base, poll-interval, job-timeout, worker-heartbeat at runtime |

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Build Tool | Maven |
| CLI Framework | Picocli |
| Database | SQLite (WAL mode) |
| JSON | Jackson |
| Logging | SLF4J + Logback |
| Testing | JUnit 5 |

> **No Spring Boot.** This is a CLI-first system where startup speed, process management, and low-level concurrency matter more than framework abstractions.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        CLI Layer (Picocli)                        │
│  enqueue │ show │ list │ status │ config │ dlq │ metrics │ worker │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                       Service Layer                               │
│  JobService │ ConfigService │ DlqService │ MetricsService │ Retry │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                      Repository Layer                             │
│     JobRepository │ WorkerRepository │ ConfigRepository           │
│              (SQLite implementations)                             │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│                     Persistence Layer                             │
│   SqliteConnectionFactory │ MigrationRunner │ DatabaseManager     │
│         (WAL mode, busy_timeout, FK enforcement)                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                       Worker Engine                               │
│  JobExecutor │ Worker │ WorkerManager │ Heartbeat │ Shutdown      │
│    (Thread pool, atomic fetch-and-lock, graceful shutdown)        │
└──────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
src/main/java/com/queuectl/
├── QueueCtlApp.java              # Entry point (main method)
├── cli/                          # CLI commands (Picocli)
│   ├── QueueCtlCommand.java      # Root command with all subcommands
│   ├── EnqueueCommand.java       # queuectl enqueue '<json>'
│   ├── ShowCommand.java          # queuectl show <jobId>
│   ├── ListCommand.java          # queuectl list [--state STATE]
│   ├── StatusCommand.java        # queuectl status
│   ├── ConfigCommand.java        # queuectl config set|get|list
│   ├── DlqCommand.java           # queuectl dlq list|retry <id>
│   ├── MetricsCommand.java       # queuectl metrics
│   └── WorkerCommand.java        # queuectl worker start|stop
├── config/
│   └── AppContext.java           # Manual DI container (no framework)
├── domain/                       # Domain models
│   ├── Job.java                  # Job entity (14 fields)
│   ├── JobState.java             # PENDING → PROCESSING → COMPLETED/FAILED/DEAD
│   ├── Priority.java             # HIGH(3) > MEDIUM(2) > LOW(1)
│   ├── WorkerInfo.java           # Worker registration
│   └── Config.java               # Key-value config
├── exception/                    # Exception hierarchy
│   ├── QueueCtlException.java    # Base exception
│   ├── JobExecutionException.java
│   ├── WorkerException.java
│   ├── ConfigException.java
│   └── PersistenceException.java
├── persistence/                  # Database layer
│   ├── SqliteConnectionFactory.java  # Connection pool (WAL, FK, busy_timeout)
│   ├── MigrationRunner.java      # Schema migrations (idempotent DDL)
│   └── DatabaseManager.java      # Init + health check
├── repository/                   # Data access
│   ├── JobRepository.java        # Interface (10 methods)
│   ├── WorkerRepository.java     # Interface (8 methods)
│   ├── ConfigRepository.java     # Interface (4 methods)
│   ├── SqliteJobRepository.java  # Atomic fetch-and-lock implementation
│   ├── SqliteWorkerRepository.java
│   └── SqliteConfigRepository.java
├── service/                      # Business logic
│   ├── JobService.java           # Enqueue validation, defaults, state counts
│   ├── ConfigService.java        # Key validation, typed getters
│   ├── DlqService.java           # Dead Letter Queue operations
│   ├── MetricsService.java       # Aggregated statistics
│   └── RetryService.java         # Exponential backoff logic
├── util/                         # Utilities
│   ├── JsonUtil.java             # Jackson singleton + helpers
│   ├── TimeUtil.java             # ISO-8601 UTC timestamps
│   ├── CommandUtil.java          # OS-specific shell wrapping
│   ├── ProcessUtil.java          # Process execution with timeout
│   └── LockUtil.java             # PID file lock
└── worker/                       # Worker engine
    ├── JobExecutor.java          # Executes shell commands
    ├── Worker.java               # Poll loop (fetch → execute → result)
    ├── WorkerManager.java        # Thread pool management
    ├── WorkerHeartbeat.java      # Liveness heartbeat scheduler
    └── ShutdownManager.java      # JVM shutdown hook
```

---

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+

### Build
```bash
mvn clean package -DskipTests
```

### Run Tests
```bash
mvn clean test
```
> 172 tests, 0 failures, ~19s execution time

---

## Usage

All commands use the fat JAR at `target/queuectl-1.0.0.jar`:

```bash
alias queuectl="java --enable-native-access=ALL-UNNAMED -jar target/queuectl-1.0.0.jar"
```

### Enqueue Jobs

```bash
# Simple job
queuectl enqueue '{"id":"job-1","command":"echo hello world"}'

# High-priority job with custom timeout
queuectl enqueue '{"id":"job-2","command":"python train.py","priority":"HIGH","timeout":300}'

# Delayed job (runs after specified time)
queuectl enqueue '{"id":"job-3","command":"echo scheduled","runAt":"2026-07-18T10:00:00Z"}'

# Job with custom retry limit
queuectl enqueue '{"id":"job-4","command":"curl https://api.example.com","maxRetries":5}'
```

### Start Workers

```bash
# Start 1 worker (default)
queuectl worker start

# Start 4 concurrent workers
queuectl worker start --count 4

# Stop workers (from another terminal)
queuectl worker stop
```

### Monitor & Inspect

```bash
# System status dashboard
queuectl status

# Processing metrics
queuectl metrics

# List all jobs
queuectl list

# Filter by state
queuectl list --state COMPLETED
queuectl list --state FAILED

# Show full details of a specific job
queuectl show job-1
```

### Dead Letter Queue

```bash
# View failed jobs
queuectl dlq list

# Retry a dead job (resets to PENDING)
queuectl dlq retry job-1
```

### Configuration

```bash
# View all settings
queuectl config list

# Update settings
queuectl config set max-retries 5
queuectl config set backoff-base 3
queuectl config set poll-interval 2
queuectl config set job-timeout 120
queuectl config set worker-heartbeat 10
```

---

## Job Lifecycle

```
                    ┌─────────────┐
                    │   PENDING   │◄──── enqueue / dlq retry
                    └──────┬──────┘
                           │ fetchAndLock (atomic)
                    ┌──────▼──────┐
                    │ PROCESSING  │◄──── worker picks up job
                    └──────┬──────┘
                           │
                ┌──────────┼──────────┐
                │          │          │
         ┌──────▼──┐  ┌───▼────┐  ┌──▼──────┐
         │COMPLETED│  │ FAILED │  │ TIMEOUT  │
         └─────────┘  └───┬────┘  └────┬─────┘
                          │            │
                     attempts < max?   │
                    ┌─────┴─────┐      │
                    │           │      │
              ┌─────▼──┐  ┌────▼──────▼──┐
              │PENDING  │  │    DEAD      │
              │(retry)  │  │   (DLQ)      │
              └─────────┘  └──────────────┘
```

---

## Key Design Decisions

### Atomic Fetch-and-Lock
Workers use a transactional SELECT + UPDATE with a `WHERE state='PENDING'` guard clause. This prevents two workers from ever processing the same job, even under high concurrency. No external locking required.

### Exponential Backoff
Retry delay = `backoff_base ^ attempt_number` (in seconds).
With default base=2: attempt 1 → 2s, attempt 2 → 4s, attempt 3 → 8s.

### WAL Mode
SQLite Write-Ahead Logging enables concurrent readers and a single writer, with `busy_timeout=5000ms` to handle lock contention gracefully.

### Manual DI
`AppContext` wires all dependencies via constructor injection — no framework overhead, instant startup, fully testable.

### Process Timeout
`ProcessUtil` calls `waitFor(timeout)` BEFORE reading stdout/stderr. This ensures the timeout is enforced even if the process produces large output.

---

## Configuration Reference

| Key | Default | Description |
|-----|---------|-------------|
| `max-retries` | 3 | Maximum retry attempts before moving to DLQ |
| `backoff-base` | 2 | Base for exponential backoff (delay = base^attempts) |
| `poll-interval` | 1 | Seconds between worker poll cycles |
| `job-timeout` | 60 | Default job timeout in seconds |
| `worker-heartbeat` | 5 | Seconds between heartbeat updates |

---

## Testing

| Category | Tests | Coverage |
|----------|-------|----------|
| Domain Models | 38 | Job, JobState, Priority, WorkerInfo, Config |
| Persistence | 40 | ConnectionFactory, MigrationRunner, DatabaseManager, JsonUtil, TimeUtil |
| Repositories | 37 | JobRepository (19), WorkerRepository (10), ConfigRepository (8) |
| Services | 36 | JobService (11), ConfigService (13), RetryService (3), DlqService (6), MetricsService (3) |
| Worker Engine | 13 | JobExecutor (4), Worker (4), WorkerManager (5) |
| Integration (E2E) | 9 | Full pipeline, priority, concurrency, DLQ, timeout, delayed jobs |
| **Total** | **172** | **0 failures** |

---

## License

This project is built as an assignment/demonstration of backend engineering skills.

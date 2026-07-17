# Design Decisions & Trade-offs

This document outlines the core technical decisions made while building QueueCTL.

## 1. SQLite vs. Redis

**Decision**: Use SQLite in WAL mode instead of Redis.

**Rationale**:
Redis is the industry standard for job queues (Sidekiq, BullMQ, Celery) because of its fast in-memory operations and atomic lists (`RPOPLPUSH`). However, Redis requires external infrastructure, complicating deployment for a CLI tool. 
QueueCTL uses SQLite with Write-Ahead Logging (WAL) enabled, giving us:
- **Zero setup**: The database is a local file (`queuectl.db`).
- **Concurrent Reads**: WAL allows multiple readers and a single writer, avoiding database locking issues during heavy polling.
- **Persistence**: Jobs survive restarts by default.

**Trade-off**: 
SQLite writes lock the database briefly. At extreme scale (10,000+ jobs/sec), lock contention (`SQLITE_BUSY`) would occur. We mitigate this using `busy_timeout=5000` which allows drivers to queue writes seamlessly.

## 2. Optimistic Locking vs. External Lock Server

**Decision**: Use atomic `UPDATE ... RETURNING` (emulated via SELECT+UPDATE in older SQLite, or fully atomic in newer versions) rather than a distributed lock server (Zookeeper/Redis).

**Implementation**:
```sql
UPDATE jobs 
SET state = 'PROCESSING', locked_by = ? 
WHERE id = (SELECT id FROM jobs WHERE state = 'PENDING' LIMIT 1)
```

**Rationale**:
Since we have a single SQLite database acting as the source of truth, atomic SQL updates guarantee exactly-once processing. No two workers can lock the same job.

## 3. Inline Schedulers vs. Background Daemon

**Decision**: Optimize delayed jobs (`runAt`) and retries (`nextRetryAt`) into the primary poll query, rather than running a separate scheduler thread.

**Rationale**:
A traditional approach uses a "Scheduler" daemon that scans for delayed jobs whose time has arrived, and moves them to the PENDING queue. 
Instead, QueueCTL leaves them in PENDING but skips them in the SQL query:
```sql
WHERE state = 'PENDING' 
  AND (run_at IS NULL OR run_at <= CURRENT_TIMESTAMP)
```
This removes the need for an extra background thread, eliminates race conditions, and reduces database write volume.

## 4. Manual Dependency Injection (DI)

**Decision**: Use a manual `AppContext` factory class instead of Spring Boot or Google Guice.

**Rationale**:
CLI applications need to start instantly. Frameworks like Spring Boot take seconds to initialize their ApplicationContext via classpath scanning. By manually wiring our classes (`new JobService(new SqliteJobRepository(...))`), QueueCTL boots in milliseconds.

## 5. Process Timeout Enforcement

**Decision**: Implement strict timeouts via `Process.waitFor(timeout)` rather than asynchronous thread interrupts.

**Rationale**:
When executing raw shell commands, a hanging command (e.g., a `sleep` or an infinite loop) will consume a worker thread indefinitely. Java's `ProcessBuilder` doesn't enforce timeouts natively if you are blocked reading its `InputStream`.
QueueCTL enforces the timeout first, and only reads stdout/stderr *after* the process exits or is forcibly destroyed.

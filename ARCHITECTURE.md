# QueueCTL Architecture

QueueCTL is designed as a standalone, zero-dependency (other than SQLite and Jackson) CLI application. The architecture favors simplicity, atomicity, and self-contained execution over distributed coordination.

## High-Level Architecture

The system is layered in a hexagonal/clean architecture style, meaning the core domain has no dependencies on the framework (CLI) or the database. 

```mermaid
graph TD
    %% CLI Layer
    CLI[Picocli Commands]
    
    %% Service Layer
    JS[JobService]
    WS[WorkerManager]
    CS[ConfigService]
    
    %% Repository Layer
    JR[(JobRepository)]
    CR[(ConfigRepository)]
    
    %% Execution Layer
    JE[JobExecutor]
    
    %% Connections
    CLI --> JS
    CLI --> WS
    CLI --> CS
    
    JS --> JR
    WS --> JR
    WS --> JE
    CS --> CR
```

## Worker Execution Flow

Workers run in a threaded `ExecutorService` pool. The poll loop uses atomic fetch-and-lock to ensure jobs are only executed exactly once.

```mermaid
sequenceDiagram
    participant Worker
    participant JobRepository
    participant JobExecutor
    participant RetryService

    loop Poll Interval (e.g. 1s)
        Worker->>JobRepository: fetchAndLock(workerId)
        
        alt Job Found
            JobRepository-->>Worker: Optional<Job> (State=PROCESSING)
            Worker->>JobExecutor: execute(job)
            
            alt Success
                JobExecutor-->>Worker: Result(success=true)
                Worker->>JobRepository: updateState(COMPLETED)
            else Failure or Timeout
                JobExecutor-->>Worker: Result(success=false)
                Worker->>RetryService: handleFailure(job, error)
                RetryService->>JobRepository: updateState(PENDING or DEAD)
            end
        else No Jobs
            JobRepository-->>Worker: Optional.empty
            Worker->>Worker: Thread.sleep(pollInterval)
        end
    end
```

## Job Lifecycle (State Machine)

Jobs move through a strict state machine.

```mermaid
stateDiagram-v2
    [*] --> PENDING : enqueue()
    PENDING --> PROCESSING : fetchAndLock()
    
    PROCESSING --> COMPLETED : success
    PROCESSING --> PENDING : failure (attempts < max_retries)
    PROCESSING --> DEAD : failure (attempts >= max_retries)
    
    DEAD --> PENDING : dlq retry()
    
    COMPLETED --> [*]
    DEAD --> [*]
```

## Database Schema

QueueCTL relies on SQLite for persistent storage. The schema is designed for fast polling.

```mermaid
erDiagram
    JOBS {
        string id PK
        string command
        string state
        string priority
        int attempts
        int max_retries
        int timeout
        string run_at
        string next_retry_at
        string locked_by
        string locked_at
        string output
        string error
        string created_at
        string updated_at
    }
    
    WORKERS {
        string id PK
        string status
        string heartbeat
        string started_at
    }
    
    CONFIG {
        string key PK
        string value
        string updated_at
    }
    
    METRICS {
        string key PK
        int value
        string updated_at
    }
```

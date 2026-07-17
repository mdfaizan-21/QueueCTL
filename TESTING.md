# Testing Strategy

QueueCTL is heavily tested with a suite of **172 tests** executing in under 20 seconds. 

## Test Matrix

| Layer | Focus | Execution Speed | Tools Used |
|-------|-------|-----------------|------------|
| **Domain** | Entity logic, enums, defaults | Milliseconds | JUnit 5 assertions |
| **Repository** | SQL queries, schema correctness, constraint enforcement | Fast (File-based DB) | `@TempDir`, SQLite JDBC |
| **Service** | Business logic, state transitions, metric aggregation | Fast (In-memory/Mocks) | JUnit 5 |
| **Worker Engine**| Thread safety, polling loops, graceful shutdown, timeouts | Moderate (Thread sleeps) | `Thread.sleep`, `ProcessBuilder` |
| **Integration** | End-to-end pipeline, concurrency, real-world CLI flows | Slower (Real DB + Threads) | JUnit 5, `@TempDir` |

## Key Testing Principles

### 1. Isolated File-based SQLite (`@TempDir`)
Instead of using SQLite's `:memory:` mode (which drops tables when the connection closes and behaves poorly with concurrent thread pools), our integration tests use JUnit 5's `@TempDir` to create a real `.db` file for each test method. This guarantees exact production-like behavior (WAL mode, file locks) while maintaining test isolation.

### 2. Concurrency Testing
The `WorkerManagerTest` and `EndToEndTest` suites intentionally spin up multiple workers to process overlapping jobs, verifying that atomic fetch-and-lock works under load without duplicate processing.

### 3. Graceful Shutdown Validation
Testing shutdown hooks is notoriously difficult. QueueCTL tests this by maintaining a volatile `Thread` reference in the worker and asserting that `interrupt()` wakes it from its `poll-interval` sleep immediately, ensuring fast and clean teardown.

## How to Run Tests

Run the full suite using Maven:
```bash
mvn clean test
```

To run a specific test class (e.g., just the integration tests):
```bash
mvn test -Dtest=EndToEndTest
```

To view test logs, adjust the root logger level in `src/test/resources/logback-test.xml`.

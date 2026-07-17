# QueueCTL Roadmap

While QueueCTL is production-ready for single-node CLI usage, the following features are planned for future major releases.

## 1. High Availability / Clustering
Currently, QueueCTL locks the worker process to a single node using a local `queuectl.pid` file to prevent conflicting daemon processes on the same machine.
**Future**: Support multi-node clustering where workers on different VMs can point to a centralized database (e.g., Postgres or a networked SQLite like LiteFS), allowing horizontal scaling.

## 2. Job Chaining / DAGs
Support defining dependent jobs where Job B only executes if Job A completes successfully. 
```json
{
  "id": "job-B",
  "command": "echo second",
  "depends_on": "job-A"
}
```

## 3. Web Dashboard
Build a lightweight HTTP server (using Java's built-in `com.sun.net.httpserver.HttpServer` or Javalin) that exposes a React/HTML dashboard for visualizing queue depth, viewing job logs, and manually retrying DLQ jobs via a GUI.

## 4. Cron Schedules
Currently, `runAt` allows for a one-off delayed execution. We want to support recurring execution via standard cron syntax (e.g., `0 0 * * *`).

## 5. Webhook Callbacks
Allow jobs to define a webhook URL that QueueCTL will POST to when the job transitions to `COMPLETED` or `DEAD`, enabling integration with external notification systems like Slack or PagerDuty.

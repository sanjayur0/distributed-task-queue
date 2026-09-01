# Distributed Task Queue

A Java-based distributed task queue system using PostgreSQL and Redis.

## Overview

This project implements a task queue where tasks can be submitted through an HTTP API, stored persistently in PostgreSQL, queued for processing, executed by workers, retried after failures, and moved to a Dead Letter Queue (DLQ) when retries are exhausted.

The system also includes recovery of stuck tasks so tasks are not permanently lost if a worker fails while processing them.

## Features

- Task creation through HTTP API
- Task persistence using PostgreSQL
- Redis-based task queue
- Priority-based task processing
- Multiple task statuses
- Idempotency key support
- Worker-based task execution
- Automatic retry mechanism
- Retry delay scheduling
- Dead Letter Queue (DLQ)
- Manual DLQ retry
- Manual DLQ deletion
- Task cancellation
- Stuck task recovery
- Task status filtering
- REST-style HTTP endpoints

## Tech Stack

- Java 25
- Maven
- PostgreSQL
- Redis
- Jedis
- JUnit 5
- Java HTTP Server

## Architecture

```text
                Client
                  |
                  v
           HTTP Task Server
                  |
          +-------+-------+
          |               |
          v               v
      PostgreSQL         Redis
          |               |
          |               v
          |          Task Queue
          |               |
          |               v
          |            Worker
          |               |
          +-------+-------+
                  |
                  v
             Task Status

Failed Task
    |
    v
Retry Scheduler
    |
    v
Redis Queue
    |
    v
Worker

Max Retries Exceeded
    |
    v
Dead Letter Queue
    |
    +----> Retry
    |
    +----> Delete

Stuck Tasks
    |
    v
Task Recovery Service
    |
    v
Redis Queue
```

## Project Structure

```text
src/main/java/com/sanjay/taskqueue
├── db
│   ├── Database.java
│   ├── DatabaseConnection.java
│   └── DatabaseTest.java
├── Main.java
├── model
│   ├── Task.java
│   ├── TaskPriority.java
│   └── TaskStatus.java
├── queue
│   └── TaskQueue.java
├── repository
│   ├── DlqTask.java
│   └── TaskRepository.java
├── scheduler
│   └── RetryScheduler.java
├── server
│   └── TaskHttpServer.java
├── service
│   └── TaskRecoveryService.java
└── worker
    └── Worker.java
```

## Task Lifecycle

```text
QUEUED
  |
  v
RUNNING
  |
  +--------> COMPLETED
  |
  v
RETRYING
  |
  +--------> QUEUED
  |
  +--------> FAILED
                 |
                 v
                DLQ
```

## Task Priority

Tasks support three priority levels:

Priority	Value
HIGH	          1
MEDIUM	          2
LOW	          3

Lower priority value means higher processing priority.

## Task Status

The system supports:

QUEUED
RUNNING
RETRYING
COMPLETED
FAILED
CANCELLED

## Database Configuration

The application requires PostgreSQL.

Default database configuration:

Database: taskqueue
User: postgres

Set the database password:

export DB_PASSWORD="your_password"

The application reads the password from the DB_PASSWORD environment variable.

## Redis

The task queue uses Redis through Jedis.

Make sure Redis is running before starting the application.

redis-server

Verify Redis:

redis-cli ping

Expected:

PONG
Build
mvn clean package

Run tests:

mvn clean test

## Running the Application

Set the database password:

export DB_PASSWORD="your_password"

Then run the application using Maven or your IDE.

The HTTP server runs on:

http://localhost:8080

## API Endpoints


### Create Task
POST /tasks

Example:
curl -X POST "http://localhost:8080/tasks?name=SendEmail&idempotencyKey=email-001&priority=HIGH"


### Get All Tasks
GET /tasks

Example:
curl "http://localhost:8080/tasks"


### Filter Tasks by Status
GET /tasks?status=QUEUED

Example:
curl "http://localhost:8080/tasks?status=COMPLETED"


### Get Task
GET /tasks/{id}

Example:
curl "http://localhost:8080/tasks/<task-id>"


### Cancel Task
DELETE /tasks/{id}

Example:
curl -X DELETE "http://localhost:8080/tasks/<task-id>"


## Dead Letter Queue

### Get DLQ Tasks
GET /dlq

Example:
curl "http://localhost:8080/dlq"


### Retry DLQ Task
POST /dlq/{id}/retry

Example:
curl -X POST "http://localhost:8080/dlq/<dlq-id>/retry"


### Delete DLQ Task
DELETE /dlq/{id}

Example:
curl -X DELETE "http://localhost:8080/dlq/<dlq-id>"

## Idempotency

Each task requires an idempotency key.

If the same idempotency key is submitted multiple times, the system returns the existing task instead of creating duplicate tasks.

Example:

idempotencyKey=email-001

Submitting the same key again does not create another task.

## Retry Mechanism

Failed tasks can be retried automatically.

The task maintains:

retryCount
maxRetries

The default maximum retry count is:

3

A retry can be scheduled with a delay before the task is placed back into the queue.

## Dead Letter Queue

When a task cannot be successfully processed after the configured number of retries, it is moved to the Dead Letter Queue.

The DLQ allows operators to:

Inspect failed tasks
Retry failed tasks
Delete failed tasks
Task Recovery

The TaskRecoveryService periodically checks for tasks that have remained in the running state longer than the configured timeout.

Such tasks are recovered and placed back into the queue.

This protects against tasks becoming permanently stuck when a worker fails unexpectedly.

## Database Persistence

PostgreSQL stores task information including:

Task ID
Task name
Idempotency key
Priority
Status
Retry count
Maximum retries

DLQ information is also persisted.

## Testing

Run:

mvn clean test

The project currently passes the Maven test/build verification.


## Future Improvements

Authentication and authorization
Better JSON request/response handling
Docker and Docker Compose support
Multiple worker processes
Distributed worker coordination
Redis Streams
Exponential backoff
Metrics and monitoring
Structured logging
Health check endpoint
Graceful worker shutdown
Integration tests
Load testing
Database migration management

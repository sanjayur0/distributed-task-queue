package com.sanjay.taskqueue.model;

import java.util.UUID;

public class Task {

    private final UUID id;
    private final String name;
    private final String idempotencyKey;

    private TaskPriority priority;
    private TaskStatus status;

    private int retryCount;
    private int maxRetries;

    // ==========================================
    // NEW TASK - DEFAULT PRIORITY
    // ==========================================

    public Task(
            String name,
            String idempotencyKey) {

        this.id = UUID.randomUUID();
        this.name = name;
        this.idempotencyKey = idempotencyKey;

        this.priority = TaskPriority.MEDIUM;
        this.status = TaskStatus.QUEUED;

        this.retryCount = 0;
        this.maxRetries = 3;
    }

    // ==========================================
    // NEW TASK - WITH PRIORITY
    // ==========================================

    public Task(
            String name,
            String idempotencyKey,
            TaskPriority priority) {

        this.id = UUID.randomUUID();
        this.name = name;
        this.idempotencyKey = idempotencyKey;

        this.priority = priority;
        this.status = TaskStatus.QUEUED;

        this.retryCount = 0;
        this.maxRetries = 3;
    }

    // ==========================================
    // LOAD TASK FROM DATABASE
    // ==========================================

    public Task(
            UUID id,
            String name,
            String idempotencyKey,
            TaskPriority priority,
            TaskStatus status,
            int retryCount,
            int maxRetries) {

        this.id = id;
        this.name = name;
        this.idempotencyKey = idempotencyKey;

        this.priority = priority;
        this.status = status;

        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
    }

    // ==========================================
    // GETTERS
    // ==========================================

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    // ==========================================
    // SETTERS
    // ==========================================

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    // ==========================================
    // RETRY
    // ==========================================

    public void incrementRetryCount() {
        retryCount++;
    }
}
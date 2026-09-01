package com.sanjay.taskqueue.repository;

import java.sql.Timestamp;
import java.util.UUID;

public class DlqTask {

    private final UUID id;
    private final UUID taskId;
    private final String name;
    private final String failureReason;
    private final int retryCount;
    private final Timestamp createdAt;

    public DlqTask(
            UUID id,
            UUID taskId,
            String name,
            String failureReason,
            int retryCount,
            Timestamp createdAt) {

        this.id = id;
        this.taskId = taskId;
        this.name = name;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}

package com.sanjay.taskqueue.model;

public enum TaskStatus {

    QUEUED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED
}
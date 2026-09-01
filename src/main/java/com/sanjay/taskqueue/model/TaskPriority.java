package com.sanjay.taskqueue.model;

public enum TaskPriority {

    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private final int value;

    TaskPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
package com.sanjay.taskqueue.service;

import com.sanjay.taskqueue.model.Task;
import com.sanjay.taskqueue.model.TaskStatus;
import com.sanjay.taskqueue.queue.TaskQueue;
import com.sanjay.taskqueue.repository.TaskRepository;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskRecoveryService {
    private final TaskRepository repository;
    private final TaskQueue queue;
    private final ScheduledExecutorService scheduler;
    private final int timeoutSeconds;

    public TaskRecoveryService(TaskRepository repository, TaskQueue queue, int timeoutSeconds) {
        this.repository = repository;
        this.queue = queue;
        this.timeoutSeconds = timeoutSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        System.out.println("Task recovery service started.");
        scheduler.scheduleAtFixedRate(this::recoverTasks, 10, 10, TimeUnit.SECONDS);
    }

    private void recoverTasks() {
        try {
            int recovered = repository.recoverStuckTasks(timeoutSeconds);
            if (recovered == 0) {
                return;
            }

            System.out.println("Recovered " + recovered + " stuck task(s).");

            List<Task> queuedTasks = repository.findByStatus(TaskStatus.QUEUED);
            for (Task task : queuedTasks) {
                queue.addTask(task);
            }
        } catch (Exception e) {
            System.err.println("Task recovery failed: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdownNow();
        System.out.println("Task recovery service stopped.");
    }
}
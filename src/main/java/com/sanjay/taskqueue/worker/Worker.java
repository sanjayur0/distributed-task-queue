package com.sanjay.taskqueue.worker;

import com.sanjay.taskqueue.model.Task;
import com.sanjay.taskqueue.model.TaskStatus;
import com.sanjay.taskqueue.queue.TaskQueue;
import com.sanjay.taskqueue.repository.TaskRepository;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Worker implements Runnable {

    private final String workerName;
    private final TaskQueue queue;
    private final TaskRepository repository;

    private volatile boolean running = true;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> heartbeatTask;

    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    public Worker(
            String workerName,
            TaskQueue queue,
            TaskRepository repository) {

        this.workerName = workerName;
        this.queue = queue;
        this.repository = repository;
    }

    public void shutdown() {
        running = false;
        stopHeartbeat();
        heartbeatExecutor.shutdownNow();
    }

    @Override
    public void run() {

        System.out.println(workerName + " started.");

        while (running) {

            Task task = null;
            int attemptNumber = 0;

            try {
                task = queue.getTask();

                if (task == null) {
                    continue;
                }

                attemptNumber = task.getRetryCount() + 1;

                boolean claimed = repository.claimTask(task.getId());

                if (!claimed) {
                    System.out.println(
                            workerName + " could not claim task " + task.getId()
                    );
                    continue;
                }

                boolean executionClaimed =
                        repository.claimTaskExecution(
                                task.getId(),
                                attemptNumber
                        );

                if (!executionClaimed) {

                    String executionStatus =
                            repository.getTaskExecutionStatus(
                                    task.getId(),
                                    attemptNumber
                            );

                    if ("COMPLETED".equals(executionStatus)) {

                        System.out.println(
                                workerName
                                        + " skipped duplicate execution of "
                                        + task.getName()
                                        + " | Attempt: "
                                        + attemptNumber
                                        + " | Already COMPLETED"
                        );

                        task.setStatus(TaskStatus.COMPLETED);
                        repository.updateStatus(task);
                        continue;
                    }

                    if ("RUNNING".equals(executionStatus)) {

                        System.out.println(
                                workerName
                                        + " skipped duplicate execution of "
                                        + task.getName()
                                        + " | Attempt: "
                                        + attemptNumber
                                        + " | Already RUNNING"
                        );

                        task.setStatus(TaskStatus.QUEUED);
                        repository.updateStatus(task);
                        continue;
                    }

                    if ("FAILED".equals(executionStatus)) {

                        System.out.println(
                                workerName
                                        + " found previous failed execution of "
                                        + task.getName()
                                        + " | Attempt: "
                                        + attemptNumber
                        );

                        handleFailure(task);
                    }

                    continue;
                }

                task.setStatus(TaskStatus.RUNNING);
                repository.updateStatus(task);

                System.out.println(
                        workerName
                                + " processing "
                                + task.getName()
                                + " | Attempt: "
                                + attemptNumber
                );

                startHeartbeat(task);

                Thread.sleep(60000);

                boolean success =
                        !task.getName().equalsIgnoreCase("FailTask");

                if (!success) {
                    throw new RuntimeException("Task failed");
                }

                repository.markTaskExecutionCompleted(
                        task.getId(),
                        attemptNumber
                );

                task.setStatus(TaskStatus.COMPLETED);
                repository.updateStatus(task);

                System.out.println(
                        workerName + " completed " + task.getName()
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                stopHeartbeat();

                if (task != null && attemptNumber > 0) {
                    try {
                        repository.markTaskExecutionFailed(
                                task.getId(),
                                attemptNumber
                        );
                    } catch (Exception dbException) {
                        System.out.println(
                                workerName
                                        + " failed to mark interrupted execution."
                        );
                    }
                }

                System.out.println(workerName + " interrupted.");
                break;

            } catch (Exception e) {

                if (task == null) {
                    continue;
                }

                System.out.println(
                        workerName
                                + " failed task "
                                + task.getId()
                                + " | Attempt: "
                                + attemptNumber
                );

                try {
                    repository.markTaskExecutionFailed(
                            task.getId(),
                            attemptNumber
                    );
                } catch (Exception dbException) {
                    System.out.println(
                            workerName
                                    + " failed to mark execution as FAILED."
                    );
                }

                stopHeartbeat();
                handleFailure(task);
            } finally {
                stopHeartbeat();
            }
        }

        stopHeartbeat();
        heartbeatExecutor.shutdownNow();

        System.out.println(workerName + " stopped.");
    }

    private void startHeartbeat(Task task) {

        stopHeartbeat();

        heartbeatTask =
                heartbeatExecutor.scheduleAtFixedRate(
                        () -> {
                            try {
                                boolean renewed =
                                        repository.renewTaskLock(task.getId());

                                if (!renewed) {
                                    System.out.println(
                                            workerName
                                                    + " failed to renew lock for "
                                                    + task.getName()
                                    );
                                }

                            } catch (Exception e) {
                                System.out.println(
                                        workerName
                                                + " heartbeat failed for "
                                                + task.getName()
                                );
                            }
                        },
                        HEARTBEAT_INTERVAL_SECONDS,
                        HEARTBEAT_INTERVAL_SECONDS,
                        TimeUnit.SECONDS
                );
    }

    private void stopHeartbeat() {

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void handleFailure(Task task) {

        task.incrementRetryCount();

        System.out.println(
                task.getName()
                        + " failed. Retry count: "
                        + task.getRetryCount()
        );

        if (task.getRetryCount() <= task.getMaxRetries()) {

            task.setStatus(TaskStatus.RETRYING);
            repository.updateStatus(task);

            long delay =
                    (long) Math.pow(
                            2,
                            task.getRetryCount() - 1
                    ) * 2000;

            System.out.println(
                    task.getName()
                            + " will be retried after "
                            + (delay / 1000)
                            + " seconds."
            );

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                System.out.println(
                        workerName
                                + " interrupted during retry delay."
                );

                return;
            }

            if (running) {
                queue.addTask(task);
            }

        } else {

            task.setStatus(TaskStatus.FAILED);
            repository.updateStatus(task);

            repository.moveToDeadLetterQueue(
                    task,
                    "Maximum retry attempts exceeded"
            );

            System.out.println(
                    task.getName() + " permanently failed."
            );
        }
    }
}
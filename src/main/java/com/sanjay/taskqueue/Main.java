package com.sanjay.taskqueue;

import com.sanjay.taskqueue.model.Task;
import com.sanjay.taskqueue.model.TaskStatus;
import com.sanjay.taskqueue.queue.TaskQueue;
import com.sanjay.taskqueue.repository.TaskRepository;
import com.sanjay.taskqueue.server.TaskHttpServer;
import com.sanjay.taskqueue.service.TaskRecoveryService;
import com.sanjay.taskqueue.worker.Worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Task Queue System...");

        TaskRepository repository = new TaskRepository();
        TaskQueue queue = new TaskQueue();

        // Restore tasks that were waiting when the application stopped.
        List<Task> queuedTasks = repository.findByStatus(TaskStatus.QUEUED);

        for (Task task : queuedTasks) {
            queue.addTask(task);
        }

        System.out.println(
                "Recovered " + queuedTasks.size() + " queued tasks."
        );

        // RETRYING tasks can safely be returned to the queue.
        List<Task> retryingTasks =
                repository.findByStatus(TaskStatus.RETRYING);

        for (Task task : retryingTasks) {
            task.setStatus(TaskStatus.QUEUED);
            repository.updateStatus(task);
            queue.addTask(task);
        }

        System.out.println(
                "Recovered " + retryingTasks.size() + " retrying tasks."
        );

        int interruptedExecutions =
                repository.recoverInterruptedExecutions();

        System.out.println(
                "Recovered " + interruptedExecutions
                        + " interrupted executions."
        );

        // A RUNNING task may belong to a worker from a previous instance.
        // Requeue it so processing can continue after restart.
        List<Task> runningTasks =
                repository.findByStatus(TaskStatus.RUNNING);

        for (Task task : runningTasks) {
            task.setStatus(TaskStatus.QUEUED);
            repository.updateStatus(task);
            queue.addTask(task);
        }

        System.out.println(
                "Recovered " + runningTasks.size() + " interrupted tasks."
        );

        TaskRecoveryService recoveryService =
                new TaskRecoveryService(repository, queue, 30);

        recoveryService.start();

        ExecutorService executor =
                Executors.newFixedThreadPool(3);

        List<Worker> workers = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            workers.add(
                    new Worker(
                            "Worker-" + i,
                            queue,
                            repository
                    )
            );
        }

        TaskHttpServer server =
                new TaskHttpServer(repository, queue);

        server.start();

        for (Worker worker : workers) {
            executor.submit(worker);
        }

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    System.out.println(
                            "\nShutting down Task Queue System..."
                    );

                    System.out.println("Stopping HTTP server...");
                    server.stop();

                    System.out.println("Stopping workers...");

                    for (Worker worker : workers) {
                        worker.shutdown();
                    }

                    executor.shutdown();

                    try {
                        boolean finished =
                                executor.awaitTermination(
                                        30,
                                        TimeUnit.SECONDS
                                );

                        if (!finished) {
                            System.out.println(
                                    "Workers did not finish within 30 seconds."
                            );
                            System.out.println(
                                    "Forcing worker shutdown..."
                            );
                            executor.shutdownNow();
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        executor.shutdownNow();
                    }

                    System.out.println("Stopping recovery service...");
                    recoveryService.stop();

                    System.out.println(
                            "Task Queue System stopped."
                    );
                })
        );

        System.out.println("Task Queue System is running.");
    }
}
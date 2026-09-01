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

    public static void main(String[] args)
            throws Exception {

        System.out.println(
                "Starting Task Queue System..."
        );

        // ==========================================
        // DATABASE
        // ==========================================

        TaskRepository repository =
                new TaskRepository();

        // ==========================================
        // TASK QUEUE
        // ==========================================

        TaskQueue queue =
                new TaskQueue();

        // ==========================================
        // RECOVER QUEUED TASKS
        // ==========================================

        List<Task> queuedTasks =
                repository.findByStatus(
                        TaskStatus.QUEUED
                );

        for (Task task : queuedTasks) {

            queue.addTask(task);
        }

        System.out.println(
                "Recovered "
                        + queuedTasks.size()
                        + " queued tasks."
        );

        // ==========================================
        // RECOVER RETRYING TASKS
        // ==========================================

        List<Task> retryingTasks =
                repository.findByStatus(
                        TaskStatus.RETRYING
                );

        for (Task task : retryingTasks) {

            /*
             * RETRYING tasks are safe to put back
             * into the queue.
             */

            task.setStatus(
                    TaskStatus.QUEUED
            );

            repository.updateStatus(task);

            queue.addTask(task);
        }

        System.out.println(
                "Recovered "
                        + retryingTasks.size()
                        + " retrying tasks."
        );

        // ==========================================
        // RECOVER INTERRUPTED EXECUTIONS
        // ==========================================

        int interruptedExecutions =
                repository.recoverInterruptedExecutions();

        System.out.println(
                "Recovered "
                        + interruptedExecutions
                        + " interrupted executions."
        );

        // ==========================================
        // RECOVER INTERRUPTED RUNNING TASKS
        // ==========================================

        List<Task> runningTasks =
                repository.findByStatus(
                        TaskStatus.RUNNING
                );

        for (Task task : runningTasks) {

            /*
             * A RUNNING task may belong to a worker
             * from a previous application instance.
             *
             * Since the application has restarted,
             * put it back into QUEUED state.
             */

            task.setStatus(
                    TaskStatus.QUEUED
            );

            repository.updateStatus(task);

            queue.addTask(task);
        }

        System.out.println(
                "Recovered "
                        + runningTasks.size()
                        + " interrupted tasks."
        );

        // ==========================================
        // START TASK RECOVERY SERVICE
        // ==========================================

        TaskRecoveryService recoveryService =
                new TaskRecoveryService(
                        repository,
                        queue,
                        30
                );

        recoveryService.start();

        // ==========================================
        // CREATE WORKER THREAD POOL
        // ==========================================

        ExecutorService executor =
                Executors.newFixedThreadPool(3);

        List<Worker> workers =
                new ArrayList<>();

        // ==========================================
        // CREATE WORKERS
        // ==========================================

        for (int i = 1; i <= 3; i++) {

            Worker worker =
                    new Worker(
                            "Worker-" + i,
                            queue,
                            repository
                    );

            workers.add(worker);
        }

        // ==========================================
        // START HTTP SERVER
        // ==========================================

        TaskHttpServer server =
                new TaskHttpServer(
                        repository,
                        queue
                );

        server.start();

        // ==========================================
        // START WORKERS
        // ==========================================

        for (Worker worker : workers) {

            executor.submit(worker);
        }

        // ==========================================
        // SHUTDOWN HOOK
        // ==========================================

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(() -> {

                            System.out.println(
                                    "\nShutting down Task Queue System..."
                            );

                            // ==========================================
                            // 1. STOP HTTP SERVER
                            // ==========================================

                            System.out.println(
                                    "Stopping HTTP server..."
                            );

                            server.stop();

                            // ==========================================
                            // 2. STOP ACCEPTING NEW WORK
                            // ==========================================

                            System.out.println(
                                    "Stopping workers..."
                            );

                            for (Worker worker :
                                    workers) {

                                worker.shutdown();
                            }

                            // ==========================================
                            // 3. SHUTDOWN EXECUTOR
                            // ==========================================

                            executor.shutdown();

                            try {

                                /*
                                 * Give currently executing
                                 * workers up to 30 seconds
                                 * to finish.
                                 */

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

                                Thread.currentThread()
                                        .interrupt();

                                executor.shutdownNow();
                            }

                            // ==========================================
                            // 4. STOP RECOVERY SERVICE
                            // ==========================================

                            System.out.println(
                                    "Stopping recovery service..."
                            );

                            recoveryService.stop();

                            // ==========================================
                            // FINAL MESSAGE
                            // ==========================================

                            System.out.println(
                                    "Task Queue System stopped."
                            );
                        })
                );

        // ==========================================
        // SYSTEM READY
        // ==========================================

        System.out.println(
                "Task Queue System is running."
        );
    }
}
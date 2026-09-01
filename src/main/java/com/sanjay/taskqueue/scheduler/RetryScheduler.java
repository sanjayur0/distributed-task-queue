package com.sanjay.taskqueue.scheduler;

import com.sanjay.taskqueue.model.Task;
import com.sanjay.taskqueue.queue.TaskQueue;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RetryScheduler {

    private final TaskQueue queue;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    public RetryScheduler(TaskQueue queue) {
        this.queue = queue;
    }

    // ==========================================
    // SCHEDULE RETRY
    // ==========================================

    public void scheduleRetry(
            Task task,
            long delay) {

        System.out.println(
                task.getName()
                        + " scheduled for retry after "
                        + delay
                        + " seconds."
        );

        scheduler.schedule(
                () -> {

                    queue.addTask(task);

                    System.out.println(
                            task.getName()
                                    + " retry delay completed. "
                                    + "Added back to queue."
                    );

                },
                delay,
                TimeUnit.SECONDS
        );
    }

    // ==========================================
    // SHUTDOWN
    // ==========================================

    public void shutdown() {

        scheduler.shutdownNow();
    }
}
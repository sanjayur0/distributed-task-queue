package com.sanjay.taskqueue.queue;

import com.sanjay.taskqueue.model.Task;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TaskQueue {

    // ==========================================
    // CONFIGURATION
    // ==========================================

    /*
     * After every 20 seconds of waiting,
     * a task gets one priority level improvement.
     *
     * LOW:
     *   after 20 sec  -> MEDIUM
     *   after 40 sec  -> HIGH
     *
     * MEDIUM:
     *   after 20 sec  -> HIGH
     */
    private static final long AGING_INTERVAL_MS = 20_000;

    // ==========================================
    // QUEUE SEQUENCE
    // ==========================================

    private final AtomicLong sequenceGenerator =
            new AtomicLong(0);

    // ==========================================
    // PRIORITY QUEUE
    // ==========================================

    private final PriorityBlockingQueue<QueueEntry> queue =
            new PriorityBlockingQueue<>(
                    11,
                    (entry1, entry2) -> {

                        int effectivePriority1 =
                                getEffectivePriority(entry1);

                        int effectivePriority2 =
                                getEffectivePriority(entry2);

                        // ==========================================
                        // FIRST: EFFECTIVE PRIORITY
                        // ==========================================

                        int priorityComparison =
                                Integer.compare(
                                        effectivePriority1,
                                        effectivePriority2
                                );

                        if (priorityComparison != 0) {

                            return priorityComparison;
                        }

                        // ==========================================
                        // SECOND: FIFO
                        // ==========================================

                        return Long.compare(
                                entry1.getSequence(),
                                entry2.getSequence()
                        );
                    }
            );

    // ==========================================
    // ADD TASK
    // ==========================================

    public void addTask(Task task) {

        long sequence =
                sequenceGenerator.getAndIncrement();

        long addedAt =
                System.currentTimeMillis();

        QueueEntry entry =
                new QueueEntry(
                        task,
                        sequence,
                        addedAt
                );

        queue.offer(entry);

        System.out.println(
                "Task added to queue: "
                        + task.getName()
                        + " | Priority: "
                        + task.getPriority()
                        + " | Sequence: "
                        + sequence
        );
    }

    // ==========================================
    // GET TASK
    // ==========================================

    public Task getTask()
            throws InterruptedException {

        QueueEntry entry =
                queue.poll(
                        5,
                        TimeUnit.SECONDS
                );

        if (entry == null) {

            return null;
        }

        return entry.getTask();
    }

    // ==========================================
    // QUEUE SIZE
    // ==========================================

    public int size() {

        return queue.size();
    }

    // ==========================================
    // EFFECTIVE PRIORITY
    // ==========================================

    private int getEffectivePriority(
            QueueEntry entry) {

        int originalPriority =
                entry.getTask()
                        .getPriority()
                        .getValue();

        long waitingTime =
                System.currentTimeMillis()
                        - entry.getAddedAt();

        long agingLevels =
                waitingTime
                        / AGING_INTERVAL_MS;

        /*
         * Lower number = higher priority.
         *
         * Example:
         *
         * LOW = 3
         *
         * waiting 0 sec:
         * 3
         *
         * waiting 20 sec:
         * 2
         *
         * waiting 40 sec:
         * 1
         *
         * Never go above HIGH.
         */
        return (int) Math.max(
                1,
                originalPriority - agingLevels
        );
    }

    // ==========================================
    // QUEUE ENTRY
    // ==========================================

    private static class QueueEntry {

        private final Task task;
        private final long sequence;
        private final long addedAt;

        public QueueEntry(
                Task task,
                long sequence,
                long addedAt) {

            this.task = task;
            this.sequence = sequence;
            this.addedAt = addedAt;
        }

        public Task getTask() {

            return task;
        }

        public long getSequence() {

            return sequence;
        }

        public long getAddedAt() {

            return addedAt;
        }
    }
}
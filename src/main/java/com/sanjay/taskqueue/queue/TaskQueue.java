package com.sanjay.taskqueue.queue;

import com.sanjay.taskqueue.model.Task;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TaskQueue {

    // A waiting task improves by one priority level every 20 seconds.
    private static final long AGING_INTERVAL_MS = 20_000;

    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    private final PriorityBlockingQueue<QueueEntry> queue =
            new PriorityBlockingQueue<>(
                    11,
                    (entry1, entry2) -> {
                        int priority1 = getEffectivePriority(entry1);
                        int priority2 = getEffectivePriority(entry2);

                        int priorityComparison =
                                Integer.compare(priority1, priority2);

                        if (priorityComparison != 0) {
                            return priorityComparison;
                        }

                        return Long.compare(
                                entry1.getSequence(),
                                entry2.getSequence()
                        );
                    }
            );

    public void addTask(Task task) {
        long sequence = sequenceGenerator.getAndIncrement();
        long addedAt = System.currentTimeMillis();

        QueueEntry entry =
                new QueueEntry(task, sequence, addedAt);

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

    public Task getTask() throws InterruptedException {
        QueueEntry entry =
                queue.poll(5, TimeUnit.SECONDS);

        if (entry == null) {
            return null;
        }

        return entry.getTask();
    }

    public int size() {
        return queue.size();
    }

    private int getEffectivePriority(QueueEntry entry) {
        int originalPriority =
                entry.getTask().getPriority().getValue();

        long waitingTime =
                System.currentTimeMillis() - entry.getAddedAt();

        long agingLevels =
                waitingTime / AGING_INTERVAL_MS;

        return (int) Math.max(
                1,
                originalPriority - agingLevels
        );
    }

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
package com.sanjay.taskqueue.repository;

import com.sanjay.taskqueue.db.DatabaseConnection;
import com.sanjay.taskqueue.model.Task;
import com.sanjay.taskqueue.model.TaskPriority;
import com.sanjay.taskqueue.model.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskRepository {

    public void save(Task task) {
        String sql = """
                INSERT INTO tasks
                (id, name, priority, status, retry_count, max_retries, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, task.getId());
            statement.setString(2, task.getName());
            statement.setString(3, task.getPriority().name());
            statement.setString(4, task.getStatus().name());
            statement.setInt(5, task.getRetryCount());
            statement.setInt(6, task.getMaxRetries());
            statement.setString(7, task.getIdempotencyKey());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save task", e);
        }
    }

    public Task findById(UUID id) {
        String sql = """
                SELECT id, name, idempotency_key, priority, status,
                       retry_count, max_retries
                FROM tasks
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapTask(resultSet);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find task: " + id, e);
        }
    }

    public Task findByIdempotencyKey(String idempotencyKey) {
        String sql = """
                SELECT id, name, idempotency_key, priority, status,
                       retry_count, max_retries
                FROM tasks
                WHERE idempotency_key = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, idempotencyKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapTask(resultSet);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to find task by idempotency key", e);
        }
    }

    public void update(Task task) {
        String sql = """
                UPDATE tasks
                SET priority = ?,
                    status = ?,
                    retry_count = ?,
                    max_retries = ?,
                    idempotency_key = ?,
                    locked_at = CASE
                        WHEN ? = 'RUNNING' THEN locked_at
                        ELSE NULL
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, task.getPriority().name());
            statement.setString(2, task.getStatus().name());
            statement.setInt(3, task.getRetryCount());
            statement.setInt(4, task.getMaxRetries());
            statement.setString(5, task.getIdempotencyKey());
            statement.setString(6, task.getStatus().name());
            statement.setObject(7, task.getId());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update task", e);
        }
    }

    public void updateStatus(Task task) {
        String sql = """
                UPDATE tasks
                SET status = ?,
                    retry_count = ?,
                    locked_at = CASE
                        WHEN ? = 'RUNNING' THEN locked_at
                        ELSE NULL
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, task.getStatus().name());
            statement.setInt(2, task.getRetryCount());
            statement.setString(3, task.getStatus().name());
            statement.setObject(4, task.getId());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update task status", e);
        }
    }

    public boolean claimTask(UUID taskId) {
        String sql = """
                UPDATE tasks
                SET status = 'RUNNING',
                    locked_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                AND status IN ('QUEUED', 'RETRYING')
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to claim task", e);
        }
    }

    public boolean renewTaskLock(UUID taskId) {
        String sql = """
                UPDATE tasks
                SET locked_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                AND status = 'RUNNING'
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);

            int rowsUpdated = statement.executeUpdate();

            System.out.println(
                    "Heartbeat: task=" + taskId
                            + " | rowsUpdated=" + rowsUpdated
            );

            return rowsUpdated == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to renew task lock", e);
        }
    }

    public int recoverStuckTasks(int timeoutSeconds) {
        String sql = """
                UPDATE tasks
                SET status = 'QUEUED',
                    locked_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'RUNNING'
                AND locked_at IS NOT NULL
                AND locked_at < CURRENT_TIMESTAMP
                    - (? * INTERVAL '1 second')
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, timeoutSeconds);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to recover stuck tasks", e);
        }
    }

    public boolean isTaskCompleted(UUID taskId) {
        String sql = """
                SELECT status
                FROM tasks
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return "COMPLETED".equals(resultSet.getString("status"));
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check task status", e);
        }
    }

    public List<Task> findAll() {
        String sql = """
                SELECT id, name, idempotency_key, priority, status,
                       retry_count, max_retries
                FROM tasks
                ORDER BY created_at DESC
                """;

        List<Task> tasks = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                tasks.add(mapTask(resultSet));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch tasks", e);
        }

        return tasks;
    }

    public List<Task> findByStatus(TaskStatus status) {
        String sql = """
                SELECT id, name, idempotency_key, priority, status,
                       retry_count, max_retries
                FROM tasks
                WHERE status = ?
                ORDER BY created_at DESC
                """;

        List<Task> tasks = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status.name());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(mapTask(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to fetch tasks by status", e);
        }

        return tasks;
    }

    public List<Task> findStaleRunningTasks() {
        String sql = """
                SELECT id, name, idempotency_key, priority, status,
                       retry_count, max_retries
                FROM tasks
                WHERE status = 'RUNNING'
                AND locked_at IS NOT NULL
                AND locked_at < CURRENT_TIMESTAMP
                    - INTERVAL '30 seconds'
                ORDER BY created_at DESC
                """;

        List<Task> tasks = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                tasks.add(mapTask(resultSet));
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to find stale running tasks", e);
        }

        return tasks;
    }

    public int recoverStuckTask(UUID taskId, int timeoutSeconds) {
        String sql = """
                UPDATE tasks
                SET status = 'QUEUED',
                    locked_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                AND status = 'RUNNING'
                AND locked_at IS NOT NULL
                AND locked_at < CURRENT_TIMESTAMP
                    - (? * INTERVAL '1 second')
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            statement.setInt(2, timeoutSeconds);

            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to recover task: " + taskId, e);
        }
    }

    public int recoverInterruptedExecutions() {
        String sql = """
                UPDATE task_executions
                SET status = 'FAILED'
                WHERE status = 'RUNNING'
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to recover interrupted executions", e);
        }
    }

    public void moveToDeadLetterQueue(Task task, String reason) {
        String sql = """
                INSERT INTO dead_letter_tasks
                (id, task_id, name, failure_reason, retry_count)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, task.getId());
            statement.setString(3, task.getName());
            statement.setString(4, reason);
            statement.setInt(5, task.getRetryCount());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to move task to DLQ", e);
        }
    }

    public boolean cancelTask(UUID taskId) {
        String sql = """
                UPDATE tasks
                SET status = 'CANCELLED',
                    locked_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                AND status IN ('QUEUED', 'RETRYING')
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cancel task", e);
        }
    }

    public List<DlqTask> findAllDeadLetterTasks() {
        String sql = """
                SELECT id, task_id, name, failure_reason,
                       retry_count, created_at
                FROM dead_letter_tasks
                ORDER BY created_at DESC
                """;

        List<DlqTask> tasks = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                tasks.add(new DlqTask(
                        (UUID) resultSet.getObject("id"),
                        (UUID) resultSet.getObject("task_id"),
                        resultSet.getString("name"),
                        resultSet.getString("failure_reason"),
                        resultSet.getInt("retry_count"),
                        resultSet.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch DLQ tasks", e);
        }

        return tasks;
    }

    public boolean retryDeadLetterTask(UUID dlqId) {
        String findSql = """
                SELECT task_id
                FROM dead_letter_tasks
                WHERE id = ?
                """;

        String updateTaskSql = """
                UPDATE tasks
                SET status = 'QUEUED',
                    retry_count = 0,
                    locked_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                AND status = 'FAILED'
                """;

        String deleteExecutionsSql = """
                DELETE FROM task_executions
                WHERE task_id = ?
                """;

        String deleteDlqSql = """
                DELETE FROM dead_letter_tasks
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            UUID taskId;

            try (PreparedStatement statement =
                         connection.prepareStatement(findSql)) {

                statement.setObject(1, dlqId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return false;
                    }

                    taskId = (UUID) resultSet.getObject("task_id");
                }
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(updateTaskSql)) {

                statement.setObject(1, taskId);

                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(deleteExecutionsSql)) {

                statement.setObject(1, taskId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(deleteDlqSql)) {

                statement.setObject(1, dlqId);

                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to retry DLQ task", e);
        }
    }

    public boolean deleteFromDeadLetterQueue(UUID dlqId) {
        String sql = """
                DELETE FROM dead_letter_tasks
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, dlqId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete DLQ task", e);
        }
    }

    public UUID findTaskIdFromDeadLetterQueue(UUID dlqId) {
        String sql = """
                SELECT task_id
                FROM dead_letter_tasks
                WHERE id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, dlqId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return (UUID) resultSet.getObject("task_id");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find DLQ task", e);
        }
    }

    public boolean removeFromDeadLetterQueue(UUID dlqId) {
        return deleteFromDeadLetterQueue(dlqId);
    }

    public boolean claimTaskExecution(UUID taskId, int attemptNumber) {
        String sql = """
                INSERT INTO task_executions
                (task_id, attempt_number, status)
                VALUES (?, ?, 'RUNNING')
                ON CONFLICT (task_id, attempt_number)
                DO NOTHING
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            statement.setInt(2, attemptNumber);

            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to claim task execution", e);
        }
    }

    public String getTaskExecutionStatus(UUID taskId, int attemptNumber) {
        String sql = """
                SELECT status
                FROM task_executions
                WHERE task_id = ?
                AND attempt_number = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            statement.setInt(2, attemptNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("status");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to get task execution status", e);
        }
    }

    public void markTaskExecutionCompleted(UUID taskId, int attemptNumber) {
        String sql = """
                UPDATE task_executions
                SET status = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP
                WHERE task_id = ?
                AND attempt_number = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            statement.setInt(2, attemptNumber);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to mark task execution completed", e);
        }
    }

    public void markTaskExecutionFailed(UUID taskId, int attemptNumber) {
        String sql = """
                UPDATE task_executions
                SET status = 'FAILED'
                WHERE task_id = ?
                AND attempt_number = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, taskId);
            statement.setInt(2, attemptNumber);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to mark task execution failed", e);
        }
    }

    private Task mapTask(ResultSet resultSet) throws SQLException {
        return new Task(
                (UUID) resultSet.getObject("id"),
                resultSet.getString("name"),
                resultSet.getString("idempotency_key"),
                TaskPriority.valueOf(resultSet.getString("priority")),
                TaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("retry_count"),
                resultSet.getInt("max_retries")
        );
    }
}
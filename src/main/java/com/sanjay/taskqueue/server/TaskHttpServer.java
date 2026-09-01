package com.sanjay.taskqueue.server;

import com.sanjay.taskqueue.model.Task;
import com.sanjay.taskqueue.model.TaskPriority;
import com.sanjay.taskqueue.model.TaskStatus;
import com.sanjay.taskqueue.queue.TaskQueue;
import com.sanjay.taskqueue.repository.DlqTask;
import com.sanjay.taskqueue.repository.TaskRepository;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class TaskHttpServer {

    private final TaskRepository repository;
    private final TaskQueue queue;

    private HttpServer server;

    public TaskHttpServer(
            TaskRepository repository,
            TaskQueue queue) {

        this.repository = repository;
        this.queue = queue;
    }

    // ==========================================
    // START SERVER
    // ==========================================

    public void start() throws IOException {

        server = HttpServer.create(
                new InetSocketAddress(8080),
                0
        );

        server.createContext(
                "/tasks",
                this::handleTasks
        );

        server.createContext(
                "/dlq",
                this::handleDlq
        );

        server.start();

        System.out.println(
                "HTTP server started on port 8080"
        );
    }

    // ==========================================
    // STOP SERVER
    // ==========================================

    public void stop() {

        if (server != null) {
            server.stop(2);
        }
    }

    // ==========================================
    // TASK REQUEST ROUTER
    // ==========================================

    private void handleTasks(
            HttpExchange exchange)
            throws IOException {

        try {

            String method =
                    exchange.getRequestMethod();

            URI uri =
                    exchange.getRequestURI();

            String path =
                    uri.getPath();

            // ==========================================
            // POST /tasks
            // ==========================================

            if (method.equals("POST")
                    && path.equals("/tasks")) {

                createTask(exchange);
                return;
            }

            // ==========================================
            // GET /tasks
            // ==========================================

            if (method.equals("GET")
                    && path.equals("/tasks")) {

                getTasks(exchange);
                return;
            }

            // ==========================================
            // GET /tasks/{id}
            // ==========================================

            if (method.equals("GET")
                    && path.startsWith("/tasks/")) {

                getTask(exchange);
                return;
            }

            // ==========================================
            // DELETE /tasks/{id}
            // ==========================================

            if (method.equals("DELETE")
                    && path.startsWith("/tasks/")) {

                cancelTask(exchange);
                return;
            }

            sendResponse(
                    exchange,
                    404,
                    "{\"error\":\"Not found\"}"
            );

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"error\":\"Internal server error\"}"
            );
        }
    }

    // ==========================================
    // DLQ REQUEST ROUTER
    // ==========================================

    private void handleDlq(
            HttpExchange exchange)
            throws IOException {

        try {

            String method =
                    exchange.getRequestMethod();

            String path =
                    exchange.getRequestURI()
                            .getPath();

            // ==========================================
            // GET /dlq
            // ==========================================

            if (method.equals("GET")
                    && path.equals("/dlq")) {

                getDeadLetterTasks(exchange);
                return;
            }

            // ==========================================
            // POST /dlq/{id}/retry
            // ==========================================

            if (method.equals("POST")
                    && path.startsWith("/dlq/")
                    && path.endsWith("/retry")) {

                retryDeadLetterTask(exchange);
                return;
            }

            // ==========================================
            // DELETE /dlq/{id}
            // ==========================================

            if (method.equals("DELETE")
                    && path.startsWith("/dlq/")) {

                deleteDeadLetterTask(exchange);
                return;
            }

            sendResponse(
                    exchange,
                    404,
                    "{\"error\":\"Not found\"}"
            );

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    "{\"error\":\"Internal server error\"}"
            );
        }
    }

    // ==========================================
    // CREATE TASK
    // ==========================================

    private void createTask(
            HttpExchange exchange)
            throws IOException {

        String query =
                exchange.getRequestURI()
                        .getRawQuery();

        // ==========================================
        // GET TASK NAME
        // ==========================================

        String name =
                getQueryParameter(
                        query,
                        "name"
                );

        if (name == null || name.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"error\":\"name is required\"}"
            );

            return;
        }

        // ==========================================
        // GET IDEMPOTENCY KEY
        // ==========================================

        String idempotencyKey =
                getQueryParameter(
                        query,
                        "idempotencyKey"
                );

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            sendResponse(
                    exchange,
                    400,
                    "{\"error\":\"idempotencyKey is required\"}"
            );

            return;
        }

        // ==========================================
        // GET PRIORITY
        // ==========================================

        String priorityParameter =
                getQueryParameter(
                        query,
                        "priority"
                );

        TaskPriority priority =
                TaskPriority.MEDIUM;

        // ==========================================
        // VALIDATE PRIORITY
        // ==========================================

        if (priorityParameter != null
                && !priorityParameter.isBlank()) {

            try {

                priority =
                        TaskPriority.valueOf(
                                priorityParameter.toUpperCase()
                        );

            } catch (IllegalArgumentException e) {

                sendResponse(
                        exchange,
                        400,
                        "{\"error\":\"Invalid task priority\"}"
                );

                return;
            }
        }

        // ==========================================
        // CHECK DUPLICATE REQUEST
        // ==========================================

        Task existingTask =
                repository.findByIdempotencyKey(
                        idempotencyKey
                );

        if (existingTask != null) {

            sendResponse(
                    exchange,
                    200,
                    taskToJson(existingTask)
            );

            return;
        }

        // ==========================================
        // CREATE NEW TASK
        // ==========================================

        Task task =
                new Task(
                        name,
                        idempotencyKey,
                        priority
                );

        // ==========================================
        // PERSIST FIRST
        // ==========================================

        try {

            repository.save(task);

        } catch (RuntimeException e) {

            /*
             * Another request may have inserted
             * the same idempotency key between
             * our lookup and save.
             *
             * Check again and return the
             * existing task.
             */

            Task existing =
                    repository.findByIdempotencyKey(
                            idempotencyKey
                    );

            if (existing != null) {

                sendResponse(
                        exchange,
                        200,
                        taskToJson(existing)
                );

                return;
            }

            throw e;
        }

        // ==========================================
        // ENQUEUE
        // ==========================================

        queue.addTask(task);

        // ==========================================
        // RESPONSE
        // ==========================================

        sendResponse(
                exchange,
                201,
                taskToJson(task)
        );
    }

    // ==========================================
    // GET ALL TASKS
    // ==========================================

    private void getTasks(
            HttpExchange exchange)
            throws IOException {

        String query =
                exchange.getRequestURI()
                        .getRawQuery();

        String status =
                getQueryParameter(
                        query,
                        "status"
                );

        List<Task> tasks;

        if (status == null) {

            tasks =
                    repository.findAll();

        } else {

            try {

                TaskStatus taskStatus =
                        TaskStatus.valueOf(
                                status.toUpperCase()
                        );

                tasks =
                        repository.findByStatus(
                                taskStatus
                        );

            } catch (IllegalArgumentException e) {

                sendResponse(
                        exchange,
                        400,
                        "{\"error\":\"Invalid task status\"}"
                );

                return;
            }
        }

        StringBuilder json =
                new StringBuilder("[");

        for (int i = 0;
             i < tasks.size();
             i++) {

            if (i > 0) {
                json.append(",");
            }

            json.append(
                    taskToJson(
                            tasks.get(i)
                    )
            );
        }

        json.append("]");

        sendResponse(
                exchange,
                200,
                json.toString()
        );
    }

    // ==========================================
    // GET TASK BY ID
    // ==========================================

    private void getTask(
            HttpExchange exchange)
            throws IOException {

        String path =
                exchange.getRequestURI()
                        .getPath();

        String id =
                path.substring(
                        "/tasks/".length()
                );

        try {

            UUID taskId =
                    UUID.fromString(id);

            Task task =
                    repository.findById(
                            taskId
                    );

            if (task == null) {

                sendResponse(
                        exchange,
                        404,
                        "{\"error\":\"Task not found\"}"
                );

                return;
            }

            sendResponse(
                    exchange,
                    200,
                    taskToJson(task)
            );

        } catch (IllegalArgumentException e) {

            sendResponse(
                    exchange,
                    400,
                    "{\"error\":\"Invalid UUID\"}"
            );
        }
    }

    // ==========================================
    // CANCEL TASK
    // ==========================================

    private void cancelTask(
            HttpExchange exchange)
            throws IOException {

        String path =
                exchange.getRequestURI()
                        .getPath();

        String id =
                path.substring(
                        "/tasks/".length()
                );

        try {

            UUID taskId =
                    UUID.fromString(id);

            Task task =
                    repository.findById(
                            taskId
                    );

            if (task == null) {

                sendResponse(
                        exchange,
                        404,
                        "{\"error\":\"Task not found\"}"
                );

                return;
            }

            boolean cancelled =
                    repository.cancelTask(
                            taskId
                    );

            if (!cancelled) {

                sendResponse(
                        exchange,
                        409,
                        "{\"error\":\"Task cannot be cancelled in its current state\"}"
                );

                return;
            }

            sendResponse(
                    exchange,
                    200,
                    "{\"message\":\"Task cancelled\"}"
            );

        } catch (IllegalArgumentException e) {

            sendResponse(
                    exchange,
                    400,
                    "{\"error\":\"Invalid UUID\"}"
            );
        }
    }

    // ==========================================
    // GET DEAD LETTER TASKS
    // ==========================================

    private void getDeadLetterTasks(
            HttpExchange exchange)
            throws IOException {

        List<DlqTask> tasks =
                repository.findAllDeadLetterTasks();

        StringBuilder json =
                new StringBuilder("[");

        for (int i = 0;
             i < tasks.size();
             i++) {

            if (i > 0) {
                json.append(",");
            }

            json.append(
                    dlqTaskToJson(
                            tasks.get(i)
                    )
            );
        }

        json.append("]");

        sendResponse(
                exchange,
                200,
                json.toString()
        );
    }

    // ==========================================
    // DELETE DLQ TASK
    // ==========================================

    private void deleteDeadLetterTask(
            HttpExchange exchange)
            throws IOException {

        String path =
                exchange.getRequestURI()
                        .getPath();

        String id =
                path.substring(
                        "/dlq/".length()
                );

        try {

            UUID dlqId =
                    UUID.fromString(id);

            boolean deleted =
                    repository.deleteFromDeadLetterQueue(
                            dlqId
                    );

            if (!deleted) {

                sendResponse(
                        exchange,
                        404,
                        "{\"error\":\"DLQ task not found\"}"
                );

                return;
            }

            sendResponse(
                    exchange,
                    200,
                    "{\"message\":\"DLQ task deleted\"}"
            );

        } catch (IllegalArgumentException e) {

            sendResponse(
                    exchange,
                    400,
                    "{\"error\":\"Invalid UUID\"}"
            );
        }
    }

    // ==========================================
    // RETRY DLQ TASK
    // ==========================================

    private void retryDeadLetterTask(
            HttpExchange exchange)
            throws IOException {

        String path =
                exchange.getRequestURI()
                        .getPath();

        String prefix = "/dlq/";
        String suffix = "/retry";

        String id =
                path.substring(
                        prefix.length(),
                        path.length() - suffix.length()
                );

        try {

            UUID dlqId =
                    UUID.fromString(id);

            // ==========================================
            // FIND ORIGINAL TASK
            // ==========================================

            UUID taskId =
                    repository.findTaskIdFromDeadLetterQueue(
                            dlqId
                    );

            if (taskId == null) {

                sendResponse(
                        exchange,
                        404,
                        "{\"error\":\"DLQ task not found\"}"
                );

                return;
            }

            // ==========================================
            // RESET TASK
            // ==========================================

            boolean retried =
                    repository.retryDeadLetterTask(
                            dlqId
                    );

            if (!retried) {

                sendResponse(
                        exchange,
                        409,
                        "{\"error\":\"Task cannot be retried in its current state\"}"
                );

                return;
            }

            // ==========================================
            // LOAD RESET TASK
            // ==========================================

            Task task =
                    repository.findById(
                            taskId
                    );

            if (task == null) {

                sendResponse(
                        exchange,
                        404,
                        "{\"error\":\"Original task not found\"}"
                );

                return;
            }

            // ==========================================
            // REQUEUE
            // ==========================================

            queue.addTask(task);

            // ==========================================
            // SUCCESS
            // ==========================================

            sendResponse(
                    exchange,
                    200,
                    """
                    {
                      "message":"Task requeued successfully",
                      "taskId":"%s"
                    }
                    """.formatted(
                            taskId
                    ).replace("\n", "")
            );

        } catch (IllegalArgumentException e) {

            sendResponse(
                    exchange,
                    400,
                    "{\"error\":\"Invalid DLQ UUID\"}"
            );
        }
    }

    // ==========================================
    // TASK -> JSON
    // ==========================================

    private String taskToJson(
            Task task) {

        return """
                {
                  "id":"%s",
                  "name":"%s",
                  "idempotencyKey":"%s",
                  "priority":"%s",
                  "maxRetries":%d,
                  "retryCount":%d,
                  "status":"%s"
                }
                """.formatted(
                task.getId(),
                escapeJson(task.getName()),
                escapeJson(task.getIdempotencyKey()),
                task.getPriority(),
                task.getMaxRetries(),
                task.getRetryCount(),
                task.getStatus()
        ).replace("\n", "");
    }

    // ==========================================
    // DLQ TASK -> JSON
    // ==========================================

    private String dlqTaskToJson(
            DlqTask task) {

        return """
                {
                  "id":"%s",
                  "taskId":"%s",
                  "name":"%s",
                  "failureReason":"%s",
                  "retryCount":%d
                }
                """.formatted(
                task.getId(),
                task.getTaskId(),
                escapeJson(task.getName()),
                escapeJson(task.getFailureReason()),
                task.getRetryCount()
        ).replace("\n", "");
    }

    // ==========================================
    // ESCAPE JSON
    // ==========================================

    private String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    // ==========================================
    // QUERY PARAMETER
    // ==========================================

    private String getQueryParameter(
            String query,
            String parameter) {

        if (query == null) {
            return null;
        }

        for (String pair :
                query.split("&")) {

            String[] parts =
                    pair.split("=", 2);

            if (parts.length == 2
                    && parts[0].equals(parameter)) {

                return URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8
                );
            }
        }

        return null;
    }

    // ==========================================
    // HTTP RESPONSE
    // ==========================================

    private void sendResponse(
            HttpExchange exchange,
            int status,
            String response)
            throws IOException {

        byte[] bytes =
                response.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json"
                );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }
}
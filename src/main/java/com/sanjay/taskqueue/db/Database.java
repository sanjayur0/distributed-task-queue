package com.sanjay.taskqueue.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    private static final String URL =
            System.getenv().getOrDefault(
                    "DB_URL",
                    "jdbc:postgresql://localhost:5432/taskqueue"
            );

    private static final String USER =
            System.getenv().getOrDefault(
                    "DB_USER",
                    "postgres"
            );

    private static final String PASSWORD =
            System.getenv("DB_PASSWORD");

    public static Connection getConnection()
            throws SQLException {

        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new IllegalStateException(
                    "DB_PASSWORD environment variable is not set"
            );
        }

        return DriverManager.getConnection(
                URL,
                USER,
                PASSWORD
        );
    }
}
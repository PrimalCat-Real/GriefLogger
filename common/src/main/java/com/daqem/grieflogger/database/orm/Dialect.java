package com.daqem.grieflogger.database.orm;

import com.daqem.grieflogger.GriefLogger;

/**
 * Database dialect abstraction for MySQL, PostgreSQL, and SQLite support.
 * Dialect is determined by {@link GriefLogger#DATABASE_TYPE}.
 */
public enum Dialect {
    SQLITE,
    MYSQL,
    POSTGRESQL;

    /**
     * Returns the current dialect based on configuration.
     */
    public static Dialect current() {
        return switch (GriefLogger.DATABASE_TYPE) {
            case 1 -> MYSQL;
            case 2 -> POSTGRESQL;
            default -> SQLITE;
        };
    }

    public String insertIgnore() {
        return switch (this) {
            case MYSQL -> "INSERT IGNORE";
            case POSTGRESQL -> "INSERT";
            case SQLITE -> "INSERT OR IGNORE";
        };
    }

    public String onConflictDoNothing() {
        return switch (this) {
            case MYSQL -> "ON DUPLICATE KEY UPDATE id = id";
            case POSTGRESQL, SQLITE -> "ON CONFLICT DO NOTHING";
        };
    }

    public String onConflictUpdate(String column) {
        return switch (this) {
            case MYSQL -> "ON DUPLICATE KEY UPDATE " + column + " = VALUES(" + column + ")";
            case POSTGRESQL, SQLITE -> "ON CONFLICT DO UPDATE SET " + column + " = excluded." + column;
        };
    }

    public String autoIncrement() {
        return switch (this) {
            case MYSQL -> "AUTO_INCREMENT";
            case POSTGRESQL -> ""; 
            case SQLITE -> "AUTOINCREMENT";
        };
    }

    public String intType() {
        return switch (this) {
            case MYSQL -> "int";
            case POSTGRESQL -> "integer";
            case SQLITE -> "integer";
        };
    }

    public String bigintType() {
        return switch (this) {
            case MYSQL, POSTGRESQL -> "bigint";
            case SQLITE -> "integer";
        };
    }

    public String textType(int length) {
        return switch (this) {
            case MYSQL -> "varchar(" + length + ")";
            case POSTGRESQL -> "varchar(" + length + ")";
            case SQLITE -> "text";
        };
    }

    public String blobType() {
        return switch (this) {
            case MYSQL, SQLITE -> "blob";
            case POSTGRESQL -> "bytea";
        };
    }

    public String primaryKeyDef(String column) {
        return switch (this) {
            case MYSQL -> column + " int NOT NULL AUTO_INCREMENT, PRIMARY KEY (" + column + ")";
            case POSTGRESQL -> column + " SERIAL PRIMARY KEY";
            case SQLITE -> column + " integer PRIMARY KEY AUTOINCREMENT";
        };
    }

    public String tableOptions() {
        return this == MYSQL ? "ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4" : "";
    }

    public String limitOffset(int limit, int offset) {
        return "LIMIT " + limit + (offset > 0 ? " OFFSET " + offset : "");
    }

    public boolean isMysql() {
        return this == MYSQL;
    }

    public boolean isPostgresql() {
        return this == POSTGRESQL;
    }

    public boolean isSqlite() {
        return this == SQLITE;
    }
}

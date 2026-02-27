package com.daqem.grieflogger.database;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.GriefLoggerExpectPlatform;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import com.daqem.grieflogger.database.queue.IQueue;
import com.daqem.grieflogger.database.queue.Queue;
import com.supermartijn642.configlib.ConfigLib;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

public class Database {

    @Nullable
    private Connection connection;
    @Nullable
    private Statement statement;
    public final IQueue queue;
    public final IQueue batchQueue;

    public Database() {
        queue = new Queue(this, false);
        batchQueue = new Queue(this, true);
    }

    public boolean createConnection() {
        boolean connected;
        if (GriefLoggerConfig.useMysql.get()) {
            connected = createMysqlConnection();
        } else {
            connected = createSqliteConnection();
        }
        if (connection != null) {
            GriefLogger.LOGGER.info("Connected to database");
            try {
                statement = connection.createStatement();
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to create statement", e);
                return false;
            }
            try {
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to set auto commit", e);
                return false;
            }
        }
        return connected && connection != null && statement != null;
    }

    public boolean createMysqlConnection() {
        String host = GriefLoggerConfig.mysqlHost.get();
        int port = GriefLoggerConfig.mysqlPort.get();
        String database = GriefLoggerConfig.mysqlDatabase.get();
        String user = GriefLoggerConfig.mysqlUsername.get();
        String password = GriefLoggerConfig.mysqlPassword.get();
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?allowReconnect=true&autoReconnect=true&connectTimeout=" + GriefLoggerConfig.mysqlTimeout.get();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            GriefLogger.LOGGER.error("Failed to load MySQL driver", e);
            return false;
        }
        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to connect to MySQL database", e);
            return false;
        }
        return connection != null;
    }

    public boolean createSqliteConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            GriefLogger.LOGGER.error("Failed to load SQLite driver", e);
            return false;
        }
        Path path = ConfigLib.getConfigFolder().toPath().resolve(GriefLogger.MOD_ID);
        if (!path.toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            path.toFile().mkdirs();
        }
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:database.db");
            configureSqlitePerformance();
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to connect to SQLite database", e);
            return false;
        }
        return connection != null;
    }

    /**
     * Configure SQLite for optimal write performance.
     * WAL mode allows concurrent reads during writes and improves throughput.
     */
    private void configureSqlitePerformance() {
        if (connection == null) return;

        try (Statement pragmaStatement = connection.createStatement()) {
            // WAL mode: Write-Ahead Logging for better concurrency
            pragmaStatement.execute("PRAGMA journal_mode=WAL;");

            // NORMAL synchronous: Good balance between safety and speed
            // FULL is safest but slower, OFF is fastest but risky
            pragmaStatement.execute("PRAGMA synchronous=NORMAL;");

            // Increase cache size (negative = KB, positive = pages)
            // 10000 pages * 4KB = ~40MB cache
            pragmaStatement.execute("PRAGMA cache_size=10000;");

            // Memory-mapped I/O size (256MB)
            pragmaStatement.execute("PRAGMA mmap_size=268435456;");

            // Temporary tables in memory
            pragmaStatement.execute("PRAGMA temp_store=MEMORY;");

            GriefLogger.LOGGER.info("SQLite WAL mode enabled with performance optimizations");
        } catch (SQLException e) {
            GriefLogger.LOGGER.warn("Failed to configure SQLite performance settings", e);
        }
    }

    /**
     * Perform WAL checkpoint to consolidate the write-ahead log.
     * Should be called periodically (e.g., every 5-10 minutes).
     */
    public void performWalCheckpoint() {
        if (GriefLogger.DATABASE_TYPE != 0 || connection == null) return;

        try (Statement checkpointStatement = connection.createStatement()) {
            checkpointStatement.execute("PRAGMA wal_checkpoint(TRUNCATE);");
        } catch (SQLException e) {
            GriefLogger.LOGGER.warn("Failed to perform WAL checkpoint", e);
        }
    }

    public void createTable(String sql) {
        try {
            if (statement != null) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to create table", e);
        }
    }

    public void execute(String sql, boolean logError) {
        try {
            if (statement != null) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            if (logError) {
                GriefLogger.LOGGER.error("Failed to execute statement", e);
            }
        }
    }

    public PreparedStatement prepareStatement(String query) throws SQLException {
        if (connection != null) {
            return connection.prepareStatement(query);
        } else {
            throw new SQLException("Connection is null");
        }
    }

    public void executeStatements(List<PreparedStatement> statements, boolean isBatch) {
        try {
            for (PreparedStatement statement : statements) {
                if (statement == null) {
                    GriefLogger.LOGGER.error("Statement is null");
                    continue;
                }

                if (statement.isClosed()) {
                    GriefLogger.LOGGER.error("Statement is closed");
                    continue;
                }

                try (statement) {
                    if (isBatch) {
                        statement.executeBatch(); // Execute as a batch
                    } else {
                        statement.executeUpdate(); // Execute individually
                    }
                }
            }
            if (!statements.isEmpty()) {
                commit();
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to execute statements", e);
        }
    }

    /**
     * Commit the current transaction.
     */
    public void commit() {
        if (connection != null) {
            try {
                connection.commit();
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to commit transaction", e);
            }
        }
    }

    /**
     * Get the underlying connection.
     * Use sparingly - prefer using prepared statements through queue.
     */
    @Nullable
    public Connection getConnection() {
        return connection;
    }

    /**
     * Check if the database connection is valid.
     */
    public boolean isConnected() {
        if (connection == null) return false;
        try {
            return connection.isValid(5);  // 5 second timeout
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Flush all pending queues immediately.
     * This ensures all queued statements are written to the database
     * before running queries that need fresh data (like inspect).
     */
    public void flushQueues() {
        queue.execute();
        batchQueue.execute();
    }
}

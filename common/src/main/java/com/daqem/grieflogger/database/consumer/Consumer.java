package com.daqem.grieflogger.database.consumer;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Double-buffer consumer for database operations.
 * Based on CoreProtect's Consumer implementation.
 *
 * Architecture:
 * Game Thread -> Queue (buffer 0) <-> Consumer Thread -> Process -> DB
 *                    | swap every 500ms
 *                Queue (buffer 1)
 *
 * This allows the game thread to continue adding to one buffer
 * while the consumer thread processes the other buffer.
 */
public class Consumer implements Runnable {

    private static Thread consumerThread = null;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * Current active buffer index (0 or 1).
     * Game thread writes to this buffer.
     */
    public static final AtomicInteger currentBuffer = new AtomicInteger(0);

    /**
     * Double buffers for queued statements.
     */
    public static final ConcurrentHashMap<Integer, List<PreparedStatement>> buffers = new ConcurrentHashMap<>();

    /**
     * Batch size for executeBatch calls.
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * Buffer swap interval in milliseconds.
     */
    private static final int SWAP_INTERVAL_MS = 500;

    private final Database database;

    public Consumer(Database database) {
        this.database = database;
    }

    /**
     * Initialize the double-buffer system.
     */
    public static void initialize() {
        buffers.put(0, new ArrayList<>());
        buffers.put(1, new ArrayList<>());
    }

    /**
     * Add a statement to the current buffer.
     * Thread-safe for game thread.
     */
    public static void addStatement(PreparedStatement statement) {
        int buffer = currentBuffer.get();
        List<PreparedStatement> list = buffers.get(buffer);
        if (list != null) {
            synchronized (list) {
                list.add(statement);
            }
        }
    }

    /**
     * Get the current queue size (for monitoring).
     */
    public static int getQueueSize() {
        int total = 0;
        for (List<PreparedStatement> list : buffers.values()) {
            synchronized (list) {
                total += list.size();
            }
        }
        return total;
    }

    @Override
    public void run() {
        GriefLogger.LOGGER.info("Consumer thread started");
        boolean lastRun = false;

        while (running.get() || !lastRun) {
            if (!running.get()) {
                lastRun = true;
            }

            try {
                int processBuffer;
                if (currentBuffer.get() == 0) {
                    currentBuffer.set(1);
                    processBuffer = 0;
                } else {
                    currentBuffer.set(0);
                    processBuffer = 1;
                }

                Thread.sleep(SWAP_INTERVAL_MS);

                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }

                processBuffer(processBuffer, lastRun);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                GriefLogger.LOGGER.error("Error in Consumer thread", e);
                try {
                    Thread.sleep(5000);  
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        GriefLogger.LOGGER.info("Consumer thread stopped");
    }

    /**
     * Process all statements in a buffer.
     */
    private void processBuffer(int bufferIndex, boolean isLastRun) {
        List<PreparedStatement> statements;

        List<PreparedStatement> buffer = buffers.get(bufferIndex);
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            statements = new ArrayList<>(buffer);
            buffer.clear();
        }

        if (statements.isEmpty()) {
            return;
        }

        int processedCount = 0;
        int errorCount = 0;

        try {
            for (int i = 0; i < statements.size(); i++) {
                PreparedStatement stmt = statements.get(i);
                if (stmt == null || stmt.isClosed()) {
                    continue;
                }

                try {
                    stmt.executeUpdate();
                    processedCount++;

                    if (processedCount % BATCH_SIZE == 0) {
                        database.commit();
                    }
                } catch (SQLException e) {
                    errorCount++;
                    if (errorCount <= 5) {  
                        GriefLogger.LOGGER.error("Failed to execute statement: {}", e.getMessage());
                    }
                } finally {
                    try {
                        stmt.close();
                    } catch (SQLException ignored) {}
                }
            }

            database.commit();

            if (processedCount > 0) {
                GriefLogger.LOGGER.debug("Consumer processed {} statements ({} errors)", processedCount, errorCount);
            }

        } catch (Exception e) {
            GriefLogger.LOGGER.error("Error processing buffer", e);
        }
    }

    /**
     * Start the consumer thread.
     */
    public static void start(Database database) {
        if (running.compareAndSet(false, true)) {
            initialize();
            consumerThread = new Thread(new Consumer(database), "GriefLogger-Consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
    }

    /**
     * Stop the consumer thread.
     */
    public static void stop() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(10000);  
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumerThread = null;
        }
    }

    /**
     * Pause/resume the consumer.
     */
    public static void setPaused(boolean pause) {
        paused.set(pause);
    }

    /**
     * Check if consumer is running.
     */
    public static boolean isRunning() {
        return running.get() && consumerThread != null && consumerThread.isAlive();
    }
}

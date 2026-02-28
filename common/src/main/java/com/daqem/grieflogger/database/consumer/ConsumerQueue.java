package com.daqem.grieflogger.database.consumer;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.queue.IQueue;

import java.sql.PreparedStatement;

/**
 * Queue implementation that uses the double-buffer Consumer system.
 * This replaces the old Queue for high-throughput operations.
 */
public class ConsumerQueue implements IQueue {

    private final Database database;
    private final boolean isBatch;

    public ConsumerQueue(Database database, boolean isBatch) {
        this.database = database;
        this.isBatch = isBatch;
    }

    @Override
    public void add(PreparedStatement statement) {
        Consumer.addStatement(statement);
    }

    @Override
    public void execute() {
    }

    @Override
    public void hello() {
        if (database.isConnected()) {
            return;
        }
        database.createConnection();
    }

    /**
     * Get the current queue size across both buffers.
     */
    public int size() {
        return Consumer.getQueueSize();
    }

    /**
     * Check if the consumer is running.
     */
    public boolean isConsumerRunning() {
        return Consumer.isRunning();
    }
}

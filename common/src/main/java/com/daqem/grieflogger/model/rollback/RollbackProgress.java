package com.daqem.grieflogger.model.rollback;

import com.daqem.grieflogger.util.Theme;
import net.kyori.adventure.text.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks rollback/restore operation progress.
 * Supports action bar updates and cancellation.
 */
public class RollbackProgress {

    private static final Map<UUID, RollbackProgress> ACTIVE_OPERATIONS = new ConcurrentHashMap<>();

    private final UUID playerUuid;
    private final String operationType;
    private final int totalBlocks;
    private final int totalContainerItems;
    private final long startTime;
    private final AtomicInteger processedBlocks = new AtomicInteger(0);
    private final AtomicInteger processedContainerItems = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public RollbackProgress(UUID playerUuid, String operationType, int totalBlocks, int totalContainerItems) {
        this.playerUuid = playerUuid;
        this.operationType = operationType;
        this.totalBlocks = totalBlocks;
        this.totalContainerItems = totalContainerItems;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Start tracking a new rollback operation for a player.
     */
    public static RollbackProgress start(UUID playerUuid, String operationType, int totalBlocks, int totalContainerItems) {
        cancel(playerUuid);

        RollbackProgress progress = new RollbackProgress(playerUuid, operationType, totalBlocks, totalContainerItems);
        ACTIVE_OPERATIONS.put(playerUuid, progress);
        return progress;
    }

    /**
     * Get active operation for a player.
     */
    public static RollbackProgress get(UUID playerUuid) {
        return ACTIVE_OPERATIONS.get(playerUuid);
    }

    /**
     * Check if a player has an active operation.
     */
    public static boolean hasActiveOperation(UUID playerUuid) {
        RollbackProgress progress = ACTIVE_OPERATIONS.get(playerUuid);
        return progress != null && !progress.isCompleted() && !progress.isCancelled();
    }

    /**
     * Cancel an active operation for a player.
     */
    public static boolean cancel(UUID playerUuid) {
        RollbackProgress progress = ACTIVE_OPERATIONS.get(playerUuid);
        if (progress != null && !progress.isCompleted()) {
            progress.cancelled.set(true);
            ACTIVE_OPERATIONS.remove(playerUuid);
            return true;
        }
        return false;
    }

    /**
     * Increment processed blocks count.
     */
    public void incrementBlocks(int count) {
        processedBlocks.addAndGet(count);
    }

    /**
     * Increment processed container items count.
     */
    public void incrementContainerItems(int count) {
        processedContainerItems.addAndGet(count);
    }

    /**
     * Mark operation as completed.
     */
    public void complete() {
        completed.set(true);
        ACTIVE_OPERATIONS.remove(playerUuid);
    }

    /**
     * Check if operation was cancelled.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Check if operation is completed.
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * Get progress percentage (0-100).
     */
    public int getPercentage() {
        int total = totalBlocks + totalContainerItems;
        if (total == 0) return 100;
        int processed = processedBlocks.get() + processedContainerItems.get();
        return Math.min(100, (processed * 100) / total);
    }

    /**
     * Get elapsed time in milliseconds.
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Send action bar progress update to player.
     */
    public void sendProgressBar(ServerPlayer player) {
        int percentage = getPercentage();
        int processed = processedBlocks.get() + processedContainerItems.get();
        int total = totalBlocks + totalContainerItems;

        int barLength = 20;
        int filledLength = (percentage * barLength) / 100;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filledLength ? "█" : "░");
        }

        Component progressComponent = Component.empty()
                .append(Theme.primary(operationType + " "))
                .append(Theme.muted("["))
                .append(Theme.success(bar.toString().substring(0, filledLength)))
                .append(Theme.muted(bar.toString().substring(filledLength)))
                .append(Theme.muted("] "))
                .append(Theme.accent(percentage + "%"))
                .append(Theme.muted(" (" + processed + "/" + total + ")"));

        sendActionBar(player, progressComponent);
    }

    /**
     * Send completion message to action bar.
     */
    public void sendCompletionBar(ServerPlayer player, int blocksProcessed, int containerItems, int dropped) {
        long elapsed = getElapsedMs();

        Component message;
        if (isCancelled()) {
            message = Theme.error(operationType + " cancelled! ")
                    .append(Theme.muted("Processed " + (blocksProcessed + containerItems) + " items before cancellation."));
        } else {
            StringBuilder details = new StringBuilder();
            if (blocksProcessed > 0) {
                details.append(blocksProcessed).append(" blocks");
            }
            if (containerItems > 0) {
                if (details.length() > 0) details.append(", ");
                details.append(containerItems).append(" items");
            }
            if (dropped > 0) {
                details.append(" (").append(dropped).append(" dropped)");
            }

            message = Theme.success(operationType + " complete! ")
                    .append(Theme.muted(details + " in " + elapsed + "ms"));
        }

        sendActionBar(player, message);
    }

    private void sendActionBar(ServerPlayer player, Component message) {
        net.minecraft.network.chat.Component minecraftComponent = Theme.toMinecraft(message);
        player.connection.send(new ClientboundSetActionBarTextPacket(minecraftComponent));
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getOperationType() {
        return operationType;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getTotalContainerItems() {
        return totalContainerItems;
    }

    public int getProcessedBlocks() {
        return processedBlocks.get();
    }

    public int getProcessedContainerItems() {
        return processedContainerItems.get();
    }
}

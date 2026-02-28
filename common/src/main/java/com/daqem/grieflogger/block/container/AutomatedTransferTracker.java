package com.daqem.grieflogger.block.container;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks containers that have been modified by automated systems (chutes, hoppers).
 * When an automated system moves items from a container, it marks the container here.
 * ContainerTransactionManager checks this before logging to avoid attributing
 * automated transfers to the player who has the container open.
 *
 * Simplified approach: track which containers had automated activity recently,
 * and skip logging for those containers during that window.
 */
public class AutomatedTransferTracker {

    private static final AutomatedTransferTracker INSTANCE = new AutomatedTransferTracker();

    /**
     * Key: BlockPos of the container being modified by automation
     * Value: Timestamp when the automated transfer happened
     */
    private final Map<BlockPos, Long> automatedContainers = new ConcurrentHashMap<>();

    /**
     * How long to suppress player logging after automated activity (in milliseconds).
     * Set to 200ms to cover 4 ticks at 20 TPS.
     */
    private static final long SUPPRESSION_WINDOW_MS = 200;

    private AutomatedTransferTracker() {}

    public static AutomatedTransferTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Mark a container as having automated activity.
     * Call this when a chute/hopper modifies items in a container.
     *
     * @param containerPos Position of the container being modified
     */
    public void markAutomatedActivity(BlockPos containerPos) {
        automatedContainers.put(containerPos.immutable(), System.currentTimeMillis());
        cleanup();
    }

    /**
     * Check if a container has had recent automated activity.
     * If so, player-attributed changes should be suppressed.
     *
     * @param containerPos Position of the container
     * @return True if there was recent automated activity and player logging should be skipped
     */
    public boolean hasRecentAutomatedActivity(BlockPos containerPos) {
        Long timestamp = automatedContainers.get(containerPos);
        if (timestamp == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - timestamp;
        if (elapsed < SUPPRESSION_WINDOW_MS) {
            return true;
        }

        automatedContainers.remove(containerPos);
        return false;
    }

    /**
     * Remove stale records older than 10x the suppression window.
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        long maxAge = SUPPRESSION_WINDOW_MS * 10;
        automatedContainers.entrySet().removeIf(entry -> now - entry.getValue() > maxAge);
    }
}

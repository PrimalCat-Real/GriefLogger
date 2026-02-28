package com.daqem.grieflogger.model.rollback;

import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.model.history.BlockHistory;
import com.daqem.grieflogger.model.history.ContainerHistory;
import com.daqem.grieflogger.model.history.IHistory;

import java.util.List;
import java.util.UUID;

/**
 * Represents a preview rollback session for a player.
 * Stores all the data needed to apply the rollback later.
 *
 * @param playerUuid   UUID of the player who initiated the preview
 * @param playerName   Name of the player
 * @param levelName    World name where rollback will occur
 * @param history      List of block changes to be rolled back
 * @param filterList   Original filter list used for the query
 * @param createdAt    Timestamp when preview was created
 * @param isRestore    True if this is a restore operation, false for rollback
 */
public record PreviewSession(
        UUID playerUuid,
        String playerName,
        String levelName,
        List<IHistory> history,
        FilterList filterList,
        long createdAt,
        boolean isRestore
) {
    private static final long EXPIRATION_TIME_MS = 5 * 60 * 1000; 

    /**
     * Create a new preview session for a rollback operation.
     */
    public static PreviewSession createRollback(
            UUID playerUuid,
            String playerName,
            String levelName,
            List<IHistory> history,
            FilterList filterList
    ) {
        return new PreviewSession(
                playerUuid,
                playerName,
                levelName,
                history,
                filterList,
                System.currentTimeMillis(),
                false
        );
    }

    /**
     * Create a new preview session for a restore operation.
     */
    public static PreviewSession createRestore(
            UUID playerUuid,
            String playerName,
            String levelName,
            List<IHistory> history,
            FilterList filterList
    ) {
        return new PreviewSession(
                playerUuid,
                playerName,
                levelName,
                history,
                filterList,
                System.currentTimeMillis(),
                true
        );
    }

    /**
     * Check if this preview session has expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > EXPIRATION_TIME_MS;
    }

    /**
     * Get the number of blocks that will be affected.
     */
    public int getBlockCount() {
        return (int) history.stream()
                .filter(h -> h instanceof BlockHistory)
                .count();
    }

    /**
     * Get the number of container items that will be affected.
     */
    public int getContainerCount() {
        return (int) history.stream()
                .filter(h -> h instanceof ContainerHistory)
                .count();
    }

    /**
     * Get the operation type name.
     */
    public String getOperationName() {
        return isRestore ? "restore" : "rollback";
    }

    /**
     * Get time remaining before expiration in seconds.
     */
    public int getSecondsRemaining() {
        long elapsed = System.currentTimeMillis() - createdAt;
        long remaining = EXPIRATION_TIME_MS - elapsed;
        return Math.max(0, (int) (remaining / 1000));
    }
}

package com.daqem.grieflogger.model.rollback;

import java.util.UUID;

/**
 * Represents a rollback/restore/undo operation.
 *
 * @param id          Database ID
 * @param time        Timestamp of operation
 * @param actorUuid   UUID of player who performed operation
 * @param actorName   Name of player
 * @param actionType  0=rollback, 1=restore, 2=undo
 * @param levelName   World name
 * @param centerX     Center X coordinate
 * @param centerY     Center Y coordinate
 * @param centerZ     Center Z coordinate
 * @param radius      Radius of operation
 * @param timeFilter  Time filter used (milliseconds back)
 * @param userFilter  User filter (comma-separated user IDs)
 * @param blockCount  Number of blocks affected
 * @param durationMs  How long the operation took
 */
public record RollbackJob(
        int id,
        long time,
        UUID actorUuid,
        String actorName,
        int actionType,
        String levelName,
        int centerX,
        int centerY,
        int centerZ,
        int radius,
        long timeFilter,
        String userFilter,
        int blockCount,
        long durationMs
) {
    public static final int TYPE_ROLLBACK = 0;
    public static final int TYPE_RESTORE = 1;
    public static final int TYPE_UNDO = 2;

    /**
     * Create a new job without ID (for insertion).
     */
    public static RollbackJob create(
            UUID actorUuid,
            String actorName,
            int actionType,
            String levelName,
            int centerX, int centerY, int centerZ,
            int radius,
            long timeFilter,
            String userFilter,
            int blockCount,
            long durationMs
    ) {
        return new RollbackJob(
                -1,
                System.currentTimeMillis(),
                actorUuid,
                actorName,
                actionType,
                levelName,
                centerX, centerY, centerZ,
                radius,
                timeFilter,
                userFilter,
                blockCount,
                durationMs
        );
    }

    public String getActionTypeName() {
        return switch (actionType) {
            case TYPE_ROLLBACK -> "rollback";
            case TYPE_RESTORE -> "restore";
            case TYPE_UNDO -> "undo";
            default -> "unknown";
        };
    }
}

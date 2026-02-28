package com.daqem.grieflogger.model.rollback;

/**
 * Represents a single block change within a rollback operation.
 * Used to reverse rollback operations (undo).
 *
 * @param id           Database ID
 * @param jobId        Parent rollback job ID
 * @param time         Timestamp
 * @param actionType   0=block_restore (was air, now block), 1=block_remove (was block, now air)
 * @param levelName    World name
 * @param x            X coordinate
 * @param y            Y coordinate
 * @param z            Z coordinate
 * @param oldStateId   Block state ID before rollback
 * @param newStateId   Block state ID after rollback
 * @param oldMaterial  Material name before rollback
 * @param newMaterial  Material name after rollback
 */
public record RollbackAction(
        int id,
        int jobId,
        long time,
        int actionType,
        String levelName,
        int x,
        int y,
        int z,
        int oldStateId,
        int newStateId,
        String oldMaterial,
        String newMaterial
) {
    public static final int TYPE_BLOCK_RESTORE = 0;  
    public static final int TYPE_BLOCK_REMOVE = 1;   

    /**
     * Create a new action without ID (for insertion).
     */
    public static RollbackAction create(
            int jobId,
            int actionType,
            String levelName,
            int x, int y, int z,
            int oldStateId,
            int newStateId,
            String oldMaterial,
            String newMaterial
    ) {
        return new RollbackAction(
                -1,
                jobId,
                System.currentTimeMillis(),
                actionType,
                levelName,
                x, y, z,
                oldStateId,
                newStateId,
                oldMaterial,
                newMaterial
        );
    }
}

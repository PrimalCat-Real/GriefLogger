package com.daqem.grieflogger.model.rollback;

/**
 * Represents a container item change during a rollback operation.
 * Used to track and potentially undo container rollbacks.
 *
 * @param id              Database ID
 * @param jobId           Parent rollback job ID
 * @param time            Timestamp
 * @param actionType      0=item_added (to container), 1=item_removed (from container)
 * @param levelName       World name
 * @param x               X coordinate of container
 * @param y               Y coordinate of container
 * @param z               Z coordinate of container
 * @param material        Item material name
 * @param amount          Item count
 * @param data            Compressed NBT data
 * @param originalAction  Original action that was rolled back (0=deposit, 1=withdraw)
 */
public record ContainerRollbackAction(
        int id,
        int jobId,
        long time,
        int actionType,
        String levelName,
        int x,
        int y,
        int z,
        String material,
        int amount,
        byte[] data,
        int originalAction
) {
    public static final int TYPE_ITEM_ADDED = 0;    
    public static final int TYPE_ITEM_REMOVED = 1;  
    public static final int TYPE_ITEM_DROPPED = 2;  

    /**
     * Create a new container rollback action without ID (for insertion).
     */
    public static ContainerRollbackAction create(
            int jobId,
            int actionType,
            String levelName,
            int x, int y, int z,
            String material,
            int amount,
            byte[] data,
            int originalAction
    ) {
        return new ContainerRollbackAction(
                -1,
                jobId,
                System.currentTimeMillis(),
                actionType,
                levelName,
                x, y, z,
                material,
                amount,
                data,
                originalAction
        );
    }

    /**
     * Get a human-readable description of the action type.
     */
    public String getActionTypeName() {
        return switch (actionType) {
            case TYPE_ITEM_ADDED -> "added";
            case TYPE_ITEM_REMOVED -> "removed";
            case TYPE_ITEM_DROPPED -> "dropped";
            default -> "unknown";
        };
    }
}

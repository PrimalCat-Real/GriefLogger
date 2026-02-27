package com.daqem.grieflogger.block.container;

import net.minecraft.server.level.ServerPlayer;

public interface IContainerTransactionManager {
    /**
     * Called every tick while the container is open.
     * Compares current state with last known state and logs changes.
     */
    void tick(ServerPlayer serverPlayer);

    /**
     * Called when the container is closed.
     */
    void finalize(ServerPlayer serverPlayer);
}

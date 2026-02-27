package com.daqem.grieflogger.block.coalesce;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class Aggregate {
    long tick;
    BlockEventKind kind;
    BlockState oldState;
    BlockState newState;
    BlockPos position;
    String preDestructionNbt;
    UUID userUuid;
    String phantomUser;  // For natural events attribution (#water, #fire, etc.)

    Aggregate(long tick, BlockEventKind kind, BlockPos position, UUID userUuid) {
        this.tick = tick;
        this.kind = kind;
        this.position = position;
        this.userUuid = userUuid;
        this.phantomUser = null;
    }

    Aggregate(long tick, BlockEventKind kind, BlockPos position, UUID userUuid, String phantomUser) {
        this.tick = tick;
        this.kind = kind;
        this.position = position;
        this.userUuid = userUuid;
        this.phantomUser = phantomUser;
    }

    /**
     * Returns true if this aggregate represents a phantom (natural) event.
     */
    public boolean isPhantom() {
        return phantomUser != null;
    }

    /**
     * Gets the effective user name for logging.
     * Returns phantom user if set, otherwise null (real user UUID should be used).
     */
    public String getEffectiveUserName() {
        return phantomUser;
    }
}

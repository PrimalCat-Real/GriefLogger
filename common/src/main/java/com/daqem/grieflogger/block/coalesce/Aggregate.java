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

    Aggregate(long tick, BlockEventKind kind, BlockPos position, UUID userUuid) {
        this.tick = tick;
        this.kind = kind;
        this.position = position;
        this.userUuid = userUuid;
    }
}

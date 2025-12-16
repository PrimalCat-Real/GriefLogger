package com.daqem.grieflogger.block.coalesce;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockEventCoalescer {

    private static final Map<String, Aggregate> byKey = new ConcurrentHashMap<>();
    private static long lastPurgeTick = -1;

    private static String makeKey(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    public static synchronized void record(ServerLevel level, BlockPos pos,
                                           BlockState oldState, BlockState newState,
                                           BlockEventKind kind, UUID userUuid) {

        BlockPos immutablePos = pos.immutable();

        if (BlockEventLock.isLocked()) {
            return;
        }



        if (GriefLogger.SYSTEM_UUID.equals(userUuid)) {
            if (!BlockChangeFilter.shouldLog(level, immutablePos, oldState, newState)) {
                return;
            }
        }

        GriefLogger.LOGGER.info("[Coalescer] INCOMING: {} at {} by {} (Kind: {})",
                newState.getBlock().getName().getString(), immutablePos.toShortString(), userUuid, kind);

        long tick = level.getGameTime();
        String key = makeKey(level.dimension(), immutablePos);
        Aggregate agg = byKey.get(key);

        if (agg == null || agg.tick != tick) {
            agg = new Aggregate(tick, kind, immutablePos, userUuid);
            byKey.put(key, agg);
            if (kind == BlockEventKind.SYSTEM_BREAK || kind == BlockEventKind.PLAYER_BREAK) {
                captureBlockEntityData(level, immutablePos, agg);
            }
            agg.oldState = oldState;
        } else {
            if (kind.priority > agg.kind.priority) {
                agg.kind = kind;
                agg.userUuid = userUuid;

                if (agg.preDestructionNbt == null && (kind == BlockEventKind.SYSTEM_BREAK || kind == BlockEventKind.PLAYER_BREAK)) {
                    captureBlockEntityData(level, immutablePos, agg);
                }
            }
        }

        agg.newState = newState;

        if (lastPurgeTick != tick) {
            purgeAndEmitOlder(level, tick);
            lastPurgeTick = tick;
        }
    }

    // Захват NBT данных из TileEntity
    private static void captureBlockEntityData(ServerLevel level, BlockPos pos, Aggregate agg) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            try {
                CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                if (tag != null) {
                    agg.preDestructionNbt = tag.toString();
                }
            } catch (Exception ignored) {

            }
        }
    }

    private static void purgeAndEmitOlder(ServerLevel level, long currentTick) {
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, Aggregate> entry : byKey.entrySet()) {
            Aggregate a = entry.getValue();
            if (a.tick < currentTick) {
                emit(level, a);
                keysToRemove.add(entry.getKey());
            }
        }

        for (String key : keysToRemove) {
            byKey.remove(key);
        }

        if (byKey.size() > 50000) {
            byKey.clear();
        }
    }

    private static void emit(ServerLevel level, Aggregate agg) {
        BlockAction action;
        BlockState targetState;

        boolean becameAir = (agg.newState == null || agg.newState.isAir());

        if (agg.kind == BlockEventKind.PLAYER_BREAK || agg.kind == BlockEventKind.SYSTEM_BREAK || becameAir) {
            action = BlockAction.BREAK_BLOCK;
            targetState = agg.oldState;
        } else {
            action = BlockAction.PLACE_BLOCK;
            targetState = agg.newState;
        }

        if (targetState == null || targetState.isAir()) return;

        GriefLogger.LOGGER.info("[Coalescer] EMIT TO DB: Action={} User={} Block={} Pos={}",
                action, agg.userUuid, targetState.getBlock().getName().getString(), agg.position.toShortString());

        Services.BLOCK.insertBlockState(
                agg.userUuid,
                level.dimension().location().toString(),
                agg.position,
                targetState,
                action
        );
    }
    public static void flush(ServerLevel level) {
        purgeAndEmitOlder(level, Long.MAX_VALUE);
    }

    public static synchronized void tick(ServerLevel level) {
        long tick = level.getGameTime();
        if (lastPurgeTick != tick) {
            purgeAndEmitOlder(level, tick);
            lastPurgeTick = tick;
        }
    }
}

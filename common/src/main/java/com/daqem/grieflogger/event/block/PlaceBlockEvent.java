package com.daqem.grieflogger.event.block;

import com.daqem.grieflogger.block.coalesce.BlockEventCoalescer;
import com.daqem.grieflogger.block.coalesce.BlockEventKind;
import com.daqem.grieflogger.block.coalesce.BlockEventLock;
import com.daqem.grieflogger.model.action.BlockAction;
import com.daqem.grieflogger.player.GriefLoggerServerPlayer;
import dev.architectury.event.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PlaceBlockEvent {

    public static void placeBlock(Level level, BlockPos pos, BlockState state, Entity placer) {
        if (placer instanceof GriefLoggerServerPlayer serverPlayer) {
            if (level instanceof ServerLevel serverLevel) {
                BlockEventCoalescer.record(
                        serverLevel,
                        pos,
                        serverLevel.getBlockState(pos),
                        state,
                        BlockEventKind.PLAYER_PLACE,
                        serverPlayer.grieflogger$asServerPlayer().getUUID()
                );
            }
//            try {
//                BlockEventLock.lock();
//                LogBlockEvent.logBlock(serverPlayer, level, state, pos, BlockAction.PLACE_BLOCK);
//            } finally {
//                BlockEventLock.unlock();
//            }

        }
    }
}

package com.daqem.grieflogger.event.block;

import com.daqem.grieflogger.block.DeployerActionContext;
import com.daqem.grieflogger.block.coalesce.BlockEventCoalescer;
import com.daqem.grieflogger.block.coalesce.BlockEventKind;
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
                BlockPos deployerPosition = DeployerActionContext.getPosition();
                if (deployerPosition != null) {
                    String phantomUser = "#deployer@" + deployerPosition.getX() + ","
                            + deployerPosition.getY() + "," + deployerPosition.getZ();
                    BlockEventCoalescer.recordExplicitWithPhantom(
                            serverLevel,
                            pos,
                            serverLevel.getBlockState(pos),
                            state,
                            BlockEventKind.MIXIN_SPECIAL,
                            phantomUser
                    );
                } else {
                    BlockEventCoalescer.record(
                            serverLevel,
                            pos,
                            serverLevel.getBlockState(pos),
                            state,
                            BlockEventKind.PLAYER_PLACE,
                            serverPlayer.grieflogger$asServerPlayer().getUUID()
                    );
                }
            }

        }
    }
}

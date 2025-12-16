package com.daqem.grieflogger.mixin;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.coalesce.BlockEventCoalescer;
import com.daqem.grieflogger.block.coalesce.BlockEventKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void onDestroyBlockProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {

        if (progress >= 9) {
            ServerLevel serverLevel = (ServerLevel)(Object)this;
//            System.out.println("[BlockBreak] Final progress at " + pos +
//                    " (breakerId: " + breakerId + ")");

            BlockState oldState = serverLevel.getBlockState(pos);

            if (oldState.isAir()) return;
            BlockEventCoalescer.record(
                    serverLevel,
                    pos,
                    oldState,
                    Blocks.AIR.defaultBlockState(),
                    BlockEventKind.SYSTEM_BREAK,
                    GriefLogger.SYSTEM_UUID
            );
        }
    }

}

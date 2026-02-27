package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.coalesce.BlockEventLock;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DrillBlockEntity.class, remap = false)
public abstract class DrillBlockEntityMixin {
    @Shadow
    protected abstract BlockPos getBreakingPos();

    @Inject(method = "onBlockBroken", at = @At("HEAD"))
    private void grieflogger$onBlockBroken(BlockState stateToBreak, CallbackInfo ci) {
        BlockEntity self = (BlockEntity)(Object) this;

        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;
        if (stateToBreak == null || stateToBreak.isAir()) return;

        BlockPos drillPos = self.getBlockPos();
        BlockPos breakPos = this.getBreakingPos();

//        grieflogger$logBlockBreak(level, breakPos, stateToBreak, drillPos);

        BlockEventLock.lock();
        try {
            grieflogger$logBlockBreak(level, breakPos, stateToBreak, drillPos);
        } finally {
            BlockEventLock.unlock();
        }
    }



    @Unique
    private void grieflogger$logBlockBreak(Level level, BlockPos pos, BlockState state, BlockPos drillPos) {
        try {
            String phantomUser = "#drill@" + drillPos.getX() + "," + drillPos.getY() + "," + drillPos.getZ();

            Services.USER.insertPhantomUser(phantomUser);
            Services.BLOCK.insertBlockStateWithPhantom(
                    phantomUser,
                    level.dimension().location().toString(),
                    pos,
                    state,
                    BlockAction.BREAK_BLOCK
            );

            GriefLogger.LOGGER.info("[drill] Action=BREAK Block={} Pos={} Drill={}",
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    pos.toShortString(),
                    drillPos.toShortString()
            );
        } catch (Exception e) {
            GriefLogger.LOGGER.error("Failed to log drill block break", e);
        }
    }
}

package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.coalesce.BlockEventCoalescer;
import com.daqem.grieflogger.block.coalesce.BlockEventKind;
import com.daqem.grieflogger.block.coalesce.BlockEventLock;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
        if (GriefLogger.isRollbackActive()) return;
        BlockEntity self = (BlockEntity)(Object) this;

        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;
        if (stateToBreak == null || stateToBreak.isAir()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos drillPos = self.getBlockPos();
        BlockPos breakPos = this.getBreakingPos();

        String phantomUser = "#drill@" + drillPos.getX() + "," + drillPos.getY() + "," + drillPos.getZ();

        BlockEventCoalescer.recordWithPhantom(
                serverLevel,
                breakPos,
                stateToBreak,
                Blocks.AIR.defaultBlockState(),
                BlockEventKind.MIXIN_SPECIAL,
                phantomUser
        );
    }
}

package com.daqem.grieflogger.mixin;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.coalesce.BlockEventCoalescer;
import com.daqem.grieflogger.block.coalesce.BlockEventKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD"))
    private void onSetBlockShort(BlockPos pos, BlockState newState, int flags, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockState oldState = level.getBlockState(pos);

        BlockEventCoalescer.record(serverLevel, pos, oldState, newState, BlockEventKind.SYSTEM_SET, GriefLogger.SYSTEM_UUID);
    }

    @Inject(method = "setBlockAndUpdate", at = @At("HEAD"))
    private void onSetBlockAndUpdate(BlockPos pos, BlockState newState, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockState oldState = level.getBlockState(pos);

        BlockEventCoalescer.record(serverLevel, pos, oldState, newState, BlockEventKind.SYSTEM_SET, GriefLogger.SYSTEM_UUID);
    }

    @Inject(method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"))
    private void onRemoveBlock(BlockPos pos, boolean isMoving, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockState oldState = level.getBlockState(pos);
        BlockState airState = Blocks.AIR.defaultBlockState();

        BlockEventCoalescer.record(serverLevel, pos, oldState, airState, BlockEventKind.SYSTEM_BREAK, GriefLogger.SYSTEM_UUID);

    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"))
    private void onDestroyBlock(BlockPos blockPos, boolean bl, Entity entity, int i, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockState oldState = level.getBlockState(blockPos);

        if (!oldState.isAir()) {
            BlockState airState = Blocks.AIR.defaultBlockState();
            BlockEventCoalescer.record(serverLevel, blockPos, oldState, airState, BlockEventKind.SYSTEM_BREAK, GriefLogger.SYSTEM_UUID);
        }

    }

    // TODO: FIX THIS SIGNATURE
//    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"))
//    private void onMarkAndNotifyBlock(BlockPos pos, LevelChunk levelChunk, BlockState oldState, BlockState newState, int flags, int recursion, CallbackInfo ci) {
//        Level level = (Level)(Object)this;
//        if (!(level instanceof ServerLevel serverLevel)) return;
//    }
}

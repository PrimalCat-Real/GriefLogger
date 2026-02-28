package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for Create's ChuteBlockEntity to log item transfers.
 * Logs when chute receives items and when it outputs items.
 */
@Mixin(value = ChuteBlockEntity.class, remap = false)
public abstract class ChuteBlockEntityMixin extends BlockEntity {

    @Shadow
    public ItemStack item;

    @Shadow
    public abstract float getItemMotion();

    public ChuteBlockEntityMixin() {
        super(null, null, null);
    }

    /**
     * Capture all item changes in chute.
     * - When old item is empty and new is not: item RECEIVED (ADD)
     * - When old item is not empty and new is empty: item SENT (REMOVE)
     */
    @Inject(
            method = "setItem(Lnet/minecraft/world/item/ItemStack;F)V",
            at = @At("HEAD")
    )
    private void grieflogger$onSetItem(ItemStack stack, float insertionPos, CallbackInfo ci) {
        if (GriefLogger.isRollbackActive()) return;

        Level level = this.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockPos chutePos = this.getBlockPos();
        ItemStack oldItem = this.item;

        if (oldItem != null && !oldItem.isEmpty() && stack.isEmpty()) {
            float motion = getItemMotion();
            boolean goingUp = motion > 0;
            BlockPos targetPos = goingUp ? chutePos.above() : chutePos.below();

            if (!grieflogger$isChute(level, targetPos)) {
                grieflogger$logTransfer(level, targetPos, oldItem, ItemAction.ADD_ITEM, chutePos);
            }
        }

        if (!stack.isEmpty()) {
            BlockPos sourcePos = insertionPos > 0.5f ? chutePos.above() : chutePos.below();

            if (!grieflogger$isChute(level, sourcePos)) {
                grieflogger$logTransfer(level, sourcePos, stack, ItemAction.REMOVE_ITEM, chutePos);
            }
        }
    }

    @Unique
    private void grieflogger$logTransfer(Level level, BlockPos pos, ItemStack stack, ItemAction action, BlockPos relatedPos) {
        try {
            SimpleItemStack simpleStack = new SimpleItemStack(stack);

            AutomatedTransferTracker.getInstance().markAutomatedActivity(pos);

            String chuteType = grieflogger$getChuteType();
            String phantomUser = "#" + chuteType;
            if (relatedPos != null) {
                phantomUser = "#" + chuteType + "@" + relatedPos.getX() + "," + relatedPos.getY() + "," + relatedPos.getZ();
            }

            Services.USER.insertPhantomUser(phantomUser);

            Services.CONTAINER.insertWithPhantom(
                    phantomUser,
                    level,
                    pos,
                    simpleStack,
                    action
            );

            GriefLogger.LOGGER.info("[{}] Action={} Item={}x{} Pos={} Related={}",
                    chuteType,
                    action == ItemAction.ADD_ITEM ? "ADD" : "REMOVE",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount(),
                    pos.toShortString(),
                    relatedPos != null ? relatedPos.toShortString() : "none"
            );
        } catch (Exception e) {
            GriefLogger.LOGGER.error("Failed to log chute transfer", e);
        }
    }

    @Unique
    private boolean grieflogger$isChute(Level level, BlockPos pos) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof ChuteBlockEntity;
        } catch (Exception e) {
            return false;
        }
    }

    @Unique
    private String grieflogger$getChuteType() {
        String className = this.getClass().getSimpleName();
        if (className.endsWith("BlockEntity")) {
            className = className.substring(0, className.length() - "BlockEntity".length());
        }
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}

package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.content.equipment.toolbox.ToolboxInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Logs every successful ToolboxInventory mutation. This covers the toolbox GUI,
 * connected hotbar slots, the dispose-all packet and third-party automation
 * using the NeoForge item-handler capability.
 */
@Mixin(value = ToolboxInventory.class, remap = false)
public abstract class ToolboxInventoryMixin extends ItemStackHandler {

    @Shadow
    @Final
    private ToolboxBlockEntity blockEntity;

    @Shadow
    boolean settling;

    @Unique
    private int grieflogger$deserializationDepth;

    protected ToolboxInventoryMixin() {
        super();
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    private void grieflogger$onInsert(
            int slot,
            ItemStack stack,
            boolean simulate,
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        if (simulate || stack.isEmpty()) {
            return;
        }

        ItemStack remainder = callbackInfo.getReturnValue();
        int insertedAmount = stack.getCount() - (remainder == null ? 0 : remainder.getCount());
        if (insertedAmount <= 0) {
            return;
        }

        ItemStack insertedStack = stack.copyWithCount(insertedAmount);
        grieflogger$log(insertedStack, ItemAction.ADD_ITEM);
    }

    /**
     * ToolboxInventory inherits extractItem without overriding it, so the mixin
     * adds the override to the target class and delegates to ItemStackHandler.
     */
    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack extractedStack = super.extractItem(slot, amount, simulate);
        if (!simulate && !extractedStack.isEmpty()) {
            grieflogger$log(extractedStack.copy(), ItemAction.REMOVE_ITEM);
        }
        return extractedStack;
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"))
    private void grieflogger$captureStackBeforeSet(
            int slot,
            ItemStack stack,
            CallbackInfo callbackInfo,
            @Share("previousStack") LocalRef<ItemStack> previousStack,
            @Share("replacementStack") LocalRef<ItemStack> replacementStack,
            @Share("ignoreSet") LocalBooleanRef ignoreSet
    ) {
        previousStack.set(getStackInSlot(slot).copy());
        replacementStack.set(stack.copy());
        ignoreSet.set(settling || grieflogger$isLoggingSuppressed());
    }

    @Inject(method = "setStackInSlot", at = @At("RETURN"))
    private void grieflogger$onStackSet(
            int slot,
            ItemStack stack,
            CallbackInfo callbackInfo,
            @Share("previousStack") LocalRef<ItemStack> previousStack,
            @Share("replacementStack") LocalRef<ItemStack> replacementStack,
            @Share("ignoreSet") LocalBooleanRef ignoreSet
    ) {
        if (ignoreSet.get()) {
            return;
        }

        ItemStack previous = previousStack.get();
        ItemStack replacement = replacementStack.get();
        if (ItemStack.isSameItemSameComponents(previous, replacement)) {
            int difference = replacement.getCount() - previous.getCount();
            if (difference > 0) {
                grieflogger$log(replacement.copyWithCount(difference), ItemAction.ADD_ITEM);
            } else if (difference < 0) {
                grieflogger$log(previous.copyWithCount(-difference), ItemAction.REMOVE_ITEM);
            }
            return;
        }

        if (!previous.isEmpty()) {
            grieflogger$log(previous, ItemAction.REMOVE_ITEM);
        }
        if (!replacement.isEmpty()) {
            grieflogger$log(replacement, ItemAction.ADD_ITEM);
        }
    }

    @Inject(
            method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD")
    )
    private void grieflogger$beginDeserialization(
            HolderLookup.Provider registries,
            CompoundTag compoundTag,
            CallbackInfo callbackInfo
    ) {
        grieflogger$deserializationDepth++;
    }

    @Inject(
            method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("RETURN")
    )
    private void grieflogger$endDeserialization(
            HolderLookup.Provider registries,
            CompoundTag compoundTag,
            CallbackInfo callbackInfo
    ) {
        if (grieflogger$deserializationDepth > 0) {
            grieflogger$deserializationDepth--;
        }
    }

    @Unique
    private boolean grieflogger$isLoggingSuppressed() {
        return grieflogger$deserializationDepth > 0 || ToolboxTransactionContext.isSuppressed();
    }

    @Unique
    private void grieflogger$log(ItemStack stack, ItemAction action) {
        if (GriefLogger.isRollbackActive() || grieflogger$isLoggingSuppressed() || stack.isEmpty()) {
            return;
        }
        if (blockEntity == null) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos toolboxPosition = blockEntity.getBlockPos();
        try {
            AutomatedTransferTracker.getInstance().markAutomatedActivity(toolboxPosition);

            ServerPlayer player = ToolboxTransactionContext.getPlayer();
            if (player != null) {
                Services.USER.insertOrUpdateName(player.getUUID(), player.getGameProfile().getName());
                Services.CONTAINER.insert(
                        player.getUUID(),
                        level,
                        toolboxPosition,
                        new SimpleItemStack(stack),
                        action
                );
            } else {
                String phantomUser = "#toolbox@" + toolboxPosition.getX() + ","
                        + toolboxPosition.getY() + "," + toolboxPosition.getZ();
                Services.USER.insertPhantomUser(phantomUser);
                Services.CONTAINER.insertWithPhantom(
                        phantomUser,
                        level,
                        toolboxPosition,
                        new SimpleItemStack(stack),
                        action
                );
            }

        } catch (Exception exception) {
            GriefLogger.LOGGER.error("Failed to log toolbox inventory change", exception);
        }
    }
}

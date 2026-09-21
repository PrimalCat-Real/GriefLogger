package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.DeployerTransactionLogger;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerItemHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Logs exact mutations performed through the deployer's item capability. */
@Mixin(value = DeployerItemHandler.class, remap = false)
public abstract class DeployerItemHandlerMixin {

    @Shadow
    private DeployerBlockEntity be;

    @Shadow
    public abstract ItemStack getStackInSlot(int slot);

    @Inject(method = "insertItem", at = @At("HEAD"))
    private void grieflogger$captureInsertedStack(
            int slot,
            ItemStack stack,
            boolean simulate,
            CallbackInfoReturnable<ItemStack> callbackInfo,
            @Share("offeredStack") LocalRef<ItemStack> offeredStack
    ) {
        offeredStack.set(stack.copy());
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    private void grieflogger$logInsertion(
            int slot,
            ItemStack stack,
            boolean simulate,
            CallbackInfoReturnable<ItemStack> callbackInfo,
            @Share("offeredStack") LocalRef<ItemStack> offeredStack
    ) {
        if (simulate) {
            return;
        }

        ItemStack offered = offeredStack.get();
        ItemStack remainder = callbackInfo.getReturnValue();
        int remainderAmount = remainder == null ? 0 : remainder.getCount();
        int insertedAmount = offered.getCount() - remainderAmount;
        if (insertedAmount > 0) {
            DeployerTransactionLogger.log(
                    be,
                    offered.copyWithCount(insertedAmount),
                    ItemAction.ADD_ITEM
            );
        }
    }

    @Inject(method = "extractItem", at = @At("RETURN"))
    private void grieflogger$logExtraction(
            int slot,
            int amount,
            boolean simulate,
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        if (!simulate) {
            DeployerTransactionLogger.log(be, callbackInfo.getReturnValue(), ItemAction.REMOVE_ITEM);
        }
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"))
    private void grieflogger$captureStackBeforeSet(
            int slot,
            ItemStack stack,
            CallbackInfo callbackInfo,
            @Share("previousStack") LocalRef<ItemStack> previousStack,
            @Share("replacementStack") LocalRef<ItemStack> replacementStack
    ) {
        previousStack.set(getStackInSlot(slot).copy());
        replacementStack.set(stack.copy());
    }

    @Inject(method = "setStackInSlot", at = @At("RETURN"))
    private void grieflogger$logStackSet(
            int slot,
            ItemStack stack,
            CallbackInfo callbackInfo,
            @Share("previousStack") LocalRef<ItemStack> previousStack,
            @Share("replacementStack") LocalRef<ItemStack> replacementStack
    ) {
        ItemStack previous = previousStack.get();
        ItemStack replacement = replacementStack.get();

        if (ItemStack.isSameItemSameComponents(previous, replacement)) {
            int difference = replacement.getCount() - previous.getCount();
            if (difference > 0) {
                DeployerTransactionLogger.log(
                        be,
                        replacement.copyWithCount(difference),
                        ItemAction.ADD_ITEM
                );
            } else if (difference < 0) {
                DeployerTransactionLogger.log(
                        be,
                        previous.copyWithCount(-difference),
                        ItemAction.REMOVE_ITEM
                );
            }
            return;
        }

        DeployerTransactionLogger.log(be, previous, ItemAction.REMOVE_ITEM);
        DeployerTransactionLogger.log(be, replacement, ItemAction.ADD_ITEM);
    }
}

package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.FunnelTransactionLogger;
import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * Logs transfers performed directly by the funnel block entity: extraction
 * into the world or onto a belt, and vertical belt input into an inventory.
 */
@Mixin(value = FunnelBlockEntity.class, remap = false)
public abstract class FunnelBlockEntityMixin {

    @WrapOperation(
            method = "activateExtractor",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;extract(Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;I)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 1
            )
    )
    private ItemStack grieflogger$logWorldExtraction(
            InvManipulationBehaviour inventory,
            ExtractionCountMode extractionCountMode,
            int amount,
            Operation<ItemStack> original
    ) {
        ItemStack extractedStack = ToolboxTransactionContext.withSuppressed(
                () -> original.call(inventory, extractionCountMode, amount)
        );
        FunnelTransactionLogger.log(
                (FunnelBlockEntity) (Object) this,
                extractedStack,
                ItemAction.REMOVE_ITEM
        );
        return extractedStack;
    }

    @WrapOperation(
            method = "activateExtractingBeltFunnel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;extract(Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;ILjava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$logBeltExtraction(
            InvManipulationBehaviour inventory,
            ExtractionCountMode extractionCountMode,
            int amount,
            Predicate<ItemStack> filter,
            Operation<ItemStack> original
    ) {
        ItemStack extractedStack = ToolboxTransactionContext.withSuppressed(
                () -> original.call(inventory, extractionCountMode, amount, filter)
        );
        FunnelTransactionLogger.log(
                (FunnelBlockEntity) (Object) this,
                extractedStack,
                ItemAction.REMOVE_ITEM
        );
        return extractedStack;
    }

    @WrapOperation(
            method = "handleDirectBeltInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;insert(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$logDirectBeltInsertion(
            InvManipulationBehaviour inventory,
            ItemStack stack,
            Operation<ItemStack> original,
            @Local(argsOnly = true) boolean simulate
    ) {
        ItemStack insertedStack = stack.copy();
        ItemStack remainder = simulate
                ? original.call(inventory, stack)
                : ToolboxTransactionContext.withSuppressed(() -> original.call(inventory, stack));

        if (!simulate) {
            logInsertedAmount(insertedStack, remainder);
        }
        return remainder;
    }

    private void logInsertedAmount(ItemStack insertedStack, ItemStack remainder) {
        int remainderAmount = remainder == null ? 0 : remainder.getCount();
        int insertedAmount = insertedStack.getCount() - remainderAmount;
        if (insertedAmount <= 0) {
            return;
        }

        FunnelTransactionLogger.log(
                (FunnelBlockEntity) (Object) this,
                insertedStack.copyWithCount(insertedAmount),
                ItemAction.ADD_ITEM
        );
    }
}

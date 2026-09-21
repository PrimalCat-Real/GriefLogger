package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.FunnelTransactionLogger;
import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.belt.transport.BeltFunnelInteractionHandler;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Logs belt-funnel transfers into the inventory attached behind the funnel. */
@Mixin(value = BeltFunnelInteractionHandler.class, remap = false)
public abstract class BeltFunnelInteractionHandlerMixin {

    @WrapOperation(
            method = "checkForFunnels",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;insert(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 1
            )
    )
    private static ItemStack grieflogger$logBeltFunnelInsertion(
            InvManipulationBehaviour inventory,
            ItemStack stack,
            Operation<ItemStack> original,
            @Local(ordinal = 0) FunnelBlockEntity funnelBlockEntity
    ) {
        ItemStack insertedStack = stack.copy();
        ItemStack remainder = ToolboxTransactionContext.withSuppressed(() -> original.call(inventory, stack));
        int remainderAmount = remainder == null ? 0 : remainder.getCount();
        int insertedAmount = insertedStack.getCount() - remainderAmount;
        if (insertedAmount > 0) {
            FunnelTransactionLogger.log(
                    funnelBlockEntity,
                    insertedStack.copyWithCount(insertedAmount),
                    ItemAction.ADD_ITEM
            );
        }
        return remainder;
    }
}

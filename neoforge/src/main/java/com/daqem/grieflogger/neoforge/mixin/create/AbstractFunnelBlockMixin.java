package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.FunnelTransactionLogger;
import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Logs item entities and player-held items inserted through a funnel. */
@Mixin(value = AbstractFunnelBlock.class, remap = false)
public abstract class AbstractFunnelBlockMixin {

    @WrapOperation(
            method = "tryInsert",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;insert(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private static ItemStack grieflogger$logInsertion(
            InvManipulationBehaviour inventory,
            ItemStack stack,
            Operation<ItemStack> original,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos funnelPosition,
            @Local(argsOnly = true) boolean simulate
    ) {
        ItemStack insertedStack = stack.copy();
        ItemStack remainder = simulate
                ? original.call(inventory, stack)
                : ToolboxTransactionContext.withSuppressed(() -> original.call(inventory, stack));

        if (!simulate && level.getBlockEntity(funnelPosition) instanceof FunnelBlockEntity funnelBlockEntity) {
            int remainderAmount = remainder == null ? 0 : remainder.getCount();
            int insertedAmount = insertedStack.getCount() - remainderAmount;
            if (insertedAmount > 0) {
                FunnelTransactionLogger.log(
                        funnelBlockEntity,
                        insertedStack.copyWithCount(insertedAmount),
                        ItemAction.ADD_ITEM
                );
            }
        }
        return remainder;
    }
}

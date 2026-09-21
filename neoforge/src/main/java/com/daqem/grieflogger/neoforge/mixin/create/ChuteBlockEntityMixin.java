package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.ChuteTransactionLogger;
import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * Logs only successful transfers between a chute and an external inventory.
 * Simulations, world ejection and internal chute-to-chute movement are ignored.
 */
@Mixin(value = ChuteBlockEntity.class, remap = false)
public abstract class ChuteBlockEntityMixin {

    @WrapOperation(
            method = "handleInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/item/ItemHelper;extract(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;Lcom/simibubi/create/foundation/item/ItemHelper$ExtractionCountMode;IZ)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 1
            )
    )
    private ItemStack grieflogger$logExtraction(
            IItemHandler inventory,
            Predicate<ItemStack> filter,
            ExtractionCountMode extractionCountMode,
            int amount,
            boolean simulate,
            Operation<ItemStack> original,
            @Local(argsOnly = true) float startLocation
    ) {
        ItemStack extractedStack = ToolboxTransactionContext.withSuppressed(
                () -> original.call(inventory, filter, extractionCountMode, amount, simulate)
        );

        if (!simulate) {
            ChuteBlockEntity chuteBlockEntity = (ChuteBlockEntity) (Object) this;
            BlockPos containerPosition = startLocation > 0.5f
                    ? chuteBlockEntity.getBlockPos().above()
                    : chuteBlockEntity.getBlockPos().below();
            ChuteTransactionLogger.log(
                    chuteBlockEntity,
                    containerPosition,
                    extractedStack,
                    ItemAction.REMOVE_ITEM
            );
        }
        return extractedStack;
    }

    @WrapOperation(
            method = "handleDownwardOutput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/ItemHandlerHelper;insertItemStacked(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$logDownwardInsertion(
            IItemHandler inventory,
            ItemStack stack,
            boolean simulate,
            Operation<ItemStack> original
    ) {
        ChuteBlockEntity chuteBlockEntity = (ChuteBlockEntity) (Object) this;
        return grieflogger$insertAndLog(
                inventory,
                stack,
                simulate,
                original,
                chuteBlockEntity.getBlockPos().relative(Direction.DOWN)
        );
    }

    @WrapOperation(
            method = "handleUpwardOutput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/ItemHandlerHelper;insertItemStacked(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$logUpwardInsertion(
            IItemHandler inventory,
            ItemStack stack,
            boolean simulate,
            Operation<ItemStack> original
    ) {
        ChuteBlockEntity chuteBlockEntity = (ChuteBlockEntity) (Object) this;
        return grieflogger$insertAndLog(
                inventory,
                stack,
                simulate,
                original,
                chuteBlockEntity.getBlockPos().relative(Direction.UP)
        );
    }

    @Unique
    private ItemStack grieflogger$insertAndLog(
            IItemHandler inventory,
            ItemStack stack,
            boolean simulate,
            Operation<ItemStack> original,
            BlockPos containerPosition
    ) {
        ItemStack insertedStack = stack.copy();
        ItemStack remainder = simulate
                ? original.call(inventory, stack, true)
                : ToolboxTransactionContext.withSuppressed(() -> original.call(inventory, stack, false));

        if (!simulate) {
            int remainderAmount = remainder == null ? 0 : remainder.getCount();
            int insertedAmount = insertedStack.getCount() - remainderAmount;
            if (insertedAmount > 0) {
                ChuteTransactionLogger.log(
                        (ChuteBlockEntity) (Object) this,
                        containerPosition,
                        insertedStack.copyWithCount(insertedAmount),
                        ItemAction.ADD_ITEM
                );
            }
        }
        return remainder;
    }
}

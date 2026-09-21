package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.neoforge.create.FunnelTransactionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Associates a manual funnel insertion with the player holding the item. */
@Mixin(value = FunnelBlock.class, remap = false)
public abstract class FunnelBlockMixin {

    @WrapOperation(
            method = "lambda$useItemOn$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/logistics/funnel/FunnelBlock;tryInsert(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private static ItemStack grieflogger$trackManualInsertion(
            Level level,
            BlockPos funnelPosition,
            ItemStack stack,
            boolean simulate,
            Operation<ItemStack> original,
            @Local(argsOnly = true) Player player
    ) {
        return FunnelTransactionContext.withPlayer(
                player,
                () -> original.call(level, funnelPosition, stack, simulate)
        );
    }
}

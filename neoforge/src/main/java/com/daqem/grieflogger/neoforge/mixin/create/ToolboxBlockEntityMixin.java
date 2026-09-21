package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.content.equipment.toolbox.ToolboxInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Supplies the player context for item changes initiated by a connected hotbar
 * slot and suppresses changes made while a placed toolbox restores its stored
 * inventory component.
 */
@Mixin(value = ToolboxBlockEntity.class, remap = false)
public abstract class ToolboxBlockEntityMixin {

    @WrapOperation(
            method = "tickPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/equipment/toolbox/ToolboxInventory;takeFromCompartment(IIZ)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$trackAutomaticReplenish(
            ToolboxInventory inventory,
            int amount,
            int compartment,
            boolean simulate,
            Operation<ItemStack> original,
            @Local Player player
    ) {
        return ToolboxTransactionContext.withPlayer(
                player,
                () -> original.call(inventory, amount, compartment, simulate)
        );
    }

    @WrapOperation(
            method = "tickPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/equipment/toolbox/ToolboxInventory;distributeToCompartment(Lnet/minecraft/world/item/ItemStack;IZ)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$trackAutomaticReturn(
            ToolboxInventory inventory,
            ItemStack stack,
            int compartment,
            boolean simulate,
            Operation<ItemStack> original,
            @Local Player player
    ) {
        return ToolboxTransactionContext.withPlayer(
                player,
                () -> original.call(inventory, stack, compartment, simulate)
        );
    }

    @WrapOperation(
            method = "unequip",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/equipment/toolbox/ToolboxInventory;distributeToCompartment(Lnet/minecraft/world/item/ItemStack;IZ)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$trackUnequipReturn(
            ToolboxInventory inventory,
            ItemStack stack,
            int compartment,
            boolean simulate,
            Operation<ItemStack> original,
            @Local(argsOnly = true) Player player
    ) {
        return ToolboxTransactionContext.withPlayer(
                player,
                () -> original.call(inventory, stack, compartment, simulate)
        );
    }

    @Inject(method = "readInventory", at = @At("HEAD"))
    private void grieflogger$beginInventoryRestore(ToolboxInventory inventory, CallbackInfo callbackInfo) {
        ToolboxTransactionContext.beginSuppressed();
    }

    @Inject(method = "readInventory", at = @At("RETURN"))
    private void grieflogger$endInventoryRestore(ToolboxInventory inventory, CallbackInfo callbackInfo) {
        ToolboxTransactionContext.endSuppressed();
    }
}

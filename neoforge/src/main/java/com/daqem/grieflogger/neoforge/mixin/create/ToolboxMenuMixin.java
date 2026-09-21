package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.simibubi.create.content.equipment.toolbox.ToolboxMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Associates GUI inventory mutations with the player who clicked the slot. */
@Mixin(value = ToolboxMenu.class, remap = false)
public abstract class ToolboxMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"))
    private void grieflogger$beginPlayerClick(
            int slot,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo callbackInfo
    ) {
        ToolboxTransactionContext.beginPlayer(player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void grieflogger$endPlayerClick(
            int slot,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo callbackInfo
    ) {
        ToolboxTransactionContext.endPlayer(player);
    }
}

package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.neoforge.create.ToolboxTransactionContext;
import com.simibubi.create.content.equipment.toolbox.ToolboxDisposeAllPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Associates the "deposit all" packet with the player who sent it. */
@Mixin(value = ToolboxDisposeAllPacket.class, remap = false)
public abstract class ToolboxDisposeAllPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void grieflogger$beginDepositAll(ServerPlayer player, CallbackInfo callbackInfo) {
        ToolboxTransactionContext.beginPlayer(player);
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private void grieflogger$endDepositAll(ServerPlayer player, CallbackInfo callbackInfo) {
        ToolboxTransactionContext.endPlayer(player);
    }
}

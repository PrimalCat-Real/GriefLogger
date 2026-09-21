package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.neoforge.create.DeployerTransactionLogger;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs a player's successful right-click exchange with a deployer. */
@Mixin(value = DeployerBlock.class, remap = false)
public abstract class DeployerBlockMixin {

    @Inject(method = "lambda$useItemOn$1", at = @At("RETURN"))
    private static void grieflogger$logManualExchange(
            ItemStack previouslyHeldByPlayer,
            Player player,
            InteractionHand hand,
            DeployerBlockEntity deployerBlockEntity,
            CallbackInfo callbackInfo
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        DeployerFakePlayer deployerPlayer = deployerBlockEntity.getPlayer();
        if (deployerPlayer == null) {
            return;
        }

        ItemStack removedFromDeployer = player.getItemInHand(hand).copy();
        ItemStack addedToDeployer = deployerPlayer.getMainHandItem().copy();

        if (ItemStack.isSameItemSameComponents(removedFromDeployer, addedToDeployer)) {
            int difference = addedToDeployer.getCount() - removedFromDeployer.getCount();
            if (difference > 0) {
                DeployerTransactionLogger.logPlayer(
                        deployerBlockEntity,
                        serverPlayer,
                        addedToDeployer.copyWithCount(difference),
                        ItemAction.ADD_ITEM
                );
            } else if (difference < 0) {
                DeployerTransactionLogger.logPlayer(
                        deployerBlockEntity,
                        serverPlayer,
                        removedFromDeployer.copyWithCount(-difference),
                        ItemAction.REMOVE_ITEM
                );
            }
            return;
        }

        DeployerTransactionLogger.logPlayer(
                deployerBlockEntity,
                serverPlayer,
                removedFromDeployer,
                ItemAction.REMOVE_ITEM
        );
        DeployerTransactionLogger.logPlayer(
                deployerBlockEntity,
                serverPlayer,
                addedToDeployer,
                ItemAction.ADD_ITEM
        );
    }
}

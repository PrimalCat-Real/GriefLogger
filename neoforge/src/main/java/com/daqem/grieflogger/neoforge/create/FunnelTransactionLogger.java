package com.daqem.grieflogger.neoforge.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Records successful Create funnel transfers against the inventory attached to
 * the back of the funnel. The funnel position is retained in the phantom user
 * so multiple funnels connected to one inventory remain distinguishable.
 */
public final class FunnelTransactionLogger {

    private FunnelTransactionLogger() {
    }

    public static void log(FunnelBlockEntity funnelBlockEntity, ItemStack stack, ItemAction action) {
        if (GriefLogger.isRollbackActive() || stack == null || stack.isEmpty()) {
            return;
        }

        Level level = funnelBlockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState funnelState = funnelBlockEntity.getBlockState();
        String funnelType = getFunnelType(funnelState);
        Direction funnelFacing = AbstractFunnelBlock.getFunnelFacing(funnelState);
        if (funnelType == null || funnelFacing == null) {
            return;
        }

        BlockPos funnelPosition = funnelBlockEntity.getBlockPos();
        BlockPos containerPosition = funnelPosition.relative(funnelFacing.getOpposite());

        try {
            AutomatedTransferTracker.getInstance().markAutomatedActivity(containerPosition);

            ServerPlayer player = FunnelTransactionContext.getPlayer();
            if (player != null) {
                Services.USER.insertOrUpdateName(player.getUUID(), player.getGameProfile().getName());
                Services.CONTAINER.insert(
                        player.getUUID(),
                        level,
                        containerPosition,
                        new SimpleItemStack(stack),
                        action
                );
            } else {
                String phantomUser = "#" + funnelType + "@" + funnelPosition.getX() + ","
                        + funnelPosition.getY() + "," + funnelPosition.getZ();
                Services.USER.insertPhantomUser(phantomUser);
                Services.CONTAINER.insertWithPhantom(
                        phantomUser,
                        level,
                        containerPosition,
                        new SimpleItemStack(stack),
                        action
                );
            }
        } catch (Exception exception) {
            GriefLogger.LOGGER.error("Failed to log funnel transfer", exception);
        }
    }

    private static String getFunnelType(BlockState funnelState) {
        if (AllBlocks.BRASS_FUNNEL.has(funnelState) || AllBlocks.BRASS_BELT_FUNNEL.has(funnelState)) {
            return "brass_funnel";
        }
        if (AllBlocks.ANDESITE_FUNNEL.has(funnelState) || AllBlocks.ANDESITE_BELT_FUNNEL.has(funnelState)) {
            return "andesite_funnel";
        }
        return null;
    }
}

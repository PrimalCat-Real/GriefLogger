package com.daqem.grieflogger.neoforge.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Records successful changes made through a deployer's item capability. */
public final class DeployerTransactionLogger {

    private DeployerTransactionLogger() {
    }

    public static void log(DeployerBlockEntity deployerBlockEntity, ItemStack stack, ItemAction action) {
        if (GriefLogger.isRollbackActive()
                || ToolboxTransactionContext.isSuppressed()
                || stack == null
                || stack.isEmpty()) {
            return;
        }

        Level level = deployerBlockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos deployerPosition = deployerBlockEntity.getBlockPos();
        try {
            AutomatedTransferTracker.getInstance().markAutomatedActivity(deployerPosition);

            String phantomUser = "#deployer@" + deployerPosition.getX() + ","
                    + deployerPosition.getY() + "," + deployerPosition.getZ();
            Services.USER.insertPhantomUser(phantomUser);
            Services.CONTAINER.insertWithPhantom(
                    phantomUser,
                    level,
                    deployerPosition,
                    new SimpleItemStack(stack),
                    action
            );
        } catch (Exception exception) {
            GriefLogger.LOGGER.error("Failed to log deployer inventory change", exception);
        }
    }

    public static void logPlayer(
            DeployerBlockEntity deployerBlockEntity,
            ServerPlayer player,
            ItemStack stack,
            ItemAction action
    ) {
        if (GriefLogger.isRollbackActive() || stack == null || stack.isEmpty()) {
            return;
        }

        Level level = deployerBlockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos deployerPosition = deployerBlockEntity.getBlockPos();
        try {
            AutomatedTransferTracker.getInstance().markAutomatedActivity(deployerPosition);
            Services.USER.insertOrUpdateName(player.getUUID(), player.getGameProfile().getName());
            Services.CONTAINER.insert(
                    player.getUUID(),
                    level,
                    deployerPosition,
                    new SimpleItemStack(stack),
                    action
            );
        } catch (Exception exception) {
            GriefLogger.LOGGER.error("Failed to log player deployer inventory change", exception);
        }
    }
}

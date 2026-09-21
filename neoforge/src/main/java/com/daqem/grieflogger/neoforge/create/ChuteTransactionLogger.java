package com.daqem.grieflogger.neoforge.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Records successful chute transfers against the external inventory involved. */
public final class ChuteTransactionLogger {

    private ChuteTransactionLogger() {
    }

    public static void log(
            ChuteBlockEntity chuteBlockEntity,
            BlockPos containerPosition,
            ItemStack stack,
            ItemAction action
    ) {
        if (GriefLogger.isRollbackActive() || stack == null || stack.isEmpty()) {
            return;
        }

        Level level = chuteBlockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        // Chute-to-chute movement is internal transport, not a container transaction.
        if (level.getBlockEntity(containerPosition) instanceof ChuteBlockEntity) {
            return;
        }

        BlockPos chutePosition = chuteBlockEntity.getBlockPos();
        String chuteType = chuteBlockEntity instanceof SmartChuteBlockEntity ? "smart_chute" : "chute";

        try {
            AutomatedTransferTracker.getInstance().markAutomatedActivity(containerPosition);

            String phantomUser = "#" + chuteType + "@" + chutePosition.getX() + ","
                    + chutePosition.getY() + "," + chutePosition.getZ();
            Services.USER.insertPhantomUser(phantomUser);
            Services.CONTAINER.insertWithPhantom(
                    phantomUser,
                    level,
                    containerPosition,
                    new SimpleItemStack(stack),
                    action
            );
        } catch (Exception exception) {
            GriefLogger.LOGGER.error("Failed to log chute transfer", exception);
        }
    }
}

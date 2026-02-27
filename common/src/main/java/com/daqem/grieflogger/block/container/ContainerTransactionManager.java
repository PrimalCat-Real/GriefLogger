package com.daqem.grieflogger.block.container;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContainerTransactionManager implements IContainerTransactionManager {

    private final BaseContainerBlockEntity blockEntity;
    private List<SimpleItemStack> lastKnownState = new ArrayList<>();
    private int tickCounter = 0;
    private static final int TICK_INTERVAL = 1; // Check every tick for real-time tracking

    public ContainerTransactionManager(BaseContainerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        captureState(lastKnownState);
    }

    @Override
    public void tick(ServerPlayer serverPlayer) {
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        List<SimpleItemStack> currentState = new ArrayList<>();
        captureState(currentState);

        // Compare and log differences
        List<SimpleItemStack> removedItems = getDifference(lastKnownState, currentState);
        List<SimpleItemStack> addedItems = getDifference(currentState, lastKnownState);

        // Check if there was recent automated activity on this container
        // If so, skip logging entirely to avoid duplicate attribution
        BlockPos containerPos = blockEntity.getBlockPos();
        boolean hasAutomatedActivity = AutomatedTransferTracker.getInstance().hasRecentAutomatedActivity(containerPos);

        if (!hasAutomatedActivity && (!removedItems.isEmpty() || !addedItems.isEmpty())) {
            // Log changes (only if no automated activity)
            for (SimpleItemStack item : removedItems) {
                GriefLogger.LOGGER.info("[Container] Action=REMOVE User={} Item={}x{} Pos={}",
                        serverPlayer.getName().getString(),
                        item.getItem().arch$registryName(), item.getCount(),
                        blockEntity.getBlockPos().toShortString());
            }
            for (SimpleItemStack item : addedItems) {
                GriefLogger.LOGGER.info("[Container] Action=ADD User={} Item={}x{} Pos={}",
                        serverPlayer.getName().getString(),
                        item.getItem().arch$registryName(), item.getCount(),
                        blockEntity.getBlockPos().toShortString());
            }

            // Insert to database
            Services.CONTAINER.insertMap(
                    serverPlayer.getUUID(),
                    blockEntity.getLevel() != null ? blockEntity.getLevel() : serverPlayer.level(),
                    blockEntity.getBlockPos(),
                    Map.of(
                            ItemAction.REMOVE_ITEM, removedItems,
                            ItemAction.ADD_ITEM, addedItems
                    )
            );
        }

        // ALWAYS update last known state, even if we skipped logging
        // This prevents re-detecting the same change on the next tick
        if (!removedItems.isEmpty() || !addedItems.isEmpty()) {
            lastKnownState = currentState;
        }
    }

    @Override
    public void finalize(ServerPlayer serverPlayer) {
        // Final check when closing
        List<SimpleItemStack> currentState = new ArrayList<>();
        captureState(currentState);

        List<SimpleItemStack> removedItems = getDifference(lastKnownState, currentState);
        List<SimpleItemStack> addedItems = getDifference(currentState, lastKnownState);

        // Check if there was recent automated activity on this container
        BlockPos containerPos = blockEntity.getBlockPos();
        boolean hasAutomatedActivity = AutomatedTransferTracker.getInstance().hasRecentAutomatedActivity(containerPos);

        if (!hasAutomatedActivity && (!removedItems.isEmpty() || !addedItems.isEmpty())) {
            for (SimpleItemStack item : removedItems) {
                GriefLogger.LOGGER.info("[Container] Action=REMOVE User={} Item={}x{} Pos={}",
                        serverPlayer.getName().getString(),
                        item.getItem().arch$registryName(), item.getCount(),
                        blockEntity.getBlockPos().toShortString());
            }
            for (SimpleItemStack item : addedItems) {
                GriefLogger.LOGGER.info("[Container] Action=ADD User={} Item={}x{} Pos={}",
                        serverPlayer.getName().getString(),
                        item.getItem().arch$registryName(), item.getCount(),
                        blockEntity.getBlockPos().toShortString());
            }

            Services.CONTAINER.insertMap(
                    serverPlayer.getUUID(),
                    blockEntity.getLevel() != null ? blockEntity.getLevel() : serverPlayer.level(),
                    blockEntity.getBlockPos(),
                    Map.of(
                            ItemAction.REMOVE_ITEM, removedItems,
                            ItemAction.ADD_ITEM, addedItems
                    )
            );
        }
    }

    private void captureState(List<SimpleItemStack> stateList) {
        for (int i = 0; i < blockEntity.getContainerSize(); i++) {
            addItem(blockEntity.getItem(i), stateList);
        }
    }

    private List<SimpleItemStack> getDifference(List<SimpleItemStack> from, List<SimpleItemStack> to) {
        List<SimpleItemStack> difference = new ArrayList<>();
        for (SimpleItemStack fromItem : from) {
            to.stream().filter(fromItem::equals).findFirst().ifPresentOrElse(toItem -> {
                if (toItem.getCount() < fromItem.getCount()) {
                    difference.add(new SimpleItemStack(fromItem.getItem(), fromItem.getCount() - toItem.getCount(), fromItem.getTag()));
                }
            }, () -> difference.add(new SimpleItemStack(fromItem.getItem(), fromItem.getCount(), fromItem.getTag())));
        }
        return difference;
    }

    private void addItem(ItemStack itemStack, List<SimpleItemStack> itemStackList) {
        if (itemStack.getItem().equals(Items.AIR)) {
            return;
        }

        if (itemStack.getCount() == 0) {
            return;
        }

        for (SimpleItemStack simpleItemStack : itemStackList) {
            if (simpleItemStack.getItem() == itemStack.getItem()) {
                if (simpleItemStack.hasTag() && !itemStack.getComponentsPatch().isEmpty() && simpleItemStack.getTag().equals(itemStack.getComponentsPatch())) {
                    simpleItemStack.addCount(itemStack.getCount());
                    return;
                } else if (simpleItemStack.hasNoTag() && itemStack.getComponentsPatch().isEmpty()) {
                    simpleItemStack.addCount(itemStack.getCount());
                    return;
                }
            }
        }

        itemStackList.add(new SimpleItemStack(itemStack));
    }
}

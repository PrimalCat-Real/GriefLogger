package com.daqem.grieflogger.model.rollback;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.model.history.ContainerHistory;
import com.daqem.grieflogger.model.history.IHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Processes container rollback/restore operations.
 * Handles adding/removing items from containers and dropping items if full.
 */
public class ContainerRollbackProcessor {

    private final ServerLevel level;
    private final String levelName;
    private final boolean isRestore;
    private int processedCount = 0;
    private int droppedCount = 0;
    private final List<ContainerRollbackAction> actions = new ArrayList<>();

    public ContainerRollbackProcessor(ServerLevel level, boolean isRestore) {
        this.level = level;
        this.levelName = level.dimension().location().toString();
        this.isRestore = isRestore;
    }

    /**
     * Process a list of container history entries.
     */
    public void process(List<IHistory> history) {
        for (IHistory entry : history) {
            if (entry instanceof ContainerHistory containerHistory) {
                processEntry(containerHistory);
            }
        }
    }

    /**
     * Process a single container history entry.
     */
    private void processEntry(ContainerHistory history) {
        BlockPos pos = new BlockPos(
                history.getPosition().x(),
                history.getPosition().y(),
                history.getPosition().z()
        );

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            if (shouldAddItem(history)) {
                dropItem(pos, history);
            }
            return;
        }

        ItemStack itemStack = history.getItemStack().toItemStack();
        int originalAction = history.getAction().getId();

        if (shouldAddItem(history)) {
            int remaining = addToContainer(pos, container, itemStack);
            if (remaining > 0) {
                dropItem(pos, itemStack.copyWithCount(remaining), originalAction);
            }
            if (remaining < itemStack.getCount()) {
                processedCount += itemStack.getCount() - remaining;
                recordAction(ContainerRollbackAction.TYPE_ITEM_ADDED, pos, history, itemStack.getCount() - remaining);
            }
        } else if (shouldRemoveItem(history)) {
            int removed = removeFromContainer(pos, container, itemStack);
            if (removed > 0) {
                processedCount += removed;
                recordAction(ContainerRollbackAction.TYPE_ITEM_REMOVED, pos, history, removed);
            }
        }
    }

    /**
     * Determine if we should add the item to the container.
     * Rollback: REMOVE_ITEM (withdraw) -> add back
     * Restore: ADD_ITEM (deposit) -> add back
     */
    private boolean shouldAddItem(ContainerHistory history) {
        int actionId = history.getAction().getId();
        if (isRestore) {
            return actionId == ItemAction.ADD_ITEM.getId();
        } else {
            return actionId == ItemAction.REMOVE_ITEM.getId();
        }
    }

    /**
     * Determine if we should remove the item from the container.
     * Rollback: ADD_ITEM (deposit) -> remove
     * Restore: REMOVE_ITEM (withdraw) -> remove
     */
    private boolean shouldRemoveItem(ContainerHistory history) {
        int actionId = history.getAction().getId();
        if (isRestore) {
            return actionId == ItemAction.REMOVE_ITEM.getId();
        } else {
            return actionId == ItemAction.ADD_ITEM.getId();
        }
    }

    /**
     * Add item to container, returns remaining count that couldn't fit.
     */
    private int addToContainer(BlockPos pos, Container container, ItemStack itemStack) {
        ItemStack toAdd = itemStack.copy();

        int remaining = com.daqem.grieflogger.GriefLoggerExpectPlatform.insertItem(level, pos, toAdd);
        if (remaining < toAdd.getCount()) {
            return remaining;
        }

        for (int i = 0; i < container.getContainerSize() && !toAdd.isEmpty(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (ItemStack.isSameItemSameComponents(slotStack, toAdd) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                int toTransfer = Math.min(space, toAdd.getCount());
                slotStack.grow(toTransfer);
                toAdd.shrink(toTransfer);
                container.setChanged();
            }
        }

        for (int i = 0; i < container.getContainerSize() && !toAdd.isEmpty(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) {
                int toTransfer = Math.min(toAdd.getMaxStackSize(), toAdd.getCount());
                container.setItem(i, toAdd.copyWithCount(toTransfer));
                toAdd.shrink(toTransfer);
                container.setChanged();
            }
        }

        return toAdd.getCount();
    }

    /**
     * Remove item from container, returns actual count removed.
     */
    private int removeFromContainer(BlockPos pos, Container container, ItemStack itemStack) {
        int extract = com.daqem.grieflogger.GriefLoggerExpectPlatform.extractItem(level, pos, itemStack);
        if (extract > 0) {
            return extract;
        }

        int toRemove = itemStack.getCount();
        int removed = 0;

        for (int i = 0; i < container.getContainerSize() && removed < toRemove; i++) {
            ItemStack slotStack = container.getItem(i);
            if (ItemStack.isSameItemSameComponents(slotStack, itemStack)) {
                int available = slotStack.getCount();
                int take = Math.min(available, toRemove - removed);
                slotStack.shrink(take);
                if (slotStack.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                }
                removed += take;
                container.setChanged();
            }
        }

        return removed;
    }

    /**
     * Drop item on the ground above the container position.
     */
    private void dropItem(BlockPos pos, ContainerHistory history) {
        ItemStack itemStack = history.getItemStack().toItemStack();
        dropItem(pos, itemStack, history.getAction().getId());
    }

    /**
     * Drop item on the ground above the container position.
     */
    private void dropItem(BlockPos pos, ItemStack itemStack, int originalAction) {
        ItemEntity itemEntity = new ItemEntity(
                level,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                itemStack.copy()
        );
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
        droppedCount += itemStack.getCount();

        actions.add(ContainerRollbackAction.create(
                -1,
                ContainerRollbackAction.TYPE_ITEM_DROPPED,
                levelName,
                pos.getX(), pos.getY(), pos.getZ(),
                itemStack.getItem().arch$registryName().toString(),
                itemStack.getCount(),
                null,
                originalAction
        ));
    }

    private void recordAction(int actionType, BlockPos pos, ContainerHistory history, int amount) {
        SimpleItemStack itemStack = history.getItemStack();
        actions.add(ContainerRollbackAction.create(
                -1,
                actionType,
                levelName,
                pos.getX(), pos.getY(), pos.getZ(),
                itemStack.getItem().arch$registryName().toString(),
                amount,
                itemStack.getTagBytes(level),
                history.getAction().getId()
        ));
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getDroppedCount() {
        return droppedCount;
    }

    public List<ContainerRollbackAction> getActions() {
        return actions;
    }
}

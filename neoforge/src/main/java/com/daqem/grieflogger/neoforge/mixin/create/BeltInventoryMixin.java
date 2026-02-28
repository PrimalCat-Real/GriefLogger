package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;

/**
 * Mixin for Create's BeltInventory to log items moving on and off conveyor belts.
 * This class serves as an example of how to track item transfers in machines or custom inventories.
 * We track 4 main events: insertion, ejection, processing/destruction, and transfer to other blocks.
 * To implement logging for new items/machines:
 * 1. Find the methods where items enter and leave the block/inventory.
 * 2. Use @Inject or @Redirect to intercept these actions.
 * 3. Extract the exact BlockPos and ItemStack.
 * 4. Call your logging method (e.g., grieflogger$logTransfer).
 */
@Mixin(value = BeltInventory.class, remap = false)
public abstract class BeltInventoryMixin {

    @Shadow @Final BeltBlockEntity belt;
    @Shadow @Final List<TransportedItemStack> toRemove;

    /**
     * 1. Insertion (+item)
     * Triggered when an item is placed onto the belt from the world or another block/machine.
     * We inject at HEAD to capture the exact item being inserted before any internal logic runs.
     */
    @Inject(method = "insert", at = @At("HEAD"))
    private void grieflogger$onInsert(TransportedItemStack newStack, CallbackInfo ci) {
        if (GriefLogger.isRollbackActive()) return;
        if (newStack != null && newStack.stack != null && !newStack.stack.isEmpty()) {
            grieflogger$logTransfer(newStack.stack.copy(), ItemAction.ADD_ITEM, newStack.beltPosition);
        }
    }

    /**
     * 2. Ejection (-item)
     * Triggered when an item falls off the end of the belt onto the ground.
     * We inject at HEAD because the item is about to be spawned into the world as an Entity.
     */
    @Inject(method = "eject", at = @At("HEAD"))
    private void grieflogger$onEject(TransportedItemStack stack, CallbackInfo ci) {
        if (GriefLogger.isRollbackActive()) return;
        if (stack != null && stack.stack != null && !stack.stack.isEmpty()) {
            grieflogger$logTransfer(stack.stack.copy(), ItemAction.REMOVE_ITEM, stack.beltPosition);
        }
    }

    /**
     * 3. Processing or Destruction (-item)
     * Items on belts can be processed by other machines like Crushers or Presses.
     * When processed, Create adds original items to the 'toRemove' list and clears them.
     * We inject right before 'toRemove.clear()' to log all items that were consumed by machines.
     */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", ordinal = 1, shift = At.Shift.BEFORE))
    private void grieflogger$beforeRemoveAll(CallbackInfo ci) {
        if (GriefLogger.isRollbackActive()) return;
        if (!toRemove.isEmpty()) {
            for (TransportedItemStack stack : toRemove) {
                if (stack != null && stack.stack != null && !stack.stack.isEmpty()) {
                    grieflogger$logTransfer(stack.stack.copy(), ItemAction.REMOVE_ITEM, stack.beltPosition);
                }
            }
        }
    }

    /**
     * 4. Transfer to another block (-item)
     * Triggered when the belt pushes items into an adjacent inventory (like a chest or hopper).
     * We use @Redirect on 'handleInsertion' to capture the state of the item before and after insertion.
     * If 'simulate' is false, the difference between 'stackBefore' and 'remainder' tells us how many items were successfully transferred.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/belt/behaviour/DirectBeltInputBehaviour;handleInsertion(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack grieflogger$onHandleInsertion(DirectBeltInputBehaviour instance, TransportedItemStack currentItem, Direction side, boolean simulate) {
        ItemStack stackBefore = currentItem.stack.copy();

        ItemStack remainder = instance.handleInsertion(currentItem, side, simulate);

        if (GriefLogger.isRollbackActive()) return remainder;

        if (!simulate) {
            int amountInserted = stackBefore.getCount() - (remainder == null ? 0 : remainder.getCount());
            if (amountInserted > 0) {
                ItemStack loggedStack = stackBefore.copy();
                loggedStack.setCount(amountInserted);
                grieflogger$logTransfer(loggedStack, ItemAction.REMOVE_ITEM, currentItem.beltPosition);
            }
        }

        return remainder;
    }

    /**
     * Universal helper to log transfer events for belt interactions.
     * Determines exact world position for the item on long belts and registers the action in the database.
     *
     * @param stack The item being modified.
     * @param action ADD_ITEM or REMOVE_ITEM depending on the flow.
     * @param beltPosition The offset on the belt to calculate exact block position.
     */
    @Unique
    private void grieflogger$logTransfer(ItemStack stack, ItemAction action, float beltPosition) {
        Object beObject = (Object) belt;
        if (!(beObject instanceof net.minecraft.world.level.block.entity.BlockEntity be)) return;

        Level level = be.getLevel();
        if (level == null || level.isClientSide() || stack == null || stack.isEmpty()) return;

        try {
            BlockPos exactPos = BeltHelper.getPositionForOffset(belt, (int) beltPosition);

            AutomatedTransferTracker tracker = AutomatedTransferTracker.getInstance();
            if (tracker != null) {
                tracker.markAutomatedActivity(exactPos);
            }

            String phantomUser = "#belt@" + exactPos.getX() + "," + exactPos.getY() + "," + exactPos.getZ();
            Services.USER.insertPhantomUser(phantomUser);

            Services.CONTAINER.insertWithPhantom(
                    phantomUser, level, exactPos, new SimpleItemStack(stack), action
            );

            GriefLogger.LOGGER.info("[belt] Action={} Item={}x{} Pos={}",
                    action == ItemAction.ADD_ITEM ? "ADD" : "REMOVE",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount(),
                    exactPos.toShortString()
            );
        } catch (Exception e) {
            GriefLogger.LOGGER.error("Failed to log belt transfer", e);
        }
    }
}

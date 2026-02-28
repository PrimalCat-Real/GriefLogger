package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ArmBlockEntity.class, remap = false)
public abstract class ArmBlockEntityMixin extends BlockEntity {

    @Shadow List<ArmInteractionPoint> inputs;
    @Shadow List<ArmInteractionPoint> outputs;
    @Shadow ItemStack heldItem;
    @Shadow int chasedPointIndex;

    @Unique private BlockPos grieflogger$targetPos;
    @Unique private ItemStack grieflogger$stackBeforeOperation;

    public ArmBlockEntityMixin() {
        super(null, null, null);
    }

    @Inject(method = "collectItem", at = @At("HEAD"))
    private void grieflogger$beforeCollect(CallbackInfo ci) {
        if (GriefLogger.isRollbackActive()) return;
        grieflogger$targetPos = (chasedPointIndex >= 0 && chasedPointIndex < inputs.size())
                ? inputs.get(chasedPointIndex).getPos() : null;
        grieflogger$stackBeforeOperation = heldItem != null ? heldItem.copy() : ItemStack.EMPTY;
    }

    @Inject(method = "collectItem", at = @At("RETURN"))
    private void grieflogger$afterCollect(CallbackInfo ci) {
        Level level = ((BlockEntity)(Object) this).getLevel();
        if (level == null || level.isClientSide() || grieflogger$targetPos == null) return;
        if (heldItem == null || heldItem.isEmpty()) return;

        int countBefore = grieflogger$stackBeforeOperation.isEmpty() ? 0 : grieflogger$stackBeforeOperation.getCount();
        int countAfter = heldItem.getCount();
        int extractedCount = countAfter - countBefore;

        if (extractedCount > 0) {
            ItemStack extractedStack = heldItem.copy();
            extractedStack.setCount(extractedCount);
            BlockPos armPos = ((BlockEntity)(Object) this).getBlockPos();

            grieflogger$logTransfer(level, grieflogger$targetPos, extractedStack, ItemAction.REMOVE_ITEM, armPos);
        }
    }

    @Inject(method = "depositItem", at = @At("HEAD"))
    private void grieflogger$beforeDeposit(CallbackInfo ci) {
        if (GriefLogger.isRollbackActive()) return;
        grieflogger$targetPos = (chasedPointIndex >= 0 && chasedPointIndex < outputs.size())
                ? outputs.get(chasedPointIndex).getPos() : null;
        grieflogger$stackBeforeOperation = heldItem != null ? heldItem.copy() : ItemStack.EMPTY;
    }

    @Inject(method = "depositItem", at = @At("RETURN"))
    private void grieflogger$afterDeposit(CallbackInfo ci) {
        Level level = ((BlockEntity)(Object) this).getLevel();
        if (level == null || level.isClientSide() || grieflogger$targetPos == null) return;
        if (grieflogger$stackBeforeOperation.isEmpty()) return;

        int countBefore = grieflogger$stackBeforeOperation.getCount();
        int countAfter = (heldItem == null || heldItem.isEmpty()) ? 0 : heldItem.getCount();
        int depositedCount = countBefore - countAfter;

        if (depositedCount > 0) {
            ItemStack depositedStack = grieflogger$stackBeforeOperation.copy();
            depositedStack.setCount(depositedCount);
            BlockPos armPos = ((BlockEntity)(Object) this).getBlockPos();

            grieflogger$logTransfer(level, grieflogger$targetPos, depositedStack, ItemAction.ADD_ITEM, armPos);
        }
    }

    @Unique
    private void grieflogger$logTransfer(Level level, BlockPos containerPos, ItemStack stack,
                                         ItemAction containerAction, BlockPos armPos) {
        try {
            AutomatedTransferTracker tracker = AutomatedTransferTracker.getInstance();
            if (tracker != null) {
                tracker.markAutomatedActivity(containerPos);
            }

            String phantomUser = "#mechanical_arm@" + armPos.getX() + "," + armPos.getY() + "," + armPos.getZ();
            Services.USER.insertPhantomUser(phantomUser);

            Services.CONTAINER.insertWithPhantom(
                    phantomUser, level, containerPos, new SimpleItemStack(stack), containerAction
            );

            GriefLogger.LOGGER.info("[mechanical_arm] Container={} Item={}x{} ContainerPos={} ArmPos={}",
                    containerAction == ItemAction.ADD_ITEM ? "ADD" : "REMOVE",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount(),
                    containerPos.toShortString(),
                    armPos.toShortString()
            );
        } catch (Exception e) {
            GriefLogger.LOGGER.error("Failed to log mechanical arm transfer", e);
        }
    }
}
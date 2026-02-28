package com.daqem.grieflogger.command;

import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;
import com.daqem.grieflogger.model.history.BlockHistory;
import com.daqem.grieflogger.model.history.ContainerHistory;
import com.daqem.grieflogger.model.history.IHistory;
import com.daqem.grieflogger.model.rollback.ContainerRollbackProcessor;
import com.daqem.grieflogger.model.rollback.PreviewManager;
import com.daqem.grieflogger.model.rollback.PreviewSession;
import com.daqem.grieflogger.model.rollback.RollbackAction;
import com.daqem.grieflogger.model.rollback.RollbackJob;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Command to apply a previewed rollback/restore operation.
 * Usage: /gl apply
 */
public class ApplyCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("apply")
                .requires(source -> source.hasPermission(2))
                .executes(context -> apply(context.getSource()));
    }

    private int apply(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Theme.toMinecraft(Theme.error("This command can only be used by players.")));
            return 0;
        }

        Optional<PreviewSession> previewOpt = PreviewManager.getInstance().getPreview(player.getUUID());

        if (previewOpt.isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("No active preview to apply. Use /gl rollback ... #preview first.")));
            return 0;
        }

        PreviewSession preview = previewOpt.get();

        String currentLevel = source.getLevel().dimension().location().toString();
        if (!currentLevel.equals(preview.levelName())) {
            source.sendFailure(Theme.toMinecraft(
                    Theme.error("Preview was created in a different world: " + preview.levelName())
            ));
            return 0;
        }

        source.sendSuccess(() -> Theme.toMinecraft(
                Theme.muted("Applying " + preview.getOperationName() + "...")
        ), false);

        long startTime = System.currentTimeMillis();

        source.getServer().execute(() -> {
            executePreview(source, player, preview, startTime);
        });

        return 1;
    }

    private void executePreview(
            CommandSourceStack source,
            ServerPlayer player,
            PreviewSession preview,
            long startTime
    ) {
        ServerLevel level = source.getLevel();
        String levelName = preview.levelName();
        AtomicInteger blockCount = new AtomicInteger();
        List<RollbackAction> rollbackActions = new ArrayList<>();

        for (IHistory entry : preview.history()) {
            if (entry instanceof BlockHistory blockHistory) {
                RollbackAction action = processBlock(level, levelName, blockHistory, preview.isRestore(), blockCount);
                if (action != null) {
                    rollbackActions.add(action);
                }
            }
        }

        List<IHistory> containerHistory = preview.history().stream()
                .filter(h -> h instanceof ContainerHistory)
                .toList();

        int containerItemsProcessed = 0;
        int containerItemsDropped = 0;

        if (!containerHistory.isEmpty()) {
            ContainerRollbackProcessor containerProcessor = new ContainerRollbackProcessor(level, preview.isRestore());
            containerProcessor.process(containerHistory);
            containerItemsProcessed = containerProcessor.getProcessedCount();
            containerItemsDropped = containerProcessor.getDroppedCount();
        }

        long duration = System.currentTimeMillis() - startTime;
        int blocksProcessed = blockCount.get();

        if (blocksProcessed > 0 || containerItemsProcessed > 0) {
            recordJob(
                    player,
                    preview,
                    levelName,
                    blocksProcessed,
                    duration,
                    rollbackActions
            );
        }

        PreviewManager.getInstance().clearPreview(player.getUUID());

        String operationName = preview.isRestore() ? "Restore" : "Rollback";
        final int finalContainerItems = containerItemsProcessed;
        final int finalDropped = containerItemsDropped;

        Component resultMessage = Theme.success(operationName + " applied! ");
        if (blocksProcessed > 0) {
            resultMessage = resultMessage.append(Theme.muted(blocksProcessed + " blocks"));
        }
        if (finalContainerItems > 0) {
            if (blocksProcessed > 0) {
                resultMessage = resultMessage.append(Theme.muted(", "));
            }
            resultMessage = resultMessage.append(Theme.muted(finalContainerItems + " container items"));
        }
        if (finalDropped > 0) {
            resultMessage = resultMessage.append(Theme.muted(" (" + finalDropped + " dropped)"));
        }
        resultMessage = resultMessage.append(Theme.muted(" in " + duration + "ms."));

        final Component finalMessage = resultMessage;
        source.sendSuccess(() -> Theme.toMinecraft(finalMessage), true);
    }

    private RollbackAction processBlock(
            ServerLevel level,
            String levelName,
            BlockHistory history,
            boolean isRestore,
            AtomicInteger count
    ) {
        BlockPos pos = new BlockPos(
                history.getPosition().x(),
                history.getPosition().y(),
                history.getPosition().z()
        );
        int action = history.getAction().getId();

        BlockState currentState = level.getBlockState(pos);
        String currentMaterial = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();
        int currentStateId = 0;

        boolean shouldRestoreBlock = isRestore
                ? action == BlockAction.PLACE_BLOCK.getId()
                : action == BlockAction.BREAK_BLOCK.getId();

        boolean shouldRemoveBlock = isRestore
                ? action == BlockAction.BREAK_BLOCK.getId()
                : action == BlockAction.PLACE_BLOCK.getId();

        if (shouldRestoreBlock) {
            BlockState previousState = Services.BLOCK.getBlockStateFromId(history.getStateId());
            if (previousState != null) {
                level.setBlock(pos, previousState, 2 | 16);
                count.incrementAndGet();

                String newMaterial = BuiltInRegistries.BLOCK.getKey(previousState.getBlock()).toString();
                return RollbackAction.create(
                        -1,
                        RollbackAction.TYPE_BLOCK_RESTORE,
                        levelName,
                        pos.getX(), pos.getY(), pos.getZ(),
                        currentStateId,
                        history.getStateId(),
                        currentMaterial,
                        newMaterial
                );
            }
        } else if (shouldRemoveBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            count.incrementAndGet();

            BlockState originalState = Services.BLOCK.getBlockStateFromId(history.getStateId());
            String originalMaterial = originalState != null
                    ? BuiltInRegistries.BLOCK.getKey(originalState.getBlock()).toString()
                    : "minecraft:unknown";

            return RollbackAction.create(
                    -1,
                    RollbackAction.TYPE_BLOCK_REMOVE,
                    levelName,
                    pos.getX(), pos.getY(), pos.getZ(),
                    history.getStateId(),
                    0,
                    originalMaterial,
                    "minecraft:air"
            );
        }

        return null;
    }

    private void recordJob(
            ServerPlayer player,
            PreviewSession preview,
            String levelName,
            int blockCount,
            long duration,
            List<RollbackAction> actions
    ) {
        int centerX = (int) player.getX();
        int centerY = (int) player.getY();
        int centerZ = (int) player.getZ();
        int radius = preview.filterList().getRadiusFilter()
                .map(r -> Math.max(Math.abs(r.getMaxX() - r.getMinX()), Math.abs(r.getMaxZ() - r.getMinZ())) / 2)
                .orElse(0);

        int actionType = preview.isRestore() ? RollbackJob.TYPE_RESTORE : RollbackJob.TYPE_ROLLBACK;

        RollbackJob job = RollbackJob.create(
                player.getUUID(),
                player.getName().getString(),
                actionType,
                levelName,
                centerX, centerY, centerZ,
                radius,
                preview.filterList().getTime(),
                preview.filterList().getUserString(),
                blockCount,
                duration
        );

        ThreadManager.submit(() -> {
            return Services.ROLLBACK.recordOperation(job, actions);
        }, jobId -> {
        });
    }
}

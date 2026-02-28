package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.argument.FilterArgument;
import com.daqem.grieflogger.command.filter.FilterList;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Restore command - opposite of rollback.
 * Re-applies changes that were rolled back.
 *
 * Rollback: BREAK -> restore block, PLACE -> remove block
 * Restore:  BREAK -> remove block,  PLACE -> restore block
 *
 * Usage: /gl restore u:Steve t:1h
 */
public class RestoreCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("restore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("filters", StringArgumentType.greedyString())
                        .suggests(FilterArgument::suggestFilters)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "filters");
                            FilterList filterList = FilterArgument.parseFilters(raw, context.getSource());
                            return restore(context.getSource(), filterList);
                        }));
    }

    private int restore(CommandSourceStack source, FilterList filterList) {
        if (filterList.getTime() == 0 && filterList.getRadiusFilter().isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("You must specify a time (t:) or radius (r:) filter!")));
            return 0;
        }

        boolean isPreview = filterList.isPreview();
        boolean includeContainers = filterList.isContainer();
        String startMessage = isPreview ? "Generating restore preview..." : "Starting restore...";
        source.sendSuccess(() -> Theme.toMinecraft(Theme.muted(startMessage)), false);

        long startTime = System.currentTimeMillis();

        ThreadManager.submit(() -> {
            GriefLogger.getDatabase().flushQueues();

            List<IHistory> allHistory = new ArrayList<>(Services.BLOCK.getFilteredBlockHistory(source.getLevel(), filterList));

            if (includeContainers) {
                allHistory.addAll(Services.CONTAINER.getFilteredContainerHistory(source.getLevel(), filterList));
            }

            return allHistory;
        }, history -> {
            if (history == null || history.isEmpty()) {
                source.sendFailure(Theme.toMinecraft(Theme.error("No actions found to restore.")));
                return;
            }

            if (isPreview) {
                handlePreviewMode(source, filterList, history, startTime);
                return;
            }

            executeRestore(source, filterList, history, startTime);
        });

        return 1;
    }

    /**
     * Handle preview mode - store preview session without applying changes.
     */
    private void handlePreviewMode(
            CommandSourceStack source,
            FilterList filterList,
            List<IHistory> history,
            long startTime
    ) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Theme.toMinecraft(Theme.error("Preview mode can only be used by players.")));
            return;
        }

        ServerLevel level = source.getLevel();
        String levelName = level.dimension().location().toString();

        PreviewSession session = PreviewSession.createRestore(
                player.getUUID(),
                player.getName().getString(),
                levelName,
                history,
                filterList
        );

        PreviewManager.getInstance().storePreview(session);

        long duration = System.currentTimeMillis() - startTime;
        int blockCount = session.getBlockCount();
        int containerCount = session.getContainerCount();

        Component previewMessage = Component.empty()
                .append(Theme.header("Restore Preview"))
                .append(Component.newline())
                .append(Theme.labelValue("Blocks to restore", String.valueOf(blockCount), Theme.ACCENT));

        if (containerCount > 0) {
            previewMessage = previewMessage
                    .append(Component.newline())
                    .append(Theme.labelValue("Container items", String.valueOf(containerCount), Theme.ACCENT));
        }

        previewMessage = previewMessage
                .append(Component.newline())
                .append(Theme.labelValue("World", levelName))
                .append(Component.newline())
                .append(Theme.muted("Query took " + duration + "ms"))
                .append(Component.newline())
                .append(Component.newline())
                .append(Theme.info("Use "))
                .append(Theme.accent("/gl apply"))
                .append(Theme.info(" to apply, or "))
                .append(Theme.accent("/gl cancel"))
                .append(Theme.info(" to discard."))
                .append(Component.newline())
                .append(Theme.muted("Preview expires in " + session.getSecondsRemaining() + " seconds."));

        final Component finalMessage = previewMessage;
        source.sendSuccess(() -> Theme.toMinecraft(finalMessage), false);
    }

    /**
     * Execute the actual restore operation.
     */
    private void executeRestore(
            CommandSourceStack source,
            FilterList filterList,
            List<IHistory> history,
            long startTime
    ) {
        AtomicInteger blockCount = new AtomicInteger();
        List<RollbackAction> restoreActions = new ArrayList<>();

        source.getServer().execute(() -> {
            ServerLevel level = source.getLevel();
            String levelName = level.dimension().location().toString();

            for (IHistory entry : history) {
                if (entry instanceof BlockHistory blockHistory) {
                    RollbackAction action = processRestore(level, levelName, blockHistory, blockCount);
                    if (action != null) {
                        restoreActions.add(action);
                    }
                }
            }

            List<IHistory> containerHistory = history.stream()
                    .filter(h -> h instanceof ContainerHistory)
                    .toList();

            int containerItemsProcessed = 0;
            int containerItemsDropped = 0;

            if (!containerHistory.isEmpty()) {
                ContainerRollbackProcessor containerProcessor = new ContainerRollbackProcessor(level, true);
                containerProcessor.process(containerHistory);
                containerItemsProcessed = containerProcessor.getProcessedCount();
                containerItemsDropped = containerProcessor.getDroppedCount();
            }

            long duration = System.currentTimeMillis() - startTime;
            int blocksProcessed = blockCount.get();

            if (source.getEntity() instanceof ServerPlayer player && (blocksProcessed > 0 || containerItemsProcessed > 0)) {
                recordRestoreJob(
                        player,
                        filterList,
                        levelName,
                        blocksProcessed,
                        duration,
                        restoreActions
                );
            }

            final int finalContainerItems = containerItemsProcessed;
            final int finalDropped = containerItemsDropped;

            Component resultMessage = Theme.success("Restore complete! ");
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
        });
    }

    /**
     * Process restore - opposite of rollback logic.
     * BREAK -> remove block (confirm the break)
     * PLACE -> restore the placed block
     */
    private RollbackAction processRestore(ServerLevel level, String levelName, BlockHistory history, AtomicInteger count) {
        BlockPos pos = new BlockPos(
                history.getPosition().x(),
                history.getPosition().y(),
                history.getPosition().z()
        );
        int action = history.getAction().getId();

        BlockState currentState = level.getBlockState(pos);
        String currentMaterial = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();

        if (action == BlockAction.BREAK_BLOCK.getId()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            count.incrementAndGet();

            return RollbackAction.create(
                    -1,
                    RollbackAction.TYPE_BLOCK_REMOVE,
                    levelName,
                    pos.getX(), pos.getY(), pos.getZ(),
                    0, 
                    0, 
                    currentMaterial,
                    "minecraft:air"
            );
        }
        else if (action == BlockAction.PLACE_BLOCK.getId()) {
            BlockState placedState = Services.BLOCK.getBlockStateFromId(history.getStateId());
            if (placedState != null) {
                level.setBlock(pos, placedState, 2 | 16);
                count.incrementAndGet();

                String newMaterial = BuiltInRegistries.BLOCK.getKey(placedState.getBlock()).toString();
                return RollbackAction.create(
                        -1,
                        RollbackAction.TYPE_BLOCK_RESTORE,
                        levelName,
                        pos.getX(), pos.getY(), pos.getZ(),
                        0,
                        history.getStateId(),
                        currentMaterial,
                        newMaterial
                );
            }
        }

        return null;
    }

    private void recordRestoreJob(
            ServerPlayer player,
            FilterList filterList,
            String levelName,
            int blockCount,
            long duration,
            List<RollbackAction> actions
    ) {
        int centerX = (int) player.getX();
        int centerY = (int) player.getY();
        int centerZ = (int) player.getZ();
        int radius = filterList.getRadiusFilter()
                .map(r -> Math.max(Math.abs(r.getMaxX() - r.getMinX()), Math.abs(r.getMaxZ() - r.getMinZ())) / 2)
                .orElse(0);

        RollbackJob job = RollbackJob.create(
                player.getUUID(),
                player.getName().getString(),
                RollbackJob.TYPE_RESTORE,
                levelName,
                centerX, centerY, centerZ,
                radius,
                filterList.getTime(),
                filterList.getUserString(),
                blockCount,
                duration
        );

        ThreadManager.submit(() -> {
            return Services.ROLLBACK.recordOperation(job, actions);
        }, jobId -> {
        });
    }
}

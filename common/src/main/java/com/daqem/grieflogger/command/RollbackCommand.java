package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.argument.FilterArgument;
import com.daqem.grieflogger.command.filter.FilterList;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.history.IHistory;
import com.daqem.grieflogger.model.rollback.BatchProcessor;
import com.daqem.grieflogger.model.rollback.PreviewManager;
import com.daqem.grieflogger.model.rollback.PreviewSession;
import com.daqem.grieflogger.model.rollback.RollbackAction;
import com.daqem.grieflogger.model.rollback.RollbackJob;
import com.daqem.grieflogger.model.rollback.RollbackProgress;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class RollbackCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("rollback")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("filters", StringArgumentType.greedyString())
                        .suggests(FilterArgument::suggestFilters)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "filters");
                            FilterList filterList = FilterArgument.parseFilters(raw, context.getSource());
                            return rollback(context.getSource(), filterList);
                        }));
    }

    private int rollback(CommandSourceStack source, FilterList filterList) {
        if (filterList.getTime() == 0 && filterList.getRadiusFilter().isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("You must specify a time (t:) or radius (r:) filter!")));
            return 0;
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            if (RollbackProgress.hasActiveOperation(player.getUUID())) {
                source.sendFailure(Theme.toMinecraft(Theme.error("A rollback operation is already in progress. Use /gl cancel to stop it.")));
                return 0;
            }
        }

        boolean isPreview = filterList.isPreview();
        boolean includeContainers = filterList.isContainer();
        String startMessage = isPreview ? "Generating rollback preview..." : "Loading rollback data...";
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
                source.sendFailure(Theme.toMinecraft(Theme.error("No actions found to rollback.")));
                return;
            }

            if (isPreview) {
                handlePreviewMode(source, filterList, history, startTime);
                return;
            }

            executeRollback(source, filterList, history);
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

        PreviewSession session = PreviewSession.createRollback(
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
                .append(Theme.header("Rollback Preview"))
                .append(Component.newline())
                .append(Theme.labelValue("Blocks to rollback", String.valueOf(blockCount), Theme.ACCENT));

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
     * Execute the actual rollback operation using batch processing.
     */
    private void executeRollback(
            CommandSourceStack source,
            FilterList filterList,
            List<IHistory> history
    ) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Theme.toMinecraft(Theme.error("Rollback can only be executed by players.")));
            return;
        }

        ServerLevel level = source.getLevel();
        String levelName = level.dimension().location().toString();
        int batchSize = GriefLoggerConfig.rollbackBatchSize.get();

        source.getServer().execute(() -> {
            new BatchProcessor(player, level, history, false, batchSize)
                    .onComplete(result -> {
                        if (!result.wasCancelled() && result.totalProcessed() > 0) {
                            recordRollbackJob(
                                    player,
                                    filterList,
                                    levelName,
                                    result.blocksProcessed(),
                                    result.durationMs(),
                                    result.actions(),
                                    RollbackJob.TYPE_ROLLBACK
                            );
                        }

                        if (!result.wasCancelled()) {
                            Component chatMessage = buildResultMessage(
                                    "Rollback",
                                    result.blocksProcessed(),
                                    result.containerItemsProcessed(),
                                    result.containerItemsDropped(),
                                    result.durationMs()
                            );
                            source.sendSuccess(() -> Theme.toMinecraft(chatMessage), true);
                        }
                    })
                    .start();
        });
    }

    /**
     * Build a result message for rollback/restore operations.
     */
    private Component buildResultMessage(String operation, int blocks, int containerItems, int dropped, long durationMs) {
        Component message = Theme.success(operation + " complete! ");
        if (blocks > 0) {
            message = message.append(Theme.muted(blocks + " blocks"));
        }
        if (containerItems > 0) {
            if (blocks > 0) {
                message = message.append(Theme.muted(", "));
            }
            message = message.append(Theme.muted(containerItems + " container items"));
        }
        if (dropped > 0) {
            message = message.append(Theme.muted(" (" + dropped + " dropped)"));
        }
        message = message.append(Theme.muted(" in " + durationMs + "ms."));
        return message;
    }

    private void recordRollbackJob(
            ServerPlayer player,
            FilterList filterList,
            String levelName,
            int blockCount,
            long duration,
            List<RollbackAction> actions,
            int actionType
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
                actionType,
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

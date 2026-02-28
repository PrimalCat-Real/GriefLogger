package com.daqem.grieflogger.model.rollback;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;
import com.daqem.grieflogger.model.history.BlockHistory;
import com.daqem.grieflogger.model.history.ContainerHistory;
import com.daqem.grieflogger.model.history.IHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Processes rollback/restore operations in batches to prevent server lag.
 * Shows progress in action bar and supports cancellation.
 */
public class BatchProcessor {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int PROGRESS_UPDATE_INTERVAL = 5; 

    private final ServerPlayer player;
    private final ServerLevel level;
    private final String levelName;
    private final List<IHistory> history;
    private final boolean isRestore;
    private final int batchSize;
    private final RollbackProgress progress;
    private final List<RollbackAction> rollbackActions = new ArrayList<>();
    private final AtomicInteger blocksProcessed = new AtomicInteger(0);
    private final AtomicInteger containerItemsProcessed = new AtomicInteger(0);
    private final AtomicInteger containerItemsDropped = new AtomicInteger(0);
    private int currentIndex = 0;
    private int batchCount = 0;
    private Consumer<BatchResult> onComplete;

    public BatchProcessor(
            ServerPlayer player,
            ServerLevel level,
            List<IHistory> history,
            boolean isRestore,
            int batchSize
    ) {
        this.player = player;
        this.level = level;
        this.levelName = level.dimension().location().toString();
        this.history = history;
        this.isRestore = isRestore;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;

        int blockCount = (int) history.stream().filter(h -> h instanceof BlockHistory).count();
        int containerCount = (int) history.stream().filter(h -> h instanceof ContainerHistory).count();

        String operationType = isRestore ? "Restore" : "Rollback";
        this.progress = RollbackProgress.start(player.getUUID(), operationType, blockCount, containerCount);
    }

    /**
     * Set callback for when processing is complete.
     */
    public BatchProcessor onComplete(Consumer<BatchResult> callback) {
        this.onComplete = callback;
        return this;
    }

    /**
     * Start processing batches.
     */
    public void start() {
        processBatch();
    }

    /**
     * Process one batch of entries.
     */
    private void processBatch() {
        if (progress.isCancelled()) {
            finishProcessing(true);
            return;
        }

        MinecraftServer server = level.getServer();
        int processed = 0;

        GriefLogger.setRollbackActive(true);
        try {
            while (currentIndex < history.size() && processed < batchSize) {
                if (progress.isCancelled()) {
                    finishProcessing(true);
                    return;
                }

                IHistory entry = history.get(currentIndex);
                currentIndex++;

                if (entry instanceof BlockHistory blockHistory) {
                    RollbackAction action = processBlock(blockHistory);
                    if (action != null) {
                        rollbackActions.add(action);
                        blocksProcessed.incrementAndGet();
                        progress.incrementBlocks(1);
                    }
                    processed++;
                } else if (entry instanceof ContainerHistory containerHistory) {
                    processContainer(containerHistory);
                    processed++;
                }
            }
        } finally {
            GriefLogger.setRollbackActive(false);
        }

        batchCount++;

        if (batchCount % PROGRESS_UPDATE_INTERVAL == 0) {
            progress.sendProgressBar(player);
        }

        if (currentIndex >= history.size()) {
            finishProcessing(false);
        } else {
            server.execute(this::processBatch);
        }
    }

    private RollbackAction processBlock(BlockHistory history) {
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

    private void processContainer(ContainerHistory history) {
        ContainerRollbackProcessor processor = new ContainerRollbackProcessor(level, isRestore);
        processor.process(List.of(history));

        int processed = processor.getProcessedCount();
        int dropped = processor.getDroppedCount();

        containerItemsProcessed.addAndGet(processed);
        containerItemsDropped.addAndGet(dropped);
        progress.incrementContainerItems(processed + dropped);
    }

    private void finishProcessing(boolean wasCancelled) {
        progress.complete();

        int blocks = blocksProcessed.get();
        int items = containerItemsProcessed.get();
        int dropped = containerItemsDropped.get();

        progress.sendCompletionBar(player, blocks, items, dropped);

        if (onComplete != null) {
            BatchResult result = new BatchResult(
                    blocks,
                    items,
                    dropped,
                    rollbackActions,
                    wasCancelled,
                    progress.getElapsedMs()
            );
            onComplete.accept(result);
        }
    }

    /**
     * Result of batch processing.
     */
    public record BatchResult(
            int blocksProcessed,
            int containerItemsProcessed,
            int containerItemsDropped,
            List<RollbackAction> actions,
            boolean wasCancelled,
            long durationMs
    ) {
        public int totalProcessed() {
            return blocksProcessed + containerItemsProcessed;
        }
    }
}

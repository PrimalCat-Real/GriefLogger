package com.daqem.grieflogger.command;

import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.rollback.RollbackAction;
import com.daqem.grieflogger.model.rollback.RollbackJob;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
 * Undo command - reverses the last rollback/restore operation.
 * Usage: /gl undo
 */
public class UndoCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("undo")
                .requires(source -> source.hasPermission(2))
                .executes(context -> undo(context.getSource()));
    }

    private int undo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Theme.toMinecraft(Theme.error("This command can only be used by players.")));
            return 0;
        }

        source.sendSuccess(() -> Theme.toMinecraft(Theme.muted("Looking for last operation...")), false);

        long startTime = System.currentTimeMillis();

        ThreadManager.submit(() -> {
            return Services.ROLLBACK.getLastJob(player.getUUID());
        }, optionalJob -> {
            if (optionalJob.isEmpty()) {
                source.sendFailure(Theme.toMinecraft(Theme.error("No rollback/restore operations found to undo.")));
                return;
            }

            RollbackJob lastJob = optionalJob.get();

            if (lastJob.actionType() == RollbackJob.TYPE_UNDO) {
                source.sendFailure(Theme.toMinecraft(Theme.error("Cannot undo an undo operation. Use rollback/restore instead.")));
                return;
            }

            ThreadManager.submit(() -> {
                return Services.ROLLBACK.getActionsForJob(lastJob.id());
            }, actions -> {
                if (actions.isEmpty()) {
                    source.sendFailure(Theme.toMinecraft(Theme.error("No block changes found for the last operation.")));
                    return;
                }

                source.getServer().execute(() -> {
                    AtomicInteger count = new AtomicInteger();
                    List<RollbackAction> undoActions = new ArrayList<>();
                    ServerLevel level = source.getLevel();
                    String levelName = level.dimension().location().toString();

                    for (RollbackAction action : actions) {
                        if (!action.levelName().equals(levelName)) {
                            continue;
                        }

                        RollbackAction undoAction = processUndo(level, action, count);
                        if (undoAction != null) {
                            undoActions.add(undoAction);
                        }
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    int blocksProcessed = count.get();

                    if (blocksProcessed > 0) {
                        recordUndoJob(player, lastJob, blocksProcessed, duration, undoActions);
                    }

                    String actionName = lastJob.getActionTypeName();
                    source.sendSuccess(() -> Theme.toMinecraft(
                            Theme.success("Undo complete! ")
                                    .append(Theme.muted("Reversed " + actionName + " of " + blocksProcessed + " blocks in " + duration + "ms."))
                    ), true);
                });
            });
        });

        return 1;
    }

    /**
     * Process undo - reverse the action.
     * TYPE_BLOCK_RESTORE (was air, became block) -> set back to air
     * TYPE_BLOCK_REMOVE (was block, became air) -> restore the block
     */
    private RollbackAction processUndo(ServerLevel level, RollbackAction action, AtomicInteger count) {
        BlockPos pos = new BlockPos(action.x(), action.y(), action.z());
        String levelName = action.levelName();

        if (action.actionType() == RollbackAction.TYPE_BLOCK_RESTORE) {
            BlockState currentState = level.getBlockState(pos);
            String currentMaterial = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            count.incrementAndGet();

            return RollbackAction.create(
                    -1,
                    RollbackAction.TYPE_BLOCK_REMOVE,
                    levelName,
                    pos.getX(), pos.getY(), pos.getZ(),
                    action.newStateId(),
                    0,
                    currentMaterial,
                    "minecraft:air"
            );
        }
        else if (action.actionType() == RollbackAction.TYPE_BLOCK_REMOVE) {
            BlockState previousState = Services.BLOCK.getBlockStateFromId(action.oldStateId());
            if (previousState != null) {
                level.setBlock(pos, previousState, 2 | 16);
                count.incrementAndGet();

                String newMaterial = BuiltInRegistries.BLOCK.getKey(previousState.getBlock()).toString();
                return RollbackAction.create(
                        -1,
                        RollbackAction.TYPE_BLOCK_RESTORE,
                        levelName,
                        pos.getX(), pos.getY(), pos.getZ(),
                        0,
                        action.oldStateId(),
                        "minecraft:air",
                        newMaterial
                );
            }
        }

        return null;
    }

    private void recordUndoJob(
            ServerPlayer player,
            RollbackJob originalJob,
            int blockCount,
            long duration,
            List<RollbackAction> actions
    ) {
        RollbackJob undoJob = RollbackJob.create(
                player.getUUID(),
                player.getName().getString(),
                RollbackJob.TYPE_UNDO,
                originalJob.levelName(),
                originalJob.centerX(),
                originalJob.centerY(),
                originalJob.centerZ(),
                originalJob.radius(),
                originalJob.timeFilter(),
                originalJob.userFilter(),
                blockCount,
                duration
        );

        ThreadManager.submit(() -> {
            return Services.ROLLBACK.recordOperation(undoJob, actions);
        }, jobId -> {
        });
    }
}

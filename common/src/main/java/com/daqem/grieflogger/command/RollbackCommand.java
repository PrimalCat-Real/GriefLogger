package com.daqem.grieflogger.command;

import com.daqem.grieflogger.command.argument.FilterArgument;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;
import com.daqem.grieflogger.model.history.BlockHistory;
import com.daqem.grieflogger.model.history.IHistory;
import com.daqem.grieflogger.thread.ThreadManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RollbackCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("rollback")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("filter1", StringArgumentType.string())
                        .suggests((context, builder) -> new FilterArgument().listSuggestions(context, builder))
                        .then(Commands.argument("filter2", StringArgumentType.string())
                                .suggests((context, builder) -> new FilterArgument().listSuggestions(context, builder))
                                .then(Commands.argument("filter3", StringArgumentType.string())
                                        .suggests((context, builder) -> new FilterArgument().listSuggestions(context, builder))
                                        .executes(context -> rollback(context.getSource(), new FilterList(List.of(
                                                FilterArgument.getFilter(context, "filter1"),
                                                FilterArgument.getFilter(context, "filter2"),
                                                FilterArgument.getFilter(context, "filter3")), context.getSource()))))
                                .executes(context -> rollback(context.getSource(), new FilterList(List.of(
                                        FilterArgument.getFilter(context, "filter1"),
                                        FilterArgument.getFilter(context, "filter2")), context.getSource()))))
                        .executes(context -> rollback(context.getSource(), new FilterList(List.of(
                                FilterArgument.getFilter(context, "filter1")), context.getSource()))));
    }

    private int rollback(CommandSourceStack source, FilterList filterList) {
        source.sendSuccess(() -> Component.literal("§7Starting rollback..."), false);

        if (filterList.getTime() == 0 && filterList.getRadiusFilter().isEmpty()) {
            source.sendFailure(Component.literal("§cYou must specify a time (t:) or radius (r:) filter!"));
            return 0;
        }

        ThreadManager.submit(() -> {

            return Services.BLOCK.getFilteredBlockHistory(source.getLevel(), filterList);
        }, history -> {
            if (history == null || history.isEmpty()) {
                source.sendFailure(Component.literal("§cNo actions found to rollback."));
                return;
            }

            AtomicInteger count = new AtomicInteger();

            source.getServer().execute(() -> {
                ServerLevel level = source.getLevel();

                for (IHistory entry : history) {
                    if (entry instanceof BlockHistory blockHistory) {
                        processRollback(level, blockHistory, count);
                    }
                }

                source.sendSuccess(() -> Component.literal("§aRollback complete! §7Processed " + count.get() + " blocks."), true);
            });
        });

        return 1;
    }

    private void processRollback(ServerLevel level, BlockHistory history, AtomicInteger count) {
        BlockPos pos = new BlockPos(
                history.getPosition().x(),
                history.getPosition().y(),
                history.getPosition().z()
        );
        int action = history.getAction().getId();

        if (action == BlockAction.BREAK_BLOCK.getId()) {
            BlockState previousState = Services.BLOCK.getBlockStateFromId(history.getStateId());
            if (previousState != null) {
                level.setBlock(pos, previousState, 2 | 16);
                count.incrementAndGet();
            }
        }
        else if (action == BlockAction.PLACE_BLOCK.getId()) {
            BlockState currentState = level.getBlockState(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            count.incrementAndGet();
        }
    }
}

package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.argument.FilterArgument;
import com.daqem.grieflogger.command.filter.*;
import com.daqem.grieflogger.command.page.Page;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.history.*;
import com.daqem.grieflogger.player.GriefLoggerServerPlayer;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LookupCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("lookup")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("filters", StringArgumentType.greedyString())
                        .suggests(FilterArgument::suggestFilters)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "filters");
                            FilterList filterList = FilterArgument.parseFilters(raw, context.getSource());
                            return lookup(context.getSource(), filterList);
                        }));
    }

    @SuppressWarnings("SameReturnValue")
    private static int lookup(CommandSourceStack source, FilterList filterList) {
        if (source.getPlayer() instanceof ServerPlayer serverPlayer) {
            executeLookup(serverPlayer, filterList, 1);
        }
        return 1;
    }

    /**
     * Execute a lookup with the given filters and display the specified page.
     * Can be called from other commands (e.g., NearCommand).
     *
     * @param player The player to show results to
     * @param filterList The filters to apply
     * @param pageNumber The page number to display (1-indexed)
     */
    public static void executeLookup(ServerPlayer player, FilterList filterList, int pageNumber) {
        if (player instanceof GriefLoggerServerPlayer griefLoggerPlayer) {
            ThreadManager.submit(() -> getHistory(player.level(), filterList), filteredHistory -> {
                if (filteredHistory.isEmpty()) {
                    player.sendSystemMessage(GriefLogger.translate("lookup.no_results", GriefLogger.getName()));
                    return;
                }

                // Handle #count flag - only show count, not results
                if (filterList.isCountOnly()) {
                    int count = filteredHistory.size();
                    player.sendSystemMessage(Theme.toMinecraft(
                            Theme.success("Found ")
                                    .append(Theme.accent(String.valueOf(count)))
                                    .append(Theme.success(" records matching your query."))
                    ));
                    return;
                }

                List<Page> pages = Page.convertToPages(filteredHistory, false);
                griefLoggerPlayer.grieflogger$setPages(pages);

                int displayPage = Math.max(1, Math.min(pageNumber, pages.size()));
                Page pageToDisplay = pages.get(displayPage - 1);
                pageToDisplay.sendToPlayer(player);
            });
        }
    }

    private static List<IHistory> getHistory(Level level, FilterList filterList) {
        // Flush pending queues to ensure all recent changes are written to DB
        GriefLogger.getDatabase().flushQueues();

        List<SessionHistory> filteredSessionHistory = Services.SESSION.getFilteredSessionHistory(level, filterList);
        List<IHistory> filteredBlockHistory = Services.BLOCK.getFilteredBlockHistory(level, filterList);
        List<IHistory> filteredContainerHistory = Services.CONTAINER.getFilteredContainerHistory(level, filterList);
        List<ItemHistory> filteredItemHistory = Services.ITEM.getFilteredItemHistory(level, filterList);
        return new ArrayList<>(List.of(filteredSessionHistory, filteredBlockHistory, filteredContainerHistory, filteredItemHistory))
                .stream()
                .flatMap(List::stream)
                .sorted((x, y) -> Long.compare(y.getTime().time(), x.getTime().time()))
                .collect(Collectors.toList());
    }
}

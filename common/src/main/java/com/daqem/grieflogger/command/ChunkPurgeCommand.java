package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.argument.FilterArgument;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.database.orm.query.DeleteBuilder;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Command: /gl chunk_purge u:<player> t:<time> r:<radius>
 * Deletes chunk backups based on filters.
 */
public class ChunkPurgeCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("chunk_purge")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("filters", StringArgumentType.greedyString())
                        .suggests(FilterArgument::suggestFilters)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "filters");
                            FilterList filterList = FilterArgument.parseFilters(raw, context.getSource());
                            return handlePurge(context.getSource(), filterList);
                        }));
    }

    private int handlePurge(CommandSourceStack source, FilterList filterList) {
        if (filterList.getUserString() == null && filterList.getTime() == 0 && filterList.getRadiusFilter().isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("You must specify at least one filter (u:, t:, or r:)!")));
            return 0;
        }

        source.sendSuccess(() -> Theme.toMinecraft(Theme.muted("Purging chunk backups matching filters...")), false);

        ThreadManager.submit(() -> {
            DeleteBuilder delete = Query.delete("chunk_backups");
            boolean hasCriteria = false;

            // Filter by User
            if (filterList.getUserFilter().isPresent()) {
                var userFilter = filterList.getUserFilter().get();
                var userIds = userFilter.getUserIds();
                var usernames = userFilter.getUsernames().values();
                var uuids = com.daqem.grieflogger.database.service.Services.USER.getUuidsByIds(userIds);

                List<String> userConditions = new ArrayList<>();
                if (!usernames.isEmpty()) {
                    userConditions.add("LOWER(player_name) IN ('" + String.join("','", usernames).toLowerCase() + "')");
                }
                if (!uuids.isEmpty()) {
                    userConditions.add("LOWER(player_uuid) IN ('" + String.join("','", uuids).toLowerCase() + "')");
                }

                if (!userConditions.isEmpty()) {
                    delete.whereRaw("(" + String.join(" OR ", userConditions) + ")");
                    hasCriteria = true;
                }
            }

            // Filter by Time (older than)
            if (filterList.getTime() > 0) {
                // filterList.getTime() already returns the timestamp (now - offset)
                delete.whereLt("time", filterList.getTime());
                hasCriteria = true;
            }

            // Filter by Radius (coordinates)
            if (filterList.getRadiusFilter().isPresent()) {
                var r = filterList.getRadiusFilter().get();
                if (!r.isGlobal()) {
                    delete.whereEq("world", source.getLevel().dimension().location().toString());
                    delete.whereRaw("player_x >= ? AND player_x <= ? AND player_z >= ? AND player_z <= ?",
                            r.getMinX(), r.getMaxX(), r.getMinZ(), r.getMaxZ());
                    hasCriteria = true;
                }
            }

            if (!hasCriteria) return 0;

            return delete.execute(GriefLogger.getDatabase());
        }, deleted -> {
            source.sendSuccess(() -> Theme.toMinecraft(Theme.success(
                    "Purged " + deleted + " chunk backup records.")), true);
        });

        return 1;
    }
}


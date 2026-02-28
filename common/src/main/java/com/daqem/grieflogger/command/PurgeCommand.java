package com.daqem.grieflogger.command;

import com.daqem.grieflogger.command.argument.FilterArgument;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.command.filter.TimeFilter;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Command to purge old data from the database.
 * Usage: /gl purge t:30d
 *
 * Requires confirmation: /gl purge t:30d confirm
 */
public class PurgeCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("purge")
                .requires(source -> source.hasPermission(4)) 
                .then(Commands.argument("time", StringArgumentType.greedyString())
                        .suggests(FilterArgument::suggestFilters)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "time");
                            boolean confirmed = raw.trim().endsWith("confirm");
                            String filterPart = confirmed ? raw.replace("confirm", "").trim() : raw;
                            FilterList filterList = FilterArgument.parseFilters(filterPart, context.getSource());
                            return purge(context.getSource(), filterList, confirmed);
                        }));
    }

    private int purge(CommandSourceStack source, FilterList filterList, boolean confirmed) {
        if (filterList.getTimeFilter().isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("You must specify a time filter (t:30d).")));
            return 0;
        }

        long timeMs = filterList.getTime();
        if (timeMs == 0) {
            source.sendFailure(Theme.toMinecraft(Theme.error("Invalid time filter.")));
            return 0;
        }

        int minDays = GriefLoggerConfig.purgeMinDays.get();
        long minMs = TimeUnit.DAYS.toMillis(minDays);
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - timeMs;

        if (timeMs < minMs) {
            source.sendFailure(Theme.toMinecraft(
                    Theme.error("For safety, you cannot purge data less than " + minDays + " days old.")
            ));
            return 0;
        }

        String timeDisplay = formatTime(timeMs);

        if (!confirmed) {
            Component warningMessage = Component.empty()
                    .append(Theme.header("Purge Warning"))
                    .append(Component.newline())
                    .append(Theme.error("This will permanently delete all records older than " + timeDisplay + "!"))
                    .append(Component.newline())
                    .append(Theme.muted("This action cannot be undone."))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Theme.info("To confirm, run: "))
                    .append(Theme.accent("/gl purge t:" + timeDisplay + " confirm"));

            source.sendSuccess(() -> Theme.toMinecraft(warningMessage), false);
            return 1;
        }

        source.sendSuccess(() -> Theme.toMinecraft(Theme.muted("Starting purge of records older than " + timeDisplay + "...")), false);

        ThreadManager.submit(() -> {
            return Services.purgeOldData(cutoffTime);
        }, result -> {
            if (result == null) {
                source.sendFailure(Theme.toMinecraft(Theme.error("Purge failed. Check server logs for details.")));
                return;
            }

            Component resultMessage = Component.empty()
                    .append(Theme.header("Purge Complete"))
                    .append(Component.newline())
                    .append(Theme.labelValue("Blocks", String.valueOf(result.blocks())))
                    .append(Component.newline())
                    .append(Theme.labelValue("Containers", String.valueOf(result.containers())))
                    .append(Component.newline())
                    .append(Theme.labelValue("Items", String.valueOf(result.items())))
                    .append(Component.newline())
                    .append(Theme.labelValue("Sessions", String.valueOf(result.sessions())))
                    .append(Component.newline())
                    .append(Theme.labelValue("Chat", String.valueOf(result.chat())))
                    .append(Component.newline())
                    .append(Theme.labelValue("Commands", String.valueOf(result.commands())))
                    .append(Component.newline())
                    .append(Theme.muted("Total: " + result.total() + " records deleted"));

            source.sendSuccess(() -> Theme.toMinecraft(resultMessage), true);
        });

        return 1;
    }

    private String formatTime(long ms) {
        long days = TimeUnit.MILLISECONDS.toDays(ms);
        if (days >= 365) {
            return (days / 365) + "y";
        } else if (days >= 30) {
            return (days / 30) + "mo";
        } else if (days >= 7) {
            return (days / 7) + "w";
        } else if (days > 0) {
            return days + "d";
        } else {
            long hours = TimeUnit.MILLISECONDS.toHours(ms);
            if (hours > 0) {
                return hours + "h";
            }
            long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
            return minutes + "m";
        }
    }
}

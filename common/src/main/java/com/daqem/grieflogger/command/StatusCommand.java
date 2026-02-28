package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Status command showing mod information and database status.
 * Usage: /gl status
 */
public class StatusCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("status")
                .requires(source -> source.hasPermission(2))  
                .executes(context -> {
                    CommandSourceStack source = context.getSource();

                    source.sendSuccess(() -> Theme.toMinecraft(Theme.header("GriefLogger Status")), false);

                    source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Mod ID", GriefLogger.MOD_ID)), false);

                    String dbType = GriefLoggerConfig.useMysql.get() ? "MySQL" : "SQLite";
                    source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Database", dbType)), false);

                    Database database = GriefLogger.getDatabase();
                    boolean connected = database != null;
                    String connectionStatus = connected ? "Connected" : "Disconnected";
                    source.sendSuccess(() -> Theme.toMinecraft(
                            Theme.labelValue("Status", connectionStatus, connected ? Theme.SUCCESS : Theme.ERROR)
                    ), false);

                    if (GriefLoggerConfig.useMysql.get()) {
                        String host = GriefLoggerConfig.mysqlHost.get() + ":" + GriefLoggerConfig.mysqlPort.get();
                        source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Host", host)), false);
                        source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Database", GriefLoggerConfig.mysqlDatabase.get())), false);
                    }

                    source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Indexes",
                            GriefLoggerConfig.useIndexes.get() ? "Enabled" : "Disabled")), false);
                    source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Page Size",
                            String.valueOf(GriefLoggerConfig.maxPageSize.get()))), false);
                    source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Queue Interval",
                            GriefLoggerConfig.queueFrequency.get() + " ticks")), false);

                    Runtime runtime = Runtime.getRuntime();
                    long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                    long maxMB = runtime.maxMemory() / (1024 * 1024);
                    source.sendSuccess(() -> Theme.toMinecraft(Theme.labelValue("Memory", usedMB + "MB / " + maxMB + "MB")), false);

                    return 1;
                });
    }
}

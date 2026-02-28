package com.daqem.grieflogger.command;

import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ChunkListCommand implements ICommand {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("chunk_list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> listBackups(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> listBackups(context, IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int listBackups(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        
        ThreadManager.submit(() -> {
            return Services.CHUNK_BACKUP.getBackupsList();
        }, backups -> {
            if (backups.isEmpty()) {
                source.sendSuccess(() -> Theme.toMinecraft(Theme.info("No chunk backups found.")), false);
                return;
            }

            int pageSize = 10;
            int totalPages = (int) Math.ceil((double) backups.size() / pageSize);
            int currentPage = Math.min(Math.max(1, page), totalPages);
            int startIndex = (currentPage - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, backups.size());

            source.sendSuccess(() -> Theme.toMinecraft(Theme.header("Chunk Backups (Page " + currentPage + "/" + totalPages + ")")), false);

            for (int i = startIndex; i < endIndex; i++) {
                Map<String, Object> backupData = backups.get(i);
                String uuid = (String) backupData.get("backup_uuid");
                long time = (Long) backupData.get("time");
                String dateStr = FORMATTER.format(Instant.ofEpochMilli(time));
                int count = (Integer) backupData.get("chunks");
                String playerName = (String) backupData.get("player_name");
                int radius = (Integer) backupData.get("radius");

                Component line = Component.empty()
                        .append(Theme.muted("- "))
                        .append(Theme.accent(uuid.substring(0, 8)))
                        .append(Theme.muted(" | "))
                        .append(Theme.secondary(dateStr))
                        .append(Theme.muted(" | "))
                        .append(Theme.primary(count + " chunks"))
                        .append(Theme.muted(" | r:" + radius));

                if (playerName != null) {
                    line = line.append(Theme.muted(" | ")).append(Theme.secondary(playerName));
                }

                final Component finalLine = line;
                source.sendSuccess(() -> Theme.toMinecraft(finalLine), false);
            }
        });

        return 1;
    }
}

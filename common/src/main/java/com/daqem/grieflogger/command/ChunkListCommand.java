package com.daqem.grieflogger.command;

import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.daqem.grieflogger.thread.ThreadManager;

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
                UUID uuid = UUID.fromString((String) backupData.get("backup_uuid"));
                long time = (Long) backupData.get("time");
                String dateStr = FORMATTER.format(Instant.ofEpochMilli(time));
                int count = (Integer) backupData.get("chunks");

                Component line = Theme.toMinecraft(Theme.parse(
                        String.format("<primary>- Backup ID: </primary><secondary>%s</secondary> " +
                                      "<muted>[%s]</muted> " +
                                      "<info>(%d chunks)</info>", 
                                      uuid.toString(), dateStr, count)
                ));

                source.sendSuccess(() -> line, false);
            }
        });

        return 1;
    }
}

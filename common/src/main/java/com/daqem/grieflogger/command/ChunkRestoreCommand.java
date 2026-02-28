package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.chunk.ChunkManager;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Command: /gl chunk_restore id:<backup_uuid>
 *
 * Supported filters:
 *   id:<uuid>  — restore a specific backup by its UUID (or partial UUID)
 */
public class ChunkRestoreCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("chunk_restore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("filters", StringArgumentType.greedyString())
                        .suggests(ChunkRestoreCommand::suggestFilters)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "filters");
                            return handleRestore(context.getSource(), raw);
                        }));
    }

    /**
     * Provide suggestions: id:<available backup UUIDs>
     */
    private static CompletableFuture<Suggestions> suggestFilters(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String input = builder.getRemaining();
        int lastSpace = input.lastIndexOf(' ');
        String currentToken = lastSpace >= 0 ? input.substring(lastSpace + 1) : input;
        String prefix = lastSpace >= 0 ? input.substring(0, lastSpace + 1) : "";

        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + prefix.length());

        // If typing nothing or just "i" / "id" -> suggest "id:"
        if (!currentToken.contains(":")) {
            List<String> prefixSuggestions = new ArrayList<>();
            if ("id:".startsWith(currentToken.toLowerCase()) || currentToken.isEmpty()) {
                prefixSuggestions.add("id:");
            }
            return SharedSuggestionProvider.suggest(prefixSuggestions, offsetBuilder);
        }

        int colonIdx = currentToken.indexOf(':');
        String filterPrefix = currentToken.substring(0, colonIdx).toLowerCase();
        String valuePart = currentToken.substring(colonIdx + 1);

        if (filterPrefix.equals("id") || filterPrefix.equals("i")) {
            // Fetch backup UUIDs async for suggestions
            return CompletableFuture.supplyAsync(() -> {
                List<Map<String, Object>> backups = Services.CHUNK_BACKUP.getBackupsList();
                List<String> suggestions = new ArrayList<>();
                for (Map<String, Object> backup : backups) {
                    String uuid = (String) backup.get("backup_uuid");
                    if (uuid.startsWith(valuePart) || valuePart.isEmpty()) {
                        suggestions.add("id:" + uuid);
                    }
                }
                return suggestions;
            }).thenApply(suggestions -> {
                for (String s : suggestions) {
                    offsetBuilder.suggest(s);
                }
                return offsetBuilder.build();
            });
        }

        return offsetBuilder.buildFuture();
    }

    /**
     * Parse the filter string and execute the restore.
     */
    private int handleRestore(CommandSourceStack source, String raw) {
        String backupId = null;

        String[] tokens = raw.trim().split("\\s+");
        for (String token : tokens) {
            int colonIdx = token.indexOf(':');
            if (colonIdx == -1) {
                colonIdx = token.indexOf('.');
            }
            if (colonIdx <= 0) continue;

            String key = token.substring(0, colonIdx).toLowerCase();
            String value = token.substring(colonIdx + 1);

            if (key.equals("id") || key.equals("i")) {
                backupId = value;
            }
        }

        if (backupId == null || backupId.isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("You must specify a backup ID! Usage: /gl chunk_restore id:<uuid>")));
            return 0;
        }

        final String finalBackupId = backupId;
        source.sendSuccess(() -> Theme.toMinecraft(Theme.muted("Loading backup data...")), false);

        ThreadManager.submit(() -> {
            GriefLogger.getDatabase().flushQueues();

            // Support partial UUID matching
            List<Map<String, Object>> backups = Services.CHUNK_BACKUP.getBackupsList();
            String matchedId = null;
            for (Map<String, Object> b : backups) {
                String uuid = (String) b.get("backup_uuid");
                if (uuid.equals(finalBackupId) || uuid.startsWith(finalBackupId)) {
                    matchedId = uuid;
                    break;
                }
            }
            if (matchedId == null) {
                return new RestoreData(null, null);
            }
            return new RestoreData(matchedId, Services.CHUNK_BACKUP.getBackupData(matchedId));
        }, result -> {
            if (result.backupId == null || result.chunks == null || result.chunks.isEmpty()) {
                source.sendFailure(Theme.toMinecraft(Theme.error("No backup found with ID: " + finalBackupId)));
                return;
            }

            int totalChunks = result.chunks.size();
            String worldName = (String) result.chunks.get(0).get("world");

            // Find the correct ServerLevel
            ServerLevel level = null;
            for (ServerLevel sl : source.getServer().getAllLevels()) {
                if (sl.dimension().location().toString().equals(worldName)) {
                    level = sl;
                    break;
                }
            }

            if (level == null) {
                source.sendFailure(Theme.toMinecraft(Theme.error("World not found: " + worldName)));
                return;
            }

            source.sendSuccess(() -> Theme.toMinecraft(Theme.primary(
                    "Restoring " + totalChunks + " chunks from backup " + result.backupId.substring(0, 8) + "...")), false);

            final ServerLevel targetLevel = level;

            // Execute restoration on the server thread
            source.getServer().execute(() -> {
                int success = 0;
                int fail = 0;

                for (Map<String, Object> chunkData : result.chunks) {
                    int chunkX = (Integer) chunkData.get("chunk_x");
                    int chunkZ = (Integer) chunkData.get("chunk_z");
                    byte[] data = (byte[]) chunkData.get("chunk_data");

                    try {
                        ChunkManager.deserializeChunk(targetLevel, chunkX, chunkZ, data);
                        success++;
                    } catch (Exception e) {
                        fail++;
                        GriefLogger.LOGGER.error("Failed to restore chunk [{}, {}]", chunkX, chunkZ, e);
                    }
                }

                final int s = success;
                final int f = fail;
                source.sendSuccess(() -> Theme.toMinecraft(Theme.success(
                        String.format("Chunk restore complete! %d/%d chunks restored (%d failed).",
                                s, totalChunks, f))), true);
            });
        });

        return 1;
    }

    /**
     * Internal record to pass data between async stages.
     */
    private record RestoreData(String backupId, List<Map<String, Object>> chunks) {}
}

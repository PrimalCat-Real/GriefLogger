package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.chunk.ChunkManager;
import com.daqem.grieflogger.thread.ThreadManager;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChunkBackupCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("chunk_backup")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("center", Vec2Argument.vec2())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                .executes(context -> {
                                    Vec2 pos = Vec2Argument.getVec2(context, "center");
                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                    return backupChunksXY(context.getSource(), pos.x, pos.y, radius);
                                })))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "target");
                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                    return backupChunksPlayer(context.getSource(), player, radius);
                                })));
    }

    private int backupChunksPlayer(CommandSourceStack source, ServerPlayer target, int radius) {
        return performBackup(source, (ServerLevel) target.level(), target.chunkPosition(), radius, target.getUUID(), target.getName().getString(), (int) target.getX(), (int) target.getZ());
    }

    private int backupChunksXY(CommandSourceStack source, float x, float z, int radius) {
        ChunkPos centerChunk = new ChunkPos((int) x >> 4, (int) z >> 4);
        return performBackup(source, source.getLevel(), centerChunk, radius, null, null, (int) x, (int) z);
    }

    private int performBackup(CommandSourceStack source, ServerLevel level, ChunkPos centerChunk, int radius, UUID playerUuid, String playerName, int exactX, int exactZ) {
        String world = level.dimension().location().toString();
        String backupUuid = UUID.randomUUID().toString();
        long time = System.currentTimeMillis();

        List<ChunkPos> chunksToBackup = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunksToBackup.add(new ChunkPos(centerChunk.x + x, centerChunk.z + z));
            }
        }

        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Starting backup of " + chunksToBackup.size() + " chunks...").withStyle(net.minecraft.ChatFormatting.AQUA), false);

        ThreadManager.submit(() -> {
            int successCount = 0;
            int failCount = 0;

            for (ChunkPos chunkPos : chunksToBackup) {
                try {
                    // Have to load chunk if not loaded (or just get it)
                    // We must execute chunk retrieval on the main thread
                    byte[][] chunkDataRef = new byte[1][];
                    Exception[] errorRef = new Exception[1];

                    source.getServer().submit(() -> {
                        try {
                            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                            chunkDataRef[0] = ChunkManager.serializeChunk(chunk);
                        } catch (Exception e) {
                            errorRef[0] = e;
                        }
                    }).join();

                    if (errorRef[0] != null) {
                        throw errorRef[0];
                    }

                    if (chunkDataRef[0] != null) {
                        Services.CHUNK_BACKUP.insertBackup(
                                backupUuid, time,
                                playerUuid != null ? playerUuid.toString() : null,
                                playerName,
                                exactX, exactZ, radius, world,
                                chunkPos.x, chunkPos.z,
                                chunkDataRef[0]
                        );
                        successCount++;
                    }

                } catch (Exception e) {
                    failCount++;
                    GriefLogger.LOGGER.error("Failed to backup chunk [{}, {}]", chunkPos.x, chunkPos.z, e);
                }
            }

            return new int[]{successCount, failCount};
        }, result -> {
            int success = result[0];
            int fail = result[1];
            source.sendSuccess(() -> Theme.toMinecraft(Theme.primary(
                            String.format("Backup Complete! Successfully backed up %d chunks (%d failed). Backup ID: %s", success, fail, backupUuid))), true);
        });

        return chunksToBackup.size();
    }
}

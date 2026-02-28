package com.daqem.grieflogger.model.chunk;

import com.daqem.grieflogger.GriefLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Optional;

public class ChunkManager {

    public static byte[] serializeChunk(LevelChunk chunk) throws Exception {
        CompoundTag chunkTag = new CompoundTag();
        ChunkPos pos = chunk.getPos();
        CompoundTag blocksTag = new CompoundTag();
        int blockCount = 0;

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); ++y) {
                    BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    BlockState state = chunk.getBlockState(blockPos);
                    if (!state.isAir()) {
                        String key = x + "," + y + "," + z;
                        CompoundTag blockTag = new CompoundTag();
                        blockTag.putString("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                        CompoundTag stateTag = new CompoundTag();
                        state.getValues().forEach((property, value) -> stateTag.putString(property.getName(), value.toString()));
                        if (!stateTag.isEmpty()) {
                            blockTag.put("properties", stateTag);
                        }

                        blocksTag.put(key, blockTag);
                        ++blockCount;
                    }
                }
            }
        }

        chunkTag.put("Blocks", blocksTag);
        chunkTag.putInt("BlockCount", blockCount);
        CompoundTag blockEntitiesTag = new CompoundTag();

        for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
            BlockEntity blockEntity = chunk.getBlockEntity(bePos);
            if (blockEntity != null) {
                CompoundTag beTag = blockEntity.saveWithFullMetadata(chunk.getLevel().registryAccess());
                int localX = bePos.getX() & 15;
                int localZ = bePos.getZ() & 15;
                String key = localX + "," + bePos.getY() + "," + localZ;
                blockEntitiesTag.put(key, beTag);
            }
        }

        chunkTag.put("BlockEntities", blockEntitiesTag);
        CompoundTag entitiesTag = new CompoundTag();
        int entityIndex = 0;

        for (Entity entity : chunk.getLevel().getEntities((Entity) null, new AABB((double) pos.getMinBlockX(), (double) chunk.getMinBuildHeight(), (double) pos.getMinBlockZ(), (double) (pos.getMaxBlockX() + 1), (double) chunk.getMaxBuildHeight(), (double) (pos.getMaxBlockZ() + 1)))) {
            if (!(entity instanceof Player)) {
                CompoundTag entityTag = new CompoundTag();
                if (entity.saveAsPassenger(entityTag)) {
                    entitiesTag.put("entity_" + entityIndex++, entityTag);
                }
            }
        }

        chunkTag.put("Entities", entitiesTag);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NbtIo.writeCompressed(chunkTag, baos);
        return baos.toByteArray();
    }

    public static void deserializeChunk(ServerLevel level, int chunkX, int chunkZ, byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        CompoundTag chunkTag = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap());
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        ChunkPos pos = chunk.getPos();
        int expectedBlocks = chunkTag.getInt("BlockCount");
        GriefLogger.LOGGER.info("Starting chunk restore at [{}, {}] - expecting {} blocks", chunkX, chunkZ, expectedBlocks);

        for (Entity entity : new ArrayList<>(level.getEntities((Entity) null, new AABB((double) pos.getMinBlockX(), (double) chunk.getMinBuildHeight(), (double) pos.getMinBlockZ(), (double) (pos.getMaxBlockX() + 1), (double) chunk.getMaxBuildHeight(), (double) (pos.getMaxBlockZ() + 1))))) {
            if (!(entity instanceof Player)) {
                entity.discard();
            }
        }

        int clearedBlocks = 0;

        GriefLogger.setRollbackActive(true);
        try {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); ++y) {
                        BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        BlockState currentState = chunk.getBlockState(blockPos);
                        if (!currentState.isAir()) {
                            BlockEntity blockEntity = level.getBlockEntity(blockPos);
                            if (blockEntity != null) {
                                if (blockEntity instanceof Container container) {
                                    container.clearContent();
                                }
                                level.removeBlockEntity(blockPos);
                            }
                            level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 112);
                            ++clearedBlocks;
                        }
                    }
                }
            }
        } finally {
            GriefLogger.setRollbackActive(false);
        }

        GriefLogger.LOGGER.info("Cleared {} blocks from chunk [{}, {}]", clearedBlocks, chunkX, chunkZ);
        CompoundTag blocksTag = chunkTag.getCompound("Blocks");
        Registry<Block> blockRegistry = level.registryAccess().registryOrThrow(Registries.BLOCK);
        int restoredBlocks = 0;
        int failedBlocks = 0;

        GriefLogger.setRollbackActive(true);
        try {
            for (String key : blocksTag.getAllKeys()) {
                try {
                    String[] coords = key.split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);
                    BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    CompoundTag blockTag = blocksTag.getCompound(key);
                    String blockId = blockTag.getString("block");
                    ResourceLocation blockLocation = ResourceLocation.parse(blockId);
                    Block block = blockRegistry.get(blockLocation);
                    if (block != null && block != Blocks.AIR) {
                        BlockState state = block.defaultBlockState();
                        if (blockTag.contains("properties")) {
                            CompoundTag propsTag = blockTag.getCompound("properties");

                            for (String propName : propsTag.getAllKeys()) {
                                String propValue = propsTag.getString(propName);

                                for (Property<?> property : state.getProperties()) {
                                    if (property.getName().equals(propName)) {
                                        Optional<? extends Comparable<?>> optional = property.getValue(propValue);
                                        if (optional.isPresent()) {
                                            state = state.setValue((Property) property, (Comparable) optional.get());
                                        }
                                    }
                                }
                            }
                        }

                        level.setBlock(blockPos, state, 18);
                        ++restoredBlocks;
                    } else {
                        ++failedBlocks;
                        GriefLogger.LOGGER.warn("Block not found in registry: {}", blockId);
                    }
                } catch (Exception e) {
                    ++failedBlocks;
                    GriefLogger.LOGGER.error("Failed to restore block at key {}: {}", key, e.getMessage());
                }
            }
        } finally {
            GriefLogger.setRollbackActive(false);
        }

        GriefLogger.LOGGER.info("Restored {}/{} blocks, {} failed", restoredBlocks, expectedBlocks, failedBlocks);
        CompoundTag blockEntitiesTag = chunkTag.getCompound("BlockEntities");
        int restoredBE = 0;

        for (String key : blockEntitiesTag.getAllKeys()) {
            try {
                String[] coords = key.split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                CompoundTag beTag = blockEntitiesTag.getCompound(key);
                BlockState blockState = level.getBlockState(blockPos);
                if (!blockState.isAir() && blockState.hasBlockEntity()) {
                    BlockEntity existingBE = level.getBlockEntity(blockPos);
                    if (existingBE != null) {
                        level.removeBlockEntity(blockPos);
                    }

                    BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, blockState, beTag, level.registryAccess());
                    if (blockEntity != null) {
                        level.setBlockEntity(blockEntity);
                        blockEntity.setChanged();
                        ++restoredBE;
                        GriefLogger.LOGGER.debug("Restored BlockEntity at [{}, {}, {}]", blockPos.getX(), blockPos.getY(), blockPos.getZ());
                    } else {
                        GriefLogger.LOGGER.warn("Failed to create BlockEntity at [{}, {}, {}]", blockPos.getX(), blockPos.getY(), blockPos.getZ());
                    }
                } else {
                    GriefLogger.LOGGER.warn("Block at [{}, {}, {}] cannot have BlockEntity (type: {})", blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockState.getBlock());
                }
            } catch (Exception e) {
                GriefLogger.LOGGER.error("Failed to restore block entity at key {}: {}", key, e.getMessage());
            }
        }

        GriefLogger.LOGGER.info("Restored {} block entities", restoredBE);
        CompoundTag entitiesTag = chunkTag.getCompound("Entities");
        int restoredEntities = 0;

        for (String key : entitiesTag.getAllKeys()) {
            try {
                CompoundTag entityTag = entitiesTag.getCompound(key);
                Optional<Entity> entityOptional = EntityType.create(entityTag, level);
                if (entityOptional.isPresent()) {
                    Entity entity = entityOptional.get();
                    level.addFreshEntity(entity);
                    ++restoredEntities;
                }
            } catch (Exception e) {
                GriefLogger.LOGGER.error("Failed to restore entity: {}", e.getMessage());
            }
        }

        GriefLogger.LOGGER.info("Restored {} entities", restoredEntities);
        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.getPlayers(pos, false).forEach(player -> {
            try {
                player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), (BitSet) null, (BitSet) null));
            } catch (Exception e) {
                GriefLogger.LOGGER.error("Failed to send chunk update to player: {}", e.getMessage());
            }
        });
        GriefLogger.LOGGER.info("Chunk restore complete at [{}, {}]", chunkX, chunkZ);
    }
}

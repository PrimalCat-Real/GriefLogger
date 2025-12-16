package com.daqem.grieflogger.block.coalesce;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class BlockChangeFilter {
    private BlockChangeFilter() {}

    public static final int DEFAULT_CHUNK_RADIUS = 8;

    private static final Set<String> IGNORED_BLOCK_IDS = Set.of(
            "minecraft:ice", "minecraft:frosted_ice",
            "minecraft:cave_vines", "minecraft:cave_vines_plant",
            "minecraft:brown_mushroom", "minecraft:red_mushroom",
            "minecraft:warped_fungus", "minecraft:crimson_fungus",
            "minecraft:vine", "minecraft:twisting_vines", "minecraft:weeping_vines",
            "minecraft:snow", "minecraft:snow_layer", "minecraft:powder_snow",
            "minecraft:grass", "minecraft:grass_block", "minecraft:dirt",
            "minecraft:water", "minecraft:lava", "minecraft:bubble_column",
            "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:kelp_plant", "minecraft:kelp", "minecraft:fire",
            "minecraft:bamboo", "minecraft:bamboo_sapling",
            "minecraft:sugar_cane", "minecraft:cactus",
            "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots",
            "minecraft:small_amethyst_bud",
            "minecraft:medium_amethyst_bud",
            "minecraft:large_amethyst_bud",
            "minecraft:amethyst_cluster",
            "minecraft:budding_amethyst"
    );

    public static boolean shouldLog(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState) return false;

        if (newState != null && !newState.isAir()) {
            String newId = getBlockId(newState);
            if (IGNORED_BLOCK_IDS.contains(newId)) return false;
        }

        if (oldState != null && !oldState.isAir()) {
            String oldId = getBlockId(oldState);
            if (IGNORED_BLOCK_IDS.contains(oldId)) return false;
        }

        return hasPlayerInChunkRadius(level, pos);
    }

    public static String getBlockId(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.toString();
    }

    private static boolean hasPlayerInChunkRadius(ServerLevel level, BlockPos pos) {
        ChunkPos target = new ChunkPos(pos);
        for (Player player : level.players()) {
            if (player.chunkPosition().getChessboardDistance(target) <= BlockChangeFilter.DEFAULT_CHUNK_RADIUS) return true;
        }
        return false;
    }
}

package com.daqem.grieflogger.block.coalesce;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.cache.SpreadCache;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Determines how to handle block changes based on their source.
 * Instead of blacklisting blocks, we categorize them with phantom users
 * for proper attribution and enable rollback of natural events.
 */
public final class BlockChangeFilter {
    private BlockChangeFilter() {}

    public static final int DEFAULT_CHUNK_RADIUS = 8;

    // ============================================
    // BLOCK CATEGORIES
    // ============================================

    /**
     * Fluid blocks - attributed to #water or #lava
     */
    private static final Set<Block> WATER_BLOCKS = Set.of(
            Blocks.WATER, Blocks.BUBBLE_COLUMN
    );

    private static final Set<Block> LAVA_BLOCKS = Set.of(
            Blocks.LAVA
    );

    /**
     * Fire blocks - attributed to #fire
     */
    private static final Set<Block> FIRE_BLOCKS = Set.of(
            Blocks.FIRE, Blocks.SOUL_FIRE
    );

    /**
     * Ice/Snow - can form naturally, attributed to environment
     */
    private static final Set<Block> ICE_SNOW_BLOCKS = Set.of(
            Blocks.ICE, Blocks.FROSTED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE,
            Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW
    );

    /**
     * Vine/Plant growth blocks - attributed to #vine
     */
    private static final Set<Block> VINE_BLOCKS = Set.of(
            // Vines
            Blocks.VINE, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
            Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT,
            Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT,
            Blocks.GLOW_LICHEN,
            // Kelp
            Blocks.KELP, Blocks.KELP_PLANT,
            // Grass
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.SEAGRASS, Blocks.TALL_SEAGRASS,
            // Mushrooms
            Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
            Blocks.WARPED_FUNGUS, Blocks.CRIMSON_FUNGUS,
            Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS,
            // Other growth
            Blocks.BAMBOO, Blocks.BAMBOO_SAPLING,
            Blocks.SUGAR_CANE, Blocks.CACTUS,
            Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER,
            // Amethyst
            Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER,
            // Sculk
            Blocks.SCULK, Blocks.SCULK_VEIN,
            // Moss
            Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET,
            // Dripleaf
            Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.SMALL_DRIPLEAF
    );

    /**
     * Gravity-affected blocks - attributed to #gravity
     */
    private static final Set<Block> GRAVITY_BLOCKS = Set.of(
            Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
            Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
            Blocks.DRAGON_EGG, Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL
    );

    /**
     * Crop blocks - typically player-placed but can tick-grow
     * These are logged normally when placed by player
     */
    private static final Set<Block> CROP_BLOCKS = Set.of(
            Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
            Blocks.MELON_STEM, Blocks.PUMPKIN_STEM,
            Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM,
            Blocks.SWEET_BERRY_BUSH, Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP
    );

    // ============================================
    // FILTER RESULT
    // ============================================

    /**
     * Result of the filter check containing whether to log and the phantom user to use.
     */
    public record FilterResult(boolean shouldLog, @Nullable String phantomUser) {
        public static final FilterResult SKIP = new FilterResult(false, null);
        public static final FilterResult LOG_NORMAL = new FilterResult(true, null);

        public static FilterResult withPhantom(String phantomUser) {
            return new FilterResult(true, phantomUser);
        }
    }

    // ============================================
    // MAIN FILTER LOGIC
    // ============================================

    /**
     * Determines if a block change should be logged and with what attribution.
     *
     * @param level The server level
     * @param pos The block position
     * @param oldState Previous block state
     * @param newState New block state
     * @param isPlayerCaused Whether a player directly caused this change
     * @return FilterResult containing logging decision and phantom user
     */
    public static FilterResult evaluateChange(ServerLevel level, BlockPos pos,
                                               BlockState oldState, BlockState newState,
                                               boolean isPlayerCaused) {
        // If same state, skip
        if (oldState == newState) {
            return FilterResult.SKIP;
        }

        // Player-caused changes are always logged normally
        // Also invalidate spread cache since player action takes precedence
        if (isPlayerCaused) {
            SpreadCache.invalidate(pos, level.dimension().location().toString());
            return FilterResult.LOG_NORMAL;
        }

        // Check proximity to players (optimization)
        if (!hasPlayerInChunkRadius(level, pos)) {
            return FilterResult.SKIP;
        }

        // Check spread cache for deduplication of natural events
        Block targetBlock = newState != null && !newState.isAir() ? newState.getBlock() :
                           (oldState != null ? oldState.getBlock() : null);

        if (targetBlock != null && isNaturalSpreadBlock(targetBlock)) {
            String worldId = level.dimension().location().toString();
            if (SpreadCache.isDuplicate(pos, worldId, targetBlock)) {
                return FilterResult.SKIP;  // Duplicate natural event
            }
        }

        // Determine phantom user based on block type
        return categorizeNaturalChange(oldState, newState);
    }

    /**
     * Check if a block type is subject to natural spread deduplication.
     */
    private static boolean isNaturalSpreadBlock(Block block) {
        return WATER_BLOCKS.contains(block) ||
               LAVA_BLOCKS.contains(block) ||
               FIRE_BLOCKS.contains(block) ||
               VINE_BLOCKS.contains(block);
    }

    /**
     * Categorize a natural (non-player) block change and determine phantom user.
     */
    private static FilterResult categorizeNaturalChange(BlockState oldState, BlockState newState) {
        Block oldBlock = oldState != null ? oldState.getBlock() : null;
        Block newBlock = newState != null && !newState.isAir() ? newState.getBlock() : null;

        // Water flow
        if (shouldLogWater()) {
            if ((newBlock != null && WATER_BLOCKS.contains(newBlock)) ||
                (oldBlock != null && WATER_BLOCKS.contains(oldBlock))) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_WATER);
            }
        }

        // Lava flow
        if (shouldLogLava()) {
            if ((newBlock != null && LAVA_BLOCKS.contains(newBlock)) ||
                (oldBlock != null && LAVA_BLOCKS.contains(oldBlock))) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_LAVA);
            }
        }

        // Fire spread
        if (shouldLogFire()) {
            if ((newBlock != null && FIRE_BLOCKS.contains(newBlock)) ||
                (oldBlock != null && FIRE_BLOCKS.contains(oldBlock))) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_FIRE);
            }
        }

        // Leaf decay
        if (shouldLogLeafDecay() && oldBlock != null) {
            if (oldState.is(BlockTags.LEAVES)) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_DECAY);
            }
        }

        // Vine/plant growth
        if (shouldLogVineGrowth()) {
            if ((newBlock != null && VINE_BLOCKS.contains(newBlock)) ||
                (oldBlock != null && VINE_BLOCKS.contains(oldBlock))) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_VINE);
            }
        }

        // Gravity blocks
        if (shouldLogGravity()) {
            if ((newBlock != null && GRAVITY_BLOCKS.contains(newBlock)) ||
                (oldBlock != null && GRAVITY_BLOCKS.contains(oldBlock))) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_GRAVITY);
            }
        }

        // Ice/Snow natural changes - attribute to environment (water for ice)
        if (shouldLogWater()) {
            if ((newBlock != null && ICE_SNOW_BLOCKS.contains(newBlock)) ||
                (oldBlock != null && ICE_SNOW_BLOCKS.contains(oldBlock))) {
                return FilterResult.withPhantom(GriefLogger.PHANTOM_WATER);
            }
        }

        // Crop growth state changes - skip (block state changes, not block changes)
        if (oldBlock != null && newBlock != null && oldBlock == newBlock) {
            if (CROP_BLOCKS.contains(oldBlock)) {
                return FilterResult.SKIP;
            }
        }

        // Unknown natural change - log with unknown phantom
        return FilterResult.withPhantom(GriefLogger.PHANTOM_UNKNOWN);
    }

    // ============================================
    // LEGACY COMPATIBILITY
    // ============================================

    /**
     * Legacy method for backwards compatibility.
     * @deprecated Use {@link #evaluateChange} instead
     */
    @Deprecated
    public static boolean shouldLog(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        FilterResult result = evaluateChange(level, pos, oldState, newState, false);
        return result.shouldLog();
    }

    // ============================================
    // CONFIG HELPERS
    // ============================================

    private static boolean shouldLogWater() {
        return GriefLoggerConfig.logWaterFlow.get();
    }

    private static boolean shouldLogLava() {
        return GriefLoggerConfig.logLavaFlow.get();
    }

    private static boolean shouldLogFire() {
        return GriefLoggerConfig.logFireSpread.get();
    }

    private static boolean shouldLogLeafDecay() {
        return GriefLoggerConfig.logLeafDecay.get();
    }

    private static boolean shouldLogVineGrowth() {
        return GriefLoggerConfig.logVineGrowth.get();
    }

    private static boolean shouldLogGravity() {
        return GriefLoggerConfig.logGravity.get();
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    public static String getBlockId(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.toString();
    }

    private static boolean hasPlayerInChunkRadius(ServerLevel level, BlockPos pos) {
        ChunkPos target = new ChunkPos(pos);
        for (Player player : level.players()) {
            if (player.chunkPosition().getChessboardDistance(target) <= DEFAULT_CHUNK_RADIUS) {
                return true;
            }
        }
        return false;
    }
}

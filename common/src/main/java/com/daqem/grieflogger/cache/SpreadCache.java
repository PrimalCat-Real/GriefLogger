package com.daqem.grieflogger.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for tracking natural spread events (water flow, plant growth, etc.)
 * to prevent duplicate logging of the same event within a time window.
 *
 * Based on CoreProtect's spreadCache implementation.
 */
public final class SpreadCache {

    private SpreadCache() {}

    /**
     * Cache entry containing timestamp and material type.
     */
    public record CacheEntry(long timestamp, String blockId) {}

    /**
     * Main cache: position key -> cache entry
     * Key format: "worldId:x:y:z"
     */
    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>(16, 0.75f, 2);

    /**
     * Default TTL for spread cache entries (30 minutes).
     */
    private static final long DEFAULT_TTL_MS = 30 * 60 * 1000L;

    /**
     * Check if this block change is a duplicate (already cached).
     * If not a duplicate, adds it to the cache.
     *
     * @param pos Block position
     * @param worldId World/dimension identifier
     * @param block The block being placed/broken
     * @return true if this is a duplicate (should skip logging), false if new
     */
    public static boolean isDuplicate(BlockPos pos, String worldId, Block block) {
        String key = makeKey(worldId, pos);
        String blockId = block.builtInRegistryHolder().key().location().toString();
        long now = System.currentTimeMillis();

        CacheEntry existing = cache.get(key);

        if (existing != null && existing.blockId().equals(blockId)) {
            cache.put(key, new CacheEntry(now, blockId));
            return true;
        }

        cache.put(key, new CacheEntry(now, blockId));
        return false;
    }

    /**
     * Check if position is cached without adding/updating.
     *
     * @param pos Block position
     * @param worldId World/dimension identifier
     * @return true if position is in cache
     */
    public static boolean isCached(BlockPos pos, String worldId) {
        String key = makeKey(worldId, pos);
        return cache.containsKey(key);
    }

    /**
     * Remove entry from cache.
     *
     * @param pos Block position
     * @param worldId World/dimension identifier
     */
    public static void invalidate(BlockPos pos, String worldId) {
        String key = makeKey(worldId, pos);
        cache.remove(key);
    }

    /**
     * Clean up entries older than the TTL.
     * Should be called periodically (e.g., every second).
     *
     * @return Number of entries removed
     */
    public static int cleanup() {
        return cleanup(DEFAULT_TTL_MS);
    }

    /**
     * Clean up entries older than the specified TTL.
     *
     * @param ttlMs Time-to-live in milliseconds
     * @return Number of entries removed
     */
    public static int cleanup(long ttlMs) {
        long cutoff = System.currentTimeMillis() - ttlMs;
        int removed = 0;

        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().timestamp() < cutoff) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    /**
     * Get current cache size.
     */
    public static int size() {
        return cache.size();
    }

    /**
     * Clear all cache entries.
     */
    public static void clear() {
        cache.clear();
    }

    /**
     * Create cache key from world ID and position.
     */
    private static String makeKey(String worldId, BlockPos pos) {
        return worldId + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }
}

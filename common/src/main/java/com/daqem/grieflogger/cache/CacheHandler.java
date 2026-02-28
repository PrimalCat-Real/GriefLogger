package com.daqem.grieflogger.cache;

import com.daqem.grieflogger.GriefLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background thread that manages cache cleanup for various caches.
 * Based on CoreProtect's CacheHandler implementation.
 *
 * Manages:
 * - SpreadCache: Natural event deduplication (30 min TTL)
 * - LookupCache: Recent lookup results (30 sec TTL)
 * - BreakCache: Block break tracking for rollback correlation
 */
public class CacheHandler implements Runnable {

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static Thread cacheThread = null;

    /**
     * Lookup cache for recent queries (30 second TTL).
     * Used to track source of water/lava flow.
     */
    public static final Map<String, Object[]> lookupCache = Collections.synchronizedMap(new HashMap<>());

    /**
     * Break cache for correlating block breaks with subsequent events.
     * Used to attribute chain reactions (TNT, fire spread, etc.)
     */
    public static final Map<String, Object[]> breakCache = Collections.synchronizedMap(new HashMap<>());

    /**
     * Piston cache for tracking piston movements (15 min TTL).
     */
    public static final ConcurrentHashMap<String, Object[]> pistonCache = new ConcurrentHashMap<>(16, 0.75f, 2);

    /**
     * Entity cache for tracking entity-caused changes (60 min TTL).
     */
    public static final ConcurrentHashMap<String, Object[]> entityCache = new ConcurrentHashMap<>(16, 0.75f, 2);

    private static final int LOOKUP_TTL = 30;
    private static final int BREAK_TTL = 30;
    private static final int PISTON_TTL = 15 * 60;  
    private static final int ENTITY_TTL = 60 * 60;  

    @Override
    public void run() {
        GriefLogger.LOGGER.info("CacheHandler thread started");

        while (running.get()) {
            try {
                for (int cacheId = 0; cacheId < 5; cacheId++) {
                    if (!running.get()) break;

                    Thread.sleep(1000);  

                    int ttlSeconds;
                    Map<?, ?> cacheMap;

                    switch (cacheId) {
                        case 0 -> {
                            int removed = SpreadCache.cleanup();
                            if (removed > 0) {
                                GriefLogger.LOGGER.debug("SpreadCache cleanup: removed {} entries", removed);
                            }
                            continue;
                        }
                        case 1 -> {
                            cacheMap = lookupCache;
                            ttlSeconds = LOOKUP_TTL;
                        }
                        case 2 -> {
                            cacheMap = breakCache;
                            ttlSeconds = BREAK_TTL;
                        }
                        case 3 -> {
                            cacheMap = pistonCache;
                            ttlSeconds = PISTON_TTL;
                        }
                        case 4 -> {
                            cacheMap = entityCache;
                            ttlSeconds = ENTITY_TTL;
                        }
                        default -> {
                            continue;
                        }
                    }

                    cleanupCache(cacheMap, ttlSeconds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                GriefLogger.LOGGER.error("Error in CacheHandler", e);
            }
        }

        GriefLogger.LOGGER.info("CacheHandler thread stopped");
    }

    /**
     * Clean up a cache map by removing entries older than TTL.
     */
    @SuppressWarnings("unchecked")
    private void cleanupCache(Map<?, ?> cache, int ttlSeconds) {
        long cutoffTime = (System.currentTimeMillis() / 1000L) - ttlSeconds;

        synchronized (cache) {
            Iterator<? extends Map.Entry<?, ?>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                try {
                    Map.Entry<?, ?> entry = iterator.next();
                    Object[] data = (Object[]) entry.getValue();
                    if (data == null || data.length == 0) {
                        iterator.remove();
                        continue;
                    }

                    long timestamp;
                    if (data[0] instanceof Long) {
                        timestamp = (long) data[0] / 1000L;  
                    } else if (data[0] instanceof Integer) {
                        timestamp = (int) data[0];
                    } else {
                        continue;
                    }

                    if (timestamp < cutoffTime) {
                        iterator.remove();
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    /**
     * Start the cache handler thread.
     */
    public static void start() {
        if (running.compareAndSet(false, true)) {
            cacheThread = new Thread(new CacheHandler(), "GriefLogger-CacheHandler");
            cacheThread.setDaemon(true);
            cacheThread.start();
        }
    }

    /**
     * Stop the cache handler thread.
     */
    public static void stop() {
        running.set(false);
        if (cacheThread != null) {
            cacheThread.interrupt();
            try {
                cacheThread.join(5000);  
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cacheThread = null;
        }
    }

    /**
     * Check if the cache handler is running.
     */
    public static boolean isRunning() {
        return running.get() && cacheThread != null && cacheThread.isAlive();
    }


    /**
     * Add entry to lookup cache.
     *
     * @param key Position key
     * @param data Data to cache (first element should be timestamp)
     */
    public static void putLookup(String key, Object[] data) {
        lookupCache.put(key, data);
    }

    /**
     * Get entry from lookup cache.
     */
    public static Object[] getLookup(String key) {
        return lookupCache.get(key);
    }

    /**
     * Add entry to break cache.
     */
    public static void putBreak(String key, Object[] data) {
        breakCache.put(key, data);
    }

    /**
     * Get entry from break cache.
     */
    public static Object[] getBreak(String key) {
        return breakCache.get(key);
    }

    /**
     * Add entry to piston cache.
     */
    public static void putPiston(String key, Object[] data) {
        pistonCache.put(key, data);
    }

    /**
     * Get entry from piston cache.
     */
    public static Object[] getPiston(String key) {
        return pistonCache.get(key);
    }

    /**
     * Create position key for caching.
     */
    public static String makeKey(String worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}

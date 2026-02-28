package com.daqem.grieflogger.model.home;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.chunk.ChunkManager;
import com.daqem.grieflogger.thread.ThreadManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.*;

/**
 * Tracks player positions, detects frequently visited areas ("homes"),
 * and auto-backs up chunks around detected homes.
 *
 * All heavy work (clustering, DB writes) runs async via ThreadManager.
 * Only position sampling touches the server thread (read-only, very cheap).
 */
public class PlayerHomeTracker {

    private static final PlayerHomeTracker INSTANCE = new PlayerHomeTracker();

    // UUID -> list of sampled chunk positions (hot points)
    private final Map<UUID, List<ChunkPos>> hotPoints = new ConcurrentHashMap<>();

    // UUID -> world name for current hot points
    private final Map<UUID, String> playerWorlds = new ConcurrentHashMap<>();

    // UUID -> (homeId -> consecutive stay checks)
    private final Map<UUID, Map<Integer, Integer>> stayCounters = new ConcurrentHashMap<>();

    // Delayed quit processing
    private final Map<UUID, ScheduledFuture<?>> quitTimers = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GriefLogger-HomeQuit");
        t.setDaemon(true);
        return t;
    });

    private long tickCounter = 0;

    private PlayerHomeTracker() {}

    public static PlayerHomeTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Called from TickEvents every server tick.
     * Extremely lightweight — just increments counter and checks intervals.
     */
    public void tick(MinecraftServer server) {
        if (!GriefLoggerConfig.homesEnabled.get()) return;

        tickCounter++;

        int trackInterval = GriefLoggerConfig.homeTrackInterval.get();
        int clusterInterval = GriefLoggerConfig.homeClusterInterval.get();

        // Sample positions (every ~1 minute)
        if (tickCounter % trackInterval == 0) {
            samplePositions(server);
        }

        // Cluster & detect homes (every ~10 minutes)
        if (tickCounter % clusterInterval == 0) {
            clusterAll();
        }

        // Check auto-backup at each sampling tick (reuses the same interval)
        if (tickCounter % trackInterval == 0) {
            checkAutoBackups(server);
        }
    }

    /**
     * Sample all online player positions. Runs on server thread but is read-only and O(n).
     */
    private void samplePositions(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            ChunkPos chunkPos = player.chunkPosition();
            String world = player.level().dimension().location().toString();

            hotPoints.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(chunkPos);
            playerWorlds.put(uuid, world);
        }
    }

    /**
     * Cluster all players' hot points async and upsert detected homes.
     */
    private void clusterAll() {
        // Snapshot and clear hot points for all players
        Map<UUID, List<ChunkPos>> snapshot = new HashMap<>();
        Map<UUID, String> worldSnapshot = new HashMap<>();

        for (Map.Entry<UUID, List<ChunkPos>> entry : hotPoints.entrySet()) {
            UUID uuid = entry.getKey();
            List<ChunkPos> points = new ArrayList<>(entry.getValue());
            if (!points.isEmpty()) {
                snapshot.put(uuid, points);
                worldSnapshot.put(uuid, playerWorlds.get(uuid));
                entry.getValue().clear();
            }
        }

        if (snapshot.isEmpty()) return;

        // Do clustering and DB writes async
        ThreadManager.execute(() -> {
            int minPoints = GriefLoggerConfig.homeMinClusterPoints.get();

            for (Map.Entry<UUID, List<ChunkPos>> entry : snapshot.entrySet()) {
                UUID uuid = entry.getKey();
                List<ChunkPos> points = entry.getValue();
                String world = worldSnapshot.get(uuid);

                if (world == null || points.size() < minPoints) continue;

                List<HomeCluster.Cluster> clusters = HomeCluster.cluster(points);

                for (HomeCluster.Cluster cluster : clusters) {
                    if (cluster.size() >= minPoints) {
                        Services.PLAYER_HOME.upsertHome(
                                uuid.toString(),
                                world,
                                cluster.center().x,
                                cluster.center().z,
                                cluster.radius()
                        );
                        GriefLogger.LOGGER.debug("Detected home cluster for {} at [{}, {}] r={} ({} points)",
                                uuid, cluster.center().x, cluster.center().z, cluster.radius(), cluster.size());
                    }
                }
            }
        });
    }

    /**
     * Check if any online player has been at a home long enough to trigger backup.
     * Runs the stay-check logic on server thread (cheap), backup itself is async.
     */
    private void checkAutoBackups(MinecraftServer server) {
        int stayThreshold = GriefLoggerConfig.homeStayChecks.get();
        int cooldownSec = GriefLoggerConfig.homeBackupCooldown.get();
        long now = System.currentTimeMillis();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            ChunkPos playerChunk = player.chunkPosition();
            String world = player.level().dimension().location().toString();

            // Async: fetch homes from DB and check stay
            ThreadManager.execute(() -> {
                List<Map<String, Object>> homes = Services.PLAYER_HOME.getHomes(
                        uuid.toString(), GriefLoggerConfig.homeMinClusterPoints.get());

                for (Map<String, Object> home : homes) {
                    int homeId = (Integer) home.get("id");
                    String homeWorld = (String) home.get("world");
                    int centerX = (Integer) home.get("center_x");
                    int centerZ = (Integer) home.get("center_z");
                    int radius = (Integer) home.get("radius");
                    long lastBackup = home.get("last_backup_time") != null ? (Long) home.get("last_backup_time") : 0;

                    // Check if player is within the home area
                    if (!world.equals(homeWorld)) continue;
                    if (Math.abs(playerChunk.x - centerX) > radius + 1
                            || Math.abs(playerChunk.z - centerZ) > radius + 1) {
                        // Player left this home — reset stay counter
                        stayCounters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(homeId, 0);
                        continue;
                    }

                    // Increment stay counter
                    Map<Integer, Integer> playerStay = stayCounters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
                    int stays = playerStay.getOrDefault(homeId, 0) + 1;
                    playerStay.put(homeId, stays);

                    if (stays < stayThreshold) continue;

                    // Check cooldown
                    if (now - lastBackup < cooldownSec * 1000L) continue;

                    // Trigger auto-backup!
                    GriefLogger.LOGGER.info("Auto-backup triggered for {} at home [{}, {}] r={}",
                            uuid, centerX, centerZ, radius);
                    playerStay.put(homeId, 0); // reset counter

                    performAutoBackup(server, uuid, player.getName().getString(), homeWorld, centerX, centerZ, radius, homeId);
                }
            });
        }
    }

    /**
     * Perform the actual chunk backup for a detected home.
     */
    private void performAutoBackup(MinecraftServer server, UUID playerUuid, String playerName, String worldName,
                                   int centerX, int centerZ, int radius, int homeId) {
        String backupUuid = UUID.randomUUID().toString();
        long time = System.currentTimeMillis();

        // Collect chunks to backup
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunks.add(new ChunkPos(centerX + x, centerZ + z));
            }
        }

        // Serialize on server thread, save to DB async
        server.execute(() -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(worldName)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) return;

            final ServerLevel targetLevel = level;
            List<byte[]> serializedChunks = new ArrayList<>();
            List<ChunkPos> successChunks = new ArrayList<>();

            for (ChunkPos pos : chunks) {
                try {
                    net.minecraft.world.level.chunk.LevelChunk chunk = targetLevel.getChunk(pos.x, pos.z);
                    byte[] data = ChunkManager.serializeChunk(chunk);
                    serializedChunks.add(data);
                    successChunks.add(pos);
                } catch (Exception e) {
                    GriefLogger.LOGGER.error("Auto-backup: failed to serialize chunk [{}, {}]", pos.x, pos.z, e);
                }
            }

            // Write to DB async
            ThreadManager.execute(() -> {
                int maxBackups = GriefLoggerConfig.homeMaxBackupsPerHome.get();

                for (int i = 0; i < successChunks.size(); i++) {
                    ChunkPos pos = successChunks.get(i);
                    Services.CHUNK_BACKUP.insertBackup(
                            backupUuid, time,
                            playerUuid.toString(), playerName,
                            centerX * 16, centerZ * 16, radius, worldName,
                            pos.x, pos.z, serializedChunks.get(i)
                    );
                }

                // Update last backup time
                Services.PLAYER_HOME.updateLastBackupTime(homeId, time);

                // Auto-cleanup: count unique backup UUIDs for this home area
                // and delete oldest if exceeding max
                cleanupOldBackups(worldName, centerX, centerZ, radius, maxBackups);

                GriefLogger.LOGGER.info("Auto-backup complete: {} chunks saved with UUID {}",
                        successChunks.size(), backupUuid);
            });
        });
    }

    /**
     * Delete oldest backups if a home area has too many.
     */
    private void cleanupOldBackups(String world, int centerX, int centerZ, int radius, int maxBackups) {
        // Get all backup UUIDs for chunks in this home area, grouped and ordered
        List<Map<String, Object>> backups = Services.CHUNK_BACKUP.getBackupsList();

        // Filter to backups in this area (approximate match)
        List<String> homeBackupUuids = new ArrayList<>();
        for (Map<String, Object> b : backups) {
            String bWorld = (String) b.get("world");
            if (!world.equals(bWorld)) continue;

            int bPlayerX = (Integer) b.get("player_x");
            int bPlayerZ = (Integer) b.get("player_z");
            // Check if this backup's center is near this home
            if (Math.abs(bPlayerX - centerX * 16) <= (radius + 2) * 16
                    && Math.abs(bPlayerZ - centerZ * 16) <= (radius + 2) * 16) {
                homeBackupUuids.add((String) b.get("backup_uuid"));
            }
        }

        // Delete the oldest ones if over limit
        while (homeBackupUuids.size() > maxBackups) {
            String oldestUuid = homeBackupUuids.remove(homeBackupUuids.size() - 1); // list is desc by time
            com.daqem.grieflogger.database.orm.query.Query.delete("chunk_backups")
                    .whereEq("backup_uuid", oldestUuid)
                    .execute(GriefLogger.getDatabase());
            GriefLogger.LOGGER.info("Auto-cleanup: deleted old backup {}", oldestUuid);
        }
    }

    /**
     * Called when a player joins — cancel any pending quit timer.
     */
    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ScheduledFuture<?> timer = quitTimers.remove(uuid);
        if (timer != null) {
            timer.cancel(false);
            GriefLogger.LOGGER.debug("Player {} rejoined, cancelled quit processing", uuid);
        }
    }

    /**
     * Called when a player quits — schedule delayed processing.
     */
    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int delaySec = GriefLoggerConfig.homeQuitDelay.get();

        ScheduledFuture<?> timer = SCHEDULER.schedule(() -> {
            quitTimers.remove(uuid);

            // Process remaining hot points
            List<ChunkPos> points = hotPoints.remove(uuid);
            String world = playerWorlds.remove(uuid);

            if (points != null && !points.isEmpty() && world != null) {
                int minPoints = GriefLoggerConfig.homeMinClusterPoints.get();
                List<HomeCluster.Cluster> clusters = HomeCluster.cluster(points);

                for (HomeCluster.Cluster cluster : clusters) {
                    if (cluster.size() >= minPoints) {
                        Services.PLAYER_HOME.upsertHome(
                                uuid.toString(), world,
                                cluster.center().x, cluster.center().z,
                                cluster.radius()
                        );
                    }
                }
            }

            // Clean up stay counters
            stayCounters.remove(uuid);
            GriefLogger.LOGGER.debug("Processed quit data for player {}", uuid);
        }, delaySec, TimeUnit.SECONDS);

        quitTimers.put(uuid, timer);
    }
}

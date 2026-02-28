package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerHomeRepository extends Repository {

    private final Database database;

    public PlayerHomeRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("player_homes")
                .id("id")
                .string("player_uuid", 36, false)
                .string("world", 255, false)
                .integer("center_x", false)
                .integer("center_z", false)
                .integer("radius", false)
                .integer("visits", false)
                .bigint("last_backup_time", true)
                .bigint("created_time", false)
                .index("idx_player_homes_uuid", "player_uuid")
                .index("idx_player_homes_coords", "world", "center_x", "center_z")
                .build(database);
    }

    /**
     * Find an existing home near the given center position.
     * A home is "near" if it's within maxDist chunks of the center.
     */
    public List<Map<String, Object>> findNearbyHome(String playerUuid, String world, int centerX, int centerZ, int maxDist) {
        return Query.select("player_homes")
                .columns("id", "center_x", "center_z", "radius", "visits", "last_backup_time")
                .whereEq("player_uuid", playerUuid)
                .whereEq("world", world)
                .whereRaw("ABS(center_x - ?) <= ? AND ABS(center_z - ?) <= ?",
                        centerX, maxDist, centerZ, maxDist)
                .limit(1)
                .execute(database, rs -> {
                    Map<String, Object> map = new HashMap<>();
                    try {
                        map.put("id", rs.getInt("id"));
                        map.put("center_x", rs.getInt("center_x"));
                        map.put("center_z", rs.getInt("center_z"));
                        map.put("radius", rs.getInt("radius"));
                        map.put("visits", rs.getInt("visits"));
                        map.put("last_backup_time", rs.getLong("last_backup_time"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return map;
                });
    }

    /**
     * Insert a new home or update visits/radius if one already exists nearby.
     */
    public void upsertHome(String playerUuid, String world, int centerX, int centerZ, int radius) {
        List<Map<String, Object>> existing = findNearbyHome(playerUuid, world, centerX, centerZ, 3);

        if (!existing.isEmpty()) {
            Map<String, Object> home = existing.get(0);
            int homeId = (Integer) home.get("id");
            int currentVisits = (Integer) home.get("visits");
            int currentRadius = (Integer) home.get("radius");
            int newRadius = Math.max(currentRadius, radius);

            Query.update("player_homes")
                    .set("visits", currentVisits + 1)
                    .set("radius", newRadius)
                    .set("center_x", centerX)
                    .set("center_z", centerZ)
                    .whereEq("id", homeId)
                    .execute(database);
        } else {
            Query.insert("player_homes")
                    .value("player_uuid", playerUuid)
                    .value("world", world)
                    .value("center_x", centerX)
                    .value("center_z", centerZ)
                    .value("radius", radius)
                    .value("visits", 1)
                    .value("last_backup_time", null)
                    .value("created_time", System.currentTimeMillis())
                    .execute(database);
        }
    }

    /**
     * Get all confirmed homes for a player (visits >= minVisits).
     */
    public List<Map<String, Object>> getHomes(String playerUuid, int minVisits) {
        return Query.select("player_homes")
                .columns("id", "world", "center_x", "center_z", "radius", "visits", "last_backup_time")
                .whereEq("player_uuid", playerUuid)
                .where("visits", ">=", minVisits)
                .execute(database, rs -> {
                    Map<String, Object> map = new HashMap<>();
                    try {
                        map.put("id", rs.getInt("id"));
                        map.put("world", rs.getString("world"));
                        map.put("center_x", rs.getInt("center_x"));
                        map.put("center_z", rs.getInt("center_z"));
                        map.put("radius", rs.getInt("radius"));
                        map.put("visits", rs.getInt("visits"));
                        map.put("last_backup_time", rs.getLong("last_backup_time"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return map;
                });
    }

    /**
     * Update last backup time for a home.
     */
    public void updateLastBackupTime(int homeId, long time) {
        Query.update("player_homes")
                .set("last_backup_time", time)
                .whereEq("id", homeId)
                .execute(database);
    }

    /**
     * Get all homes (for listing).
     */
    public List<Map<String, Object>> getAllHomes() {
        return Query.select("player_homes")
                .columns("id", "player_uuid", "world", "center_x", "center_z", "radius", "visits", "last_backup_time")
                .orderByDesc("visits")
                .execute(database, rs -> {
                    Map<String, Object> map = new HashMap<>();
                    try {
                        map.put("id", rs.getInt("id"));
                        map.put("player_uuid", rs.getString("player_uuid"));
                        map.put("world", rs.getString("world"));
                        map.put("center_x", rs.getInt("center_x"));
                        map.put("center_z", rs.getInt("center_z"));
                        map.put("radius", rs.getInt("radius"));
                        map.put("visits", rs.getInt("visits"));
                        map.put("last_backup_time", rs.getLong("last_backup_time"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return map;
                });
    }
}

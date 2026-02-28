package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkBackupRepository extends Repository {

    private final Database database;

    public ChunkBackupRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("chunk_backups")
                .id("id")
                .string("backup_uuid", 36, false)
                .bigint("time", false)
                .string("player_uuid", 36, true)
                .string("player_name", 36, true)
                .integer("player_x", true)
                .integer("player_z", true)
                .integer("radius", true)
                .string("world", 255, false)
                .integer("chunk_x", false)
                .integer("chunk_z", false)
                .blob("chunk_data", false)
                .index("idx_chunk_backup_uuid", "backup_uuid")
                .index("idx_chunk_backup_time", "time")
                .index("idx_chunk_backup_world_coords", "world", "chunk_x", "chunk_z")
                .build(database);
    }

    public void insertBackup(String backupUuid, long time, String playerUuid, String playerName,
                             int playerX, int playerZ, int radius, String world,
                             int chunkX, int chunkZ, byte[] chunkData) {
        Query.insert("chunk_backups")
                .value("backup_uuid", backupUuid)
                .value("time", time)
                .value("player_uuid", playerUuid)
                .value("player_name", playerName)
                .value("player_x", playerX)
                .value("player_z", playerZ)
                .value("radius", radius)
                .value("world", world)
                .value("chunk_x", chunkX)
                .value("chunk_z", chunkZ)
                .value("chunk_data", chunkData)
                .queue(database);
    }

    public List<Map<String, Object>> getBackupsList() {
        return Query.select("chunk_backups")
                .columns("backup_uuid", "MIN(time) as time", "player_uuid", "player_name",
                         "player_x", "player_z", "radius", "world", "COUNT(*) as chunks")
                .groupBy("backup_uuid")
                .orderByDesc("time")
                .execute(database, rs -> {
                    Map<String, Object> map = new HashMap<>();
                    try {
                        map.put("backup_uuid", rs.getString("backup_uuid"));
                        map.put("time", rs.getLong("time"));
                        map.put("player_uuid", rs.getString("player_uuid"));
                        map.put("player_name", rs.getString("player_name"));
                        map.put("player_x", rs.getInt("player_x"));
                        map.put("player_z", rs.getInt("player_z"));
                        map.put("radius", rs.getInt("radius"));
                        map.put("world", rs.getString("world"));
                        map.put("chunks", rs.getInt("chunks"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return map;
                });
    }

    public List<Map<String, Object>> getBackupData(String backupUuid) {
        return Query.select("chunk_backups")
                .columns("chunk_x", "chunk_z", "chunk_data", "world")
                .whereEq("backup_uuid", backupUuid)
                .execute(database, rs -> {
                    Map<String, Object> map = new HashMap<>();
                    try {
                        map.put("chunk_x", rs.getInt("chunk_x"));
                        map.put("chunk_z", rs.getInt("chunk_z"));
                        map.put("chunk_data", rs.getBytes("chunk_data"));
                        map.put("world", rs.getString("world"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return map;
                });
    }
}

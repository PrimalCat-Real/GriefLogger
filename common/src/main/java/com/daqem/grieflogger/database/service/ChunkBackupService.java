package com.daqem.grieflogger.database.service;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.repository.ChunkBackupRepository;

import java.util.List;
import java.util.Map;

public class ChunkBackupService {

    private final Database database;
    private final ChunkBackupRepository repository;

    public ChunkBackupService(Database database) {
        this.database = database;
        this.repository = new ChunkBackupRepository(database);
    }

    public void createTable() {
        repository.createTable();
    }

    public void insertBackup(String backupUuid, long time, String playerUuid, String playerName, int playerX, int playerZ, int radius, String world, int chunkX, int chunkZ, byte[] chunkData) {
        repository.insertBackup(backupUuid, time, playerUuid, playerName, playerX, playerZ, radius, world, chunkX, chunkZ, chunkData);
    }

    public List<Map<String, Object>> getBackupsList() {
        return repository.getBackupsList();
    }

    public List<Map<String, Object>> getBackupData(String backupUuid) {
        return repository.getBackupData(backupUuid);
    }
}

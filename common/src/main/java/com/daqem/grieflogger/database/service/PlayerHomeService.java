package com.daqem.grieflogger.database.service;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.repository.PlayerHomeRepository;

import java.util.List;
import java.util.Map;

public class PlayerHomeService {

    private final Database database;
    private final PlayerHomeRepository repository;

    public PlayerHomeService(Database database) {
        this.database = database;
        this.repository = new PlayerHomeRepository(database);
    }

    public void createTable() {
        repository.createTable();
    }

    public void upsertHome(String playerUuid, String world, int centerX, int centerZ, int radius) {
        repository.upsertHome(playerUuid, world, centerX, centerZ, radius);
    }

    public List<Map<String, Object>> getHomes(String playerUuid, int minVisits) {
        return repository.getHomes(playerUuid, minVisits);
    }

    public void updateLastBackupTime(int homeId, long time) {
        repository.updateLastBackupTime(homeId, time);
    }

    public List<Map<String, Object>> getAllHomes() {
        return repository.getAllHomes();
    }
}

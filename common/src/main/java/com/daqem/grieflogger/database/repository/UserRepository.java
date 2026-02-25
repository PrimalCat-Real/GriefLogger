package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class UserRepository extends Repository {

    private final Database database;

    public UserRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("users")
                .id("id")
                .string("name", 16, false)
                .string("uuid", 36, true, true)
                .build(database);
    }

    public void insertOrUpdateName(String name, String uuid) {
        // This needs special handling due to ON CONFLICT/ON DUPLICATE KEY
        String query = Dialect.current() == Dialect.MYSQL
                ? "INSERT INTO users(name, uuid) VALUES(?, ?) ON DUPLICATE KEY UPDATE name = ?"
                : "INSERT INTO users(name, uuid) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET name = ?";

        try {
            PreparedStatement stmt = database.prepareStatement(query);
            stmt.setString(1, name);
            stmt.setString(2, uuid);
            stmt.setString(3, name);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert username into database", e);
        }
    }

    public void insertNonPlayer(String name) {
        Query.insert("users")
                .value("name", name)
                .ignore()
                .queue(database);
    }

    public Map<Integer, String> getAllUsernames() {
        Map<Integer, String> usernames = new HashMap<>();
        Query.select("users")
                .columns("id", "name")
                .execute(database, rs -> {
                    try {
                        usernames.put(rs.getInt("id"), rs.getString("name"));
                    } catch (SQLException e) {
                        GriefLogger.LOGGER.error("Failed to read username", e);
                    }
                    return null;
                });
        return usernames;
    }
}

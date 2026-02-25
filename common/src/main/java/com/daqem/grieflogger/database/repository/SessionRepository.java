package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.filter.*;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.query.SelectBuilder;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;
import com.daqem.grieflogger.model.history.SessionHistory;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.List;

public class SessionRepository extends Repository {

    private final Database database;

    public SessionRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("sessions")
                .bigint("time")
                .integer("user")
                .integer("level")
                .integer("x")
                .integer("y")
                .integer("z")
                .integer("action")
                .foreignKey("user", "users", "id")
                .foreignKey("level", "levels", "id")
                .index("coordinates", "x", "y", "z")
                .build(database);
    }

    public void createIndexes() {
        // Indexes are now created in createTable via SchemaBuilder
    }

    public void insert(long time, String userUuid, String levelName, int x, int y, int z, int sessionAction) {
        Dialect dialect = Dialect.current();
        String query = dialect.insertIgnore() + """
                 INTO sessions(time, user, level, x, y, z, action)
                VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?), ?, ?, ?, ?)""";

        try {
            PreparedStatement stmt = database.prepareStatement(query);
            stmt.setLong(1, time);
            stmt.setString(2, userUuid);
            stmt.setString(3, levelName);
            stmt.setInt(4, x);
            stmt.setInt(5, y);
            stmt.setInt(6, z);
            stmt.setInt(7, sessionAction);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert session into database", e);
        }
    }

    public List<SessionHistory> getFilteredSessionHistory(String levelName, FilterList filterList) {
        @Nullable String actions = filterList.getActionString();
        @Nullable String users = filterList.getUserString();

        SelectBuilder builder = Query.select("sessions")
                .columns("sessions.time", "users.name", "users.uuid",
                        "sessions.x", "sessions.y", "sessions.z", "sessions.action")
                .join("users", "sessions.user = users.id")
                .join("levels", "sessions.level = levels.id")
                .whereEq("levels.name", levelName)
                .whereGt("sessions.time", filterList.getTime())
                .whereBetween("sessions.x", filterList.getRadiusMinX(), filterList.getRadiusMaxX())
                .whereBetween("sessions.y", filterList.getRadiusMinY(), filterList.getRadiusMaxY())
                .whereBetween("sessions.z", filterList.getRadiusMinZ(), filterList.getRadiusMaxZ())
                .orderByDesc("sessions.time")
                .limit(1000);

        if (actions != null && !actions.isEmpty()) {
            builder.whereRaw("sessions.action IN (" + actions + ")");
        }
        if (users != null && !users.isEmpty()) {
            builder.whereRaw("users.id IN (" + users + ")");
        }

        return builder.execute(database, rs -> {
            try {
                return new SessionHistory(
                        rs.getLong("time"),
                        rs.getString("name"),
                        rs.getString("uuid"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getInt("action")
                );
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to map session history", e);
                return null;
            }
        }).stream().filter(h -> h != null).toList();
    }
}

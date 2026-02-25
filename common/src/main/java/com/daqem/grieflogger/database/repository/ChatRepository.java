package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ChatRepository extends Repository {

    private final Database database;

    public ChatRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("chats")
                .bigint("time")
                .integer("user")
                .integer("level")
                .integer("x")
                .integer("y")
                .integer("z")
                .string("message", 256, false)
                .foreignKey("user", "users", "id")
                .foreignKey("level", "levels", "id")
                .index("coordinates", "x", "y", "z")
                .build(database);
    }

    public void createIndexes() {
        // Indexes are now created in createTable via SchemaBuilder
    }

    public void insert(long time, String userUuid, String levelName, int x, int y, int z, String message) {
        // Subqueries for user/level need raw SQL
        Dialect dialect = Dialect.current();
        String query = dialect.insertIgnore() + """
                 INTO chats(time, user, level, x, y, z, message)
                VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?), ?, ?, ?, ?)""";

        try {
            PreparedStatement stmt = database.prepareStatement(query);
            stmt.setLong(1, time);
            stmt.setString(2, userUuid);
            stmt.setString(3, levelName);
            stmt.setInt(4, x);
            stmt.setInt(5, y);
            stmt.setInt(6, z);
            stmt.setString(7, message);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert chat into database", e);
        }
    }
}

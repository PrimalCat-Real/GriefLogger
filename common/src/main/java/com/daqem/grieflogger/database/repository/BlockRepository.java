package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.query.SelectBuilder;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;
import com.daqem.grieflogger.model.history.BlockHistory;
import com.daqem.grieflogger.model.history.IHistory;
import com.daqem.grieflogger.util.BlockStateUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class BlockRepository extends Repository {

    private final Database database;

    public BlockRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        // block_states table
        SchemaBuilder.create("block_states")
                .id("id")
                .string("state_string", 255, false, true)
                .build(database);

        // blocks table
        SchemaBuilder.create("blocks")
                .bigint("time")
                .integer("user")
                .integer("level")
                .integer("x")
                .integer("y")
                .integer("z")
                .integer("state_id", true)
                .integer("type")
                .integer("action")
                .foreignKey("state_id", "block_states", "id")
                .foreignKey("user", "users", "id")
                .foreignKey("level", "levels", "id")
                .foreignKey("type", "materials", "id")
                .index("state_idx", "state_id")
                .build(database);
    }

    public void createIndexes() {
        // Indexes are now created in createTable via SchemaBuilder
        // Only add coordinates index here
        Dialect dialect = Dialect.current();
        if (dialect == Dialect.MYSQL) {
            database.execute("ALTER TABLE blocks ADD INDEX coordinates (x, y, z)", false);
        } else {
            database.execute("CREATE INDEX IF NOT EXISTS coordinates ON blocks (x, y, z)", false);
        }
    }

    public void insertBlockState(long time, String userUuid, String levelName, int x, int y, int z, BlockState state, int blockAction) {
        String stateString = BlockStateUtils.serialize(state);
        String materialName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Dialect dialect = Dialect.current();

        // Insert state
        Query.insert("block_states")
                .value("state_string", stateString)
                .ignore()
                .queue(database);

        // Insert material
        Query.insert("materials")
                .value("name", materialName)
                .ignore()
                .queue(database);

        // Insert block with subqueries
        String blockQuery = """
                INSERT INTO blocks(time, user, level, x, y, z, state_id, type, action) VALUES(
                ?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, (SELECT id FROM block_states WHERE state_string = ?),
                (SELECT id FROM materials WHERE name = ?), ?)""";

        try {
            PreparedStatement stmt = database.prepareStatement(blockQuery);
            stmt.setLong(1, time);
            stmt.setString(2, userUuid);
            stmt.setString(3, levelName);
            stmt.setInt(4, x);
            stmt.setInt(5, y);
            stmt.setInt(6, z);
            stmt.setString(7, stateString);
            stmt.setString(8, materialName);
            stmt.setInt(9, blockAction);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert block state", e);
        }
    }

    /**
     * Insert block state with phantom user attribution (e.g., #water, #fire).
     * Phantom users represent natural events and are stored as special UUIDs.
     */
    public void insertBlockStateWithPhantom(long time, String phantomUser, String levelName, int x, int y, int z, BlockState state, int blockAction) {
        String stateString = BlockStateUtils.serialize(state);
        String materialName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        // Ensure phantom user exists in users table
        // Use phantom user name as both UUID and name for easy identification
        Query.insert("users")
                .value("uuid", phantomUser)
                .value("name", phantomUser)
                .ignore()
                .queue(database);

        // Insert state
        Query.insert("block_states")
                .value("state_string", stateString)
                .ignore()
                .queue(database);

        // Insert material
        Query.insert("materials")
                .value("name", materialName)
                .ignore()
                .queue(database);

        // Insert block with phantom user
        String blockQuery = """
                INSERT INTO blocks(time, user, level, x, y, z, state_id, type, action) VALUES(
                ?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, (SELECT id FROM block_states WHERE state_string = ?),
                (SELECT id FROM materials WHERE name = ?), ?)""";

        try {
            PreparedStatement stmt = database.prepareStatement(blockQuery);
            stmt.setLong(1, time);
            stmt.setString(2, phantomUser);  // Use phantom user as UUID
            stmt.setString(3, levelName);
            stmt.setInt(4, x);
            stmt.setInt(5, y);
            stmt.setInt(6, z);
            stmt.setString(7, stateString);
            stmt.setString(8, materialName);
            stmt.setInt(9, blockAction);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert block state with phantom user", e);
        }
    }

    public void insertMaterial(long time, String userUuid, String levelName, int x, int y, int z, String material, int blockAction) {
        // Insert material
        Query.insert("materials")
                .value("name", material)
                .ignore()
                .queue(database);

        // Insert block with subqueries
        String blockQuery = """
                INSERT INTO blocks(time, user, level, x, y, z, state_id, type, action) VALUES(
                ?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, NULL, (SELECT id FROM materials WHERE name = ?), ?)""";

        try {
            PreparedStatement stmt = database.prepareStatement(blockQuery);
            stmt.setLong(1, time);
            stmt.setString(2, userUuid);
            stmt.setString(3, levelName);
            stmt.setInt(4, x);
            stmt.setInt(5, y);
            stmt.setInt(6, z);
            stmt.setString(7, material);
            stmt.setInt(8, blockAction);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert block into database", e);
        }
    }

    public void insertEntity(long time, String userUuid, String levelName, int x, int y, int z, String entity, int blockAction) {
        // Insert entity
        Query.insert("entities")
                .value("name", entity)
                .ignore()
                .queue(database);

        // Insert block with subqueries
        String blockQuery = """
                INSERT INTO blocks(time, user, level, x, y, z, state_id, type, action) VALUES(
                ?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, NULL, (SELECT id FROM entities WHERE name = ?), ?)""";

        try {
            PreparedStatement stmt = database.prepareStatement(blockQuery);
            stmt.setLong(1, time);
            stmt.setString(2, userUuid);
            stmt.setString(3, levelName);
            stmt.setInt(4, x);
            stmt.setInt(5, y);
            stmt.setInt(6, z);
            stmt.setString(7, entity);
            stmt.setInt(8, blockAction);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert entity into database", e);
        }
    }

    public List<IHistory> getBlockHistory(String levelName, int x, int y, int z) {
        return Query.select("blocks")
                .columns("blocks.time", "users.name", "users.uuid",
                        "blocks.x", "blocks.y", "blocks.z", "materials.name",
                        "blocks.action", "blocks.state_id")
                .join("users", "blocks.user = users.id")
                .join("levels", "blocks.level = levels.id")
                .join("materials", "blocks.type = materials.id")
                .whereEq("levels.name", levelName)
                .whereEq("blocks.x", x)
                .whereEq("blocks.y", y)
                .whereEq("blocks.z", z)
                .whereIn("blocks.action", List.of(0, 1))
                .orderByDesc("blocks.time")
                .execute(database, rs -> mapBlockHistory(rs))
                .stream().filter(h -> h != null).map(h -> (IHistory) h).toList();
    }

    public List<IHistory> getInteractionHistory(String levelName, int x, int y, int z) {
        return Query.select("blocks")
                .columns("blocks.time", "users.name", "users.uuid",
                        "blocks.x", "blocks.y", "blocks.z", "materials.name",
                        "blocks.action", "blocks.state_id")
                .join("users", "blocks.user = users.id")
                .join("levels", "blocks.level = levels.id")
                .join("materials", "blocks.type = materials.id")
                .whereEq("levels.name", levelName)
                .whereEq("blocks.x", x)
                .whereEq("blocks.y", y)
                .whereEq("blocks.z", z)
                .whereEq("blocks.action", 2)
                .orderByDesc("blocks.time")
                .execute(database, rs -> mapBlockHistory(rs))
                .stream().filter(h -> h != null).map(h -> (IHistory) h).toList();
    }

    public void removeInteractionsForPosition(String levelName, int x, int y, int z) {
        // Need raw SQL for subquery in WHERE
        String query = """
                DELETE FROM blocks WHERE level = (SELECT id FROM levels WHERE name = ?)
                AND x = ? AND y = ? AND z = ? AND action = 2""";

        try {
            PreparedStatement stmt = database.prepareStatement(query);
            stmt.setString(1, levelName);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to remove interactions for position", e);
        }
    }

    private BlockHistory mapBlockHistory(ResultSet rs) {
        try {
            return new BlockHistory(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getInt(4),
                    rs.getInt(5),
                    rs.getInt(6),
                    rs.getString(7),
                    rs.getInt(8),
                    rs.getInt(9)
            );
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to map block history", e);
            return null;
        }
    }

    public List<IHistory> getFilteredBlockHistory(String levelName, FilterList filterList) {
        @Nullable String actions = filterList.getActionString();
        @Nullable String users = filterList.getUserString();
        @Nullable String includeMaterials = filterList.getIncludeMaterialsString();
        @Nullable String excludeMaterials = filterList.getExcludeMaterialsString();

        String query = """
                SELECT
                    blocks.time,
                    users.name,
                    users.uuid,
                    blocks.x,
                    blocks.y,
                    blocks.z,
                    CASE
                        WHEN blocks.action = 3 THEN entities.name
                        ELSE materials.name
                    END AS type_name,
                    blocks.action,
                    blocks.state_id  -- ВАЖНО: Добавил запятую выше и эту колонку
                FROM
                    blocks
                INNER JOIN users ON blocks.user = users.id
                INNER JOIN levels ON blocks.level = levels.id
                LEFT JOIN materials ON blocks.type = materials.id AND blocks.action != 3
                LEFT JOIN entities ON blocks.type = entities.id AND blocks.action = 3
                WHERE
                    levels.name = ?
                    AND blocks.time > ?
                    AND (? IS NULL OR blocks.action IN (%s))
                    AND (? IS NULL OR users.id IN (%s))
                    AND (? IS NULL OR materials.name IN ('%s'))
                    AND (? IS NULL OR materials.name NOT IN ('%s'))
                    AND blocks.x BETWEEN ? AND ?
                    AND blocks.y BETWEEN ? AND ?
                    AND blocks.z BETWEEN ? AND ?
                ORDER BY
                    blocks.time DESC
                LIMIT 1000;
                """.formatted(actions, users, includeMaterials, excludeMaterials);

        try (PreparedStatement preparedStatement = database.prepareStatement(query)) {
            // ... (установка параметров без изменений) ...
            preparedStatement.setString(1, levelName);
            preparedStatement.setLong(2, filterList.getTime());

            if (actions == null || actions.isEmpty()) {
                preparedStatement.setNull(3, Types.VARCHAR);
            } else {
                preparedStatement.setString(3, "not null");
            }

            if (users == null || users.isEmpty()) {
                preparedStatement.setNull(4, Types.VARCHAR);
            } else {
                preparedStatement.setString(4, "not null");
            }

            if (includeMaterials == null || includeMaterials.isEmpty()) {
                preparedStatement.setNull(5, Types.VARCHAR);
            } else {
                preparedStatement.setString(5, "not null");
            }

            if (excludeMaterials == null || excludeMaterials.isEmpty()) {
                preparedStatement.setNull(6, Types.VARCHAR);
            } else {
                preparedStatement.setString(6, "not null");
            }

            preparedStatement.setInt(7, filterList.getRadiusMinX());
            preparedStatement.setInt(8, filterList.getRadiusMaxX());
            preparedStatement.setInt(9, filterList.getRadiusMinY());
            preparedStatement.setInt(10, filterList.getRadiusMaxY());
            preparedStatement.setInt(11, filterList.getRadiusMinZ());
            preparedStatement.setInt(12, filterList.getRadiusMaxZ());

            List<IHistory> blockHistory = new ArrayList<>();
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                blockHistory.add(new BlockHistory(
                        resultSet.getLong(1),
                        resultSet.getString(2),
                        resultSet.getString(3),
                        resultSet.getInt(4),
                        resultSet.getInt(5),
                        resultSet.getInt(6),
                        resultSet.getString(7),
                        resultSet.getInt(8),
                        resultSet.getInt(9)
                ));
            }
            return blockHistory;
        } catch (SQLException exception) {
            GriefLogger.LOGGER.error("Failed to get block history from database", exception);
            return List.of();
        }
    }

    public BlockState getBlockStateById(int stateId) {
        if (stateId == 0) return null;

        String query = "SELECT state_string FROM block_states WHERE id = ?";
        try (PreparedStatement statement = database.prepareStatement(query)) {
            statement.setInt(1, stateId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                String stateStr = rs.getString(1);
                return BlockStateUtils.deserialize(stateStr);
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to fetch block state", e);
        }
        return null;
    }
}

package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.model.history.ItemHistory;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemRepository extends Repository {

    private final Database database;

    public ItemRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("items")
                .bigint("time")
                .integer("user")
                .integer("level")
                .integer("x")
                .integer("y")
                .integer("z")
                .integer("type")
                .blob("data")
                .integer("amount")
                .integer("action")
                .foreignKey("user", "users", "id")
                .foreignKey("level", "levels", "id")
                .foreignKey("type", "materials", "id")
                .index("coordinates", "x", "y", "z")
                .build(database);
    }

    public void createIndexes() {
        // Indexes are now created in createTable via SchemaBuilder
    }

    public void insert(long time, String userUuid, Level level, int x, int y, int z, SimpleItemStack item, int action) {
        if (item.isEmpty()) {
            return;
        }

        ResourceLocation itemLocation = item.getItem().arch$registryName();
        if (itemLocation != null) {
            String materialName = itemLocation.toString().replace("minecraft:", "");

            // Insert material
            Query.insert("materials")
                    .value("name", materialName)
                    .ignore()
                    .queue(database);

            // Insert item with subqueries
            String insertQuery = """
                    INSERT INTO items(time, user, level, x, y, z, type, data, amount, action)
                    VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                    ?, ?, ?, (SELECT id FROM materials WHERE name = ?), ?, ?, ?)""";

            try {
                PreparedStatement stmt = database.prepareStatement(insertQuery);
                stmt.setLong(1, time);
                stmt.setString(2, userUuid);
                stmt.setString(3, level.dimension().location().toString());
                stmt.setInt(4, x);
                stmt.setInt(5, y);
                stmt.setInt(6, z);
                stmt.setString(7, materialName);
                stmt.setBytes(8, item.getTagBytes(level));
                stmt.setInt(9, item.getCount());
                stmt.setInt(10, action);
                database.queue.add(stmt);
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to insert item into database", e);
            }
        }
    }

    public void insertMap(long time, String userUuid, Level level, int x, int y, int z, Map<ItemAction, List<SimpleItemStack>> itemsMap) {
        String insertQuery = """
                INSERT INTO items(time, user, level, x, y, z, type, data, amount, action)
                VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, (SELECT id FROM materials WHERE name = ?), ?, ?, ?)""";

        try {
            PreparedStatement itemStmt = database.prepareStatement(insertQuery);

            for (Map.Entry<ItemAction, List<SimpleItemStack>> entry : itemsMap.entrySet()) {
                for (SimpleItemStack item : entry.getValue()) {
                    if (item.isEmpty()) {
                        continue;
                    }
                    ResourceLocation itemLocation = item.getItem().arch$registryName();
                    if (itemLocation != null) {
                        String materialName = itemLocation.toString().replace("minecraft:", "");

                        // Insert material
                        Query.insert("materials")
                                .value("name", materialName)
                                .ignore()
                                .queue(database);

                        itemStmt.setLong(1, time);
                        itemStmt.setString(2, userUuid);
                        itemStmt.setString(3, level.dimension().location().toString());
                        itemStmt.setInt(4, x);
                        itemStmt.setInt(5, y);
                        itemStmt.setInt(6, z);
                        itemStmt.setString(7, materialName);
                        itemStmt.setBytes(8, item.getTagBytes(level));
                        itemStmt.setInt(9, item.getCount());
                        itemStmt.setInt(10, entry.getKey().getId());
                        itemStmt.addBatch();
                    }
                }
            }
            database.batchQueue.add(itemStmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert items", e);
        }
    }

    public List<ItemHistory> getFilteredItemHistory(Level level, FilterList filterList) {
        @Nullable String actions = filterList.getActionString();
        @Nullable String users = filterList.getUserString();
        @Nullable String includeMaterials = filterList.getIncludeMaterialsString();
        @Nullable String excludeMaterials = filterList.getExcludeMaterialsString();

        String query = """
                SELECT items.time, users.name, users.uuid, items.x, items.y, items.z, materials.name, items.data, items.amount, items.action
                FROM items
                INNER JOIN users ON items.user = users.id
                INNER JOIN levels ON items.level = levels.id
                INNER JOIN materials ON items.type = materials.id
                WHERE levels.name = ?
                AND items.time > ?
                AND (? IS NULL OR items.action IN (%s))
                AND (? IS NULL OR users.id IN (%s))
                AND (? IS NULL OR materials.name IN ('%s'))
                AND (? IS NULL OR materials.name NOT IN ('%s'))
                AND items.x BETWEEN ? AND ?
                AND items.y BETWEEN ? AND ?
                AND items.z BETWEEN ? AND ?
                ORDER BY items.time DESC
                LIMIT 1000;
                """.formatted(actions, users, includeMaterials, excludeMaterials);

        try (PreparedStatement preparedStatement = database.prepareStatement(query)) {
            preparedStatement.setString(1, level.dimension().location().toString());
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

            List<ItemHistory> itemHistory = new ArrayList<>();
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                ItemHistory history = mapItemHistory(resultSet, level);
                if (history != null) {
                    itemHistory.add(history);
                }
            }
            return itemHistory;
        } catch (SQLException exception) {
            GriefLogger.LOGGER.error("Failed to get item history from database", exception);
            return List.of();
        }
    }

    private ItemHistory mapItemHistory(ResultSet rs, Level level) {
        try {
            DataComponentPatch patch = SimpleItemStack.tagFromBytes(rs.getBytes(8), level);
            return new ItemHistory(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getInt(4),
                    rs.getInt(5),
                    rs.getInt(6),
                    rs.getString(7),
                    patch,
                    rs.getInt(9),
                    rs.getInt(10)
            );
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to map item history", e);
            return null;
        }
    }
}

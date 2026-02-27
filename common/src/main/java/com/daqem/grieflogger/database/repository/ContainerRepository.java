package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.model.history.ContainerHistory;
import com.daqem.grieflogger.model.history.IHistory;
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

public class ContainerRepository extends Repository {

    private final Database database;

    public ContainerRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("containers")
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

    public void insert(long time, String userUuid, Level level, int x, int y, int z, SimpleItemStack item, int itemAction) {
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

            // Insert container with subqueries
            String insertQuery = """
                    INSERT INTO containers(time, user, level, x, y, z, type, data, amount, action)
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
                stmt.setInt(10, itemAction);
                database.queue.add(stmt);
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to insert container", e);
            }
        }
    }

    /**
     * Insert container transaction with phantom user (automated transfer).
     * Phantom user is used as both UUID and name.
     */
    public void insertWithPhantom(long time, String phantomUser, Level level, int x, int y, int z, SimpleItemStack item, int itemAction) {
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

            // Insert container with phantom user
            String insertQuery = """
                    INSERT INTO containers(time, user, level, x, y, z, type, data, amount, action)
                    VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                    ?, ?, ?, (SELECT id FROM materials WHERE name = ?), ?, ?, ?)""";

            try {
                PreparedStatement stmt = database.prepareStatement(insertQuery);
                stmt.setLong(1, time);
                stmt.setString(2, phantomUser);  // Phantom user as UUID
                stmt.setString(3, level.dimension().location().toString());
                stmt.setInt(4, x);
                stmt.setInt(5, y);
                stmt.setInt(6, z);
                stmt.setString(7, materialName);
                stmt.setBytes(8, item.getTagBytes(level));
                stmt.setInt(9, item.getCount());
                stmt.setInt(10, itemAction);
                database.queue.add(stmt);
            } catch (SQLException e) {
                GriefLogger.LOGGER.error("Failed to insert container with phantom user", e);
            }
        }
    }

    public void insertList(long time, String userUuid, Level level, int x, int y, int z, List<SimpleItemStack> items, int itemAction) {
        String insertQuery = """
                INSERT INTO containers(time, user, level, x, y, z, type, data, amount, action)
                VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, (SELECT id FROM materials WHERE name = ?), ?, ?, ?)""";

        try {
            PreparedStatement containerStmt = database.prepareStatement(insertQuery);

            for (SimpleItemStack item : items) {
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

                    containerStmt.setLong(1, time);
                    containerStmt.setString(2, userUuid);
                    containerStmt.setString(3, level.dimension().location().toString());
                    containerStmt.setInt(4, x);
                    containerStmt.setInt(5, y);
                    containerStmt.setInt(6, z);
                    containerStmt.setString(7, materialName);
                    containerStmt.setBytes(8, item.getTagBytes(level));
                    containerStmt.setInt(9, item.getCount());
                    containerStmt.setInt(10, itemAction);
                    containerStmt.addBatch();
                }
            }
            database.batchQueue.add(containerStmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert containers", e);
        }
    }

    public void insertMap(long time, String userUuid, Level level, int x, int y, int z, Map<ItemAction, List<SimpleItemStack>> itemsMap) {
        String insertQuery = """
                INSERT INTO containers(time, user, level, x, y, z, type, data, amount, action)
                VALUES(?, (SELECT id FROM users WHERE uuid = ?), (SELECT id FROM levels WHERE name = ?),
                ?, ?, ?, (SELECT id FROM materials WHERE name = ?), ?, ?, ?)""";

        try {
            PreparedStatement containerStmt = database.prepareStatement(insertQuery);

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

                        containerStmt.setLong(1, time);
                        containerStmt.setString(2, userUuid);
                        containerStmt.setString(3, level.dimension().location().toString());
                        containerStmt.setInt(4, x);
                        containerStmt.setInt(5, y);
                        containerStmt.setInt(6, z);
                        containerStmt.setString(7, materialName);
                        containerStmt.setBytes(8, item.getTagBytes(level));
                        containerStmt.setInt(9, item.getCount());
                        containerStmt.setInt(10, entry.getKey().getId());
                        containerStmt.addBatch();
                    }
                }
            }
            database.batchQueue.add(containerStmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert containers", e);
        }
    }

    public List<IHistory> getHistory(Level level, int x, int y, int z) {
        return Query.select("containers")
                .columns("containers.time", "users.name", "users.uuid",
                        "containers.x", "containers.y", "containers.z", "materials.name",
                        "containers.data", "containers.amount", "containers.action")
                .join("users", "containers.user = users.id")
                .join("levels", "containers.level = levels.id")
                .join("materials", "containers.type = materials.id")
                .whereEq("levels.name", level.dimension().location().toString())
                .whereEq("containers.x", x)
                .whereEq("containers.y", y)
                .whereEq("containers.z", z)
                .whereIn("containers.action", List.of(0, 1))
                .orderByDesc("containers.time")
                .execute(database, rs -> mapContainerHistory(rs, level))
                .stream().filter(h -> h != null).map(h -> (IHistory) h).toList();
    }

    public List<IHistory> getHistory(Level level, int x, int y, int z, int x2, int y2, int z2) {
        return Query.select("containers")
                .columns("containers.time", "users.name", "users.uuid",
                        "containers.x", "containers.y", "containers.z", "materials.name",
                        "containers.data", "containers.amount", "containers.action")
                .join("users", "containers.user = users.id")
                .join("levels", "containers.level = levels.id")
                .join("materials", "containers.type = materials.id")
                .whereEq("levels.name", level.dimension().location().toString())
                .whereBetween("containers.x", x, x2)
                .whereBetween("containers.y", y, y2)
                .whereBetween("containers.z", z, z2)
                .whereIn("containers.action", List.of(0, 1))
                .orderByDesc("containers.time")
                .execute(database, rs -> mapContainerHistory(rs, level))
                .stream().filter(h -> h != null).map(h -> (IHistory) h).toList();
    }

    private ContainerHistory mapContainerHistory(ResultSet rs, Level level) {
        try {
            DataComponentPatch patch = SimpleItemStack.tagFromBytes(rs.getBytes(8), level);
            return new ContainerHistory(
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
            GriefLogger.LOGGER.error("Failed to map container history", e);
            return null;
        }
    }

    public List<IHistory> getFilteredContainerHistory(Level level, FilterList filterList) {
        @Nullable String actions = filterList.getActionString();
        @Nullable String users = filterList.getUserString();
        @Nullable String includeMaterials = filterList.getIncludeMaterialsString();
        @Nullable String excludeMaterials = filterList.getExcludeMaterialsString();

        String query = """
                SELECT containers.time, users.name, users.uuid, containers.x, containers.y, containers.z, materials.name, containers.data, containers.amount, containers.action
                FROM containers
                INNER JOIN users ON containers.user = users.id
                INNER JOIN levels ON containers.level = levels.id
                INNER JOIN materials ON containers.type = materials.id
                WHERE levels.name = ?
                AND containers.time > ?
                AND (? IS NULL OR containers.action IN (%s))
                AND (? IS NULL OR users.id IN (%s))
                AND (? IS NULL OR materials.name IN ('%s'))
                AND (? IS NULL OR materials.name NOT IN ('%s'))
                AND containers.x BETWEEN ? AND ?
                AND containers.y BETWEEN ? AND ?
                AND containers.z BETWEEN ? AND ?
                ORDER BY containers.time DESC
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

            List<IHistory> containerHistory = new ArrayList<>();
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                ContainerHistory history = mapContainerHistory(resultSet, level);
                if (history != null) {
                    containerHistory.add(history);
                }
            }
            return containerHistory;
        } catch (SQLException exception) {
            GriefLogger.LOGGER.error("Failed to get container history from database", exception);
            return List.of();
        }
    }
}

package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;
import com.daqem.grieflogger.model.rollback.RollbackAction;
import com.daqem.grieflogger.model.rollback.RollbackJob;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for rollback history tracking.
 * Stores rollback jobs and their individual actions for undo support.
 */
public class RollbackRepository extends Repository {

    private final Database database;

    public RollbackRepository(Database database) {
        this.database = database;
    }

    @Override
    public void createTable() {
        SchemaBuilder.create("rollback_history")
                .id("id")
                .bigint("time")
                .string("actor_uuid", 36)
                .string("actor_name", 255)
                .integer("action_type")  
                .string("level_name", 255)
                .integer("center_x")
                .integer("center_y")
                .integer("center_z")
                .integer("radius")
                .bigint("time_filter")
                .text("user_filter")
                .integer("block_count")
                .bigint("duration_ms")
                .build(database);

        SchemaBuilder.create("rollback_actions")
                .id("id")
                .integer("job_id")
                .bigint("time")
                .integer("action_type")  
                .string("level_name", 255)
                .integer("x")
                .integer("y")
                .integer("z")
                .integer("old_state_id")  
                .integer("new_state_id")  
                .string("old_material", 255)
                .string("new_material", 255)
                .foreignKey("job_id", "rollback_history", "id")
                .index("idx_rollback_actions_job", "job_id")
                .build(database);
    }

    public void createIndexes() {
        database.execute("CREATE INDEX IF NOT EXISTS idx_rollback_history_actor ON rollback_history (actor_uuid)", false);
        database.execute("CREATE INDEX IF NOT EXISTS idx_rollback_history_time ON rollback_history (time)", false);
    }

    /**
     * Insert a new rollback job and return its ID.
     */
    public int insertJob(RollbackJob job) {
        String sql = """
            INSERT INTO rollback_history
            (time, actor_uuid, actor_name, action_type, level_name, center_x, center_y, center_z,
             radius, time_filter, user_filter, block_count, duration_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setLong(1, job.time());
            statement.setString(2, job.actorUuid().toString());
            statement.setString(3, job.actorName());
            statement.setInt(4, job.actionType());
            statement.setString(5, job.levelName());
            statement.setInt(6, job.centerX());
            statement.setInt(7, job.centerY());
            statement.setInt(8, job.centerZ());
            statement.setInt(9, job.radius());
            statement.setLong(10, job.timeFilter());
            statement.setString(11, job.userFilter() != null ? job.userFilter() : "");
            statement.setInt(12, job.blockCount());
            statement.setLong(13, job.durationMs());

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert rollback job", e);
        }
        return -1;
    }

    /**
     * Insert a rollback action (individual block change).
     */
    public void insertAction(int jobId, RollbackAction action) {
        String sql = """
            INSERT INTO rollback_actions
            (job_id, time, action_type, level_name, x, y, z, old_state_id, new_state_id, old_material, new_material)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setInt(1, jobId);
            statement.setLong(2, action.time());
            statement.setInt(3, action.actionType());
            statement.setString(4, action.levelName());
            statement.setInt(5, action.x());
            statement.setInt(6, action.y());
            statement.setInt(7, action.z());
            statement.setInt(8, action.oldStateId());
            statement.setInt(9, action.newStateId());
            statement.setString(10, action.oldMaterial());
            statement.setString(11, action.newMaterial());

            statement.executeUpdate();
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to insert rollback action", e);
        }
    }

    /**
     * Batch insert rollback actions.
     */
    public void insertActions(int jobId, List<RollbackAction> actions) {
        if (actions.isEmpty()) return;

        String sql = """
            INSERT INTO rollback_actions
            (job_id, time, action_type, level_name, x, y, z, old_state_id, new_state_id, old_material, new_material)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            for (RollbackAction action : actions) {
                statement.setInt(1, jobId);
                statement.setLong(2, action.time());
                statement.setInt(3, action.actionType());
                statement.setString(4, action.levelName());
                statement.setInt(5, action.x());
                statement.setInt(6, action.y());
                statement.setInt(7, action.z());
                statement.setInt(8, action.oldStateId());
                statement.setInt(9, action.newStateId());
                statement.setString(10, action.oldMaterial());
                statement.setString(11, action.newMaterial());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to batch insert rollback actions", e);
        }
    }

    /**
     * Get the most recent rollback job for a player.
     */
    public Optional<RollbackJob> getLastJobByActor(UUID actorUuid) {
        String sql = """
            SELECT * FROM rollback_history
            WHERE actor_uuid = ?
            ORDER BY time DESC
            LIMIT 1
            """;

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setString(1, actorUuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapJob(rs));
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to get last rollback job", e);
        }
        return Optional.empty();
    }

    /**
     * Get a rollback job by ID.
     */
    public Optional<RollbackJob> getJobById(int jobId) {
        String sql = "SELECT * FROM rollback_history WHERE id = ?";

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setInt(1, jobId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapJob(rs));
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to get rollback job by ID", e);
        }
        return Optional.empty();
    }

    /**
     * Get all actions for a rollback job.
     */
    public List<RollbackAction> getActionsByJobId(int jobId) {
        List<RollbackAction> actions = new ArrayList<>();
        String sql = "SELECT * FROM rollback_actions WHERE job_id = ? ORDER BY id";

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setInt(1, jobId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    actions.add(mapAction(rs));
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to get rollback actions", e);
        }
        return actions;
    }

    /**
     * Get recent rollback jobs for a player.
     */
    public List<RollbackJob> getRecentJobsByActor(UUID actorUuid, int limit) {
        List<RollbackJob> jobs = new ArrayList<>();
        String sql = """
            SELECT * FROM rollback_history
            WHERE actor_uuid = ?
            ORDER BY time DESC
            LIMIT ?
            """;

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setString(1, actorUuid.toString());
            statement.setInt(2, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    jobs.add(mapJob(rs));
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to get recent rollback jobs", e);
        }
        return jobs;
    }

    private RollbackJob mapJob(ResultSet rs) throws SQLException {
        return new RollbackJob(
                rs.getInt("id"),
                rs.getLong("time"),
                UUID.fromString(rs.getString("actor_uuid")),
                rs.getString("actor_name"),
                rs.getInt("action_type"),
                rs.getString("level_name"),
                rs.getInt("center_x"),
                rs.getInt("center_y"),
                rs.getInt("center_z"),
                rs.getInt("radius"),
                rs.getLong("time_filter"),
                rs.getString("user_filter"),
                rs.getInt("block_count"),
                rs.getLong("duration_ms")
        );
    }

    private RollbackAction mapAction(ResultSet rs) throws SQLException {
        return new RollbackAction(
                rs.getInt("id"),
                rs.getInt("job_id"),
                rs.getLong("time"),
                rs.getInt("action_type"),
                rs.getString("level_name"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getInt("old_state_id"),
                rs.getInt("new_state_id"),
                rs.getString("old_material"),
                rs.getString("new_material")
        );
    }
}

package com.daqem.grieflogger.database.service;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface Services {

    BlockService BLOCK = new BlockService(GriefLogger.getDatabase());
    ChatService CHAT = new ChatService(GriefLogger.getDatabase());
    CommandService COMMAND = new CommandService(GriefLogger.getDatabase());
    ContainerService CONTAINER = new ContainerService(GriefLogger.getDatabase());
    EntityService ENTITY = new EntityService(GriefLogger.getDatabase());
    ItemService ITEM = new ItemService(GriefLogger.getDatabase());
    LevelService LEVEL = new LevelService(GriefLogger.getDatabase());
    MaterialService MATERIAL = new MaterialService(GriefLogger.getDatabase());
    RollbackService ROLLBACK = new RollbackService(GriefLogger.getDatabase());
    SessionService SESSION = new SessionService(GriefLogger.getDatabase());
    UsernameService USERNAME = new UsernameService(GriefLogger.getDatabase());
    UserService USER = new UserService(GriefLogger.getDatabase());
    ChunkBackupService CHUNK_BACKUP = new ChunkBackupService(GriefLogger.getDatabase());
    PlayerHomeService PLAYER_HOME = new PlayerHomeService(GriefLogger.getDatabase());

    /**
     * Purge all data older than the specified cutoff time.
     *
     * @param cutoffTime Records with time < cutoffTime will be deleted
     * @return PurgeResult with counts of deleted records, or null on error
     */
    static PurgeResult purgeOldData(long cutoffTime) {
        Database database = GriefLogger.getDatabase();

        try {
            int blocks = purgeTable(database, "blocks", cutoffTime);
            int containers = purgeTable(database, "containers", cutoffTime);
            int items = purgeTable(database, "items", cutoffTime);
            int sessions = purgeTable(database, "sessions", cutoffTime);
            int chat = purgeTable(database, "chats", cutoffTime);
            int commands = purgeTable(database, "commands", cutoffTime);

            return new PurgeResult(blocks, containers, items, sessions, chat, commands);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to purge old data", e);
            return null;
        }
    }

    private static int purgeTable(Database database, String tableName, long cutoffTime) throws SQLException {
        String query = "DELETE FROM " + tableName + " WHERE time < ?";
        try (PreparedStatement stmt = database.prepareStatement(query)) {
            stmt.setLong(1, cutoffTime);
            return stmt.executeUpdate();
        }
    }

    /**
     * Result of a purge operation.
     */
    record PurgeResult(int blocks, int containers, int items, int sessions, int chat, int commands) {
        public int total() {
            return blocks + containers + items + sessions + chat + commands;
        }
    }
}

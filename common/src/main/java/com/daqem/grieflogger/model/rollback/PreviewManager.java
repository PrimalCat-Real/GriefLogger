package com.daqem.grieflogger.model.rollback;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages preview rollback sessions for all players.
 * Singleton pattern - use getInstance() to access.
 */
public class PreviewManager {

    private static final PreviewManager INSTANCE = new PreviewManager();

    private final Map<UUID, PreviewSession> previewSessions = new ConcurrentHashMap<>();

    private PreviewManager() {
    }

    /**
     * Get the singleton instance.
     */
    public static PreviewManager getInstance() {
        return INSTANCE;
    }

    /**
     * Store a preview session for a player.
     * Replaces any existing preview for the same player.
     */
    public void storePreview(PreviewSession session) {
        previewSessions.put(session.playerUuid(), session);
    }

    /**
     * Get the preview session for a player.
     * Returns empty if no preview exists or if it has expired.
     */
    public Optional<PreviewSession> getPreview(UUID playerUuid) {
        PreviewSession session = previewSessions.get(playerUuid);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            previewSessions.remove(playerUuid);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Check if a player has an active preview.
     */
    public boolean hasPreview(UUID playerUuid) {
        return getPreview(playerUuid).isPresent();
    }

    /**
     * Clear the preview session for a player.
     * Returns the cleared session, or empty if none existed.
     */
    public Optional<PreviewSession> clearPreview(UUID playerUuid) {
        PreviewSession session = previewSessions.remove(playerUuid);
        return Optional.ofNullable(session);
    }

    /**
     * Clear all expired preview sessions.
     * Should be called periodically.
     */
    public void cleanupExpired() {
        previewSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Get the number of active preview sessions.
     */
    public int getActiveCount() {
        cleanupExpired();
        return previewSessions.size();
    }

    /**
     * Clear all preview sessions.
     * Used for server shutdown.
     */
    public void clearAll() {
        previewSessions.clear();
    }
}

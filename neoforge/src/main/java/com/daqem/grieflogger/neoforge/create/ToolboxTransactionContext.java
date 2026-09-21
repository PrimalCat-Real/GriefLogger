package com.daqem.grieflogger.neoforge.create;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Carries the player responsible for nested ToolboxInventory calls. Capability
 * calls that do not establish this context are treated as automation.
 */
public final class ToolboxTransactionContext {

    private static final ThreadLocal<Deque<ServerPlayer>> PLAYERS = new ThreadLocal<>();
    private static final ThreadLocal<Integer> SUPPRESSION_DEPTH = new ThreadLocal<>();

    private ToolboxTransactionContext() {
    }

    public static void beginPlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Deque<ServerPlayer> players = PLAYERS.get();
        if (players == null) {
            players = new ArrayDeque<>();
            PLAYERS.set(players);
        }
        players.push(serverPlayer);
    }

    public static void endPlayer(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }

        Deque<ServerPlayer> players = PLAYERS.get();
        if (players == null) {
            return;
        }
        if (!players.isEmpty()) {
            players.pop();
        }
        if (players.isEmpty()) {
            PLAYERS.remove();
        }
    }

    public static <T> T withPlayer(Player player, Supplier<T> operation) {
        beginPlayer(player);
        try {
            return operation.get();
        } finally {
            endPlayer(player);
        }
    }

    public static ServerPlayer getPlayer() {
        Deque<ServerPlayer> players = PLAYERS.get();
        return players == null || players.isEmpty() ? null : players.peek();
    }

    public static void beginSuppressed() {
        Integer depth = SUPPRESSION_DEPTH.get();
        SUPPRESSION_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void endSuppressed() {
        Integer depth = SUPPRESSION_DEPTH.get();
        if (depth == null || depth <= 1) {
            SUPPRESSION_DEPTH.remove();
        } else {
            SUPPRESSION_DEPTH.set(depth - 1);
        }
    }

    public static <T> T withSuppressed(Supplier<T> operation) {
        beginSuppressed();
        try {
            return operation.get();
        } finally {
            endSuppressed();
        }
    }

    public static boolean isSuppressed() {
        Integer depth = SUPPRESSION_DEPTH.get();
        return depth != null && depth > 0;
    }
}

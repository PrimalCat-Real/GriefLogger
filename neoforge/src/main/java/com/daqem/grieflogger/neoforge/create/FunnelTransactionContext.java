package com.daqem.grieflogger.neoforge.create;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/** Carries the player responsible for a nested manual funnel insertion. */
public final class FunnelTransactionContext {

    private static final ThreadLocal<Deque<ServerPlayer>> PLAYERS = new ThreadLocal<>();

    private FunnelTransactionContext() {
    }

    public static <T> T withPlayer(Player player, Supplier<T> operation) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return operation.get();
        }

        Deque<ServerPlayer> players = PLAYERS.get();
        if (players == null) {
            players = new ArrayDeque<>();
            PLAYERS.set(players);
        }
        players.push(serverPlayer);
        try {
            return operation.get();
        } finally {
            players.pop();
            if (players.isEmpty()) {
                PLAYERS.remove();
            }
        }
    }

    public static ServerPlayer getPlayer() {
        Deque<ServerPlayer> players = PLAYERS.get();
        return players == null || players.isEmpty() ? null : players.peek();
    }
}

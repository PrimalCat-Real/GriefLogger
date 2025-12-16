package com.daqem.grieflogger.block.coalesce;

public class BlockEventLock {

    private static final ThreadLocal<Boolean> IS_PLAYER_EVENT = ThreadLocal.withInitial(() -> false);

    public static void lock() {
        IS_PLAYER_EVENT.set(true);
    }

    public static void unlock() {
        IS_PLAYER_EVENT.set(false);
    }

    public static boolean isLocked() {
        return IS_PLAYER_EVENT.get();
    }
}

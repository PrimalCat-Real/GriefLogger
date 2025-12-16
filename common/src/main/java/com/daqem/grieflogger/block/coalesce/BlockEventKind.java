package com.daqem.grieflogger.block.coalesce;

public enum BlockEventKind {
    MARK_NOTIFY(1),
    SYSTEM_SET(2),
    SYSTEM_BREAK(3),
    PLAYER_PLACE(10),
    PLAYER_BREAK(11);
    public final int priority;

    BlockEventKind(int priority) {
        this.priority = priority;
    }
}

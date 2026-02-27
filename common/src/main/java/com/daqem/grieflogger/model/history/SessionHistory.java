package com.daqem.grieflogger.model.history;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.model.BlockPosition;
import com.daqem.grieflogger.model.Time;
import com.daqem.grieflogger.model.User;
import com.daqem.grieflogger.model.action.IAction;
import com.daqem.grieflogger.model.action.ItemAction;
import com.daqem.grieflogger.model.action.SessionAction;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class SessionHistory extends History {

    public SessionHistory(long time, String name, String uuid, int x, int y, int z, int sessionAction) {
        this(new Time(time), new User(name, parseUuidOrNull(uuid)), new BlockPosition(x, y, z), SessionAction.fromId(sessionAction));
    }

    private static UUID parseUuidOrNull(String uuid) {
        if (uuid == null || uuid.startsWith("#")) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public SessionHistory(Time time, User user, BlockPosition position, IAction action) {
        super(time, user, position, action);
    }

    @Override
    public Component getComponent() {
        return getTime().getFormattedTimeAgo().append(" ")
                .append(getAction().getPrefix()).append(" ")
                .append(getUser().getNameComponent()).append(" ")
                .append(getAction().getPastTense());
    }

    @Override
    public Component getMaterialComponent() {
        return null;
    }
}

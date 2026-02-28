package com.daqem.grieflogger.model.history;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.model.BlockPosition;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.Time;
import com.daqem.grieflogger.model.User;
import com.daqem.grieflogger.model.action.IAction;
import com.daqem.grieflogger.model.action.ItemAction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import com.daqem.grieflogger.util.Theme;

import java.util.UUID;

public class ItemHistory extends History {

    protected final SimpleItemStack itemStack;

    public ItemHistory(long time, String name, String uuid, int x, int y, int z, String material, DataComponentPatch data, int amount, int action) {
        this(new Time(time), new User(name, parseUuidOrNull(uuid)), new BlockPosition(x, y, z), new SimpleItemStack(ResourceLocation.parse(material), amount, data), ItemAction.fromId(action));
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

    public ItemHistory(Time time, User user, BlockPosition position, SimpleItemStack itemStack, IAction action) {
        super(time, user, position, action);
        this.itemStack = itemStack;
    }

    public SimpleItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public Component getComponent() {
        return Theme.toMinecraft(
                Theme.muted("") // Base for easy appending
        ).copy().append(Theme.toMinecraft(Theme.fromMinecraft(getTime().getFormattedTimeAgo()).color(Theme.MUTED)))
         .append(Theme.toMinecraft(Theme.muted(" - ")))
         .append(Theme.toMinecraft(Theme.primary(getUser().getName())))
         .append(Theme.toMinecraft(Theme.secondary(" ")))
         .append(Theme.toMinecraft(Theme.fromMinecraft(getAction().getPastTense()).color(Theme.SECONDARY)))
         .append(Theme.toMinecraft(Theme.secondary(" " + getItemStack().getCount() + " ")))
         .append(getMaterialComponent());
    }

    @Override
    public Component getMaterialComponent() {
        int cappedCount = Math.min(itemStack.getCount(), 64); 
        var cappedItemStack = itemStack.toItemStack().copyWithCount(cappedCount);

        MutableComponent mutableComponent = GriefLogger.themedLiteral(this.itemStack.getItem().arch$registryName().toString().replace("minecraft:", ""));
        return mutableComponent
                .withStyle(mutableComponent
                        .getStyle()
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_ITEM,
                                        new HoverEvent.ItemStackInfo(cappedItemStack))));

    }
}

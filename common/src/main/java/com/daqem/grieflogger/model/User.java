package com.daqem.grieflogger.model;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.util.Theme;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class User {

    private static final Set<String> LOCALIZED_PHANTOM_TYPES = Set.of(
            "water", "lava", "fire", "decay", "vine", "explosion", "piston", "enderman",
            "gravity", "hopper", "chute", "smart_chute", "mechanical_arm", "belt", "drill",
            "toolbox", "andesite_funnel", "brass_funnel", "deployer", "unknown"
    );

    private final String name;
    private final @Nullable UUID uuid;

    public User(String name, @Nullable UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public Optional<UUID> getUuid() {
        return Optional.ofNullable(uuid);
    }

    /**
     * Returns true if this is a phantom user (e.g., #chute, #hopper).
     */
    public boolean isPhantom() {
        return name != null && name.startsWith("#");
    }

    public Component getNameComponent() {
        if (isPhantom() && name.contains("@")) {
            String[] parts = name.substring(1).split("@", 2);
            String phantomType = parts[0];
            String coords = parts[1].replace(",", ", ");
            return Theme.toMinecraft(
                    Theme.fromMinecraft(getPhantomTypeComponent(phantomType)).color(Theme.ACCENT)
                            .append(Theme.muted(" (" + coords + ")"))
            );
        } else if (isPhantom()) {
            return Theme.toMinecraft(
                    Theme.fromMinecraft(getPhantomTypeComponent(name.substring(1))).color(Theme.ACCENT)
            );
        }
        return GriefLogger.themedLiteral(name);
    }

    private Component getPhantomTypeComponent(String phantomType) {
        if (LOCALIZED_PHANTOM_TYPES.contains(phantomType)) {
            return GriefLogger.translate("phantom." + phantomType);
        }
        return Component.literal(phantomType);
    }
}

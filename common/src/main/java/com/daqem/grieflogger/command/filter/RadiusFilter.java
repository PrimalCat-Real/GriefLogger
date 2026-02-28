package com.daqem.grieflogger.command.filter;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.model.BlockPosition;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Radius filter for lookup/rollback commands.
 * Supports CoreProtect-style format:
 * - r:10 - radius in blocks
 * - r:#global - entire world (no radius limit)
 * - r:c4 - radius in chunks (4 chunks = 64 blocks) [future]
 */
public class RadiusFilter implements IFilter {

    private static final int GLOBAL_RADIUS = -1;
    private static final List<Integer> COMMON_RADII = List.of(5, 10, 15, 20, 25, 50, 100);

    private final int radius;
    private final boolean isGlobal;
    private BlockPosition position = new BlockPosition(0, 0, 0);

    public RadiusFilter() {
        this(0, false);
    }

    public RadiusFilter(int radius) {
        this(radius, false);
    }

    public RadiusFilter(int radius, boolean isGlobal) {
        this.radius = radius;
        this.isGlobal = isGlobal;
    }

    @Override
    public String getName() {
        return GriefLogger.translate("filter.radius").getString();
    }

    @Override
    public List<String> getOptions() {
        List<String> options = new java.util.ArrayList<>();
        options.add("#global");
        options.addAll(COMMON_RADII.stream().map(String::valueOf).toList());
        return options;
    }

    @Override
    public String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        String suggestionPrefix = "r:";

        if (suffix.isEmpty()) {
            List<String> suggestions = new java.util.ArrayList<>();
            suggestions.add(suggestionPrefix + "#global");
            COMMON_RADII.forEach(r -> suggestions.add(suggestionPrefix + r));
            return suggestions.toArray(String[]::new);
        }

        if (suffix.startsWith("#")) {
            if ("#global".startsWith(suffix.toLowerCase())) {
                return new String[]{suggestionPrefix + "#global"};
            }
            return new String[0];
        }

        if (suffix.chars().allMatch(Character::isDigit)) {
            return COMMON_RADII.stream()
                    .map(String::valueOf)
                    .filter(s -> s.startsWith(suffix))
                    .map(s -> suggestionPrefix + s)
                    .toArray(String[]::new);
        }

        return new String[0];
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException {
        String lower = suffix.toLowerCase().trim();

        if (lower.equals("#global") || lower.equals("global")) {
            return new RadiusFilter(GLOBAL_RADIUS, true);
        }

        if (lower.startsWith("c") && lower.length() > 1) {
            try {
                int chunks = Integer.parseInt(lower.substring(1));
                return new RadiusFilter(chunks * 16, false);  
            } catch (NumberFormatException e) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt()
                        .createWithContext(reader, lower.substring(1));
            }
        }

        try {
            int radius = Integer.parseInt(lower);
            if (radius < 0) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt()
                        .createWithContext(reader, lower);
            }
            return new RadiusFilter(radius, false);
        } catch (NumberFormatException e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt()
                    .createWithContext(reader, lower);
        }
    }

    @Override
    public String toString() {
        return "RadiusFilter{" +
                "radius=" + radius +
                ", isGlobal=" + isGlobal +
                '}';
    }

    public int getRadius() {
        return radius;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public int getMinX() {
        return isGlobal ? Integer.MIN_VALUE : position.x() - radius;
    }

    public int getMaxX() {
        return isGlobal ? Integer.MAX_VALUE : position.x() + radius;
    }

    public int getMinY() {
        return isGlobal ? Integer.MIN_VALUE : position.y() - radius;
    }

    public int getMaxY() {
        return isGlobal ? Integer.MAX_VALUE : position.y() + radius;
    }

    public int getMinZ() {
        return isGlobal ? Integer.MIN_VALUE : position.z() - radius;
    }

    public int getMaxZ() {
        return isGlobal ? Integer.MAX_VALUE : position.z() + radius;
    }

    public void setPosition(BlockPosition position) {
        this.position = position;
    }
}

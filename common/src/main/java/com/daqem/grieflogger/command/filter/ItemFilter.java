package com.daqem.grieflogger.command.filter;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for item/block filters (include/exclude).
 * Supports CoreProtect-style format: i:stone, i:diamond_ore,gold_ore
 * Also supports namespaced items: i:minecraft:stone
 */
public abstract class ItemFilter implements IFilter {

    private static List<String> cachedItemNames = null;

    private final List<Item> items;

    public ItemFilter() {
        this(new ArrayList<>());
    }

    public ItemFilter(List<Item> items) {
        this.items = items;
    }

    @Override
    public List<String> getOptions() {
        if (cachedItemNames == null) {
            cachedItemNames = BuiltInRegistries.ITEM.stream()
                    .map(item -> {
                        ResourceLocation loc = item.arch$registryName();
                        if (loc == null) return null;
                        return loc.getNamespace().equals("minecraft")
                                ? loc.getPath()
                                : loc.toString();
                    })
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());
        }
        return cachedItemNames;
    }

    /**
     * Get the short prefix for suggestions (i: for include, e: for exclude)
     */
    protected abstract char getSuggestionPrefix();

    @Override
    public String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        String suggestionPrefix = getSuggestionPrefix() + ":";
        List<String> allItems = getOptions();

        if (suffix.isEmpty()) {
            return allItems.stream()
                    .filter(s -> isCommonBlock(s))
                    .limit(15)
                    .map(s -> suggestionPrefix + s)
                    .toArray(String[]::new);
        }

        if (suffix.contains(",")) {
            int lastComma = suffix.lastIndexOf(",");
            String[] usedItems = suffix.substring(0, lastComma).split(",");
            Set<String> usedSet = new HashSet<>(Arrays.asList(usedItems));
            String currentPart = suffix.substring(lastComma + 1).toLowerCase();

            return allItems.stream()
                    .filter(s -> !usedSet.contains(s))
                    .filter(s -> s.toLowerCase().startsWith(currentPart))
                    .limit(10)
                    .map(s -> suggestionPrefix + suffix.substring(0, lastComma + 1) + s)
                    .toArray(String[]::new);
        }

        return allItems.stream()
                .filter(s -> s.toLowerCase().startsWith(suffix.toLowerCase()))
                .limit(15)
                .map(s -> suggestionPrefix + s)
                .toArray(String[]::new);
    }

    /**
     * Check if this is a commonly used block (for better default suggestions)
     */
    private boolean isCommonBlock(String name) {
        return name.contains("stone") || name.contains("dirt") || name.contains("ore")
                || name.contains("log") || name.contains("plank") || name.contains("glass")
                || name.contains("brick") || name.contains("chest") || name.contains("diamond")
                || name.contains("iron") || name.contains("gold") || name.contains("wood")
                || name.equals("grass_block") || name.equals("cobblestone") || name.equals("sand")
                || name.equals("gravel") || name.equals("obsidian") || name.equals("tnt");
    }

    protected List<Item> getItemsFromSuffix(StringReader reader, String suffix) throws CommandSyntaxException {
        String[] split = suffix.split(",");
        List<Item> foundItems = new ArrayList<>();

        for (String itemName : split) {
            String trimmed = itemName.trim().toLowerCase();

            Item found = BuiltInRegistries.ITEM.stream()
                    .filter(item -> {
                        ResourceLocation loc = item.arch$registryName();
                        if (loc == null) return false;

                        String fullName = loc.toString();
                        String path = loc.getPath();

                        return fullName.equalsIgnoreCase(trimmed)
                                || path.equalsIgnoreCase(trimmed)
                                || ("minecraft:" + trimmed).equalsIgnoreCase(fullName);
                    })
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                        .createWithContext(reader);
            }
            foundItems.add(found);
        }

        return foundItems;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "items=" + items.stream().map(Item::arch$registryName).toList() +
                '}';
    }

    public List<Item> getItems() {
        return items;
    }

    public List<String> getMaterials() {
        return items.stream()
                .map(Item::arch$registryName)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(x -> x.replace("minecraft:", ""))
                .toList();
    }
}

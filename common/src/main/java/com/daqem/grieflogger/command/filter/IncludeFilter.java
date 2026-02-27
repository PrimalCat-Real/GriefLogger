package com.daqem.grieflogger.command.filter;

import com.daqem.grieflogger.GriefLogger;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Include filter for lookup/rollback commands.
 * Supports CoreProtect-style format: i:stone, include:diamond_ore
 * Aliases: i, include, item, items, b, block, blocks
 */
public class IncludeFilter extends ItemFilter {

    public IncludeFilter() {
        super();
    }

    public IncludeFilter(List<Item> items) {
        super(items);
    }

    @Override
    public String getName() {
        return GriefLogger.translate("filter.include").getString();
    }

    @Override
    public List<String> getAllPrefixes() {
        return List.of("i", "include", "item", "items", "b", "block", "blocks");
    }

    @Override
    protected char getSuggestionPrefix() {
        return 'i';
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException {
        return new IncludeFilter(getItemsFromSuffix(reader, suffix));
    }
}

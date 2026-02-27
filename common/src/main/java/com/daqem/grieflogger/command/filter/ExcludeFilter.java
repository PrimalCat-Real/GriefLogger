package com.daqem.grieflogger.command.filter;

import com.daqem.grieflogger.GriefLogger;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Exclude filter for lookup/rollback commands.
 * Supports CoreProtect-style format: e:stone, exclude:dirt
 */
public class ExcludeFilter extends ItemFilter {

    public ExcludeFilter() {
        super();
    }

    public ExcludeFilter(List<Item> items) {
        super(items);
    }

    @Override
    public String getName() {
        return GriefLogger.translate("filter.exclude").getString();
    }

    @Override
    public List<String> getAllPrefixes() {
        return List.of("e", "exclude");
    }

    @Override
    protected char getSuggestionPrefix() {
        return 'e';
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException {
        return new ExcludeFilter(getItemsFromSuffix(reader, suffix));
    }
}

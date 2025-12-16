package com.daqem.grieflogger.util;

import com.daqem.grieflogger.GriefLogger;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.stream.Collectors;

public class BlockStateUtils {
    public static String serialize(BlockState state) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        if (!state.getValues().isEmpty()) {
            stringBuilder.append("[");
            stringBuilder.append(state.getValues().entrySet().stream()
                    .map(entry -> entry.getKey().getName() + "=" + getName(entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(",")));
            stringBuilder.append("]");
        }
        return stringBuilder.toString();
    }

    private static <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }

    public static BlockState deserialize(String stateString) {
        try {
            BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(
                    BuiltInRegistries.BLOCK.asLookup(),
                    new StringReader(stateString),
                    false
            );
            return result.blockState();
        } catch (CommandSyntaxException e) {
            GriefLogger.LOGGER.error("Failed to parse block state: " + stateString, e);
            return Blocks.AIR.defaultBlockState();
        }
    }
}

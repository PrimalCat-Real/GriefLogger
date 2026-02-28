package com.daqem.grieflogger.command;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.command.filter.FilterList;
import com.daqem.grieflogger.command.filter.RadiusFilter;
import com.daqem.grieflogger.command.filter.TimeFilter;
import com.daqem.grieflogger.model.BlockPosition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Quick lookup command for nearby changes.
 * Equivalent to: /gl lookup r:5 t:3d
 */
public class NearCommand implements ICommand {

    private static final int DEFAULT_RADIUS = 5;
    private static final long DEFAULT_TIME_HOURS = 72;  

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("near")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();

                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("This command can only be used by players."));
                        return 0;
                    }

                    FilterList filters = new FilterList();

                    RadiusFilter radiusFilter = new RadiusFilter(DEFAULT_RADIUS);
                    radiusFilter.setPosition(new BlockPosition(
                            player.getBlockX(),
                            player.getBlockY(),
                            player.getBlockZ()
                    ));
                    filters.setRadiusFilter(radiusFilter);

                    long startTime = System.currentTimeMillis() - (DEFAULT_TIME_HOURS * 60 * 60 * 1000);
                    filters.setTimeFilter(new TimeFilter(startTime, 0));

                    LookupCommand.executeLookup(player, filters, 1);

                    return 1;
                });
    }
}

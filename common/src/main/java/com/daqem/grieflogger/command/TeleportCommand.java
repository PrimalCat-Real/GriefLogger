package com.daqem.grieflogger.command;

import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Teleport command to quickly navigate to logged positions.
 * Usage: /gl tp <x> <y> <z>
 * Alias: /gl teleport <x> <y> <z>
 */
public class TeleportCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("tp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> teleport(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z")
                                        )))));
    }

    /**
     * Get the full "teleport" alias command
     */
    public LiteralArgumentBuilder<CommandSourceStack> getTeleportAlias() {
        return Commands.literal("teleport")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> teleport(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z")
                                        )))));
    }

    private int teleport(CommandSourceStack source, int x, int y, int z) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Theme.toMinecraft(Theme.error("This command can only be used by players.")));
            return 0;
        }

        double targetX = x + 0.5;
        double targetY = y;
        double targetZ = z + 0.5;

        player.teleportTo(
                source.getLevel(),
                targetX,
                targetY,
                targetZ,
                player.getYRot(),
                player.getXRot()
        );

        source.sendSuccess(() -> Theme.toMinecraft(
                Theme.success("Teleported to ")
                        .append(Theme.accent(x + ", " + y + ", " + z))
        ), false);

        return 1;
    }
}

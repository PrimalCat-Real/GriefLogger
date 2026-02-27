package com.daqem.grieflogger.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class GriefLoggerCommand {

    private static final ICommand INSPECT = new InspectCommand();
    private static final ICommand LOOKUP = new LookupCommand();
    private static final ICommand PAGE = new PageCommand();
    private static final ICommand ROLLBACK = new RollbackCommand();
    private static final ICommand RESTORE = new RestoreCommand();
    private static final ICommand UNDO = new UndoCommand();
    private static final ICommand NEAR = new NearCommand();
    private static final ICommand STATUS = new StatusCommand();
    private static final TeleportCommand TELEPORT = new TeleportCommand();
    private static final ICommand APPLY = new ApplyCommand();
    private static final ICommand CANCEL = new CancelCommand();
    private static final ICommand PURGE = new PurgeCommand();

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(commandWithPrefix("grieflogger"));
        dispatcher.register(commandWithPrefix("gl"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandWithPrefix(String prefix) {
        return Commands.literal(prefix)
                .then(INSPECT.getCommand())
                .then(LOOKUP.getCommand())
                .then(ROLLBACK.getCommand())
                .then(RESTORE.getCommand())
                .then(UNDO.getCommand())
                .then(PAGE.getCommand())
                .then(NEAR.getCommand())
                .then(STATUS.getCommand())
                .then(TELEPORT.getCommand())
                .then(TELEPORT.getTeleportAlias())
                .then(APPLY.getCommand())
                .then(CANCEL.getCommand())
                .then(PURGE.getCommand());
    }
}

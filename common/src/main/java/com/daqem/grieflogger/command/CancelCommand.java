package com.daqem.grieflogger.command;

import com.daqem.grieflogger.model.rollback.PreviewManager;
import com.daqem.grieflogger.model.rollback.PreviewSession;
import com.daqem.grieflogger.model.rollback.RollbackProgress;
import com.daqem.grieflogger.util.Theme;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Command to cancel a preview or active rollback/restore operation.
 * Usage: /gl cancel
 */
public class CancelCommand implements ICommand {

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand() {
        return Commands.literal("cancel")
                .requires(source -> source.hasPermission(2))
                .executes(context -> cancel(context.getSource()));
    }

    private int cancel(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Theme.toMinecraft(Theme.error("This command can only be used by players.")));
            return 0;
        }

        if (RollbackProgress.hasActiveOperation(player.getUUID())) {
            RollbackProgress.cancel(player.getUUID());
            source.sendSuccess(() -> Theme.toMinecraft(
                    Theme.success("Rollback operation cancelled. ")
                            .append(Theme.muted("Any processed changes remain in effect."))
            ), false);
            return 1;
        }

        Optional<PreviewSession> previewOpt = PreviewManager.getInstance().clearPreview(player.getUUID());

        if (previewOpt.isEmpty()) {
            source.sendFailure(Theme.toMinecraft(Theme.error("No active operation or preview to cancel.")));
            return 0;
        }

        PreviewSession preview = previewOpt.get();
        String operationName = preview.isRestore() ? "Restore" : "Rollback";

        source.sendSuccess(() -> Theme.toMinecraft(
                Theme.success(operationName + " preview cancelled. ")
                        .append(Theme.muted(preview.getBlockCount() + " blocks were not modified."))
        ), false);

        return 1;
    }
}

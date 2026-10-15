package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;

import java.util.*;

@Command(
        label = "transmuter",
        usage = {"status", "(resume|cancel) <operationId>"},
        permission = "server.transmuter",
        permissionTargeted = "server.transmuter.others")
public final class ArtifactTransmuterCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null || args == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        String action = args.get(0).toLowerCase(Locale.ROOT);
        try {
            if (action.equals("status") && args.size() == 1) {
                target.getArtifactTransmuter()
                        .inspect()
                        .forEach(line -> CommandHandler.sendMessage(sender, line));
            } else if ((action.equals("resume") || action.equals("cancel")) && args.size() == 2) {
                target.getArtifactTransmuter().reconcile(args.get(1), action.equals("resume"));
                CommandHandler.sendMessage(
                        sender,
                        "已处理 uid="
                                + target.getUid()
                                + " operation="
                                + args.get(1)
                                + " action="
                                + action);
            } else sendUsageMessage(sender);
        } catch (RuntimeException failure) {
            CommandHandler.sendMessage(sender, "核对未完成，保留未确认记录：" + failure.getMessage());
        }
    }
}

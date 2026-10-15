package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.*;
import emu.grasscutter.data.SoundRegistry;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketScenePlayerSoundNotify;

import java.util.List;

@Command(
        label = "sound",
        aliases = {"audio"},
        usage = {"[@<UID>] <audioname> [<x> <y> <z>]", "list [filter] [--page N]"},
        permission = "player.sound",
        permissionTargeted = "player.sound.others",
        targetRequirement = Command.TargetRequirement.NONE)
public final class SoundCommand implements CommandHandler {
    private static final int PAGE_SIZE = 20;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        if ("list".equalsIgnoreCase(args.get(0))) {
            list(sender, args.subList(1, args.size()));
            return;
        }
        if (targetPlayer == null || !targetPlayer.isOnline() || targetPlayer.getScene() == null) {
            sendUsageMessage(sender);
            CommandHandler.sendMessage(
                    sender, translate(sender, "commands.execution.need_target_online"));
            return;
        }
        var soundName = args.get(0);
        if (soundName.isBlank()) {
            sendUsageMessage(sender);
            return;
        }
        var playPosition = targetPlayer.getPosition();
        if (args.size() == 4) {
            try {
                float x, y, z;
                x = Float.parseFloat(args.get(1));
                y = Float.parseFloat(args.get(2));
                z = Float.parseFloat(args.get(3));
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
                    throw new NumberFormatException("Coordinates must be finite");
                playPosition = new Position(x, y, z);
            } catch (NumberFormatException ignored) {
                CommandHandler.sendMessage(
                        sender, translate(sender, "commands.execution.argument_error"));
                return;
            }
        } else if (args.size() > 1) {
            sendUsageMessage(sender);
            return;
        }
        targetPlayer
                .getScene()
                .broadcastPacket(new PacketScenePlayerSoundNotify(playPosition, soundName, 1));
    }

    private void list(Player sender, List<String> args) {
        int page = 1;
        String filter = null;
        boolean hasPage = false;
        for (int i = 0; i < args.size(); i++) {
            String value = args.get(i);
            if (value.equals("--page") || value.startsWith("--page=")) {
                if (hasPage) {
                    sendUsageMessage(sender);
                    return;
                }
                hasPage = true;
                if (value.equals("--page")) {
                    if (++i >= args.size()) {
                        sendUsageMessage(sender);
                        return;
                    }
                    value = args.get(i);
                } else value = value.substring("--page=".length());
                try {
                    page = Integer.parseInt(value);
                } catch (NumberFormatException invalid) {
                    sendUsageMessage(sender);
                    return;
                }
                if (page < 1) {
                    sendUsageMessage(sender);
                    return;
                }
            } else if (filter == null && !value.startsWith("--")) filter = value;
            else {
                sendUsageMessage(sender);
                return;
            }
        }
        var matches = SoundRegistry.names(filter);
        if (matches.isEmpty()) {
            CommandHandler.sendMessage(
                    sender,
                    "No observed sound names match. Names are learned from scene scripts and sound"
                        + " commands.");
            return;
        }
        int pages = 1 + (matches.size() - 1) / PAGE_SIZE;
        if (page > pages) {
            CommandHandler.sendMessage(sender, "Page out of range (1-" + pages + ").");
            return;
        }
        CommandHandler.sendMessage(
                sender,
                "Observed sounds ("
                        + page
                        + "/"
                        + pages
                        + ", "
                        + matches.size()
                        + " matches; not a complete catalog):");
        int start = (page - 1) * PAGE_SIZE;
        for (String name : matches.subList(start, Math.min(start + PAGE_SIZE, matches.size())))
            CommandHandler.sendMessage(sender, "  " + name);
        if (page < pages)
            CommandHandler.sendMessage(
                    sender,
                    "Next: sound list "
                            + (filter == null ? "" : filter + " ")
                            + "--page "
                            + (page + 1));
    }
}

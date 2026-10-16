package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.data.DungeonDropCatalog;
import emu.grasscutter.data.DungeonDropReferenceCatalog;
import emu.grasscutter.game.player.Player;

import java.util.List;

@Command(
        label = "reload",
        usage = {"[dungeon-drops|reference-drops]"},
        permission = "server.reload",
        targetRequirement = Command.TargetRequirement.NONE)
public final class ReloadCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_start"));

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("reference-drops")) {
            reloadReferenceDrops(sender);
            return;
        }

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("dungeon-drops")) {
            reloadDungeonDrops(sender);
            return;
        }

        Grasscutter.loadConfig();
        Grasscutter.loadLanguage();
        Grasscutter.getGameServer().getGachaSystem().load();
        Grasscutter.getGameServer().getShopSystem().load();

        reloadDungeonDrops(sender);
        var rewards = Grasscutter.getConfig().server.game.gameOptions.dungeonRewards;
        if (rewards != null && rewards.referenceDropTables) reloadReferenceDrops(sender);
    }

    private void reloadReferenceDrops(Player sender) {
        try {
            DungeonDropReferenceCatalog.reload();
            CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_done"));
        } catch (java.io.IOException invalid) {
            Grasscutter.getLogger()
                    .warn("Reference dungeon reload failed; keeping the previous catalog", invalid);
            CommandHandler.sendMessage(
                    sender, "DungeonDropReference.json: " + invalid.getMessage());
        }
    }

    private void reloadDungeonDrops(Player sender) {
        try {
            DungeonDropCatalog.reload();
            CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_done"));
        } catch (java.io.IOException invalid) {
            Grasscutter.getLogger()
                    .warn("Dungeon reward reload failed; keeping the previous catalog", invalid);
            CommandHandler.sendMessage(sender, "DungeonDrop.json: " + invalid.getMessage());
        }
    }
}

package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;

import java.util.List;
import java.util.Locale;

@Command(
        label = "dps",
        usage = {"[reset]"},
        permission = "player.dps",
        permissionTargeted = "player.dps.others")
public final class DpsCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null
                || target.getWorld() == null
                || args.size() > 1
                || args.size() == 1 && !"reset".equalsIgnoreCase(args.get(0))) {
            sendUsageMessage(sender);
            return;
        }
        var log = target.getWorld().getDamageLog();
        if (!args.isEmpty()) {
            log.reset();
            CommandHandler.sendMessage(sender, "当前世界伤害统计已重置。");
            return;
        }
        var s = log.snapshot();
        CommandHandler.sendMessage(
                sender,
                String.format(
                        Locale.ROOT,
                        "当前世界：伤害 %.2f · 承伤 %.2f · DPS %.2f · 活跃 %.2f 秒",
                        s.get("totalDamage").getAsDouble(),
                        s.get("totalTaken").getAsDouble(),
                        s.get("dps").getAsDouble(),
                        s.get("activeSeconds").getAsDouble()));
        for (var value : s.getAsJsonArray("rows")) {
            var r = value.getAsJsonObject();
            CommandHandler.sendMessage(
                    sender,
                    String.format(
                            Locale.ROOT,
                            "%s · UID %d · 伤害 %.2f · 承伤 %.2f · %d 次 · 最大 %.2f",
                            r.get("name").getAsString(),
                            r.get("uid").getAsInt(),
                            r.get("damage").getAsDouble(),
                            r.get("taken").getAsDouble(),
                            r.get("hits").getAsLong(),
                            r.get("maxHit").getAsDouble()));
        }
    }
}

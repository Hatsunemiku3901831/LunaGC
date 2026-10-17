package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.activity.ActivityCoinTransaction;
import emu.grasscutter.game.player.Player;

import java.util.*;

@Command(
        label = "activitycoins",
        usage = {
            "pending",
            "audit <activityId> <operationId>",
            "resume <activityId> <operationId>",
            "cancel <activityId> <operationId>"
        },
        permission = "server.activitycoins",
        permissionTargeted = "server.activitycoins.others")
public final class ActivityCoinsCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        try {
            String action = args.get(0).toLowerCase(Locale.ROOT);
            if (action.equals("pending") && args.size() == 1) {
                var rows = ActivityCoinTransaction.list(target);
                CommandHandler.sendMessage(
                        sender, rows.isEmpty() ? "没有活动货币操作记录。" : String.join("\n", rows));
            } else if (args.size() == 3 && Set.of("audit", "resume", "cancel").contains(action)) {
                int activity = Integer.parseInt(args.get(1));
                var transaction =
                        ActivityCoinTransaction.find(target, activity).getCoinTransaction();
                String result =
                        switch (action) {
                            case "audit" -> transaction.audit(args.get(2));
                            case "resume" -> transaction.resume(args.get(2));
                            default -> transaction.cancel(args.get(2));
                        };
                CommandHandler.sendMessage(sender, result);
            } else sendUsageMessage(sender);
        } catch (RuntimeException failure) {
            CommandHandler.sendMessage(sender, "活动货币核对未完成，保留记录：" + failure.getMessage());
        }
    }
}

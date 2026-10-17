package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.activity.ActivityCoinTransaction;
import emu.grasscutter.game.activity.ActivityWatcherRewards;
import emu.grasscutter.game.player.Player;

import java.util.*;

@Command(
        label = "activityrewards",
        usage = {
            "pending",
            "audit <activityId> <operationId>",
            "resume <activityId> <operationId>",
            "cancel <activityId> <operationId>"
        },
        permission = "server.activityrewards",
        permissionTargeted = "server.activityrewards.others")
public final class ActivityRewardsCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        try {
            String action = args.get(0).toLowerCase(Locale.ROOT);
            if (action.equals("pending") && args.size() == 1) {
                var rows = ActivityWatcherRewards.list(target);
                CommandHandler.sendMessage(
                        sender, rows.isEmpty() ? "没有待处理的活动领奖。" : String.join("\n", rows));
            } else if (args.size() == 3 && Set.of("audit", "resume", "cancel").contains(action)) {
                var rewards =
                        ActivityCoinTransaction.find(target, Integer.parseInt(args.get(1)))
                                .getWatcherRewards();
                String result =
                        switch (action) {
                            case "audit" -> rewards.audit(args.get(2));
                            case "resume" -> rewards.resume(args.get(2));
                            default -> rewards.cancel(args.get(2));
                        };
                CommandHandler.sendMessage(sender, result);
            } else sendUsageMessage(sender);
        } catch (RuntimeException failure) {
            CommandHandler.sendMessage(sender, "活动领奖核对未完成，保留计划：" + failure.getMessage());
        }
    }
}

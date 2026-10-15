package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.inventory.ItemUseRecovery;
import emu.grasscutter.game.player.Player;

import java.util.*;

@Command(
        label = "itemuse",
        usage = {"status", "(refund-cost|settle) <operationId>"},
        permission = "server.itemuse",
        permissionTargeted = "server.itemuse.others")
public final class ItemUseCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null || args == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        var recovery = new ItemUseRecovery(target);
        String action = args.get(0).toLowerCase(Locale.ROOT);
        try {
            if (action.equals("status") && args.size() == 1)
                recovery.inspect().forEach(line -> CommandHandler.sendMessage(sender, line));
            else if ((action.equals("refund-cost") || action.equals("settle"))
                    && args.size() == 2) {
                recovery.resolve(args.get(1), action);
                CommandHandler.sendMessage(
                        sender,
                        "已处理 uid="
                                + target.getUid()
                                + " operation="
                                + args.get(1)
                                + "；"
                                + (action.equals("refund-cost") ? "仅退库存成本" : "已接受当前消耗与效果")
                                + "，饱食度、冷却和角色效果保持当前状态。");
            } else sendUsageMessage(sender);
        } catch (RuntimeException failure) {
            CommandHandler.sendMessage(sender, "物品使用核对未完成，保留未确认记录：" + failure.getMessage());
        }
    }
}

package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.managers.mapmark.MaterialTracking;
import emu.grasscutter.game.player.Player;

import java.util.List;

@Command(
        label = "trackmat",
        aliases = {"trackmaterial"},
        usage = {"<itemId|materialName>", "clear"},
        permission = "player.trackmaterial",
        permissionTargeted = "player.trackmaterial.others")
public final class TrackMatCommand implements CommandHandler {
    private static final MaterialTracking tracking = new MaterialTracking();

    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        try {
            if (args.size() == 1 && "clear".equalsIgnoreCase(args.get(0))) {
                CommandHandler.sendMessage(sender, "已清除 " + tracking.clear(target) + " 个材料标记。");
                return;
            }
            int id = tracking.resolve(String.join(" ", args));
            if (id <= 0) {
                CommandHandler.sendMessage(sender, "未识别材料，请使用完整名称或材料 ID。");
                return;
            }
            int count = tracking.track(target, id);
            CommandHandler.sendMessage(
                    sender,
                    count > 0 ? "已标记材料 " + id + "，共 " + count + " 处。" : "当前场景没有可用采集点，或地图标记已满。");
        } catch (RuntimeException failure) {
            CommandHandler.sendMessage(sender, "材料标记失败：" + failure.getMessage());
        }
    }
}

package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;

import java.util.*;

@Command(
        label = "build",
        usage = {"<gadgetId> [x y z [rx ry rz]] [temporary]", "list", "remove <buildingId>"},
        permission = "server.build",
        permissionTargeted = "server.build.others")
public final class BuildCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> arguments) {
        if (target == null || target.getScene() == null) {
            sendUsageMessage(sender);
            return;
        }
        var args = new ArrayList<>(arguments);
        var manager = target.getScene().getBuildingManager();
        try {
            if (args.isEmpty() || args.size() == 1 && "list".equalsIgnoreCase(args.get(0))) {
                var rows = manager.list(target);
                if (rows.isEmpty()) CommandHandler.sendMessage(sender, "当前场景没有建造物。");
                for (var row : rows)
                    CommandHandler.sendMessage(
                            sender,
                            row.getId()
                                    + " · "
                                    + row.getGadgetId()
                                    + " · "
                                    + row.getPosition()
                                    + " · "
                                    + (row.isPersistent() ? "已保存" : "临时"));
                return;
            }
            if (args.size() == 2 && "remove".equalsIgnoreCase(args.get(0))) {
                CommandHandler.sendMessage(
                        sender, manager.remove(target, args.get(1)) ? "建造物已移除。" : "未找到这个建造物。");
                return;
            }
            boolean persistent = true;
            if (!args.isEmpty() && "temporary".equalsIgnoreCase(args.get(args.size() - 1))) {
                persistent = false;
                args.remove(args.size() - 1);
            }
            if (args.size() != 1 && args.size() != 4 && args.size() != 7) {
                sendUsageMessage(sender);
                return;
            }
            int gadget = Integer.parseInt(args.get(0));
            var pos = new Position(target.getPosition());
            var rot = new Position(target.getRotation());
            if (args.size() == 7)
                rot.set(
                        CommandHelpers.parseRelative(args.get(4), rot.getX()),
                        CommandHelpers.parseRelative(args.get(5), rot.getY()),
                        CommandHelpers.parseRelative(args.get(6), rot.getZ()));
            if (args.size() >= 4)
                pos = CommandHelpers.parsePosition(args.get(1), args.get(2), args.get(3), pos, rot);
            var row = manager.create(target, gadget, pos, rot, persistent);
            CommandHandler.sendMessage(
                    sender,
                    (manager.isSpawned(row.getId()) ? "建造完成：" : "已保存，场景已满，等待恢复：")
                            + row.getId()
                            + " · "
                            + (persistent ? "已保存" : "临时"));
        } catch (RuntimeException error) {
            CommandHandler.sendMessage(sender, "建造操作失败：" + error.getMessage());
        }
    }
}

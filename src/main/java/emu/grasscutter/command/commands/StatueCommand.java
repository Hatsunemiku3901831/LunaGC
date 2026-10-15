package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketGetScenePointRsp;

import java.util.*;

/** Adapted from AstaPS StatueCommand to this project's command and progress managers. */
@Command(
        label = "statue",
        aliases = {"sots"},
        usage = {
            "city [cityId]",
            "level <cityId> <level>",
            "reset [cityId=8]",
            "(lock|unlock|status) [pointId]"
        },
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others")
public final class StatueCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player target, List<String> args) {
        if (target == null || target.getScene() == null || args == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        String operation = args.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "city" -> {
                    if (args.size() > 2) {
                        sendUsageMessage(sender);
                        return;
                    }
                    Integer id = args.size() == 2 ? Integer.parseInt(args.get(1)) : null;
                    int scene =
                            id == null
                                    ? target.getSceneId()
                                    : target.getSotsManager().citySceneId(id);
                    var cities =
                            target.getSotsManager().citySnapshot(scene).stream()
                                    .filter(x -> id == null || x.getCityId() == id)
                                    .toList();
                    if (cities.isEmpty()) {
                        CommandHandler.sendMessage(sender, "未找到此场景的神像城市。");
                        return;
                    }
                    for (var city : cities) {
                        var saved = target.getCityInfoData();
                        var info = saved == null ? null : saved.get(city.getCityId());
                        var pending = info == null ? null : info.getPendingOffer();
                        CommandHandler.sendMessage(
                                sender,
                                "uid="
                                        + target.getUid()
                                        + " city="
                                        + city.getCityId()
                                        + " level="
                                        + city.getLevel()
                                        + " crystal="
                                        + city.getCrystalNum()
                                        + (pending == null
                                                ? ""
                                                : " pending="
                                                        + pending.getOperationId()
                                                        + " state="
                                                        + pending.getState()));
                    }
                }
                case "level", "reset" -> {
                    if (operation.equals("level") && args.size() != 3
                            || operation.equals("reset") && args.size() > 2) {
                        sendUsageMessage(sender);
                        return;
                    }
                    int city = args.size() > 1 ? Integer.parseInt(args.get(1)) : 8;
                    int level = operation.equals("reset") ? 1 : Integer.parseInt(args.get(2));
                    target.getSotsManager().setCityLevel(city, level);
                    CommandHandler.sendMessage(
                            sender,
                            "已设置 uid="
                                    + target.getUid()
                                    + " city="
                                    + city
                                    + " level="
                                    + level
                                    + " crystal=0；未发放奖励或修改体力。");
                }
                case "lock", "unlock", "status" -> {
                    if (args.size() > 2) {
                        sendUsageMessage(sender);
                        return;
                    }
                    int scene = target.getSceneId();
                    int point = args.size() == 2 ? Integer.parseInt(args.get(1)) : nearest(target);
                    var entry = GameData.getScenePointEntryById(scene, point);
                    if (point <= 0
                            || entry == null
                            || entry.getSceneId() != scene
                            || entry.getPointData() == null
                            || entry.getPointData().getId() != point
                            || entry.getPointData().getMaxSpringVolume() <= 0) {
                        CommandHandler.sendMessage(sender, "未找到神像，请指定当前场景的神像 pointId。");
                        return;
                    }
                    if (operation.equals("lock"))
                        target.getProgressManager().lockTransPoint(scene, point);
                    else if (operation.equals("unlock"))
                        target.getProgressManager().unlockTransPoint(scene, point, true);
                    if (!operation.equals("status"))
                        target.sendPacket(new PacketGetScenePointRsp(target, scene));
                    CommandHandler.sendMessage(
                            sender,
                            "scene="
                                    + scene
                                    + " point="
                                    + point
                                    + " locked="
                                    + target.isScenePointForceLocked(scene, point)
                                    + " discovered="
                                    + target.getUnlockedScenePoints()
                                            .getOrDefault(scene, Set.of())
                                            .contains(point)
                                    + " spring="
                                    + entry.getPointData().getMaxSpringVolume());
                }
                default -> sendUsageMessage(sender);
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            CommandHandler.sendMessage(sender, "神像操作失败：" + error.getMessage());
        } catch (RuntimeException error) {
            emu.grasscutter.Grasscutter.getLogger()
                    .error("Statue command failed for UID {}", target.getUid(), error);
            CommandHandler.sendMessage(sender, "神像操作未确认，请检查服务器日志。");
        }
    }

    private static int nearest(Player player) {
        var position = player.getPosition();
        if (position == null) return -1;
        int best = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (var entry : GameData.getScenePointEntryMap().values()) {
            if (entry == null || entry.getSceneId() != player.getSceneId()) continue;
            PointData point = entry.getPointData();
            if (point == null
                    || point.getId() <= 0
                    || point.getMaxSpringVolume() <= 0
                    || point.getPos() == null) continue;
            double dx = (double) point.getPos().getX() - position.getX();
            double dy = (double) point.getPos().getY() - position.getY();
            double dz = (double) point.getPos().getZ() - position.getZ();
            double next = dx * dx + dy * dy + dz * dz;
            if (Double.isFinite(next)
                    && (next < distance || next == distance && point.getId() < best)) {
                distance = next;
                best = point.getId();
            }
        }
        return best;
    }
}

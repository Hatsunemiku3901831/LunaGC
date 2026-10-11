package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketActivityInfoNotify;

import java.util.List;

/** Backend entry until the current client's anonymous special-reward RPC is identified. */
@Command(
        label = "aster",
        usage = {"status", "take"},
        permission = "activity.aster",
        permissionTargeted = "activity.aster.others")
public final class AsterCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() != 1
                || !(args.get(0).equalsIgnoreCase("status")
                        || args.get(0).equalsIgnoreCase("take"))) {
            sendUsageMessage(sender);
            return;
        }
        var data =
                targetPlayer
                        .getActivityManager()
                        .getPlayerActivityDataMap()
                        .get(AsterSchedule.ACTIVITY_ID);
        boolean take = args.get(0).equalsIgnoreCase("take");
        int code;
        try {
            code =
                    take
                            ? AsterSpecialRewards.take(data)
                            : AsterSpecialRewards.status(
                                    data,
                                    data == null
                                            ? null
                                            : ActivityManager.getScheduleActivityConfigMap()
                                                    .get(data.getScheduleId()),
                                    targetPlayer.getLevel(),
                                    System.currentTimeMillis());
        } catch (RuntimeException failure) {
            Grasscutter.getLogger()
                    .error(
                            "Aster special reward command failed: UID {}",
                            targetPlayer.getUid(),
                            failure);
            code = Retcode.RET_SVR_ERROR_VALUE;
        }
        if (code == 0 && take) {
            targetPlayer.sendPacket(
                    new PacketActivityInfoNotify(
                            targetPlayer
                                    .getActivityManager()
                                    .getInfoProtoByActivityId(AsterSchedule.ACTIVITY_ID)));
        }
        CommandHandler.sendTranslatedMessage(
                sender,
                code == 0
                        ? (take ? "commands.aster.taken" : "commands.aster.ready")
                        : "commands.aster.rejected",
                Retcode.forNumber(code) == null ? code : Retcode.forNumber(code).name(),
                code);
    }
}

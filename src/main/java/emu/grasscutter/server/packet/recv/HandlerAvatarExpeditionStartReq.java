package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.expedition.ExpeditionCatalog;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionStartReqOuterClass.AvatarExpeditionStartReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionStartRsp;
import emu.grasscutter.utils.Utils;

import java.util.*;

@Opcodes(PacketOpcodes.AvatarExpeditionStartReq)
public class HandlerAvatarExpeditionStartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        AvatarExpeditionStartReq req;
        try {
            req = AvatarExpeditionStartReq.parseFrom(payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | NullPointerException malformed) {
            session.send(
                    new PacketAvatarExpeditionStartRsp(
                            player.getExpeditionInfo(), Retcode.RET_FAIL_VALUE));
            return;
        }

        // Validate the whole batch before assigning any owned avatar to a resource-backed trip.
        synchronized (player.getInventory()) {
            if (req.getBasicInfoListCount() == 0
                    || (long) player.getExpeditionInfo().size() + req.getBasicInfoListCount()
                            > player.getExpeditionLimit()) {
                session.send(
                        new PacketAvatarExpeditionStartRsp(
                                player.getExpeditionInfo(), Retcode.RET_FAIL_VALUE));
                return;
            }
            var guids = new HashSet<Long>();
            var places = new HashSet<Integer>();
            player.getExpeditionInfo().values().forEach(info -> places.add(info.getExpId()));
            for (var info : req.getBasicInfoListList()) {
                var current = player.getExpeditionInfo(info.getAvatarGuid());
                var avatar = player.getAvatars().getAvatarByGuid(info.getAvatarGuid());
                var location = GameData.getExpeditionDataMap().get(info.getExpId());
                var rewards =
                        session.getServer().getExpeditionSystem() == null
                                ? null
                                : session.getServer()
                                        .getExpeditionSystem()
                                        .getResolvedRewardDataList(info.getExpId());
                if (current != null
                        || !guids.add(info.getAvatarGuid())
                        || !places.add(info.getExpId())
                        || avatar == null
                        || avatar.getTrialAvatarId() != 0
                        || location == null
                        || location.getId() != info.getExpId()
                        || !ExpeditionCatalog.open(player, location)
                        || info.getHourTime() <= 0
                        || rewards == null
                        || rewards.stream()
                                        .filter(Objects::nonNull)
                                        .filter(r -> r.getHourTime() == info.getHourTime())
                                        .count()
                                != 1) {
                    session.send(
                            new PacketAvatarExpeditionStartRsp(
                                    player.getExpeditionInfo(),
                                    current != null && current.getRewardState() == 1
                                            ? Retcode.RET_SVR_ERROR_VALUE
                                            : Retcode.RET_FAIL_VALUE));
                    return;
                }
            }
            int startTime = Utils.getCurrentSeconds();
            for (var info : req.getBasicInfoListList()) {
                player.addExpeditionInfo(
                        info.getAvatarGuid(), info.getExpId(), info.getHourTime(), startTime);
            }
            player.save();
            session.send(new PacketAvatarExpeditionStartRsp(player.getExpeditionInfo()));
        }
    }
}

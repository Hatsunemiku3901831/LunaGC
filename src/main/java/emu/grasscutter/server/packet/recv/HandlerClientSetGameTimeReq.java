package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeReqOuterClass.ClientSetGameTimeReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketClientSetGameTimeRsp;
import emu.grasscutter.server.packet.send.PacketPlayerGameTimeNotify;

import java.util.List;

/**
 * Client absolute world time is expressed in total game minutes, not minutes of the current day.
 */
@Opcodes(PacketOpcodes.ClientSetGameTimeReq)
public final class HandlerClientSetGameTimeReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = ClientSetGameTimeReq.parseFrom(payload);
        int sequence =
                header == null || header.length == 0
                        ? 0
                        : PacketHead.parseFrom(header).getClientSequenceId();
        var player = session.getPlayer();
        var world = player == null ? null : player.getWorld();
        int retcode;
        if (world == null || world.getHost() == null) {
            retcode = Retcode.RET_FAIL_VALUE;
        } else if (!world.getPlayers().contains(player) || world.getHost().getWorld() != world) {
            retcode = Retcode.RET_FORBIDDEN_VALUE;
        } else if (world.isTimeLocked()) {
            retcode = Retcode.RET_PLAYER_TIME_LOCKED_VALUE;
        } else {
            // Settle elapsed time before replacing it, so an old clock origin is not added again.
            world.getTotalGameTimeMinutes();
            long updated = Integer.toUnsignedLong(req.getGameTime()) * 1000L;
            world.changeTime(updated);
            world.getHost().updatePlayerGameTime(updated);
            for (var member : List.copyOf(world.getPlayers())) {
                if (member.getWorld() != world) continue;
                member.sendPacket(new PacketPlayerGameTimeNotify(member));
                member.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_GAME_TIME_TICK);
            }
            retcode = 0;
        }
        session.send(
                new PacketClientSetGameTimeRsp(
                        world == null ? 0 : world.getTotalGameTimeMinutes(),
                        req.getClientGameTime(),
                        sequence,
                        retcode));
    }
}

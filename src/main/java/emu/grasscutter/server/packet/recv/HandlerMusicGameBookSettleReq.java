package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookSettleReqOuterClass.MusicGameBookSettleReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.MusicGameBookSettleReq)
public final class HandlerMusicGameBookSettleReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MusicGameBookSettleReq.parseFrom(payload);
        var response =
                new BasePacket(
                        PacketOpcodes.MusicGameBookSettleRsp,
                        header == null ? 0 : PacketHead.parseFrom(header).getClientSequenceId());
        response.setData(session.getPlayer().getMusicGameBook().settlePerformance(request));
        session.send(response);
    }
}

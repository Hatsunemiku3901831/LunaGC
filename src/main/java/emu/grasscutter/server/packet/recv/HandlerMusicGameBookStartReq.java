package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookStartReqOuterClass.MusicGameBookStartReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.MusicGameBookStartReq)
public final class HandlerMusicGameBookStartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MusicGameBookStartReq.parseFrom(payload);
        var response =
                new BasePacket(
                        PacketOpcodes.MusicGameBookStartRsp,
                        header == null ? 0 : PacketHead.parseFrom(header).getClientSequenceId());
        response.setData(session.getPlayer().getMusicGameBook().startPerformance(request));
        session.send(response);
    }
}

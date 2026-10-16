package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookSaveModsReqOuterClass.MusicGameBookSaveModsReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.MusicGameBookSaveModsReq)
public final class HandlerMusicGameBookSaveModsReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MusicGameBookSaveModsReq.parseFrom(payload);
        var response =
                new BasePacket(
                        PacketOpcodes.MusicGameBookSaveModsRsp,
                        header == null ? 0 : PacketHead.parseFrom(header).getClientSequenceId());
        response.setData(session.getPlayer().getMusicGameBook().saveMods(request));
        session.send(response);
    }
}

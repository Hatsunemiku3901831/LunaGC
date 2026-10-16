package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookSaveSettingsReqOuterClass.MusicGameBookSaveSettingsReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.MusicGameBookSaveSettingsReq)
public final class HandlerMusicGameBookSaveSettingsReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = MusicGameBookSaveSettingsReq.parseFrom(payload);
        var response =
                new BasePacket(
                        PacketOpcodes.MusicGameBookSaveSettingsRsp,
                        header == null ? 0 : PacketHead.parseFrom(header).getClientSequenceId());
        response.setData(session.getPlayer().getMusicGameBook().saveSettings(request));
        session.send(response);
    }
}

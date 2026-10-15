package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.LevelupCityReqOuterClass.LevelupCityReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketLevelupCityRsp;

@Opcodes(PacketOpcodes.LevelupCityReq)
public class HandlerLevelupCityReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        final LevelupCityReq req;
        try {
            req = LevelupCityReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.send(new PacketLevelupCityRsp(0, 1, 0, 0, 0, Retcode.RET_FAIL_VALUE));
            return;
        }

        // Level up city
        session.getPlayer()
                .getSotsManager()
                .levelUpSotS(req.getAreaId(), req.getSceneId(), req.getItemNum());
    }
}

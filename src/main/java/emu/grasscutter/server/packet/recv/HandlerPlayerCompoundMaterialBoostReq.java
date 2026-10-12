package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCompoundMaterialBoostReqOuterClass.PlayerCompoundMaterialBoostReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerCompoundMaterialBoostRsp;

import java.util.List;

@Opcodes(PacketOpcodes.PlayerCompoundMaterialBoostReq)
public final class HandlerPlayerCompoundMaterialBoostReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        PlayerCompoundMaterialBoostReq request;
        try {
            request = PlayerCompoundMaterialBoostReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.getPlayer()
                    .sendPacket(
                            new PacketPlayerCompoundMaterialBoostRsp(
                                    Retcode.RET_FAIL_VALUE, 0, List.of(), List.of()));
            return;
        }
        session.getPlayer().getCookingCompoundManager().handleCompoundBoost(request);
    }
}

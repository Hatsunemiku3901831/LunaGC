package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeAllForgeOutputReqOuterClass.TakeAllForgeOutputReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeAllForgeOutputRsp;

import java.util.List;

@Opcodes(PacketOpcodes.TakeAllForgeOutputReq)
public final class HandlerTakeAllForgeOutputReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        try {
            TakeAllForgeOutputReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.getPlayer()
                    .sendPacket(
                            new PacketTakeAllForgeOutputRsp(
                                    Retcode.RET_FAIL_VALUE, List.of(), List.of(), List.of()));
            return;
        }
        session.getPlayer().getForgingManager().handleTakeAllForgeOutputReq();
    }
}

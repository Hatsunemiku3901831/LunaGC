package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerBuffSelectReqOuterClass.TowerBuffSelectReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerBuffSelectRsp;

@Opcodes(PacketOpcodes.TowerBuffSelectReq)
public class HandlerTowerBuffSelectReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int id = TowerBuffSelectReq.parseFrom(payload).getTowerBuffId();
        boolean accepted = session.getPlayer().getTowerManager().trySelectBlessing(id);
        session.send(new PacketTowerBuffSelectRsp(id, accepted));
    }
}

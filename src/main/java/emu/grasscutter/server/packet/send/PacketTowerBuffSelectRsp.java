package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerBuffSelectRspOuterClass.TowerBuffSelectRsp;

public class PacketTowerBuffSelectRsp extends BasePacket {
    public PacketTowerBuffSelectRsp(int id, boolean accepted) {
        super(PacketOpcodes.TowerBuffSelectRsp);
        setData(
                TowerBuffSelectRsp.newBuilder()
                        .setTowerBuffId(id)
                        .setRetcode((accepted ? Retcode.RET_SUCC : Retcode.RET_FAIL).getNumber()));
    }
}

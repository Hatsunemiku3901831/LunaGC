package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerTeamSelectRspOuterClass.TowerTeamSelectRsp;

public class PacketTowerTeamSelectRsp extends BasePacket {

    public PacketTowerTeamSelectRsp() {
        this(Retcode.RET_SUCC);
    }

    public PacketTowerTeamSelectRsp(Retcode retcode) {
        super(PacketOpcodes.TowerTeamSelectRsp);

        TowerTeamSelectRsp proto =
                TowerTeamSelectRsp.newBuilder().setRetcode(retcode.getNumber()).build();

        this.setData(proto);
    }
}

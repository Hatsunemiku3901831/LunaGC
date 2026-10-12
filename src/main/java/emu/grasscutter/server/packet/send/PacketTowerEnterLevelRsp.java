package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerEnterLevelRspOuterClass.TowerEnterLevelRsp;

public class PacketTowerEnterLevelRsp extends BasePacket {

    public PacketTowerEnterLevelRsp(int floorId, int levelIndex) {
        this(floorId, levelIndex, Retcode.RET_SUCC);
    }

    public PacketTowerEnterLevelRsp(int floorId, int levelIndex, Retcode retcode) {
        super(PacketOpcodes.TowerEnterLevelRsp);

        var proto =
                TowerEnterLevelRsp.newBuilder()
                        .setFloorId(floorId)
                        .setLevelIndex(levelIndex)
                        .setRetcode(retcode.getNumber());
        if (retcode == Retcode.RET_SUCC)
            proto.addTowerBuffIdList(4).addTowerBuffIdList(28).addTowerBuffIdList(18);

        this.setData(proto.build());
    }
}

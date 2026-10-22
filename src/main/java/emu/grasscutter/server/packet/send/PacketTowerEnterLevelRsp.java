package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerEnterLevelRspOuterClass.TowerEnterLevelRsp;
import java.util.List;

public class PacketTowerEnterLevelRsp extends BasePacket {

    public PacketTowerEnterLevelRsp(int floorId, int levelIndex) {
        this(floorId, levelIndex, Retcode.RET_SUCC);
    }

    public PacketTowerEnterLevelRsp(int floorId, int levelIndex, Retcode retcode) {
        this(floorId, levelIndex, retcode, List.of());
    }

    public PacketTowerEnterLevelRsp(int floorId, int levelIndex, Retcode retcode, List<Integer> offers) {
        super(PacketOpcodes.TowerEnterLevelRsp);

        var proto =
                TowerEnterLevelRsp.newBuilder()
                        .setFloorId(floorId)
                        .setLevelIndex(levelIndex)
                        .setRetcode(retcode.getNumber());
        if (retcode == Retcode.RET_SUCC) proto.addAllTowerBuffIdList(offers);

        this.setData(proto.build());
    }
}

package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.tower.TowerLevelRecord;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerFloorRecordChangeNotifyOuterClass.TowerFloorRecordChangeNotify;

public class PacketTowerFloorRecordChangeNotify extends BasePacket {
    public PacketTowerFloorRecordChangeNotify(TowerLevelRecord record, boolean canEnterScheduleFloor) {
        super(PacketOpcodes.TowerFloorRecordChangeNotify);
        setData(TowerFloorRecordChangeNotify.newBuilder()
                .addTowerFloorRecordList(record.toProto())
                .setIsFinishedEntranceFloor(canEnterScheduleFloor).build());
    }
}

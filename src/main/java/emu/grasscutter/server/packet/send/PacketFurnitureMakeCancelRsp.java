package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeCancelRspOuterClass.FurnitureMakeCancelRsp;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;

import java.util.List;

public final class PacketFurnitureMakeCancelRsp extends BasePacket {
    public PacketFurnitureMakeCancelRsp(
            int retcode, int makeId, List<FurnitureMakeData> remaining) {
        super(PacketOpcodes.FurnitureMakeCancelRsp);
        var response = FurnitureMakeCancelRsp.newBuilder().setRetcode(retcode).setMakeId(makeId);
        if (retcode == 0)
            response.setFurnitureMakeSlot(
                    FurnitureMakeSlot.newBuilder()
                            .addAllFurnitureMakeDataList(
                                    remaining == null ? List.of() : remaining));
        setData(response.build());
    }
}

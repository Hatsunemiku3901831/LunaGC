package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;
import emu.grasscutter.net.proto.FurnitureMakeStartBatchRspOuterClass.FurnitureMakeStartBatchRsp;

import java.util.List;

public final class PacketFurnitureMakeStartBatchRsp extends BasePacket {
    public PacketFurnitureMakeStartBatchRsp(int retcode, List<FurnitureMakeData> jobs) {
        super(PacketOpcodes.FurnitureMakeStartBatchRsp);
        var response = FurnitureMakeStartBatchRsp.newBuilder().setRetcode(retcode);
        if (retcode == 0 && jobs != null)
            response.setFurnitureMakeSlot(
                    FurnitureMakeSlot.newBuilder().addAllFurnitureMakeDataList(jobs));
        setData(response.build());
    }
}

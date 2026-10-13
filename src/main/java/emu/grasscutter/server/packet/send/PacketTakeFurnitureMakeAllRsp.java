package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeDataOuterClass.FurnitureMakeData;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.TakeFurnitureMakeAllRspOuterClass.TakeFurnitureMakeAllRsp;

import java.util.List;

public final class PacketTakeFurnitureMakeAllRsp extends BasePacket {
    public PacketTakeFurnitureMakeAllRsp(
            int retcode, List<ItemParam> products, List<FurnitureMakeData> remaining) {
        super(PacketOpcodes.TakeFurnitureMakeAllRsp);
        var response = TakeFurnitureMakeAllRsp.newBuilder().setRetcode(retcode);
        if (retcode == 0) {
            if (products != null) response.addAllOutputItemList(products);
            response.setFurnitureMakeSlot(
                    FurnitureMakeSlot.newBuilder()
                            .addAllFurnitureMakeDataList(
                                    remaining == null ? List.of() : remaining));
        }
        setData(response.build());
    }
}

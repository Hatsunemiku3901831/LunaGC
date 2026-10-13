package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import java.util.List;

public class PacketTakeFurnitureMakeRsp extends BasePacket {

    public PacketTakeFurnitureMakeRsp(
            int ret,
            int makeId,
            List<ItemParamOuterClass.ItemParam> output,
            List<FurnitureMakeDataOuterClass.FurnitureMakeData> others) {
        super(PacketOpcodes.TakeFurnitureMakeRsp);

        var proto = TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp.newBuilder();

        proto.setRetcode(ret).setMakeId(makeId);

        // The fingerprint-checked 7.1 consumer uses native field 12 for primary reward hints.
        if (ret == 0 && output != null) proto.addAllOutputItemList(output);

        if (others != null) {
            proto.setFurnitureMakeSlot(
                    FurnitureMakeSlotOuterClass.FurnitureMakeSlot.newBuilder()
                            .addAllFurnitureMakeDataList(others)
                            .build());
        }

        this.setData(proto);
    }
}

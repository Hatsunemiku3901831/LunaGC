package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CompoundQueueDataOuterClass.CompoundQueueData;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.PlayerCompoundMaterialBoostRspOuterClass.PlayerCompoundMaterialBoostRsp;

import java.util.List;

public final class PacketPlayerCompoundMaterialBoostRsp extends BasePacket {
    public PacketPlayerCompoundMaterialBoostRsp(
            int retcode, int takeStatus, List<CompoundQueueData> queues, List<ItemParam> items) {
        super(PacketOpcodes.PlayerCompoundMaterialBoostRsp);
        setData(
                PlayerCompoundMaterialBoostRsp.newBuilder()
                        .setRetcode(retcode)
                        .setTakeStatusValue(takeStatus)
                        .addAllCompoundQueDataList(queues)
                        .addAllTakeItemList(items)
                        .build());
    }
}

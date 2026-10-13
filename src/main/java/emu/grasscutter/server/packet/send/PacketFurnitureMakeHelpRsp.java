package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeHelpDataOuterClass.FurnitureMakeHelpData;
import emu.grasscutter.net.proto.FurnitureMakeHelpRspOuterClass.FurnitureMakeHelpRsp;

import java.util.List;

public final class PacketFurnitureMakeHelpRsp extends BasePacket {
    public PacketFurnitureMakeHelpRsp(int retcode, List<FurnitureMakeHelpData> confirmed) {
        super(PacketOpcodes.FurnitureMakeHelpRsp);
        var response = FurnitureMakeHelpRsp.newBuilder().setRetcode(retcode);
        if (retcode == 0 && confirmed != null) response.addAllHelpDataList(confirmed);
        setData(response.build());
    }
}

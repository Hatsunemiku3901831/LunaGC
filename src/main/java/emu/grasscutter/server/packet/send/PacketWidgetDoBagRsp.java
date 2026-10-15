package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WidgetDoBagRspOuterClass;

public class PacketWidgetDoBagRsp extends BasePacket {

    public PacketWidgetDoBagRsp(int materialId) {
        this(0, materialId);
    }

    public PacketWidgetDoBagRsp(int retcode, int materialId) {
        super(PacketOpcodes.WidgetDoBagRsp);

        WidgetDoBagRspOuterClass.WidgetDoBagRsp proto =
                WidgetDoBagRspOuterClass.WidgetDoBagRsp.newBuilder()
                        .setMaterialId(materialId)
                        .setRetcode(retcode)
                        .build();

        this.setData(proto);
    }
}

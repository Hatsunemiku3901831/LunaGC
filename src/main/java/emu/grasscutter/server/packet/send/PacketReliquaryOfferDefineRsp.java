package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryOfferDefineRspOuterClass.ReliquaryOfferDefineRsp;

public final class PacketReliquaryOfferDefineRsp extends BasePacket {
    public PacketReliquaryOfferDefineRsp(ReliquaryOfferDefineRsp data, int sequence) {
        super(PacketOpcodes.ReliquaryOfferDefineRsp, sequence);
        setData(data);
    }
}

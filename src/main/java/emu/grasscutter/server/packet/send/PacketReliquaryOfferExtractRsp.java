package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryOfferExtractRspOuterClass.ReliquaryOfferExtractRsp;

public final class PacketReliquaryOfferExtractRsp extends BasePacket {
    public PacketReliquaryOfferExtractRsp(ReliquaryOfferExtractRsp response, int sequence) {
        super(PacketOpcodes.ReliquaryOfferExtractRsp, sequence);
        setData(response);
    }
}

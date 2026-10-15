package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryDustSelectRspOuterClass.ReliquaryDustSelectRsp;

public final class PacketReliquaryDustSelectRsp extends BasePacket {
    public PacketReliquaryDustSelectRsp(ReliquaryDustSelectRsp data, int sequence) {
        super(PacketOpcodes.ReliquaryDustSelectRsp, sequence);
        setData(data);
    }
}

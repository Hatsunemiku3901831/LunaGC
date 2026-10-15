package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryDustRspOuterClass.ReliquaryDustRsp;

public final class PacketReliquaryDustRsp extends BasePacket {
    public PacketReliquaryDustRsp(ReliquaryDustRsp data, int sequence) {
        super(PacketOpcodes.ReliquaryDustRsp, sequence);
        setData(data);
    }
}

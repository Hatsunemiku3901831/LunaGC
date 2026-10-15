package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ClientSetGameTimeRspOuterClass.ClientSetGameTimeRsp;

public final class PacketClientSetGameTimeRsp extends BasePacket {
    public PacketClientSetGameTimeRsp(long gameTime, int clientTime, int sequence, int retcode) {
        super(PacketOpcodes.ClientSetGameTimeRsp, sequence);
        setData(
                ClientSetGameTimeRsp.newBuilder()
                        .setGameTime((int) gameTime)
                        .setClientGameTime(clientTime)
                        .setRetcode(retcode));
    }
}

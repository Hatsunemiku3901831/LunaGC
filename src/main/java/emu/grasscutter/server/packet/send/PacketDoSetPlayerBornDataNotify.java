package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DoSetPlayerBornDataNotifyOuterClass.DoSetPlayerBornDataNotify;

public class PacketDoSetPlayerBornDataNotify extends BasePacket {
    public PacketDoSetPlayerBornDataNotify() {
        super(PacketOpcodes.DoSetPlayerBornDataNotify);
        setData(DoSetPlayerBornDataNotify.getDefaultInstance());
    }
}

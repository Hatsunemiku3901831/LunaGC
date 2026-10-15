package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryOfferDataNotifyOuterClass.ReliquaryOfferDataNotify;

public final class PacketReliquaryOfferDataNotify extends BasePacket {
    public PacketReliquaryOfferDataNotify(ReliquaryOfferDataNotify data) {
        super(PacketOpcodes.ReliquaryOfferDataNotify);
        setData(data);
    }
}

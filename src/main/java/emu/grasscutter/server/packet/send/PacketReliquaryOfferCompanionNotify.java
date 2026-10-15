package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryOfferCompanionNotifyOuterClass.ReliquaryOfferCompanionNotify;

/** Each notification replaces the client's candidate dictionaries and progress. */
public final class PacketReliquaryOfferCompanionNotify extends BasePacket {
    public PacketReliquaryOfferCompanionNotify() {
        this(ReliquaryOfferCompanionNotify.getDefaultInstance());
    }

    public PacketReliquaryOfferCompanionNotify(ReliquaryOfferCompanionNotify snapshot) {
        super(PacketOpcodes.ReliquaryOfferCompanionNotify);
        setData(snapshot);
    }
}

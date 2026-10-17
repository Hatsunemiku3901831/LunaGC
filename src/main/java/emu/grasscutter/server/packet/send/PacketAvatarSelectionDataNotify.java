package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarSelectionDataNotifyOuterClass.AvatarSelectionDataNotify;

/** Initializes the client's selection caches; an empty payload is still a required notification. */
public final class PacketAvatarSelectionDataNotify extends BasePacket {
    public PacketAvatarSelectionDataNotify() {
        super(PacketOpcodes.AvatarSelectionDataNotify);
        // No additional selection lists are configured on this server. Omitting the
        // notification leaves both native caches null and interrupts synthesis SetupView.
        setData(AvatarSelectionDataNotify.getDefaultInstance());
    }
}

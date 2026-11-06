package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarSelectionDataNotifyOuterClass.AvatarSelectionDataNotify;

/** Retired historical payload: neither its CN72 identity nor its old opcode is verified. */
public final class PacketAvatarSelectionDataNotify extends BasePacket {
    public PacketAvatarSelectionDataNotify() {
        super(PacketOpcodes.AvatarSelectionDataNotify);
        // Kept only as an unmigrated descriptor diagnostic; no built-in login send remains.
        setData(AvatarSelectionDataNotify.getDefaultInstance());
    }
}

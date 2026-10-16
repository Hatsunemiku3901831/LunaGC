package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;

public final class PacketMusicGameBookAllDataNotify extends BasePacket {
    public PacketMusicGameBookAllDataNotify(MusicGameBookAllDataNotify data) {
        super(PacketOpcodes.MusicGameBookAllDataNotify);
        setData(data);
    }
}

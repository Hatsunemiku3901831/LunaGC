package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameThemeUnlockNotifyOuterClass.MusicGameThemeUnlockNotify;

public final class PacketMusicGameThemeUnlockNotify extends BasePacket {
    public PacketMusicGameThemeUnlockNotify(int themeId) {
        super(PacketOpcodes.MusicGameThemeUnlockNotify);
        setData(MusicGameThemeUnlockNotify.newBuilder().setThemeId(themeId).build());
    }
}

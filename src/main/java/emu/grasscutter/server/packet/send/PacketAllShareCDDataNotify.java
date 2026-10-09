package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AllShareCDDataNotifyOuterClass.AllShareCDDataNotify;
import emu.grasscutter.net.proto.ShareCDInfoOuterClass.ShareCDInfo;
import emu.grasscutter.net.proto.ShareCDOuterClass.ShareCD;

import java.util.List;
import java.util.Map;

public final class PacketAllShareCDDataNotify extends BasePacket {
    public PacketAllShareCDDataNotify(Map<Integer, List<ShareCD>> groups, boolean all) {
        super(PacketOpcodes.AllShareCDDataNotify);
        var packet = AllShareCDDataNotify.newBuilder().setIsAll(all);
        groups.forEach(
                (id, entries) ->
                        packet.putShareCdInfoMap(
                                id,
                                ShareCDInfo.newBuilder()
                                        .setShareCdId(id)
                                        .addAllShareCdList(entries)
                                        .build()));
        setData(packet);
    }
}

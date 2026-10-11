package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelScenePlayTeamEntityNotifyOuterClass.DelScenePlayTeamEntityNotify;

import java.util.Collection;

public final class PacketDelScenePlayTeamEntityNotify extends BasePacket {
    public PacketDelScenePlayTeamEntityNotify(int sceneId, Collection<Integer> entityIds) {
        super(PacketOpcodes.DelScenePlayTeamEntityNotify);
        setData(
                DelScenePlayTeamEntityNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllDelEntityIdList(entityIds)
                        .build());
    }
}

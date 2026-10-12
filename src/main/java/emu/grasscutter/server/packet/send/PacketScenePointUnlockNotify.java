package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;

public class PacketScenePointUnlockNotify extends BasePacket {
    private PacketScenePointUnlockNotify() {
        super(PacketOpcodes.ScenePointUnlockNotify);
    }

    /** Native 7.1 fields 4 and 6 lock and hide; field 15 must remain empty. */
    public static PacketScenePointUnlockNotify lock(int sceneId, int pointId) {
        var packet = new PacketScenePointUnlockNotify();
        packet.setData(
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addLockedPointList(pointId)
                        .addHidePointList(pointId));
        return packet;
    }

    /** Clear the hide flag from a quest lock using native field 8, then unlock with field 15. */
    public static PacketScenePointUnlockNotify releaseQuestLock(int sceneId, int pointId) {
        var packet = new PacketScenePointUnlockNotify();
        packet.setData(
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addUnhidePointList(pointId)
                        .addPointList(pointId));
        return packet;
    }

    public PacketScenePointUnlockNotify(int sceneId, int pointId) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder().setSceneId(sceneId).addPointList(pointId);

        this.setData(p);
    }

    public PacketScenePointUnlockNotify(int sceneId, Iterable<Integer> pointIds) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder().setSceneId(sceneId).addAllPointList(pointIds);

        this.setData(p);
    }
}

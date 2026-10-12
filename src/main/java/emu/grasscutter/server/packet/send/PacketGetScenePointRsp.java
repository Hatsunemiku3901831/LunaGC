package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;

public class PacketGetScenePointRsp extends BasePacket {

    public PacketGetScenePointRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetScenePointRsp);

        GetScenePointRsp.Builder p =
                GetScenePointRsp.newBuilder().setSceneId(sceneId).setBelongUid(player.getUid());
        synchronized (player) {
            GameData.getScenePointEntryMap().values().stream()
                    .filter(
                            entry ->
                                    entry != null
                                            && entry.getSceneId() == sceneId
                                            && entry.getPointData() != null)
                    .map(entry -> entry.getPointData().getId())
                    .distinct()
                    .sorted()
                    .forEach(
                            point -> {
                                if (player.isScenePointForceLocked(sceneId, point))
                                    p.addHidePointList(point);
                                else if (player.getUnlockedScenePoints(sceneId).contains(point))
                                    p.addUnlockedPointList(point);
                            });
            p.addAllUnlockAreaList(player.getUnlockedSceneAreas(sceneId));
        }

        this.setData(p);
    }
}

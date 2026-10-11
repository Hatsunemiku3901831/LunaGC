package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePlayBattleInfoListNotifyOuterClass.ScenePlayBattleInfoListNotify;
import emu.grasscutter.net.proto.ScenePlayBattleInfoNotifyOuterClass.ScenePlayBattleInfoNotify;
import emu.grasscutter.net.proto.ScenePlayBattleInfoOuterClass.ScenePlayBattleInfo;
import emu.grasscutter.net.proto.ScenePlayBattleInterruptNotifyOuterClass.ScenePlayBattleInterruptNotify;
import emu.grasscutter.net.proto.ScenePlayBattleResultNotifyOuterClass.ScenePlayBattleResultNotify;
import emu.grasscutter.net.proto.ScenePlayBattleUidOpNotifyOuterClass.ScenePlayBattleUidOpNotify;

import java.util.List;

/**
 * Current 7.1 scene battle notifications. Field numbers are verified against the native readers.
 */
public final class PacketScenePlayBattle {
    private PacketScenePlayBattle() {}

    public static BasePacket info(ScenePlayBattleInfo info) {
        var packet = new BasePacket(PacketOpcodes.ScenePlayBattleInfoNotify);
        packet.setData(ScenePlayBattleInfoNotify.newBuilder().setBattleInfo(info).build());
        return packet;
    }

    public static BasePacket list(List<ScenePlayBattleInfo> infos) {
        var packet = new BasePacket(PacketOpcodes.ScenePlayBattleInfoListNotify);
        packet.setData(
                ScenePlayBattleInfoListNotify.newBuilder().addAllBattleInfoList(infos).build());
        return packet;
    }

    public static BasePacket interrupt(int playType, int playId, int state) {
        var packet = new BasePacket(PacketOpcodes.ScenePlayBattleInterruptNotify);
        packet.setData(
                ScenePlayBattleInterruptNotify.newBuilder()
                        .setPlayType(playType)
                        .setPlayId(playId)
                        .setInterruptState(state)
                        .build());
        return packet;
    }

    public static BasePacket result(ScenePlayBattleResultNotify result) {
        var packet = new BasePacket(PacketOpcodes.ScenePlayBattleResultNotify);
        packet.setData(result);
        return packet;
    }

    public static BasePacket uidOp(ScenePlayBattleUidOpNotify operation) {
        var packet = new BasePacket(PacketOpcodes.ScenePlayBattleUidOpNotify);
        packet.setData(operation);
        return packet;
    }
}

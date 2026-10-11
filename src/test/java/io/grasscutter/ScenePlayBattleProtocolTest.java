package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ExhibitionDisplayInfoOuterClass.ExhibitionDisplayInfo;
import emu.grasscutter.net.proto.ScenePlayBattleInfoListNotifyOuterClass.ScenePlayBattleInfoListNotify;
import emu.grasscutter.net.proto.ScenePlayBattleInfoNotifyOuterClass.ScenePlayBattleInfoNotify;
import emu.grasscutter.net.proto.ScenePlayBattleInfoOuterClass.ScenePlayBattleInfo;
import emu.grasscutter.net.proto.ScenePlayBattleInterruptNotifyOuterClass.ScenePlayBattleInterruptNotify;
import emu.grasscutter.net.proto.ScenePlayBattleResultNotifyOuterClass.ScenePlayBattleResultNotify;
import emu.grasscutter.net.proto.ScenePlayBattleUidOpNotifyOuterClass.ScenePlayBattleUidOpNotify;
import emu.grasscutter.server.packet.send.PacketScenePlayBattle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Independent tag fixtures from the native 7.1 readers; no generated-field constants. */
class ScenePlayBattleProtocolTest {
    interface Writer {
        void write(CodedOutputStream stream) throws Exception;
    }

    private static byte[] bytes(Writer writer) throws Exception {
        var output = new ByteArrayOutputStream();
        var stream = CodedOutputStream.newInstance(output);
        writer.write(stream);
        stream.flush();
        return output.toByteArray();
    }

    private static byte[] info(boolean packed) throws Exception {
        return bytes(
                wire -> {
                    wire.writeUInt32(2, 1);
                    wire.writeUInt32(3, 1750);
                    wire.writeUInt32(5, 600);
                    wire.writeUInt32(7, 2);
                    wire.writeUInt32(9, 203);
                    wire.writeUInt32(10, 1_791_700_010);
                    wire.writeUInt32(11, 5);
                    wire.writeUInt32(12, 2);
                    wire.writeUInt32(13, 1_791_700_000);
                    if (packed)
                        wire.writeByteArray(
                                15,
                                bytes(
                                        values -> {
                                            for (int value : List.of(0, 1500, 3000, 4500, 6500))
                                                values.writeUInt32NoTag(value);
                                        }));
                    else
                        for (int value : List.of(0, 1500, 3000, 4500, 6500))
                            wire.writeUInt32(15, value);
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeInfoReaderAcceptsPackedAndUnpackedThresholds(boolean packed) throws Exception {
        var result = ScenePlayBattleInfo.parseFrom(info(packed));
        assertEquals(1, result.getType());
        assertEquals(1750, result.getProgress());
        assertEquals(600, result.getDuration());
        assertEquals(2, result.getPlayId());
        assertEquals(203, result.getMode());
        assertEquals(1_791_700_010, result.getStartTime());
        assertEquals(5, result.getState());
        assertEquals(2, result.getPlayType());
        assertEquals(1_791_700_000, result.getPrepareEndTime());
        assertEquals(List.of(0, 1500, 3000, 4500, 6500), result.getProgressStageListList());
        assertEquals(UnknownFieldSet.getDefaultInstance(), result.getUnknownFields());
        assertEquals(10, result.getDescriptorForType().getFields().size());
    }

    @Test
    void infoContainersUseDistinctVerifiedNestedTags() throws Exception {
        var single =
                ScenePlayBattleInfoNotify.parseFrom(bytes(w -> w.writeByteArray(2, info(true))));
        var multiple =
                ScenePlayBattleInfoListNotify.parseFrom(
                        bytes(w -> w.writeByteArray(4, info(true))));
        assertEquals(single.getBattleInfo(), multiple.getBattleInfoList(0));
        assertEquals(UnknownFieldSet.getDefaultInstance(), single.getUnknownFields());
        assertEquals(UnknownFieldSet.getDefaultInstance(), multiple.getUnknownFields());
    }

    @Test
    void interruptRetainsTheInterruptedState() throws Exception {
        var message =
                ScenePlayBattleInterruptNotify.parseFrom(
                        bytes(
                                w -> {
                                    w.writeUInt32(1, 4);
                                    w.writeUInt32(6, 2);
                                    w.writeUInt32(13, 2);
                                }));
        assertEquals(4, message.getInterruptState());
        assertEquals(2, message.getPlayType());
        assertEquals(2, message.getPlayId());
        assertEquals(UnknownFieldSet.getDefaultInstance(), message.getUnknownFields());
    }

    @Test
    void uidOperationPreservesAllThreeArraysAndAllScalars() throws Exception {
        var message =
                ScenePlayBattleUidOpNotify.parseFrom(
                        bytes(
                                w -> {
                                    w.writeUInt32(1, 10001);
                                    w.writeUInt32(1, 10002);
                                    w.writeUInt32(3, 30);
                                    w.writeUInt32(4, 0x04000001);
                                    w.writeUInt32(7, 17);
                                    w.writeUInt32(7, 0);
                                    w.writeUInt32(8, 2);
                                    w.writeUInt32(9, 6);
                                    w.writeUInt32(10, 2);
                                    w.writeUInt32(11, 42);
                                    w.writeString(12, "random_buff_aster");
                                    w.writeUInt32(14, 9);
                                }));
        assertEquals(List.of(10001, 10002), message.getUidListList());
        assertEquals(List.of(17, 0), message.getParamListList());
        assertEquals(List.of(42), message.getParamTargetListList());
        assertEquals(30, message.getParamDuration());
        assertEquals(0x04000001, message.getEntityId());
        assertEquals(2, message.getPlayId());
        assertEquals(6, message.getOp());
        assertEquals(2, message.getPlayType());
        assertEquals(9, message.getParamIndex());
        assertEquals("random_buff_aster", message.getParamStr());
        assertEquals(UnknownFieldSet.getDefaultInstance(), message.getUnknownFields());
    }

    @Test
    void settlementDoesNotSwapRewardsAndPlayerProfiles() throws Exception {
        var player =
                bytes(
                        w -> {
                            w.writeString(2, "platform-id");
                            w.writeString(3, "Traveler");
                            w.writeByteArray(
                                    4,
                                    bytes(
                                            card -> {
                                                card.writeUInt32(1, 25);
                                                card.writeUInt32(2, 8);
                                                card.writeUInt32(3, 6001);
                                            }));
                            w.writeUInt32(5, 10000073);
                            w.writeUInt32(8, 10001);
                            w.writeInt64(9, -7);
                            w.writeUInt32(10, 9001);
                            w.writeByteArray(
                                    13,
                                    bytes(
                                            p -> {
                                                p.writeUInt32(2, 10000073);
                                                p.writeUInt32(4, 207301);
                                            }));
                        });
        var reward =
                bytes(
                        w -> {
                            w.writeByteArray(
                                    1,
                                    bytes(
                                            item -> {
                                                item.writeUInt32(1, 110);
                                                item.writeUInt32(2, 50);
                                            }));
                            w.writeUInt32(10, 10002);
                        });
        var message =
                ScenePlayBattleResultNotify.parseFrom(
                        bytes(
                                w -> {
                                    w.writeUInt32(1, 2);
                                    w.writeBool(4, true);
                                    w.writeUInt32(5, 512);
                                    w.writeByteArray(8, reward);
                                    w.writeByteArray(9, player);
                                    w.writeUInt32(13, 2);
                                }));
        assertEquals(2, message.getPlayId());
        assertEquals(2, message.getPlayType());
        assertTrue(message.getIsWin());
        assertEquals(512, message.getCostTime());
        var p = message.getSettlePlayerInfoList(0);
        assertEquals(10001, p.getUid());
        assertEquals("Traveler", p.getNickname());
        assertEquals("platform-id", p.getOnlineId());
        assertEquals(-7, p.getParam());
        assertEquals(9001, p.getStatisticId());
        assertEquals(10000073, p.getHeadImage());
        assertEquals(10000073, p.getProfilePicture().getAvatarId());
        assertEquals(207301, p.getProfilePicture().getCostumeId());
        assertEquals(6001, p.getCardList(0).getId());
        assertEquals(8, p.getCardList(0).getParam());
        assertEquals(25, p.getCardList(0).getDetailParam());
        var r = message.getSettleRewardInfoList(0);
        assertEquals(10002, r.getUid());
        assertEquals(110, r.getRewardItemList(0).getItemId());
        assertEquals(50, r.getRewardItemList(0).getCount());
        for (var m :
                List.of(
                        message,
                        p,
                        r,
                        p.getCardList(0),
                        p.getProfilePicture(),
                        r.getRewardItemList(0)))
            assertEquals(UnknownFieldSet.getDefaultInstance(), m.getUnknownFields());
    }

    @Test
    void exhibitionCardUsesCurrentNativeJumpTable() throws Exception {
        var message =
                ExhibitionDisplayInfo.newBuilder()
                        .setId(6001)
                        .setParam(8)
                        .setDetailParam(25)
                        .build();
        var raw = UnknownFieldSet.parseFrom(message.toByteArray());
        assertEquals(List.of(25L), raw.getField(1).getVarintList());
        assertEquals(List.of(8L), raw.getField(2).getVarintList());
        assertEquals(List.of(6001L), raw.getField(3).getVarintList());
    }

    @Test
    void packetFactoriesUseTheCurrentNativeCommandGetters() throws Exception {
        assertEquals(2762, PacketOpcodes.ScenePlayBattleInfoNotify);
        assertEquals(20153, PacketOpcodes.ScenePlayBattleInfoListNotify);
        assertEquals(20684, PacketOpcodes.ScenePlayBattleInterruptNotify);
        assertEquals(8678, PacketOpcodes.ScenePlayBattleResultNotify);
        assertEquals(6526, PacketOpcodes.ScenePlayBattleUidOpNotify);
        var info = ScenePlayBattleInfo.parseFrom(info(true));
        assertEquals(2762, PacketScenePlayBattle.info(info).getOpcode());
        assertEquals(
                Set.of(2),
                UnknownFieldSet.parseFrom(PacketScenePlayBattle.info(info).getData())
                        .asMap()
                        .keySet());
        assertEquals(20153, PacketScenePlayBattle.list(List.of(info)).getOpcode());
        assertEquals(
                Set.of(4),
                UnknownFieldSet.parseFrom(PacketScenePlayBattle.list(List.of(info)).getData())
                        .asMap()
                        .keySet());
        assertEquals(20684, PacketScenePlayBattle.interrupt(2, 2, 4).getOpcode());
        assertEquals(
                8678,
                PacketScenePlayBattle.result(ScenePlayBattleResultNotify.getDefaultInstance())
                        .getOpcode());
        assertEquals(
                6526,
                PacketScenePlayBattle.uidOp(ScenePlayBattleUidOpNotify.getDefaultInstance())
                        .getOpcode());
    }
}

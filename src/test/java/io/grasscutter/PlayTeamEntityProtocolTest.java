package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DelScenePlayTeamEntityNotifyOuterClass.DelScenePlayTeamEntityNotify;
import emu.grasscutter.net.proto.DelTeamEntityNotifyOuterClass.DelTeamEntityNotify;
import emu.grasscutter.net.proto.SyncScenePlayTeamEntityNotifyOuterClass.SyncScenePlayTeamEntityNotify;
import emu.grasscutter.server.packet.send.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Independent wire fixtures from the verified native readers and typed message handlers. */
class PlayTeamEntityProtocolTest {
    @Test
    void resolvedDeletionMessagesAreRemovedFromAmbiguousFanOutTable() throws Exception {
        var table =
                com.google.gson.JsonParser.parseString(
                                java.nio.file.Files.readString(
                                        java.nio.file.Path.of("data/proto-fanout.json")))
                        .getAsJsonObject();
        assertFalse(table.has("DelTeamEntityNotify"));
        assertFalse(table.has("DelScenePlayTeamEntityNotify"));
        assertTrue(table.has("BlossomBriefInfoNotify"));
    }

    interface Writer {
        void write(CodedOutputStream wire) throws Exception;
    }

    private byte[] bytes(Writer writer) throws Exception {
        var output = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(output);
        writer.write(wire);
        wire.flush();
        return output.toByteArray();
    }

    private byte[] deletion(int tag, boolean packed) throws Exception {
        return bytes(
                w -> {
                    w.writeUInt32(15, 3);
                    if (packed)
                        w.writeByteArray(
                                tag,
                                bytes(
                                        ids -> {
                                            ids.writeUInt32NoTag(0x0c000001);
                                            ids.writeUInt32NoTag(0x0c000002);
                                        }));
                    else for (int id : List.of(0x0c000001, 0x0c000002)) w.writeUInt32(tag, id);
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryTeamDeletionUsesListTagElevenAndSceneFifteen(boolean packed) throws Exception {
        var message = DelTeamEntityNotify.parseFrom(deletion(11, packed));
        assertEquals(3, message.getSceneId());
        assertEquals(List.of(0x0c000001, 0x0c000002), message.getDelEntityIdListList());
        assertEquals(UnknownFieldSet.getDefaultInstance(), message.getUnknownFields());
        assertEquals(2, message.getDescriptorForType().getFields().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scenePlayDeletionUsesListTagThreeAndSceneFifteen(boolean packed) throws Exception {
        var message = DelScenePlayTeamEntityNotify.parseFrom(deletion(3, packed));
        assertEquals(3, message.getSceneId());
        assertEquals(List.of(0x0c000001, 0x0c000002), message.getDelEntityIdListList());
        assertEquals(UnknownFieldSet.getDefaultInstance(), message.getUnknownFields());
        assertEquals(2, message.getDescriptorForType().getFields().size());
    }

    @Test
    void syncAndChildUseNativeIdentityAndAbilityTags() throws Exception {
        byte[] child =
                bytes(
                        w -> {
                            w.writeUInt32(1, 0x0c000001);
                            w.writeUInt32(2, 10002);
                            w.writeUInt32(3, 2);
                            w.writeUInt32(5, 70370002);
                            w.writeByteArray(6, new byte[0]);
                        });
        var result =
                SyncScenePlayTeamEntityNotify.parseFrom(
                        bytes(
                                w -> {
                                    w.writeUInt32(15, 3);
                                    w.writeByteArray(4, child);
                                }));
        assertEquals(3, result.getSceneId());
        var entity = result.getEntityInfoList(0);
        assertEquals(0x0c000001, entity.getEntityId());
        assertEquals(10002, entity.getPlayerUid());
        assertEquals(2, entity.getAuthorityPeerId());
        assertEquals(70370002, entity.getGadgetConfigId());
        assertTrue(entity.hasAbilityInfo());
        assertFalse(entity.getAbilityInfo().getIsInited());
        assertEquals(UnknownFieldSet.getDefaultInstance(), result.getUnknownFields());
        assertEquals(UnknownFieldSet.getDefaultInstance(), entity.getUnknownFields());
        assertEquals(5, entity.getDescriptorForType().getFields().size());
    }

    @Test
    void sendersUseDistinctCommandsAndPayloads() throws Exception {
        var ids = List.of(0x0c000001, 0x0c000002);
        var ordinary = new PacketDelTeamEntityNotify(3, ids);
        var play = new PacketDelScenePlayTeamEntityNotify(3, ids);
        assertEquals(2050, ordinary.getOpcode());
        assertEquals(26830, play.getOpcode());
        assertEquals(25654, PacketOpcodes.SyncScenePlayTeamEntityNotify);
        var normalWire = UnknownFieldSet.parseFrom(ordinary.getData()).asMap();
        var playWire = UnknownFieldSet.parseFrom(play.getData()).asMap();
        assertEquals(java.util.Set.of(11, 15), normalWire.keySet());
        assertEquals(java.util.Set.of(3, 15), playWire.keySet());
        var single = new PacketDelTeamEntityNotify(3, 0x09000001);
        assertEquals(
                List.of(0x09000001),
                DelTeamEntityNotify.parseFrom(single.getData()).getDelEntityIdListList());
    }

    @Test
    void playTeamEntityIdKindIsSeparateFromSpatialGadgetsAndOrdinaryTeams() {
        assertEquals(12, EntityIdType.PLAY_TEAM.getId());
        assertEquals(EntityType.PlayTeam, EntityIdType.toEntityType(12));
        assertEquals(48, EntityType.PlayTeam.getValue());
        assertNotEquals(EntityIdType.TEAM, EntityIdType.PLAY_TEAM);
        assertNotEquals(EntityIdType.GADGET, EntityIdType.PLAY_TEAM);
    }
}

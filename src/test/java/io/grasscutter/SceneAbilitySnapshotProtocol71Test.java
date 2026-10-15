package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AvatarEnterSceneInfoOuterClass.AvatarEnterSceneInfo;
import emu.grasscutter.net.proto.PlayerEnterSceneInfoNotifyOuterClass.PlayerEnterSceneInfoNotify;
import emu.grasscutter.net.proto.SceneTeamAvatarOuterClass.SceneTeamAvatar;
import emu.grasscutter.net.proto.SceneTeamUpdateNotifyOuterClass.SceneTeamUpdateNotify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Current native types 55935/48536 use different tags for avatar and weapon snapshots. */
class SceneAbilitySnapshotProtocol71Test {
    private static final String ENTER = "2a0608011a02200b420608011a022016";
    private static final String TEAM = "520608011a022016720608011a02200b";

    private byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private AbilitySyncStateInfo snapshot(int id) {
        return AbilitySyncStateInfo.newBuilder()
                .setIsInited(true)
                .addAppliedAbilities(AbilityAppliedAbility.newBuilder().setInstancedAbilityId(id))
                .build();
    }

    private byte[] legacy(int first, int second) throws Exception {
        var stream = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(stream);
        out.writeMessage(first, snapshot(11));
        out.writeMessage(second, snapshot(22));
        out.flush();
        return stream.toByteArray();
    }

    private Map<Integer, ByteString> nested(byte[] payload) throws Exception {
        var input = CodedInputStream.newInstance(payload);
        var messages = new LinkedHashMap<Integer, ByteString>();
        for (int tag; (tag = input.readTag()) != 0; ) {
            if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED)
                messages.put(WireFormat.getTagFieldNumber(tag), input.readBytes());
            else input.skipField(tag);
        }
        return messages;
    }

    @Test
    void enter_descriptor_uses_the_current_native_snapshot_tags() {
        var schema = AvatarEnterSceneInfo.getDescriptor();
        assertEquals(5, schema.findFieldByName("avatar_ability_info").getNumber());
        assertEquals(8, schema.findFieldByName("weapon_ability_info").getNumber());
    }

    @Test
    void team_descriptor_uses_the_current_native_snapshot_tags() {
        var schema = SceneTeamAvatar.getDescriptor();
        assertEquals(14, schema.findFieldByName("avatar_ability_info").getNumber());
        assertEquals(10, schema.findFieldByName("weapon_ability_info").getNumber());
    }

    @Test
    void native_enter_fixture_distinguishes_avatar_and_weapon() throws Exception {
        var entry = AvatarEnterSceneInfo.parseFrom(bytes(ENTER));
        assertEquals(
                11, entry.getAvatarAbilityInfo().getAppliedAbilities(0).getInstancedAbilityId());
        assertEquals(
                22, entry.getWeaponAbilityInfo().getAppliedAbilities(0).getInstancedAbilityId());
        assertTrue(entry.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void native_team_fixture_distinguishes_avatar_and_weapon() throws Exception {
        var entry = SceneTeamAvatar.parseFrom(bytes(TEAM));
        assertEquals(
                11, entry.getAvatarAbilityInfo().getAppliedAbilities(0).getInstancedAbilityId());
        assertEquals(
                22, entry.getWeaponAbilityInfo().getAppliedAbilities(0).getInstancedAbilityId());
        assertTrue(entry.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void enter_writer_matches_the_independent_native_fixture() {
        var entry =
                AvatarEnterSceneInfo.newBuilder()
                        .setAvatarAbilityInfo(snapshot(11))
                        .setWeaponAbilityInfo(snapshot(22))
                        .build();
        assertArrayEquals(bytes(ENTER), entry.toByteArray());
    }

    @Test
    void team_writer_matches_the_independent_native_fixture() {
        var entry =
                SceneTeamAvatar.newBuilder()
                        .setAvatarAbilityInfo(snapshot(11))
                        .setWeaponAbilityInfo(snapshot(22))
                        .build();
        assertArrayEquals(bytes(TEAM), entry.toByteArray());
    }

    @Test
    void enter_placeholders_are_unknown_in_the_current_schema() throws Exception {
        var entry = AvatarEnterSceneInfo.parseFrom(legacy(50000, 50001));
        assertFalse(entry.hasAvatarAbilityInfo());
        assertFalse(entry.hasWeaponAbilityInfo());
        assertEquals(Set.of(50000, 50001), entry.getUnknownFields().asMap().keySet());
    }

    @Test
    void team_placeholders_are_unknown_in_the_current_schema() throws Exception {
        var entry = SceneTeamAvatar.parseFrom(legacy(50001, 50002));
        assertFalse(entry.hasAvatarAbilityInfo());
        assertFalse(entry.hasWeaponAbilityInfo());
        assertEquals(Set.of(50001, 50002), entry.getUnknownFields().asMap().keySet());
    }

    @Test
    void empty_present_snapshots_keep_presence_without_claiming_initialization() throws Exception {
        var entry =
                AvatarEnterSceneInfo.newBuilder()
                        .setAvatarAbilityInfo(AbilitySyncStateInfo.getDefaultInstance())
                        .setWeaponAbilityInfo(AbilitySyncStateInfo.getDefaultInstance())
                        .build();
        assertArrayEquals(bytes("2a004200"), entry.toByteArray());
        var decoded = AvatarEnterSceneInfo.parseFrom(bytes("2a004200"));
        assertTrue(decoded.hasAvatarAbilityInfo());
        assertFalse(decoded.getAvatarAbilityInfo().getIsInited());
        assertTrue(decoded.hasWeaponAbilityInfo());
        assertFalse(decoded.getWeaponAbilityInfo().getIsInited());
    }

    @Test
    void both_scene_envelopes_emit_native_inner_snapshot_tags() throws Exception {
        var enter =
                PlayerEnterSceneInfoNotify.newBuilder()
                        .setEnterSceneToken(731)
                        .addAvatarEnterInfo(
                                AvatarEnterSceneInfo.newBuilder()
                                        .setAvatarAbilityInfo(snapshot(11))
                                        .setWeaponAbilityInfo(snapshot(22)))
                        .build();
        var team =
                SceneTeamUpdateNotify.newBuilder()
                        .addSceneTeamAvatarList(
                                SceneTeamAvatar.newBuilder()
                                        .setAvatarAbilityInfo(snapshot(11))
                                        .setWeaponAbilityInfo(snapshot(22)))
                        .build();
        assertArrayEquals(bytes(ENTER), nested(enter.toByteArray()).get(12).toByteArray());
        assertArrayEquals(bytes(TEAM), nested(team.toByteArray()).get(8).toByteArray());
    }

    @Test
    void an_unknown_nested_field_is_preserved() throws Exception {
        var entry = AvatarEnterSceneInfo.parseFrom(bytes("2a050801b8037b"));
        assertEquals(
                123L,
                entry.getAvatarAbilityInfo()
                        .getUnknownFields()
                        .getField(55)
                        .getVarintList()
                        .get(0));
        assertArrayEquals(bytes("2a050801b8037b"), entry.toByteArray());
    }
}

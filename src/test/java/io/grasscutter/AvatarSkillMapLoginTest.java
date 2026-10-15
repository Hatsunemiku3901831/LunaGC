package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.net.proto.AvatarDataNotifyOuterClass.AvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/**
 * Missing skills in old Mongo saves must not prevent the avatar login snapshot from being built.
 */
class AvatarSkillMapLoginTest extends QuestTestFixture {
    Avatar model(Map<Integer, Integer> levels) throws Exception {
        var avatar = new Avatar();
        replaceField(Avatar.class, avatar, "avatarId", 10000073);
        replaceField(Avatar.class, avatar, "talentIdList", new HashSet<Integer>());
        replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<Integer>());
        replaceField(Avatar.class, avatar, "skillDepotId", 7301);
        replaceField(Avatar.class, avatar, "skillLevelMap", levels);
        return bind(avatar);
    }

    Avatar bind(Avatar avatar) throws Exception {
        var depot =
                gson.fromJson(
                        "{id:7301,skills:[10731,10732,0,10731],energySkill:10733,attackModeSkill:10734,talents:[]}",
                        AvatarSkillDepotData.class);
        // Reconnect binds the depot directly; setSkillDepotData would mask this bug by filling
        // levels.
        replaceField(Avatar.class, avatar, "skillDepot", depot);
        if (avatar.getProudSkillList() == null)
            replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<Integer>());
        avatar.setOwner(player);
        return avatar;
    }

    @Test
    void a_missing_base_skill_is_initialized_and_returned_on_the_first_read() throws Exception {
        var stored = new HashMap<Integer, Integer>();
        var avatar = model(stored);
        assertEquals(
                Map.of(10731, 1, 10732, 1, 10733, 1, 10734, 1),
                assertDoesNotThrow(avatar::getSkillLevelMap));
        assertEquals(1, stored.get(10734));
        assertEquals(4, stored.size());
    }

    @Test
    void existing_levels_and_other_depot_progress_are_preserved() throws Exception {
        var stored = new HashMap<>(Map.of(10731, 6, 10732, 4, 10733, 9, 99999, 7));
        var avatar = model(stored);
        assertEquals(
                Map.of(10731, 6, 10732, 4, 10733, 9, 10734, 1),
                assertDoesNotThrow(avatar::getSkillLevelMap));
        assertEquals(7, stored.get(99999));
        assertEquals(6, avatar.getSkillLevelMap().get(10731));
    }

    @Test
    void a_null_saved_value_receives_the_same_initial_level_as_a_missing_skill() throws Exception {
        var stored = new HashMap<Integer, Integer>();
        stored.put(10731, null);
        var avatar = model(stored);
        assertEquals(1, assertDoesNotThrow(avatar::getSkillLevelMap).get(10731));
        assertEquals(1, stored.get(10731));
    }

    @Test
    void the_returned_map_remains_a_copy() throws Exception {
        var stored = new HashMap<>(Map.of(10731, 6, 10732, 4, 10733, 9, 10734, 2));
        var avatar = model(stored);
        var copy = avatar.getSkillLevelMap();
        copy.put(10731, 1);
        assertEquals(6, stored.get(10731));
        assertEquals(6, avatar.getSkillLevelMap().get(10731));
    }

    @Test
    void effective_levels_can_read_a_newly_introduced_skill() throws Exception {
        var avatar = model(new HashMap<>());
        assertEquals(
                Map.of(10731, 1, 10732, 1, 10733, 1, 10734, 1),
                assertDoesNotThrow(avatar::getEffectiveSkillLevelMap));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void the_actual_avatar_login_packet_accepts_old_and_cold_loaded_skill_maps(boolean cold)
            throws Exception {
        store.getMapper().map(Avatar.class);
        var avatar = model(new HashMap<>(Map.of(10731, 6, 10732, 4, 10733, 9)));
        if (cold) {
            store.save(avatar);
            avatar = store.find(Avatar.class).first();
            assertNotNull(avatar);
            assertNull(avatar.getSkillDepot());
            bind(avatar);
        }
        player.getAvatars().getAvatars().put(avatar.getAvatarId(), avatar);
        player.getTeamManager().getActiveTeam().add(new EntityAvatar(player.getScene(), avatar));
        var packet = assertDoesNotThrow(() -> new PacketAvatarDataNotify(player));
        var proto = AvatarDataNotify.parseFrom(packet.getData());
        assertEquals(1, proto.getAvatarListCount());
        assertEquals(
                Map.of(10731, 6, 10732, 4, 10733, 9, 10734, 1),
                proto.getAvatarList(0).getSkillLevelMapMap());
        if (cold) {
            store.save(avatar);
            Document raw = store.getDatabase().getCollection("avatars").find().first();
            assertNotNull(raw);
            assertEquals(1, raw.get("skillLevelMap", Document.class).getInteger("10734"));
            assertEquals(6, raw.get("skillLevelMap", Document.class).getInteger("10731"));
        }
    }

    @Test
    void the_showcase_snapshot_also_handles_missing_levels() throws Exception {
        var avatar = model(new HashMap<>());
        var proto = assertDoesNotThrow(avatar::toShowAvatarInfoProto);
        assertEquals(1, proto.getSkillLevelMapOrThrow(10734));
    }
}

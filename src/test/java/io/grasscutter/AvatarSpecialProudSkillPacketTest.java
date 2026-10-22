package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.net.proto.AvatarDataNotifyOuterClass.AvatarDataNotify;
import emu.grasscutter.net.proto.AvatarSkillDepotChangeNotifyOuterClass.AvatarSkillDepotChangeNotify;
import emu.grasscutter.net.proto.ProudSkillChangeNotifyOuterClass.ProudSkillChangeNotify;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarSkillDepotChangeNotify;
import emu.grasscutter.server.packet.send.PacketProudSkillChangeNotify;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvatarSpecialProudSkillPacketTest extends QuestTestFixture {
    @ParameterizedTest
    @ValueSource(ints = {58, 31, 123})
    void coldLoadedSpecialPassivesReachEveryClientSnapshot(int character) throws Exception {
        int group = character * 100 + 51;
        int special = group * 100 + 1;
        int ordinary = (character * 100 + 21) * 100 + 1;
        var avatar = new Avatar();
        replaceField(Avatar.class, avatar, "avatarId", 10000000 + character);
        replaceField(Avatar.class, avatar, "talentIdList", new HashSet<Integer>());
        replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<>(List.of(ordinary, special)));
        avatar.setOwner(player);
        store.getMapper().map(Avatar.class);
        store.save(avatar);
        avatar = store.find(Avatar.class).first();
        var depot = gson.fromJson("{id:5801,talents:[],specialProudSkillOpens:[{proudSkillGroupId:"
                + group + "},{proudSkillGroupId:9999}]}", AvatarSkillDepotData.class);
        depot.onLoad();
        replaceField(Avatar.class, avatar, "skillDepot", depot);
        avatar.setOwner(player);
        player.getAvatars().getAvatars().put(avatar.getAvatarId(), avatar);
        var entity = new EntityAvatar(player.getScene(), avatar);
        player.getTeamManager().getActiveTeam().add(entity);
        var login = AvatarDataNotify.parseFrom(new PacketAvatarDataNotify(player).getData()).getAvatarList(0);
        assertEquals(List.of(special), login.getSpecialProudSkillListList());
        // The client merges both lists into a dictionary keyed by proud-skill group.
        // Sending the same passive twice throws ArgumentException and blanks the talent screen.
        assertClientPassives(login.getInherentProudSkillListList(), login.getSpecialProudSkillListList(), ordinary, special);
        assertEquals(List.of(special), avatar.toShowAvatarInfoProto().getSpecialProudSkillListList());
        var showcase = avatar.toShowAvatarInfoProto();
        assertClientPassives(showcase.getInherentProudSkillListList(), showcase.getSpecialProudSkillListList(), ordinary, special);
        assertEquals(List.of(special), entity.getSceneAvatarInfo().getSpecialProudSkillListList());
        var scene = entity.getSceneAvatarInfo();
        assertClientPassives(scene.getInherentProudSkillListList(), scene.getSpecialProudSkillListList(), ordinary, special);
        var change = AvatarSkillDepotChangeNotify.parseFrom(new PacketAvatarSkillDepotChangeNotify(avatar).getData());
        assertEquals(List.of(special), change.getSpecialProudSkillListList());
        assertClientPassives(change.getProudSkillListList(), change.getSpecialProudSkillListList(), ordinary, special);
        var promotion = ProudSkillChangeNotify.parseFrom(new PacketProudSkillChangeNotify(avatar).getData());
        assertClientPassives(promotion.getProudSkillListList(), login.getSpecialProudSkillListList(), ordinary, special);
        assertTrue(login.getInherentProudSkillListList().contains(ordinary));
        assertEquals(Set.of(ordinary, special), avatar.getProudSkillList());
        avatar.getProudSkillList().remove(special);
        assertTrue(avatar.toProto().getSpecialProudSkillListList().isEmpty());
    }

    private void assertClientPassives(List<Integer> inherent, List<Integer> special, int ordinaryId, int specialId) {
        var groups = new HashMap<Integer, Integer>();
        for (var list : List.of(inherent, special)) {
            for (int id : list) {
                assertNull(groups.putIfAbsent(id / 100, id), "Duplicate client passive group " + id / 100);
            }
        }
        assertEquals(List.of(ordinaryId), inherent);
        assertEquals(List.of(specialId), special);
        assertEquals(2, groups.size());
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.net.proto.AvatarDataNotifyOuterClass.AvatarDataNotify;
import emu.grasscutter.net.proto.AvatarSkillDepotChangeNotifyOuterClass.AvatarSkillDepotChangeNotify;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarSkillDepotChangeNotify;
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
        assertEquals(List.of(special), avatar.toShowAvatarInfoProto().getSpecialProudSkillListList());
        assertEquals(List.of(special), entity.getSceneAvatarInfo().getSpecialProudSkillListList());
        var change = AvatarSkillDepotChangeNotify.parseFrom(new PacketAvatarSkillDepotChangeNotify(avatar).getData());
        assertEquals(List.of(special), change.getSpecialProudSkillListList());
        assertTrue(login.getInherentProudSkillListList().contains(ordinary));
        assertEquals(Set.of(ordinary, special), avatar.getProudSkillList());
        avatar.getProudSkillList().remove(special);
        assertTrue(avatar.toProto().getSpecialProudSkillListList().isEmpty());
    }
}

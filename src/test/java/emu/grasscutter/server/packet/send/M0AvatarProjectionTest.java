package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.Client72AvatarWireOuterClass.ILEPMBEJKIA;
import emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class M0AvatarProjectionTest {
    @Test
    void actualSenderUsesTargetOpcodeNestedTagsAndSharedFullWidthEquipmentGuid() throws Exception {
        Grasscutter.getConfig();
        var player = new Player();
        var manager = new TeamManager(player);
        field(Player.class, "teamManager").set(player, manager);
        var avatar = new Avatar();
        field(Avatar.class, "avatarId").set(avatar, 10000007);
        field(Avatar.class, "guid").set(avatar, 0x20000000000001L);
        field(Avatar.class, "skillDepotId").set(avatar, 701);
        avatar.setLevel(1);
        avatar.setExp(0);
        avatar.setPromoteLevel(0);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, 911.791f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 17.808f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_BASE_DEFENSE, 57.225f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 625.5f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 911.791f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 41.053f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 57.225f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CRITICAL, 0.05f);
        var weapon = new Weapon();
        field(GameItem.class, "guid").set(weapon, 0x100000001L);
        avatar.getEquips().put(6, weapon);
        player.getInventory().getItems().put(weapon.getGuid(), weapon);
        player.getAvatars().getAvatars().put(10000007, avatar);
        manager.getCurrentSinglePlayerTeamInfo().getAvatars().add(10000007);
        manager.getCurrentSinglePlayerTeamInfo().setName("初始队伍");

        boolean previous = Configuration.GAME_INFO.m0.enabled;
        Configuration.GAME_INFO.m0.enabled = true;
        try {
            var packet = new PacketAvatarDataNotify(player);
            assertEquals(20143, packet.getOpcode());
            var root = ILEPMBEJKIA.parseFrom(packet.getData());
            assertEquals(Set.of(5, 6, 12), root.getAllFields().keySet().stream()
                    .map(f -> f.getNumber()).collect(Collectors.toSet()));
            assertEquals(1, root.getJGECGMPNGBDCount());
            var row = root.getJGECGMPNGBD(0);
            assertEquals(10000007, row.getNLNJDCDLHHE());
            assertEquals(0x20000000000001L, row.getFBLKPHOCBIK());
            assertEquals(701, row.getBLBHOJMCOJN());
            assertEquals(1, row.getDFENDOGMEAD());
            assertEquals(Set.of(1001, 1002, 4001), row.getIKIFIBAEDGDMap().keySet());
            assertTrue(row.getIKIFIBAEDGDOrThrow(1001).hasBNJKCHONGGEInt64());
            assertEquals(0, row.getIKIFIBAEDGDOrThrow(1001).getBNJKCHONGGEInt64());
            assertEquals(1, row.getIKIFIBAEDGDOrThrow(4001).getBNJKCHONGGEInt64());
            assertEquals(Set.of(1, 4, 7, 1010, 2000, 2001, 2002), row.getADJINCFKMMLMap().keySet());
            assertEquals(625.5f, row.getADJINCFKMMLOrThrow(1010));
            var store = PlayerStoreNotify.parseFrom(new PacketPlayerStoreNotify(player).getData());
            assertEquals(weapon.getGuid(), store.getItemList(0).getFBLKPHOCBIK());
            assertEquals(weapon.getGuid(), row.getAODNDDBPMFB(0));
            var team = root.getCNCCIILAJNJOrThrow(root.getOCIKDBJPOAJ());
            assertEquals("初始队伍", team.getMGIDEJKCAKD());
            assertEquals(avatar.getGuid(), team.getPIDCNPCLBCE(0));
            assertEquals(0.05f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CRITICAL));
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
            var dead = ILEPMBEJKIA.parseFrom(new PacketAvatarDataNotify(player).getData());
            assertEquals(2, dead.getJGECGMPNGBD(0).getDFENDOGMEAD());
            assertEquals(0f, dead.getJGECGMPNGBD(0).getADJINCFKMMLOrThrow(1010));
        } finally {
            Configuration.GAME_INFO.m0.enabled = previous;
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Weapon extends GameItem {
        @Override public int getItemId() { return 11101; }
        @Override public ItemType getItemType() { return ItemType.ITEM_WEAPON; }
    }
}

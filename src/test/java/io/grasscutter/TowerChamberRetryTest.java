package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ShareCDData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.ability.AbilitySkillCooldown;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tower.TowerData;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarSkillInfoNotifyOuterClass.AvatarSkillInfoNotify;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Formal World/Scene replay and cancellation with owned avatars and controlled resource data. */
class TowerChamberRetryTest extends QuestTestFixture {
    private static final int FIRST = 10000021, SECOND = 10000022, OTHER = 10000023;
    private World world;
    private Scene source;
    private DungeonSystem system;
    private DungeonData data;
    private final List<Runnable> maps = new ArrayList<>();

    static class BirthData extends DungeonData {
        boolean throwPosition;

        @Override
        public Position getStartPosition() {
            if (throwPosition) throw new IllegalStateException("Controlled birth position failure");
            return new Position(1, 2, 3);
        }

        @Override
        public Position getStartRotation() {
            return new Position(0, 90, 0);
        }
    }

    private <K, V> void remember(Map<K, V> map) {
        var old = new HashMap<>(map);
        maps.add(
                () -> {
                    map.clear();
                    map.putAll(old);
                });
    }

    @BeforeEach
    void chamber() throws Exception {
        remember(GameData.getSceneDataMap());
        remember(GameData.getDungeonDataMap());
        remember(GameData.getTowerFloorDataMap());
        remember(GameData.getTowerLevelDataMap());
        remember(GameData.getAvatarSkillDataMap());
        remember(GameData.getShareCDDataMap());
        GameData.getSceneDataMap()
                .put(9, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        data = gson.fromJson("{id:9001,sceneId:9,passCond:0}", BirthData.class);
        GameData.getDungeonDataMap().put(9001, data);
        GameData.getTowerFloorDataMap()
                .put(
                        6002,
                        gson.fromJson(
                                "{floorId:6002,floorIndex:5,levelGroupId:6201,teamNum:2}",
                                TowerFloorData.class));
        for (int i = 0; i < 3; i++)
            GameData.getTowerLevelDataMap()
                    .put(
                            6201 + i,
                            gson.fromJson(
                                    "{levelId:"
                                            + (6201 + i)
                                            + ",levelIndex:"
                                            + (i + 1)
                                            + ",levelGroupId:6201,dungeonId:9001}",
                                    TowerLevelData.class));
        GameData.getAvatarSkillDataMap()
                .put(
                        81201,
                        gson.fromJson(
                                "{id:81201,maxChargeNum:2,shareCDID:0}", AvatarSkillData.class));
        GameData.getAvatarSkillDataMap()
                .put(
                        81202,
                        gson.fromJson(
                                "{id:81202,maxChargeNum:1,shareCDID:9901}", AvatarSkillData.class));
        GameData.getAvatarSkillDataMap()
                .put(
                        81203,
                        gson.fromJson(
                                "{id:81203,maxChargeNum:1,shareCDID:9902}", AvatarSkillData.class));
        for (int group : List.of(9901, 9902))
            GameData.getShareCDDataMap()
                    .put(
                            group,
                            gson.fromJson(
                                    "{id:" + group + ",maxChargeNum:1,cdSlots:[{coolDownTime:60}]}",
                                    ShareCDData.class));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        world = new World(player.server, player);
        world.getPlayers().add(player);
        player.setWorld(world);
        player.getTeamManager().setEntity(new EntityTeam(player));
        source = world.getSceneById(9);
        player.setScene(source);
        player.setSceneId(9);
        for (int id : List.of(FIRST, SECOND, OTHER)) own(id);
        assertTrue(
                player.getTowerManager()
                        .tryTeamSelect(
                                6002,
                                List.of(
                                        List.of(avatar(FIRST).getGuid()),
                                        List.of(avatar(SECOND).getGuid()))));
        replaceField(TowerData.class, player.getTowerData(), "currentLevel", 1);
        assertEquals(6202, player.getTowerManager().getCurrentLevelId());
        replaceField(TeamManager.class, player.getTeamManager(), "useTemporarilyTeamIndex", 1);
        source.addPlayer(player);
        source.setPrevScene(3);
        source.setPrevScenePoint(16);
        var manager = new DungeonManager(source, data);
        manager.setTowerDungeon(true);
        source.setDungeonManager(manager);
        assertTrue(capture());
        for (int id : List.of(FIRST, SECOND)) {
            avatar(id).setCurrentHp(7);
            avatar(id).setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 7);
            avatar(id).setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 48);
            avatar(id).setFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, 0);
            avatar(id).setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 0);
        }
        system = new DungeonSystem(player.server);
        replaceField(
                emu.grasscutter.server.game.GameServer.class,
                player.server,
                "dungeonSystem",
                system);
        replaceField(Grasscutter.class, null, "pluginManager", new PluginManager());
        player.packets.clear();
    }

    private void own(int id) {
        var depot =
                gson.fromJson(
                        id == SECOND
                                ? "{id:901,skills:[81201],energySkill:81202,energySkillData:{id:81202,costElemType:'Grass',costElemVal:0,specialEnergyMin:50},elementType:'Grass'}"
                                : "{id:801,skills:[81201],energySkill:81202,energySkillData:{id:81202,costElemType:'Grass',costElemVal:60},elementType:'Grass'}",
                        AvatarSkillDepotData.class);
        var avatar = new TowerChamberCheckpointTest.OwnedAvatar(id, depot);
        assertTrue(player.getAvatars().addAvatar(avatar));
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setCurrentHp(id == FIRST ? 37 : 82);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, id == FIRST ? 37 : 82);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, id == FIRST ? 11 : 5);
        avatar.setFightProperty(
                id == SECOND
                        ? FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY
                        : FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY,
                id == SECOND ? 80 : 60);
        avatar.setFightProperty(
                id == SECOND
                        ? FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY
                        : FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY,
                id == FIRST ? 13 : 29);
    }

    @AfterEach
    void clean() throws Exception {
        for (var scene : List.copyOf(world.getScenes().values()))
            scene.getScriptManager().onDestroy();
        source.getScriptManager().onDestroy();
        drain();
        maps.forEach(Runnable::run);
    }

    private Avatar avatar(int id) {
        return player.getAvatars().getAvatarById(id);
    }

    private boolean capture() throws Exception {
        return (boolean)
                player.getTowerManager()
                        .getClass()
                        .getMethod("captureChamberState")
                        .invoke(player.getTowerManager());
    }

    private int index() throws Exception {
        var field = TeamManager.class.getDeclaredField("useTemporarilyTeamIndex");
        field.setAccessible(true);
        return field.getInt(player.getTeamManager());
    }

    private void event(emu.grasscutter.utils.objects.EventConsumer<PlayerTeleportEvent> listener) {
        new EventHandler<>(PlayerTeleportEvent.class).listener(listener).register(null);
    }

    private void kept() throws Exception {
        assertSame(source, player.getScene());
        assertSame(source, world.getScenes().get(9));
        assertFalse(source.getScriptManager().isDestroyed());
        assertEquals(1, index());
        assertEquals(6202, player.getTowerManager().getCurrentLevelId());
        assertEquals(7, avatar(FIRST).getCurrentHp());
    }

    private void cooldowns() {
        var cooldown = player.getAbilityManager().getSkillCooldown();
        for (int id : List.of(FIRST, SECOND, OTHER))
            assertTrue(cooldown.start(avatar(id), 81201, 60));
        for (int id : List.of(FIRST, SECOND)) assertTrue(cooldown.start(avatar(id), 81201, 60));
        assertTrue(player.getAbilityManager().getSharedCooldown().startAvatarSkill(81202, 0));
        assertTrue(player.getAbilityManager().getSharedCooldown().startAvatarSkill(81203, 0));
    }

    @Test
    void retryKeepsCurrentChamberAndReturnsToTheUpperParty() throws Exception {
        assertTrue(system.tryRestartDungeon(player));
        assertNotSame(source, player.getScene());
        assertEquals(6202, player.getTowerManager().getCurrentLevelId());
        assertEquals(2, player.getTowerManager().getCurrentLevel());
        assertEquals(0, index());
        assertEquals(
                FIRST, player.getTeamManager().getCurrentAvatarEntity().getAvatar().getAvatarId());
        assertEquals(3, player.getScene().getPrevScene());
        assertEquals(16, player.getScene().getPrevScenePoint());
        assertTrue(player.getScene().getDungeonManager().isTowerDungeon());
        assertInstanceOf(
                TowerDungeonSettleListener.class,
                player.getScene().getDungeonSettleListeners().get(0));
    }

    @Test
    void retryRestoresBothPartiesIncludingSpecialEnergy() {
        assertTrue(system.tryRestartDungeon(player));
        assertEquals(37, avatar(FIRST).getCurrentHp());
        assertEquals(82, avatar(SECOND).getCurrentHp());
        assertEquals(11, avatar(FIRST).getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(5, avatar(SECOND).getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(13, avatar(FIRST).getFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY));
        assertEquals(
                29, avatar(SECOND).getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        assertTrue(player.getTeamManager().getCurrentAvatarEntity().isAlive());
    }

    @Test
    void successfulRetryEndsOldChallengeBookkeepingAndDisablesSkillsUntilLuaStarts() {
        player.getTowerManager().onBegin();
        assertTrue(player.getTowerManager().isInProgress());
        assertTrue(system.tryRestartDungeon(player));
        assertFalse(player.getTowerManager().isInProgress());
        assertEquals(0, player.getTowerManager().getCurrentTimeLimit());
        assertTrue(
                player.packets.stream()
                        .anyMatch(p -> p.getOpcode() == PacketOpcodes.CanUseSkillNotify));
    }

    @Test
    void retryClearsBothSelectedCooldownsAndPreservesUnselectedAndUnrelatedGroups() {
        cooldowns();
        assertTrue(system.tryRestartDungeon(player));
        var cooldown = player.getAbilityManager().getSkillCooldown();
        assertEquals(0, cooldown.state(avatar(FIRST), 81201, 2).getFullCdTimeListCount());
        assertEquals(0, cooldown.state(avatar(SECOND), 81201, 2).getFullCdTimeListCount());
        assertEquals(1, cooldown.state(avatar(OTHER), 81201, 2).getFullCdTimeListCount());
        assertFalse(player.getAbilityManager().getSharedCooldown().snapshot().containsKey(9901));
        assertTrue(player.getAbilityManager().getSharedCooldown().snapshot().containsKey(9902));
    }

    @Test
    void retrySendsFullChargeStateForTheLowerPartyToo() throws Exception {
        cooldowns();
        assertTrue(system.tryRestartDungeon(player));
        var resets = new HashMap<Long, AvatarSkillInfoNotify>();
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.AvatarSkillInfoNotify) {
                var proto = AvatarSkillInfoNotify.parseFrom(packet.getData());
                resets.put(proto.getGuid(), proto);
            }
        for (int id : List.of(FIRST, SECOND)) {
            assertTrue(resets.containsKey(avatar(id).getGuid()));
            var state = resets.get(avatar(id).getGuid()).getSkillMapMap().get(81201);
            assertNotNull(state);
            assertEquals(2, state.getMaxChargeCount());
            assertEquals(0, state.getFullCdTimeListCount());
        }
    }

    @Test
    void canceledRetryKeepsBattlePartyAndCooldowns() throws Exception {
        cooldowns();
        player.getTowerManager().onBegin();
        event(PlayerTeleportEvent::cancel);
        int notifications = player.packets.size();
        assertFalse(system.tryRestartDungeon(player));
        kept();
        assertTrue(player.getTowerManager().isInProgress());
        assertEquals(
                2,
                player.getAbilityManager()
                        .getSkillCooldown()
                        .state(avatar(SECOND), 81201, 2)
                        .getFullCdTimeListCount());
        assertTrue(player.getAbilityManager().getSharedCooldown().snapshot().containsKey(9901));
        assertEquals(notifications, player.packets.size());
    }

    @Test
    void preTransferExceptionKeepsOriginalPartyAndCheckpointValues() throws Exception {
        ((BirthData) data).throwPosition = true;
        assertThrows(IllegalStateException.class, () -> system.tryRestartDungeon(player));
        kept();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"checkpoint", "roster", "floor", "chamber", "depot", "maximum", "dungeon"})
    void invalidTowerContextCannotRebuildTheScene(String invalid) throws Exception {
        switch (invalid) {
            case "checkpoint" -> replaceField(
                    TowerData.class, player.getTowerData(), "chamberState", null);
            case "roster" -> player.getAvatars().getAvatars().remove(SECOND);
            case "floor" -> GameData.getTowerFloorDataMap().remove(6002);
            case "chamber" -> GameData.getTowerLevelDataMap().remove(6202);
            case "depot" -> replaceField(
                    AvatarSkillDepotData.class, avatar(SECOND).getSkillDepot(), "id", 903);
            case "maximum" -> avatar(SECOND)
                    .setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 0);
            case "dungeon" -> replaceField(
                    TowerLevelData.class,
                    GameData.getTowerLevelDataMap().get(6202),
                    "dungeonId",
                    9002);
        }
        assertFalse(system.tryRestartDungeon(player));
        kept();
    }

    @Test
    void teleportCallbackResourceChangesAreRecheckedBeforeScenePublication() throws Exception {
        event(e -> avatar(SECOND).setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 0));
        assertFalse(system.tryRestartDungeon(player));
        kept();
    }

    @Test
    void cooldownResetRejectsForeignAvatarBeforeClearingOwnedState() throws Exception {
        cooldowns();
        var foreign = new Player();
        var depot = avatar(OTHER).getSkillDepot();
        var outsider = new TowerChamberCheckpointTest.OwnedAvatar(10000024, depot);
        assertTrue(foreign.getAvatars().addAvatar(outsider));
        assertEquals(
                false,
                AbilitySkillCooldown.class
                        .getMethod("reset", Collection.class)
                        .invoke(
                                player.getAbilityManager().getSkillCooldown(),
                                List.of(avatar(FIRST), outsider)));
        assertEquals(
                2,
                player.getAbilityManager()
                        .getSkillCooldown()
                        .state(avatar(FIRST), 81201, 2)
                        .getFullCdTimeListCount());
    }

    @Test
    void cooldownResetRejectsDuplicatesWithoutClearingTheirQueuedCharges() throws Exception {
        cooldowns();
        assertEquals(
                false,
                AbilitySkillCooldown.class
                        .getMethod("reset", Collection.class)
                        .invoke(
                                player.getAbilityManager().getSkillCooldown(),
                                List.of(avatar(FIRST), avatar(FIRST))));
        assertEquals(
                2,
                player.getAbilityManager()
                        .getSkillCooldown()
                        .state(avatar(FIRST), 81201, 2)
                        .getFullCdTimeListCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingResetSelectionCannotClearAnyCooldown(boolean missing) throws Exception {
        cooldowns();
        assertEquals(
                false,
                AbilitySkillCooldown.class
                        .getMethod("reset", Collection.class)
                        .invoke(
                                player.getAbilityManager().getSkillCooldown(),
                                missing ? null : List.of()));
        assertEquals(
                2,
                player.getAbilityManager()
                        .getSkillCooldown()
                        .state(avatar(FIRST), 81201, 2)
                        .getFullCdTimeListCount());
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityAppearNotifyOuterClass.SceneEntityAppearNotify;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled arena identity, real scene addition and scheduler; no rendered game validation. */
class DvalinLateSpawnTest {
    private final Gson gson = new Gson();
    private AbilityTestFixture environment;
    private Room scene;
    private DungeonManager manager;
    private Map<Integer, MonsterData> monsters;

    static class Room extends DungeonBossCleanupTest.BossScene {
        private int identity;

        Room(World world, int id) {
            // Disable automatic resource loading during construction, then expose the arena ID.
            super(world, new Gson().fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
            identity = id;
        }

        @Override
        public int getId() {
            return identity == 0 ? 9 : identity;
        }
    }

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        GameData.setConfigGlobalCombat(
                gson.fromJson(
                        "{defaultAbilities:{defaultMPLevelAbilities:[],levelElementAbilities:[],nonHumanoidMoveAbilities:[]}}",
                        emu.grasscutter.data.binout.config.ConfigGlobalCombat.class));
        scene = new Room(environment.scene.getWorld(), 20025);
        scene.getWorld().getScenes().put(scene.getId(), scene);
        environment.player.setScene(scene);
        environment.player.setWorld(scene.getWorld());
        manager =
                new DungeonManager(
                        scene, gson.fromJson("{id:9001,sceneId:20025}", DungeonData.class));
        scene.setDungeonManager(manager);
        scene.addDungeonSettleObserver(new BasicDungeonSettleListener());
        monsters = new HashMap<>(GameData.getMonsterDataMap());
    }

    @AfterEach
    void close() {
        scene.getScriptManager().onDestroy();
        environment.scene.getScriptManager().onDestroy();
        GameData.getMonsterDataMap().clear();
        GameData.getMonsterDataMap().putAll(monsters);
        environment.close();
    }

    private MonsterData data(int id) {
        return gson.fromJson(
                "{id:"
                        + id
                        + ",type:'MONSTER_BOSS',equips:[],affix:[],hpDrops:[],propGrowCurves:[]}",
                MonsterData.class);
    }

    private DungeonBossCleanupTest.Monster monster(int id) {
        return new DungeonBossCleanupTest.Monster(scene, data(id)) {
            @Override
            public emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo toProto() {
                return emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo
                        .newBuilder()
                        .setEntityId(getId())
                        .build();
            }
        };
    }

    private void settle() {
        manager.endDungeon(DungeonEndReason.COMPLETED);
        scene.packets.clear();
        environment.player.packets.clear();
    }

    private Set<Integer> appeared() throws Exception {
        var ids = new HashSet<Integer>();
        var packets = new ArrayList<BasePacket>(scene.packets);
        packets.addAll(environment.player.packets);
        for (var packet : packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityAppearNotify)
                for (var entity :
                        SceneEntityAppearNotify.parseFrom(packet.getData()).getEntityListList())
                    ids.add(entity.getEntityId());
        return ids;
    }

    private void rejected(EntityMonster entity) throws Exception {
        assertFalse(scene.getEntities().containsKey(entity.getId()));
        assertFalse(appeared().contains(entity.getId()));
        assertFalse(entity.isAbilityLifecycleCurrent(entity.getAbilityLifecycleVersion()));
        assertEquals(0, scene.getKilledMonsterCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {29010101, 29010102, 29010103, 29010104})
    void successfulArenaCannotRegisterOrAnnounceALateDvalin(int id) throws Exception {
        settle();
        var late = monster(id);
        scene.addEntity(late);
        rejected(late);
        assertEquals(0, late.deaths);
    }

    @Test
    void singleClientAdditionCannotShowTheLateBoss() throws Exception {
        settle();
        var late = monster(29010104);
        scene.addEntityToSingleClient(environment.player, late);
        rejected(late);
    }

    @Test
    void mixedBatchKeepsPlatformsAndLootWithoutPublishingTheLateBoss() throws Exception {
        settle();
        var late = monster(29010104);
        var platform = new EntityGadget(scene, 42901021, new Position());
        platform.setConfigId(1002);
        var flower = new EntityGadget(scene, 70210106, new Position());
        flower.setConfigId(1011);
        scene.addEntities(List.of(late, platform, flower), VisionType.VisionType_VISION_MEET);
        rejected(late);
        assertSame(platform, scene.getEntityById(platform.getId()));
        assertSame(flower, scene.getEntityById(flower.getId()));
        assertTrue(appeared().containsAll(List.of(platform.getId(), flower.getId())));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 12})
    void queuedCreationCannotReviveTheSettledBossAtLaterSchedulerTicks(int delay) throws Exception {
        var late = monster(29010104);
        scene.getScheduler().scheduleDelayedTask(() -> scene.addEntity(late), delay);
        settle();
        for (int i = 0; i < delay; i++) scene.getScheduler().runTasks();
        rejected(late);
    }

    @ParameterizedTest
    @ValueSource(strings = {"restart", "manager", "directory", "cleanup"})
    void aNewOrRetiredRunDoesNotInheritThePreviousRunsSpawnSuppression(String change)
            throws Exception {
        settle();
        switch (change) {
            case "restart" -> manager.restartDungeon();
            case "manager" -> scene.setDungeonManager(
                    new DungeonManager(scene, manager.getDungeonData()));
            case "directory" -> scene.getWorld().getScenes().remove(scene.getId());
            case "cleanup" -> manager.cleanUpScene();
            default -> fail(change);
        }
        var fresh = monster(29010104);
        scene.addEntity(fresh);
        assertSame(fresh, scene.getEntityById(fresh.getId()));
        assertTrue(appeared().contains(fresh.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "QUIT"})
    void unsuccessfulArenaKeepsNormalSpawnBehaviour(String reason) throws Exception {
        manager.endDungeon(DungeonEndReason.valueOf(reason));
        scene.packets.clear();
        var fresh = monster(29010104);
        scene.addEntity(fresh);
        assertSame(fresh, scene.getEntityById(fresh.getId()));
        assertTrue(appeared().contains(fresh.getId()));
    }

    @Test
    void anUnfinishedArenaStillCreatesItsBoss() throws Exception {
        var fresh = monster(29010104);
        scene.addEntity(fresh);
        assertSame(fresh, scene.getEntityById(fresh.getId()));
        assertTrue(appeared().contains(fresh.getId()));
    }

    @Test
    void aDifferentBossIsNotMistakenForASecondDvalin() throws Exception {
        settle();
        var fresh = monster(29020101);
        scene.addEntity(fresh);
        assertSame(fresh, scene.getEntityById(fresh.getId()));
        assertTrue(appeared().contains(fresh.getId()));
    }

    @Test
    void aGadgetCreatedForARejectedBossCannotRemainInThePlayerRegistry() throws Exception {
        settle();
        var late = monster(29010104);
        scene.addEntity(late);
        var gadget =
                new EntityClientGadget(
                        scene,
                        environment.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(900)
                                .setOwnerEntityId(late.getId())
                                .build());
        scene.onPlayerCreateGadget(gadget);
        assertFalse(scene.getEntities().containsKey(900));
        assertFalse(environment.player.getTeamManager().getGadgets().contains(gadget));
        rejected(late);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void formalFactoriesRejectTheSettledBossBeforeCreationListeners(boolean byPosition)
            throws Exception {
        var definition = new SceneMonster();
        definition.config_id = 1001;
        definition.monster_id = 29010104;
        definition.level = 1;
        definition.pos = new Position();
        definition.rot = new Position();
        var group = SceneGroup.of(220025001);
        group.setLoaded(true);
        group.monsters = Map.of(1001, definition);
        group.gadgets = Map.of();
        group.regions = Map.of();
        group.triggers = Map.of();
        group.variables = List.of();
        group.suites = List.of();
        definition.group = group;
        var scripts = scene.getScriptManager();
        scripts.registerLocalGroups(List.of(group));
        scripts.loadGroupFromScript(group);
        GameData.getMonsterDataMap().put(29010104, data(29010104));
        var calls = new AtomicInteger();
        scripts.getScriptMonsterSpawnService()
                .addMonsterCreatedListener(entity -> calls.incrementAndGet());
        settle();
        var result =
                byPosition
                        ? scripts.createMonsterByConfigIdByPos(
                                group, 1001, new Position(), new Position())
                        : scripts.createMonster(group.id, group.block_id, definition);
        assertNull(result);
        assertEquals(0, calls.get());
        assertEquals(0, scene.getKilledMonsterCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creationListenersForAnUnfinishedArenaRunUnderTheSceneMonitor(boolean byPosition)
            throws Exception {
        var definition = new SceneMonster();
        definition.config_id = 1001;
        definition.monster_id = 29010104;
        definition.level = 1;
        definition.pos = new Position();
        definition.rot = new Position();
        var group = SceneGroup.of(220025001);
        group.setLoaded(true);
        group.monsters = Map.of(1001, definition);
        group.gadgets = Map.of();
        group.regions = Map.of();
        group.triggers = Map.of();
        group.variables = List.of();
        group.suites = List.of();
        definition.group = group;
        var scripts = scene.getScriptManager();
        scripts.registerLocalGroups(List.of(group));
        scripts.loadGroupFromScript(group);
        GameData.getMonsterDataMap().put(29010104, data(29010104));
        var calls = new AtomicInteger();
        var locked = new java.util.concurrent.atomic.AtomicBoolean();
        scripts.getScriptMonsterSpawnService()
                .addMonsterCreatedListener(
                        entity -> {
                            calls.incrementAndGet();
                            locked.set(Thread.holdsLock(scene));
                        });
        var result =
                byPosition
                        ? scripts.createMonsterByConfigIdByPos(
                                group, 1001, new Position(), new Position())
                        : scripts.createMonster(group.id, group.block_id, definition);
        assertNotNull(result);
        assertEquals(1, calls.get());
        assertTrue(locked.get());
    }

    @Test
    void aCompletedDifferentArenaCanStillAddDvalin() throws Exception {
        var previous = scene;
        scene = new Room(previous.getWorld(), 20026);
        scene.getWorld().getScenes().put(scene.getId(), scene);
        manager =
                new DungeonManager(
                        scene, gson.fromJson("{id:9002,sceneId:20026}", DungeonData.class));
        scene.setDungeonManager(manager);
        scene.addDungeonSettleObserver(new BasicDungeonSettleListener());
        previous.getScriptManager().onDestroy();
        settle();
        var fresh = monster(29010104);
        scene.addEntity(fresh);
        assertSame(fresh, scene.getEntityById(fresh.getId()));
        assertTrue(appeared().contains(fresh.getId()));
    }
}

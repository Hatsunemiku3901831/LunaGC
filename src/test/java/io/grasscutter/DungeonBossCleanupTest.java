package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.MonsterType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityDisappearNotifyOuterClass.SceneEntityDisappearNotify;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.SimpleBindings;

/** Real removal, death hooks and protocol packets with isolated entities and no game process. */
class DungeonBossCleanupTest {
    private final Gson gson = new Gson();
    private AbilityTestFixture environment;
    private BossScene scene;
    private final List<BossScene> scenes = new ArrayList<>();
    private final List<String> order = new ArrayList<>();
    private final AtomicReference<Throwable> hookFailure = new AtomicReference<>();

    static class BossScene extends Scene {
        final List<BasePacket> packets = new ArrayList<>();

        BossScene(World world, SceneData data) {
            super(world, data, false);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    static class Monster extends EntityMonster {
        int deaths, removed;
        Runnable removal = () -> {};

        Monster(Scene scene, MonsterData data) {
            super(scene, data, new Position(), new Position(), 1);
        }

        @Override
        public void recalcStats() {}

        @Override
        public void initAbilities() {}

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }

        @Override
        public void onRemoved() {
            removed++;
            removal.run();
        }

        @Override
        public void onDeath(int killer) {
            deaths++;
            super.onDeath(killer);
        }
    }

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        scene = makeScene(9, "SCENE_DUNGEON");
        environment.player.setScene(scene);
        environment.player.setWorld(scene.getWorld());
    }

    @AfterEach
    void close() {
        scenes.forEach(s -> s.getScriptManager().onDestroy());
        environment.scene.getScriptManager().onDestroy();
        environment.close();
    }

    private BossScene makeScene(int id, String type) {
        var result =
                new BossScene(
                        environment.scene.getWorld(),
                        gson.fromJson("{id:" + id + ",type:'" + type + "'}", SceneData.class));
        scenes.add(result);
        result.getWorld().getScenes().put(id, result);
        return result;
    }

    private Monster monster(BossScene where, int id, String type) {
        var data =
                gson.fromJson(
                        "{id:" + id + ",type:'" + type + "',equips:[],affix:[],hpDrops:[]}",
                        MonsterData.class);
        var result = new Monster(where, data);
        where.getEntities().put(result.getId(), result);
        return result;
    }

    private Monster boss() {
        return monster(scene, 29010101, "MONSTER_BOSS");
    }

    private EntityClientGadget gadget(BossScene where, int id, int owner, int propOwner) {
        var value =
                new EntityClientGadget(
                        where,
                        environment.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(id)
                                .setOwnerEntityId(owner)
                                .setPropOwnerEntityId(propOwner)
                                .build());
        where.onPlayerCreateGadget(value);
        return value;
    }

    private List<SceneEntityDisappearNotify> disappearances() throws Exception {
        var result = new ArrayList<SceneEntityDisappearNotify>();
        for (var packet : scene.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify)
                result.add(SceneEntityDisappearNotify.parseFrom(packet.getData()));
        return result;
    }

    private void removedWith(GameEntity entity, VisionType type) throws Exception {
        assertFalse(scene.getEntities().containsKey(entity.getId()));
        var packets =
                disappearances().stream()
                        .filter(p -> p.getEntityListList().contains(entity.getId()))
                        .toList();
        assertEquals(1, packets.size(), "each entity needs exactly one disappearance");
        assertEquals(type, packets.get(0).getDisappearType());
    }

    private DungeonManager manager() {
        var value =
                new DungeonManager(scene, gson.fromJson("{id:9001,sceneId:9}", DungeonData.class));
        scene.setDungeonManager(value);
        scene.addDungeonSettleObserver(new BasicDungeonSettleListener());
        return value;
    }

    private void beforeDeath(Monster target, Runnable callback) {
        var group = SceneGroup.of(100);
        group.setLoaded(true);
        group.setBindings(new SimpleBindings());
        group.monsters = Map.of();
        group.gadgets = Map.of();
        group.regions = Map.of();
        group.triggers = Map.of();
        group.variables = List.of();
        group.suites = List.of();
        scene.getScriptManager().registerLocalGroups(List.of(group));
        scene.getScriptManager().loadGroupFromScript(group);
        target.setGroupId(group.id);
        target.onCreate();
        group.getBindings()
                .put(
                        "before",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                try {
                                    callback.run();
                                } catch (Throwable error) {
                                    hookFailure.set(error);
                                }
                                return LuaValue.ZERO;
                            }
                        });
        var trigger = new SceneTrigger();
        trigger.currentGroup = group;
        trigger.setName("before");
        trigger.setEvent(EventType.EVENT_MONSTER_DIE_BEFORE_LEAVE_SCENE);
        trigger.setSource("");
        trigger.setCondition("");
        trigger.setAction("before");
        scene.getScriptManager().registerTrigger(trigger);
    }

    @Test
    void bossDeathRemovesItsModelAndAllNestedClientGadgetsOnce() throws Exception {
        var root = boss();
        var direct = gadget(scene, 900, root.getId(), 0);
        var nested = gadget(scene, 901, direct.getId(), 0);
        long version = nested.getAbilityLifecycleVersion();
        scene.killEntity(root);
        removedWith(root, VisionType.VisionType_VISION_REMOVE);
        removedWith(direct, VisionType.VisionType_VISION_REMOVE);
        removedWith(nested, VisionType.VisionType_VISION_REMOVE);
        assertFalse(nested.isAbilityLifecycleCurrent(version));
        assertFalse(environment.player.getTeamManager().getGadgets().contains(direct));
        assertFalse(environment.player.getTeamManager().getGadgets().contains(nested));
        scene.killEntity(root);
        assertEquals(1, root.deaths);
        assertEquals(1, root.removed);
        assertEquals(1, scene.getKilledMonsterCount());
    }

    @Test
    void beforeDeathLuaStillSeesTheBossAndItsGadgetBeforeRemoval() throws Exception {
        var root = boss();
        var owned = gadget(scene, 900, root.getId(), 0);
        root.removal = () -> order.add("removed");
        beforeDeath(
                root,
                () -> {
                    assertTrue(Thread.holdsLock(scene));
                    assertSame(root, scene.getEntityById(root.getId()));
                    assertSame(owned, scene.getEntityById(owned.getId()));
                    order.add("before");
                });
        scene.killEntity(root);
        assertNull(hookFailure.get());
        assertEquals(List.of("before", "removed"), order);
        assertEquals(1, root.deaths);
        removedWith(root, VisionType.VisionType_VISION_REMOVE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aServantsGadgetsAreCleanedWithoutSkippingItsDeathAndKillCredit(boolean explicitOwner)
            throws Exception {
        var root = boss();
        var servant = monster(scene, 29080201, "MONSTER_ORDINARY");
        if (explicitOwner) servant.setOwnerEntityId(root.getId());
        root.getSummonTagMap().put(1, servant);
        var owned = gadget(scene, 900, servant.getId(), 0);
        scene.killEntity(root);
        assertEquals(1, root.deaths);
        assertEquals(1, servant.deaths);
        assertEquals(2, scene.getKilledMonsterCount());
        removedWith(owned, VisionType.VisionType_VISION_REMOVE);
        removedWith(servant, VisionType.VisionType_VISION_DIE);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                29080201, 29080202, 29080301, 29080302, 29080401, 29080501, 29080601, 29080701,
                29080702, 29090201, 29090202, 29090203, 29090304
            })
    void ordinaryResourceRowsInTheWeeklyIdRangeKeepTheirDeathBehaviour(int id) throws Exception {
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/MonsterExcelConfigData.json")),
                        MonsterData[].class);
        var actual = Arrays.stream(rows).filter(r -> r.getId() == id).findFirst().orElseThrow();
        assertEquals(MonsterType.MONSTER_ORDINARY, actual.getType());
        var root = monster(scene, id, actual.getType().name());
        var owned = gadget(scene, 900, root.getId(), 0);
        scene.killEntity(root);
        removedWith(root, VisionType.VisionType_VISION_DIE);
        assertSame(owned, scene.getEntityById(900));
    }

    @Test
    void overworldBossKeepsOrdinaryDeathAndOwnedGadgets() throws Exception {
        scene = makeScene(8, "SCENE_WORLD");
        var root = monster(scene, 29010101, "MONSTER_BOSS");
        var owned = gadget(scene, 900, root.getId(), 0);
        scene.killEntity(root);
        removedWith(root, VisionType.VisionType_VISION_DIE);
        assertSame(owned, scene.getEntityById(900));
    }

    @Test
    void completedDungeonSweepsRemainingBossModelsAndOwnedServantsWithoutKillRewards()
            throws Exception {
        var root = boss();
        var servant = monster(scene, 29080201, "MONSTER_ORDINARY");
        servant.setOwnerEntityId(root.getId());
        var owned = gadget(scene, 900, servant.getId(), 0);
        var ordinary = monster(scene, 21010101, "MONSTER_ORDINARY");
        var playerOwned = gadget(scene, 901, 42, root.getId());
        var platformSource =
                Files.readString(
                        Path.of("resources/Scripts/Scene/20025/scene20025_group220025001.lua"));
        var platforms = new ArrayList<EntityGadget>();
        for (int config = 1002; config <= 1010; config++) {
            int gadgetId = 42901021 + config - 1002;
            assertTrue(
                    platformSource.contains("config_id = " + config + ", gadget_id = " + gadgetId));
            var platform = new EntityGadget(scene, gadgetId, new Position());
            platform.setConfigId(config);
            platform.setOwner(root);
            scene.getEntities().put(platform.getId(), platform);
            platforms.add(platform);
        }
        manager().endDungeon(DungeonEndReason.COMPLETED);
        removedWith(root, VisionType.VisionType_VISION_REMOVE);
        removedWith(servant, VisionType.VisionType_VISION_REMOVE);
        removedWith(owned, VisionType.VisionType_VISION_REMOVE);
        assertEquals(0, root.deaths);
        assertEquals(0, servant.deaths);
        assertEquals(0, scene.getKilledMonsterCount());
        assertSame(ordinary, scene.getEntityById(ordinary.getId()));
        assertSame(playerOwned, scene.getEntityById(901));
        assertTrue(environment.player.getTeamManager().getGadgets().contains(playerOwned));
        for (var platform : platforms) assertSame(platform, scene.getEntityById(platform.getId()));
        int end = -1, lastRemove = -1;
        for (int i = 0; i < scene.packets.size(); i++) {
            if (scene.packets.get(i).getOpcode() == PacketOpcodes.DungeonSettleNotify) end = i;
            if (scene.packets.get(i).getOpcode() == PacketOpcodes.SceneEntityDisappearNotify)
                lastRemove = i;
        }
        assertTrue(end > lastRemove && lastRemove >= 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "QUIT"})
    void unsuccessfulSettlementDoesNotSweepLiveBosses(String kind) throws Exception {
        var root = boss();
        var owned = gadget(scene, 900, root.getId(), 0);
        manager().endDungeon(DungeonEndReason.valueOf(kind));
        assertSame(root, scene.getEntityById(root.getId()));
        assertSame(owned, scene.getEntityById(900));
        assertTrue(disappearances().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager", "scene", "destroy"})
    void retiredSettlementDoesNotSweepTheCurrentSceneEntities(String change) throws Exception {
        var root = boss();
        var owned = gadget(scene, 900, root.getId(), 0);
        var manager = manager();
        switch (change) {
            case "manager" -> scene.setDungeonManager(
                    new DungeonManager(scene, manager.getDungeonData()));
            case "scene" -> scene.getWorld().getScenes().remove(scene.getId());
            case "destroy" -> scene.getScriptManager().onDestroy();
            default -> fail(change);
        }
        manager.endDungeon(DungeonEndReason.COMPLETED);
        assertSame(root, scene.getEntityById(root.getId()));
        assertSame(owned, scene.getEntityById(900));
        assertTrue(disappearances().isEmpty());
    }

    @Test
    void aLateGadgetForARemovedBossIsRejectedFromBothRegistries() throws Exception {
        var root = boss();
        scene.killEntity(root);
        scene.packets.clear();
        var late = gadget(scene, 900, root.getId(), 0);
        removedWith(late, VisionType.VisionType_VISION_REMOVE);
        assertFalse(environment.player.getTeamManager().getGadgets().contains(late));
        assertEquals(1, root.deaths);
        assertEquals(1, scene.getKilledMonsterCount());
    }

    @Test
    void lateGadgetsForRemovedNestedOwnersAreAlsoRejected() throws Exception {
        var root = boss();
        var direct = gadget(scene, 900, root.getId(), 0);
        scene.killEntity(root);
        scene.packets.clear();
        var late = gadget(scene, 901, direct.getId(), 0);
        var next = gadget(scene, 902, late.getId(), 0);
        removedWith(late, VisionType.VisionType_VISION_REMOVE);
        removedWith(next, VisionType.VisionType_VISION_REMOVE);
    }

    @Test
    void aReusedBossIdDoesNotRejectTheNewOwnersGadget() throws Exception {
        var root = boss();
        scene.killEntity(root);
        var replacement = monster(scene, 21010101, "MONSTER_ORDINARY");
        scene.getEntities().remove(replacement.getId());
        replacement.setId(root.getId());
        scene.getEntities().put(replacement.getId(), replacement);
        scene.packets.clear();
        var owned = gadget(scene, 900, replacement.getId(), 0);
        assertSame(owned, scene.getEntityById(900));
        assertTrue(disappearances().isEmpty());
        manager().endDungeon(DungeonEndReason.COMPLETED);
        assertSame(replacement, scene.getEntityById(replacement.getId()));
        assertSame(owned, scene.getEntityById(900));
    }

    @Test
    void aForeignGadgetCannotBeRemovedByMatchingItsOwnerId() throws Exception {
        var root = boss();
        var other = makeScene(8, "SCENE_DUNGEON");
        var owned = gadget(other, 900, root.getId(), 0);
        scene.getEntities().put(owned.getId(), owned);
        scene.killEntity(root);
        assertSame(owned, scene.getEntityById(900));
        assertSame(owned, other.getEntityById(900));
        assertTrue(environment.player.getTeamManager().getGadgets().contains(owned));
    }

    @Test
    void aNestedGadgetWhoseDirectOwnerWasAlreadyRemovedStillBelongsToTheBoss() throws Exception {
        var root = boss();
        var parent = gadget(scene, 900, root.getId(), 0);
        var nested = gadget(scene, 901, parent.getId(), 0);
        assertEquals(root.getId(), nested.getOriginalOwnerEntityId());
        scene.onPlayerDestroyGadget(parent.getId());
        scene.packets.clear();
        scene.killEntity(root);
        removedWith(nested, VisionType.VisionType_VISION_REMOVE);
        assertFalse(environment.player.getTeamManager().getGadgets().contains(nested));
    }

    @Test
    void removalCallbacksCannotRemoveOrInvalidateAGadgetReplacingTheSnapshotEntry()
            throws Exception {
        var root = boss();
        var replacement = new AtomicReference<EntityClientGadget>();
        var parent =
                new EntityClientGadget(
                        scene,
                        environment.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(900)
                                .setOwnerEntityId(root.getId())
                                .build()) {
                    @Override
                    public void onRemoved() {
                        replacement.set(gadget(scene, 901, 42, 0));
                    }
                };
        scene.onPlayerCreateGadget(parent);
        var nested = gadget(scene, 901, parent.getId(), 0);
        scene.killEntity(root);
        var current = replacement.get();
        assertNotNull(current);
        assertSame(current, scene.getEntityById(901));
        assertTrue(current.isAbilityLifecycleCurrent(current.getAbilityLifecycleVersion()));
        assertTrue(environment.player.getTeamManager().getGadgets().contains(current));
        assertFalse(environment.player.getTeamManager().getGadgets().contains(nested));
        assertTrue(
                disappearances().stream()
                        .noneMatch(packet -> packet.getEntityListList().contains(901)));
    }
}

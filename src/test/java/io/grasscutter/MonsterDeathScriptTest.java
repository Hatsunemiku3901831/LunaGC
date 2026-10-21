package io.grasscutter;

import static emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager.*;
import static emu.grasscutter.scripts.constants.EventType.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.*;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import javax.script.SimpleBindings;

class MonsterDeathScriptTest {
    private AbilityTestFixture environment;
    private DeathScene scene;
    private DeathScripts scripts;
    private SceneGroup group;
    private ScriptLib library;
    private final List<ScriptArgs> events = new CopyOnWriteArrayList<>();
    private final List<String> order = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
    private Consumer<ScriptArgs> onEvent = event -> {};

    @BeforeAll
    static void initialize() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        scene =
                new DeathScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), environment.player));
        scripts = scene.scripts;
        group = group(100);
        library = ScriptLoader.getScriptLib();
        capture(group, "before", EVENT_MONSTER_DIE_BEFORE_LEAVE_SCENE);
        var player = environment.player;
        var activity = Player.class.getDeclaredField("activityManager");
        activity.setAccessible(true);
        activity.set(
                player,
                new emu.grasscutter.game.activity.ActivityManager(player) {
                    @Override
                    public synchronized void refreshActivities() {}
                });
        player.setUid(10001);
        player.setPeerId(1);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
    }

    @AfterEach
    void cleanup() {
        scene.scripts.onDestroy();
        library.removeSceneScriptManager();
        library.removeCurrentGroup();
        library.removeCurrentEntity();
        library.removeCurrentCallParams();
        environment.close();
    }

    private SceneGroup group(int id) {
        var result = SceneGroup.of(id);
        result.monsters = new HashMap<>();
        result.setBindings(new SimpleBindings());
        scripts.loaded.put(id, result);
        return result;
    }

    private SceneTrigger capture(SceneGroup owner, String functionName, int type) {
        owner.getBindings()
                .put(
                        functionName,
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                var event = (ScriptArgs) args.arg(2).touserdata(ScriptArgs.class);
                                try {
                                    assertTrue(Thread.holdsLock(scene));
                                    assertSame(owner, library.getCurrentGroup().orElseThrow());
                                    assertSame(scripts, library.getSceneScriptManagerOrNull());
                                    assertEquals(event.uid, args.arg1().get("uid").toint());
                                    events.add(event);
                                    order.add(functionName);
                                    onEvent.accept(event);
                                } catch (Throwable failure) {
                                    callbackFailure.set(failure);
                                }
                                return LuaValue.ZERO;
                            }
                        });
        var trigger = new SceneTrigger();
        trigger.currentGroup = owner;
        trigger.setName(functionName);
        trigger.setEvent(type);
        trigger.setSource("");
        trigger.setCondition("");
        trigger.setAction(functionName);
        scripts.registerTrigger(trigger);
        return trigger;
    }

    private TestMonster monster(SceneGroup owner, int config) {
        var definition = new SceneMonster();
        definition.config_id = config;
        definition.monster_id = 21010101;
        definition.group = owner;
        owner.monsters.put(config, definition);
        var result = new TestMonster(scene);
        result.setGroupId(owner.id);
        result.setConfigId(config);
        result.setMetaMonster(definition);
        result.removed = () -> order.add("removed");
        result.died = () -> order.add("after");
        scene.addEntity(result);
        return result;
    }

    private EntityAvatar avatar() {
        var player = environment.player;
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    @Test
    void beforeDeathRunsInlineWhileMonsterRemainsThenRemovalAndSettlementFollow() {
        var monster = monster(group, 7);
        var caller = Thread.currentThread();
        onEvent =
                event -> {
                    assertSame(caller, Thread.currentThread());
                    assertSame(monster, scene.getEntityById(event.source_eid));
                    assertEquals(7, event.param1);
                    assertEquals(0, event.param2);
                    assertEquals(10, event.param3);
                    assertEquals(1, event.param4);
                };
        scene.killEntity(monster, 0, ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_SCRIPT);
        assertNull(callbackFailure.get());
        assertEquals(List.of("before", "removed", "after"), order);
        assertNull(scene.getEntityById(monster.getId()));
        assertEquals(1, monster.deaths);
    }

    @Test
    void duplicateAndReentrantKillsDoNotRepeatBeforeEventOrSettlement() {
        var monster = monster(group, 1);
        onEvent = event -> scene.killEntity(monster);
        scene.killEntity(monster);
        scene.killEntity(monster);
        assertEquals(1, events.size());
        assertEquals(1, monster.deaths);
        assertNull(callbackFailure.get());
    }

    @Test
    void simultaneousKillsAreSettledOnce() throws Exception {
        var monster = monster(group, 1);
        var workers = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var work = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++)
                work.add(
                        workers.submit(
                                () -> {
                                    start.await();
                                    scene.killEntity(monster);
                                    return null;
                                }));
            start.countDown();
            for (var task : work) task.get(5, TimeUnit.SECONDS);
            assertEquals(1, events.size());
            assertEquals(1, monster.deaths);
            assertNull(callbackFailure.get());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void wrongSceneRemovedAndReplacedEntityObjectsCannotFireBeforeEvent() {
        var monster = monster(group, 1);
        environment.scene.killEntity(monster);
        assertTrue(events.isEmpty());
        scene.removeEntity(monster);
        scene.killEntity(monster);
        var replacement = monster(group, 2);
        replacement.setId(monster.getId());
        scene.getEntities().put(monster.getId(), replacement);
        scene.killEntity(monster);
        assertTrue(events.isEmpty());
        assertEquals(0, monster.deaths);
        assertSame(replacement, scene.getEntityById(monster.getId()));
    }

    @Test
    void callbackRemovalAndReplacementDoNotDeleteTheReplacement() {
        var monster = monster(group, 1);
        var replacement = monster(group, 2);
        onEvent =
                event -> {
                    scene.removeEntity(monster);
                    scene.getEntities().remove(replacement.getId());
                    replacement.setId(monster.getId());
                    scene.getEntities().put(replacement.getId(), replacement);
                };
        scene.killEntity(monster);
        assertEquals(1, events.size());
        assertSame(replacement, scene.getEntityById(monster.getId()));
        assertEquals(0, replacement.deaths);
        assertNull(callbackFailure.get());
    }

    @Test
    void avatarAttackerUidIsKeptSeparateFromMonsterEntityId() {
        var monster = monster(group, 7);
        var avatar = avatar();
        scene.killEntity(monster, avatar.getId());
        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals(10001, event.param2);
        assertEquals(10001, event.uid);
        assertEquals(monster.getId(), event.source_eid);
        assertNull(callbackFailure.get());
    }

    @Test
    void clientSummonCreditsItsOriginalAvatar() {
        var monster = monster(group, 7);
        var avatar = avatar();
        var summon =
                new EntityClientGadget(
                        scene,
                        environment.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(4321)
                                .setOwnerEntityId(avatar.getId())
                                .build());
        scene.getEntities().put(summon.getId(), summon);
        scene.killEntity(monster, summon.getId());
        assertEquals(10001, events.get(0).param2);
        assertNull(callbackFailure.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 5, 11, 12})
    void lethalDamageForwardsTheSameHpReasonAsTheHpNotification(int reason) {
        var monster = monster(group, 7);
        monster.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
        monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100f);
        monster.damage(
                100f,
                0,
                ElementType.None,
                emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason
                        .PropChangeReason_PROP_CHANGE_ABILITY,
                ChangeHpReason.forNumber(reason));
        assertEquals(1, events.size());
        assertEquals(reason, events.get(0).param3);
        assertEquals(1, monster.deaths);
        assertNull(callbackFailure.get());
    }

    @Test
    void ordinaryDamageInfersAvatarHpReason() {
        var monster = monster(group, 7);
        monster.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
        monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100f);
        monster.damage(100f, avatar().getId(), ElementType.None);
        assertEquals(1, events.get(0).param3);
        assertNull(callbackFailure.get());
    }

    @Test
    void sceneLockedOuterLuaContextIsRestoredWithoutWaitingForTheExecutor() throws Exception {
        var monster = monster(group, 7);
        var outer = group(200);
        var event = new ScriptArgs(outer.id, EVENT_SELECT_OPTION, 123, 2).setUid(10002);
        library.setSceneScriptManager(scripts);
        library.setCurrentGroup(outer);
        library.setCurrentEntity(environment.entity);
        library.setCurrentCallParams(event);
        synchronized (scene) {
            scene.killEntity(monster);
        }
        assertSame(scripts, library.getSceneScriptManagerOrNull());
        assertSame(outer, library.getCurrentGroup().orElseThrow());
        assertSame(environment.entity, library.getCurrentEntity().orElseThrow());
        assertSame(event, currentParams());
        assertNull(callbackFailure.get());
    }

    private Object currentParams() throws Exception {
        var field = ScriptLib.class.getDeclaredField("callParams");
        field.setAccessible(true);
        return ((io.netty.util.concurrent.FastThreadLocal<?>) field.get(library)).getIfExists();
    }

    @Test
    void nestedDeathRestoresOuterTriggerGroupAndEvent() {
        var innerGroup = group(200);
        capture(innerGroup, "inner", EVENT_MONSTER_DIE_BEFORE_LEAVE_SCENE);
        var inner = monster(innerGroup, 8);
        var outer = monster(group, 7);
        onEvent =
                event -> {
                    if (event.group_id != group.id) return;
                    scene.killEntity(inner);
                    assertSame(group, library.getCurrentGroup().orElseThrow());
                    try {
                        assertSame(event, currentParams());
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                };
        scene.killEntity(outer);
        assertEquals(List.of(100, 200), events.stream().map(event -> event.group_id).toList());
        assertNull(callbackFailure.get());
    }

    @Test
    void failedLuaActionDoesNotCancelDeathOrLeakContext() {
        group.getBindings()
                .put(
                        "before",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                throw new LuaError("fixture failure");
                            }
                        });
        var monster = monster(group, 7);
        library.removeSceneScriptManager();
        library.removeCurrentGroup();
        scene.killEntity(monster);
        assertEquals(1, monster.deaths);
        assertNull(library.getSceneScriptManagerOrNull());
        assertTrue(library.getCurrentGroup().isEmpty());
        assertEquals(List.of("removed", "after"), order);
    }

    @Test
    void abyssCleanupRemovesNestedSummonsAfterOwnerLeavesWithoutCountingKills() {
        var owner = monster(group, 7009);
        var otherOwner = monster(group(200), 7009);
        var child = new EntityClientGadget(scene, environment.player,
                EvtCreateGadgetNotify.newBuilder().setEntityId(4311).setOwnerEntityId(owner.getId()).build());
        scene.addEntity(child);
        var nested = new EntityClientGadget(scene, environment.player,
                EvtCreateGadgetNotify.newBuilder().setEntityId(4312).setOwnerEntityId(child.getId()).build());
        scene.addEntity(nested);
        var unrelated = new EntityClientGadget(scene, environment.player,
                EvtCreateGadgetNotify.newBuilder().setEntityId(4313).setOwnerEntityId(otherOwner.getId()).build());
        scene.addEntity(unrelated);
        scene.removeEntity(owner);
        library.setSceneScriptManager(scripts);
        library.setCurrentGroup(group);
        int count = scene.getKilledMonsterCount();
        assertEquals(0, library.DelAllSubEntityByOriginOwnerConfigId(7009));
        assertNull(scene.getEntityById(4311));
        assertNull(scene.getEntityById(4312));
        assertSame(unrelated, scene.getEntityById(4313));
        assertEquals(count, scene.getKilledMonsterCount());
        assertEquals(0, library.DelAllSubEntityByOriginOwnerConfigId(7009));
        library.setCurrentGroup(SceneGroup.of(group.id));
        assertEquals(-1, library.DelAllSubEntityByOriginOwnerConfigId(7009));
    }

    @Test
    void staleGroupAndDestroyedSceneDoNotExecuteBeforeEvent() {
        var monster = monster(group, 1);
        group(100);
        scene.killEntity(monster);
        assertTrue(events.isEmpty());
        var fresh = monster(scripts.loaded.get(100), 2);
        scripts.onDestroy();
        scene.killEntity(fresh);
        assertTrue(events.isEmpty());
        assertEquals(1, fresh.deaths);
    }

    @Test
    void staleTriggerForReplacedGroupCannotRunAlongsideFreshTrigger() {
        var current = group(100);
        capture(current, "fresh", EVENT_MONSTER_DIE_BEFORE_LEAVE_SCENE);
        scene.killEntity(monster(current, 1));
        assertEquals(List.of("fresh", "removed", "after"), order);
        assertNull(callbackFailure.get());
    }

    @Test
    void removalInConditionPreventsItsAction() {
        var monster = monster(group, 1);
        group.getBindings()
                .put(
                        "remove",
                        new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                scene.removeEntity(monster);
                                return LuaValue.TRUE;
                            }
                        });
        scripts.getTriggersByEvent(EVENT_MONSTER_DIE_BEFORE_LEAVE_SCENE)
                .forEach(trigger -> trigger.setCondition("remove"));
        scene.killEntity(monster);
        assertTrue(events.isEmpty());
        assertEquals(1, monster.deaths);
    }

    private MpPlayGroupData entry() {
        return new Gson()
                .fromJson(
                        "{\"playId\":2,\"groupList\":[100],"
                                + "\"serverLuaCallGroupList\":[100],\"prepareTime\":0}",
                        MpPlayGroupData.class);
    }

    private void prepare() {
        assertTrue(
                scene.getScenePlayBattleManager()
                        .prepare(
                                entry(),
                                new Gson()
                                        .fromJson(
                                                "{\"id\":2,\"minPlayers\":1,\"maxPlayers\":4}",
                                                MpPlayMatchData.class),
                                2,
                                DIRECT,
                                List.of(environment.player)));
    }

    private void start() {
        prepare();
        assertTrue(
                scene.getScenePlayBattleManager()
                        .prestart(
                                group.id,
                                new ScenePlayBattleParameters(600, 0, 201, List.of(0L, 2000L))));
    }

    @Test
    void activeBattleScoresButCountdownAndStoppedBattleDoNot() {
        prepare();
        scene.killEntity(monster(group, 1));
        assertTrue(events.isEmpty());
        assertTrue(
                scene.getScenePlayBattleManager()
                        .prestart(
                                group.id,
                                new ScenePlayBattleParameters(600, 0, 201, List.of(0L, 2000L))));
        scene.killEntity(monster(group, 2));
        assertEquals(1, events.size());
        scene.getScenePlayBattleManager().onPlayerLeaving(environment.player);
        scene.killEntity(monster(group, 3));
        assertEquals(1, events.size());
        assertNull(callbackFailure.get());
    }

    @Test
    void oldSurvivorCannotScoreInANewRoundUsingTheSameGroupObject() {
        start();
        var old = monster(group, 1);
        scene.getScenePlayBattleManager().onPlayerLeaving(environment.player);
        start();
        scene.killEntity(old);
        assertTrue(events.isEmpty());
        scene.killEntity(monster(group, 2));
        assertEquals(1, events.size());
        assertNull(callbackFailure.get());
    }

    @Test
    void callbackCannotReplaceTheRoundBeforeReturning() {
        start();
        var monster = monster(group, 1);
        onEvent =
                event -> {
                    scene.getScenePlayBattleManager().onPlayerLeaving(environment.player);
                    assertFalse(
                            scene.getScenePlayBattleManager()
                                    .prepare(
                                            entry(),
                                            new Gson()
                                                    .fromJson(
                                                            "{\"id\":2,\"minPlayers\":1,\"maxPlayers\":4}",
                                                            MpPlayMatchData.class),
                                            2,
                                            DIRECT,
                                            List.of(environment.player)));
                };
        scene.killEntity(monster);
        assertEquals(1, events.size());
        assertNull(callbackFailure.get());
        prepare();
    }

    @Test
    void expiredBattleStopsBeforeDeathCanScore() {
        start();
        var monster = monster(group, 1);
        scene.now.set(1600);
        scene.killEntity(monster);
        assertTrue(events.isEmpty());
        assertEquals(STOP, scene.getScenePlayBattleManager().getInfo(group.id).getState());
    }

    private static final class DeathScene extends AbilityTestFixture.TestScene {
        final AtomicLong now = new AtomicLong(1000);
        final DeathScripts scripts;

        DeathScene(AbilityTestFixture.TestWorld world) throws Exception {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_DUNGEON\"}", SceneData.class));
            scripts = new DeathScripts(this);
            var field = Scene.class.getDeclaredField("scenePlayBattleManager");
            field.setAccessible(true);
            field.set(this, new ScenePlayBattleManager(this, now::get));
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class DeathScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new HashMap<>();

        DeathScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public Future<?> callEvent(ScriptArgs event, BooleanSupplier current) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class TestMonster extends EntityMonster {
        int deaths;
        Runnable removed = () -> {}, died = () -> {};

        TestMonster(Scene scene) {
            super(
                    scene,
                    new Gson()
                            .fromJson(
                                    "{\"id\":21010101,\"type\":\"MONSTER_ORDINARY\","
                                            + "\"equips\":[],\"affix\":[],\"hpDrops\":[]}",
                                    MonsterData.class),
                    new Position(),
                    new Position(),
                    1);
        }

        @Override
        public void initAbilities() {}

        @Override
        public void recalcStats() {}

        @Override
        public void onRemoved() {
            removed.run();
        }

        @Override
        public void onDeath(int killer) {
            deaths++;
            died.run();
        }

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }
    }
}

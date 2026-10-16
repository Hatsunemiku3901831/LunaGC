package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

class DungeonChallengeEntryTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestScene scene;
    private DungeonData data;
    private DungeonManager manager;
    private SceneMeta meta;
    private SceneGroup group;
    private SceneGadget key;
    private EntityGadget entity;
    private DungeonChallengeEntry entry;
    private final AtomicLong clock = new AtomicLong();
    private final List<Runnable> restores = new ArrayList<>();

    @BeforeAll
    static void initializeLua() throws Exception {
        if (emu.grasscutter.scripts.ScriptLoader.getEngine() == null)
            emu.grasscutter.scripts.ScriptLoader.init();
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) fixture.scene.getWorld(),
                        JsonUtils.decode("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        scene.getWorld().getScenes().put(9, scene);
        fixture.player.setScene(scene);
        fixture.player.setWorld(scene.getWorld());
        scene.getPlayers().add(fixture.player);
        data =
                JsonUtils.decode(
                        "{id:55,sceneId:9,type:'DUNGEON_DAILY_FIGHT',subType:'DUNGEON_SUB_WEAPON',passCond:55}",
                        DungeonData.class);
        put(
                GameData.getDungeonPassConfigDataMap(),
                55,
                JsonUtils.decode(
                        "{id:55,logicType:'LOGIC_AND',conds:[{}]}", DungeonPassConfigData.class));
        put(
                GameData.getGadgetDataMap(),
                70360010,
                JsonUtils.decode(
                        "{id:70360010,type:'Worktop',jsonName:'TestDomainStarter'}",
                        GadgetData.class));
        manager = new DungeonManager(scene, data);
        scene.setDungeonManager(manager);
        key = new SceneGadget();
        key.config_id = 1;
        key.gadget_id = 70360010;
        key.pos = new Position(0, 0, 0);
        group = new SceneGroup();
        group.id = 42;
        group.setLoaded(true);
        group.monsters = Map.of();
        group.gadgets = new HashMap<>(Map.of(1, key));
        var suite = new SceneSuite();
        suite.gadgets = List.of(1);
        group.suites = new ArrayList<>(List.of(suite));
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        var block = new SceneBlock();
        block.id = 1;
        block.setLoaded(true);
        block.groups = Map.of(42, group);
        meta = new SceneMeta();
        meta.blocks = Map.of(1, block);
        entity = gadget();
        entry = new DungeonChallengeEntry(scene, clock::get);
    }

    @AfterEach
    void close() {
        scene.getScriptManager().onDestroy();
        scene.getWorld().getScenes().remove(9, scene);
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        fixture.close();
    }

    private EntityGadget gadget() {
        var entity = new EntityGadget(scene, 70360010, new Position());
        entity.setGroupId(42);
        entity.setConfigId(1);
        entity.setState(ScriptGadgetState.GearStop);
        entity.setFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP, 100);
        entity.buildContent();
        scene.addEntity(entity);
        return entity;
    }

    private DungeonChallengeEntry.Spawn spawn() {
        return DungeonChallengeEntry.resolve(
                data, meta, new Position(0, 0, 10), new Position(0, 180, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {70360010, 73051002})
    void entry_uses_the_entrance_side_and_faces_the_selected_starter(int gadgetId) {
        key.gadget_id = gadgetId;
        var spawn = spawn();
        assertNotNull(spawn);
        assertEquals(0, spawn.position().getX());
        assertEquals(2.2, spawn.position().getZ(), 0.0001);
        assertEquals(180, spawn.rotation().getY());
        assertEquals(42, spawn.groupId());
        assertEquals(1, spawn.configId());
    }

    @Test
    void snapshots_do_not_alias_the_resource_or_plugin_teleport_positions() {
        var spawn = spawn();
        spawn.position().setZ(80);
        spawn.rotation().setY(40);
        key.pos.setZ(99);
        assertEquals(2.2, spawn.position().getZ(), 0.0001);
        assertEquals(180, spawn.rotation().getY());
    }

    @Test
    void an_entrance_already_close_to_the_key_is_not_overshot() {
        var spawn =
                DungeonChallengeEntry.resolve(data, meta, new Position(0, 0, 1), new Position());
        assertEquals(1, spawn.position().getZ());
    }

    @Test
    void coincident_horizontal_positions_keep_the_valid_birth_snapshot() {
        var spawn =
                DungeonChallengeEntry.resolve(
                        data, meta, new Position(0, 3, 0), new Position(0, 70, 0));
        assertEquals(3, spawn.position().getY());
        assertEquals(70, spawn.rotation().getY());
    }

    @ParameterizedTest
    @ValueSource(ints = {70350096, 70350035, 70340014})
    void air_walls_defense_targets_and_reward_statues_are_not_starter_keys(int id) {
        key.gadget_id = id;
        assertNull(spawn());
    }

    @Test
    void keys_outside_the_initial_suite_are_not_used() {
        group.getSuiteByIndex(1).gadgets = List.of();
        assertNull(spawn());
    }

    @Test
    void a_dynamic_or_random_group_does_not_publish_an_unselected_key() {
        group.dynamic_load = true;
        assertNull(spawn());
        group.dynamic_load = false;
        group.suites.add(new SceneSuite());
        group.init_config.rand_suite = true;
        assertNull(spawn());
        group.init_config.io_type = 1;
        assertNotNull(spawn());
    }

    @Test
    void a_single_initial_suite_is_deterministic_even_when_marked_random() {
        group.init_config.rand_suite = true;
        assertNotNull(spawn());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void non_finite_key_positions_are_ignored(float bad) {
        key.pos.setX(bad);
        assertNull(spawn());
    }

    @Test
    void a_story_dungeon_is_not_repositioned_even_with_a_matching_subtype() {
        var story =
                JsonUtils.decode(
                        "{id:55,sceneId:9,type:'DUNGEON_PLOT',subType:'DUNGEON_SUB_WEAPON'}",
                        DungeonData.class);
        assertNull(
                DungeonChallengeEntry.resolve(story, meta, new Position(0, 0, 10), new Position()));
    }

    @Test
    void the_selected_key_is_armed_without_repeated_notifications() {
        entry.install(spawn());
        assertEquals(0, entity.getState());
        assertEquals(Set.of(7), ((GadgetWorktop) entity.getContent()).getWorktopOptions());
        fixture.scene.packets.clear();
        for (int i = 0; i < 10; i++) entry.tick(entity);
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void selecting_start_removes_the_option_once_and_hides_after_one_second() {
        entry.install(spawn());
        assertTrue(entry.select(fixture.player, entity, 7));
        assertFalse(entry.select(fixture.player, entity, 7));
        assertFalse(((GadgetWorktop) entity.getContent()).getWorktopOptions().contains(7));
        clock.set(999_999_999L);
        entry.tick(entity);
        assertEquals(0, entity.getState());
        clock.set(1_000_000_000L);
        entry.tick(entity);
        assertEquals(202, entity.getState());
        fixture.scene.packets.clear();
        entry.tick(entity);
        entry.armExisting();
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void concurrent_clicks_admit_only_one_start() throws Exception {
        entry.install(spawn());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> entry.select(fixture.player, entity, 7));
            var two = pool.submit(() -> entry.select(fixture.player, entity, 7));
            assertEquals(
                    1,
                    (one.get(5, TimeUnit.SECONDS) ? 1 : 0)
                            + (two.get(5, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void stale_scenes_entities_managers_and_players_cannot_start(int invalid) {
        entry.install(spawn());
        switch (invalid) {
            case 0 -> entity.invalidateAbilityLifecycle();
            case 1 -> scene.getWorld().getScenes().remove(9);
            case 2 -> scene.setDungeonManager(new DungeonManager(scene, data));
            case 3 -> fixture.player.setWorld(null);
            case 4 -> scene.getScriptManager().onDestroy();
        }
        assertFalse(entry.select(fixture.player, entity, 7));
    }

    @Test
    void a_delayed_hide_cannot_mutate_a_replacement_with_the_same_entity_id() {
        entry.install(spawn());
        assertTrue(entry.select(fixture.player, entity, 7));
        var replacement = gadget();
        scene.getEntities().remove(replacement.getId());
        replacement.setId(entity.getId());
        scene.getEntities().put(replacement.getId(), replacement);
        replacement.setState(0);
        clock.set(2_000_000_000L);
        entry.tick(entity);
        entry.tick(replacement);
        assertEquals(0, entity.getState());
        assertEquals(0, replacement.getState());
    }

    @Test
    void completed_dungeons_do_not_rearm_their_key() {
        entry.install(spawn());
        manager.getFinishedConditions()[0] = 1;
        entity.setState(202);
        ((GadgetWorktop) entity.getContent()).removeWorktopOption(7);
        entry.tick(entity);
        assertEquals(202, entity.getState());
        assertFalse(entry.select(fixture.player, entity, 7));
    }

    @Test
    void repeated_network_requests_acknowledge_both_but_dispatch_start_once() throws Exception {
        scene.getDungeonChallengeEntry().install(spawn());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        ((GadgetWorktop) entity.getContent())
                .setOnSelectWorktopOptionEvent(
                        (worktop, option) -> {
                            calls.incrementAndGet();
                            return false;
                        });
        var session =
                new emu.grasscutter.server.game.GameSession(scene.getWorld().getServer()) {
                    @Override
                    public emu.grasscutter.game.player.Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public void send(emu.grasscutter.net.packet.BasePacket packet) {
                        fixture.player.packets.add(packet);
                    }
                };
        var request =
                emu.grasscutter.net.proto.SelectWorktopOptionReqOuterClass.SelectWorktopOptionReq
                        .newBuilder()
                        .setGadgetEntityId(entity.getId())
                        .setOptionId(7)
                        .build()
                        .toByteArray();
        var handler = new emu.grasscutter.server.packet.recv.HandlerSelectWorktopOptionReq();
        handler.handle(session, new byte[0], request);
        handler.handle(session, new byte[0], request);
        assertEquals(1, calls.get());
        assertEquals(
                2,
                fixture.player.packets.stream()
                        .filter(
                                packet ->
                                        packet.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .SelectWorktopOptionRsp)
                        .count());
    }

    @Test
    void unrelated_worktops_and_other_options_keep_their_existing_dispatch() {
        entry.install(spawn());
        var other = gadget();
        other.setConfigId(99);
        assertTrue(entry.select(fixture.player, other, 7));
        assertTrue(entry.select(fixture.player, entity, 12));
        assertTrue(entry.select(fixture.player, entity, 7));
    }

    @Test
    void a_script_option_reset_after_failure_can_start_a_new_attempt() {
        entry.install(spawn());
        assertTrue(entry.select(fixture.player, entity, 7));
        var challenge =
                new emu.grasscutter.game.dungeons.challenge.WorldChallenge(
                        scene, group, 1, 1, List.of(), 300, 1, List.of());
        scene.setChallenge(challenge);
        challenge.start();
        challenge.fail();
        entity.setState(0);
        clock.set(2_000_000_000L);
        entry.tick(entity);
        assertEquals(0, entity.getState(), "The old delay must not undo the failure script");
        ((GadgetWorktop) entity.getContent()).addWorktopOptions(new int[] {7});
        assertTrue(entry.select(fixture.player, entity, 7));
        assertFalse(entry.select(fixture.player, entity, 7));
        // Merely repeating a stale failed challenge cannot reopen the new attempt.
        ((GadgetWorktop) entity.getContent()).addWorktopOptions(new int[] {7});
        assertFalse(entry.select(fixture.player, entity, 7));
        challenge.start();
        challenge.fail();
        entity.setState(0);
        assertTrue(entry.select(fixture.player, entity, 7));
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

class SceneBuildingTest {
    NatsaurusPossessionTest environment;
    Object manager;
    final Map<String, Object> saved = new ConcurrentHashMap<>();
    boolean failSave, failDelete, ambiguousSave;
    Runnable afterSave = () -> {};
    static final int PROP = 70300001;

    Object makeManager(Scene scene) {
        try {
            var store = Class.forName("emu.grasscutter.game.world.SceneBuildingManager$Store");
            var proxy =
                    Proxy.newProxyInstance(
                            store.getClassLoader(),
                            new Class<?>[] {store},
                            (p, m, args) ->
                                    switch (m.getName()) {
                                        case "load" -> new ArrayList<>(saved.values());
                                        case "save" -> {
                                            if (failSave)
                                                throw new IllegalStateException(
                                                        "test save failure");
                                            var row = args[0];
                                            saved.put(
                                                    (String)
                                                            row.getClass()
                                                                    .getMethod("getId")
                                                                    .invoke(row),
                                                    row);
                                            afterSave.run();
                                            if (ambiguousSave)
                                                throw new IllegalStateException(
                                                        "acknowledgement lost");
                                            yield null;
                                        }
                                        case "delete" -> {
                                            if (failDelete)
                                                throw new IllegalStateException(
                                                        "test delete failure");
                                            saved.remove((String) args[1]);
                                            yield true;
                                        }
                                        default -> throw new UnsupportedOperationException(
                                                m.getName());
                                    });
            return Class.forName("emu.grasscutter.game.world.SceneBuildingManager")
                    .getConstructor(Scene.class, store)
                    .newInstance(scene, proxy);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        if (emu.grasscutter.scripts.ScriptLoader.getEngine() == null)
            emu.grasscutter.scripts.ScriptLoader.init();
        environment.put(
                GameData.getGadgetDataMap(),
                PROP,
                JsonUtils.decode(
                        "{id:" + PROP + ",jsonName:'BuildingTestProp',type:'Gadget'}",
                        GadgetData.class));
        environment.put(
                GameData.getGadgetConfigData(),
                "BuildingTestProp",
                JsonUtils.decode(
                        "{abilities:[],combat:{property:{HP:10}}}", ConfigEntityGadget.class));
        manager = makeManager(environment.fixture.scene);
    }

    @AfterEach
    void close() {
        environment.close();
    }

    Object call(Object target, String method, Class<?>[] types, Object... args) {
        try {
            return target.getClass().getMethod(method, types).invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    Object create(boolean persistent, Position pos, Position rot) {
        return call(
                manager,
                "create",
                new Class<?>[] {
                    Player.class, int.class, Position.class, Position.class, boolean.class
                },
                environment.fixture.player,
                PROP,
                pos,
                rot,
                persistent);
    }

    String id(Object row) {
        return (String) call(row, "getId", new Class<?>[] {});
    }

    List<?> list() {
        return (List<?>)
                call(manager, "list", new Class<?>[] {Player.class}, environment.fixture.player);
    }

    boolean remove(String id) {
        return (boolean)
                call(
                        manager,
                        "remove",
                        new Class<?>[] {Player.class, String.class},
                        environment.fixture.player,
                        id);
    }

    void restore(Object target) {
        call(target, "restore", new Class<?>[] {Player.class}, environment.fixture.player);
    }

    List<GameEntity> props(Scene scene) {
        return scene.getEntities().values().stream()
                .filter(e -> e.getEntityTypeId() == PROP)
                .toList();
    }

    @Test
    void persistentCreationStoresAndSpawnsOneProp() {
        var row = create(true, new Position(1, 2, 3), new Position(0, 45, 0));
        assertEquals(1, saved.size());
        assertEquals(1, props(environment.fixture.scene).size());
        assertEquals(id(row), id(list().get(0)));
    }

    @Test
    void transientCreationStillSpawnsWithoutWritingTheStore() {
        create(false, new Position(), new Position());
        assertTrue(saved.isEmpty());
        assertEquals(1, props(environment.fixture.scene).size());
        assertEquals(1, list().size());
    }

    @Test
    void eachCreationHasAUniqueIdentifierAndNeverOverwrites() {
        var a = create(true, new Position(), new Position());
        var b = create(true, new Position(), new Position());
        assertNotEquals(id(a), id(b));
        assertEquals(2, saved.size());
    }

    @Test
    void positionsAreCopiedAtCreationAndWhenRead() {
        var p = new Position(1, 2, 3);
        var r = new Position(0, 45, 0);
        var row = create(true, p, r);
        p.setX(90);
        r.setY(90);
        var copy = (Position) call(row, "getPosition", new Class<?>[] {});
        assertEquals(1, copy.getX());
        copy.setX(80);
        assertEquals(1, ((Position) call(row, "getPosition", new Class<?>[] {})).getX());
        assertEquals(45, ((Position) call(row, "getRotation", new Class<?>[] {})).getY());
    }

    @Test
    void removalImmediatelyRemovesOnlyItsLiveEntityAndStoredRecord() {
        var a = create(true, new Position(), new Position());
        var b = create(true, new Position(), new Position());
        assertTrue(remove(id(a)));
        assertEquals(1, saved.size());
        assertEquals(1, props(environment.fixture.scene).size());
        assertEquals(id(b), id(list().get(0)));
    }

    @Test
    void aNewIdentifierAfterDeletionCannotCollideWithAnOldOne() {
        var a = create(true, new Position(), new Position());
        remove(id(a));
        var b = create(true, new Position(), new Position());
        assertNotEquals(id(a), id(b));
    }

    @Test
    void repeatedRestoreDoesNotDuplicateLiveProps() {
        create(true, new Position(), new Position());
        restore(manager);
        restore(manager);
        assertEquals(1, props(environment.fixture.scene).size());
    }

    @Test
    void aFreshSceneRestoresPersistedPropsWithTheirPositions() {
        create(true, new Position(1, 2, 3), new Position(0, 45, 0));
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) environment.fixture.scene.getWorld(),
                        environment.fixture.scene.getSceneData());
        environment.fixture.player.setScene(scene);
        var other = makeManager(scene);
        restore(other);
        assertEquals(1, props(scene).size());
        assertEquals(1, props(scene).get(0).getPosition().getX());
    }

    @Test
    void transientPropsAreNotRestoredInAFreshScene() {
        create(false, new Position(), new Position());
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) environment.fixture.scene.getWorld(),
                        environment.fixture.scene.getSceneData());
        environment.fixture.player.setScene(scene);
        restore(makeManager(scene));
        assertTrue(props(scene).isEmpty());
    }

    @Test
    void saveFailureDoesNotSpawnAnUnpersistedObject() {
        failSave = true;
        assertThrows(
                IllegalStateException.class, () -> create(true, new Position(), new Position()));
        assertTrue(props(environment.fixture.scene).isEmpty());
        assertTrue(list().isEmpty());
    }

    @Test
    void deleteFailureKeepsTheLiveObjectAndItsRecord() {
        var row = create(true, new Position(), new Position());
        failDelete = true;
        assertThrows(IllegalStateException.class, () -> remove(id(row)));
        assertEquals(1, list().size());
        assertEquals(1, props(environment.fixture.scene).size());
        assertEquals(1, saved.size());
    }

    @Test
    void invalidCoordinatesCannotBeSavedOrSpawned() {
        assertThrows(
                IllegalStateException.class,
                () -> create(true, new Position(Float.NaN, 0, 0), new Position()));
        assertTrue(saved.isEmpty());
        assertTrue(props(environment.fixture.scene).isEmpty());
    }

    @Test
    void unavailableGadgetCannotBeSavedOrSpawned() {
        environment.put(GameData.getGadgetDataMap(), PROP, null);
        assertThrows(
                IllegalStateException.class, () -> create(true, new Position(), new Position()));
        assertTrue(saved.isEmpty());
        assertTrue(props(environment.fixture.scene).isEmpty());
    }

    @Test
    void anotherPlayerCannotModifyTheHostsBuildingCatalog() {
        var guest = new AbilityTestFixture.TestPlayer();
        guest.setUid(20001);
        guest.setWorld(environment.fixture.player.getWorld());
        guest.setScene(environment.fixture.scene);
        assertThrows(
                IllegalStateException.class,
                () ->
                        call(
                                manager,
                                "create",
                                new Class<?>[] {
                                    Player.class,
                                    int.class,
                                    Position.class,
                                    Position.class,
                                    boolean.class
                                },
                                guest,
                                PROP,
                                new Position(),
                                new Position(),
                                true));
        assertTrue(saved.isEmpty());
    }

    @Test
    void aRemovedManagedEntityDoesNotCauseRemovalOfAnUnrelatedReplacement() {
        var row = create(true, new Position(), new Position());
        var original = props(environment.fixture.scene).get(0);
        environment.fixture.scene.removeEntity(original);
        var unrelated =
                new emu.grasscutter.game.entity.EntityGadget(
                        environment.fixture.scene, PROP, new Position(), new Position());
        unrelated.setId(original.getId());
        environment.fixture.scene.getEntities().put(unrelated.getId(), unrelated);
        assertTrue(remove(id(row)));
        assertSame(unrelated, environment.fixture.scene.getEntities().get(unrelated.getId()));
    }

    @Test
    void concurrentCreationKeepsAllUniqueRecords() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<String>>();
            for (int i = 0; i < 8; i++)
                jobs.add(pool.submit(() -> id(create(true, new Position(), new Position()))));
            var ids = new HashSet<String>();
            for (var job : jobs) ids.add(job.get(10, TimeUnit.SECONDS));
            assertEquals(8, ids.size());
            assertEquals(8, saved.size());
            assertEquals(8, props(environment.fixture.scene).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFullSceneDoesNotLoseTheCatalogOrPreventLaterRestoration() throws Exception {
        var row = create(true, new Position(), new Position());
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) environment.fixture.scene.getWorld(),
                        environment.fixture.scene.getSceneData());
        environment.fixture.player.setScene(scene);
        var other = makeManager(scene);
        int previous = emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit;
        try {
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit = 0;
            restore(other);
            assertTrue(props(scene).isEmpty());
            assertEquals(
                    1,
                    ((List<?>)
                                    call(
                                            other,
                                            "list",
                                            new Class<?>[] {Player.class},
                                            environment.fixture.player))
                            .size());
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit = previous;
            restore(other);
            assertEquals(1, props(scene).size());
        } finally {
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit = previous;
        }
    }

    @Test
    void unloadedCatalogEntryCanBeRemovedWhileTheSceneIsFull() {
        var row = create(true, new Position(), new Position());
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) environment.fixture.scene.getWorld(),
                        environment.fixture.scene.getSceneData());
        environment.fixture.player.setScene(scene);
        var other = makeManager(scene);
        int previous = emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit;
        try {
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit = 0;
            restore(other);
            assertTrue(
                    (boolean)
                            call(
                                    other,
                                    "remove",
                                    new Class<?>[] {Player.class, String.class},
                                    environment.fixture.player,
                                    id(row)));
            assertTrue(saved.isEmpty());
        } finally {
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit = previous;
        }
    }

    void installManager() throws Exception {
        var field = Scene.class.getDeclaredField("buildingManager");
        field.setAccessible(true);
        field.set(environment.fixture.scene, manager);
    }

    void command(String... args) throws Exception {
        var instance =
                (emu.grasscutter.command.CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.BuildCommand")
                                .getConstructor()
                                .newInstance();
        instance.execute(null, environment.fixture.player, List.of(args));
    }

    @Test
    void commandTemporaryModeCreatesARealUnsavedProp() throws Exception {
        installManager();
        command(String.valueOf(PROP), "temporary");
        assertEquals(1, props(environment.fixture.scene).size());
        assertTrue(saved.isEmpty());
    }

    @Test
    void commandParsesExactPositionAndRotationWithoutRandomJitter() throws Exception {
        installManager();
        command(String.valueOf(PROP), "1", "2", "3", "0", "45", "0");
        var entity = props(environment.fixture.scene).get(0);
        assertEquals(1, entity.getPosition().getX());
        assertEquals(2, entity.getPosition().getY());
        assertEquals(3, entity.getPosition().getZ());
        assertEquals(45, entity.getRotation().getY());
    }

    @Test
    void commandRejectsInvalidCoordinateInsteadOfFallingThroughToSpawn() throws Exception {
        installManager();
        command(String.valueOf(PROP), "bad", "2", "3");
        assertTrue(saved.isEmpty());
        assertTrue(props(environment.fixture.scene).isEmpty());
    }

    @Test
    void commandRemovalWorksImmediatelyWithoutClosingThePlayerSession() throws Exception {
        installManager();
        var row = create(true, new Position(), new Position());
        var session = environment.fixture.player.getSession();
        command("remove", id(row));
        assertTrue(props(environment.fixture.scene).isEmpty());
        assertSame(session, environment.fixture.player.getSession());
    }

    @Test
    void anAcknowledgementLostAfterSaveCanBeRecoveredWithoutAnotherPlacement() {
        ambiguousSave = true;
        assertThrows(
                IllegalStateException.class, () -> create(true, new Position(), new Position()));
        assertEquals(1, saved.size());
        assertTrue(props(environment.fixture.scene).isEmpty());
        ambiguousSave = false;
        restore(manager);
        assertEquals(1, list().size());
        assertEquals(1, props(environment.fixture.scene).size());
    }

    @Test
    void changingWorldHostCannotReuseThePreviousHostsCatalog() throws Exception {
        create(true, new Position(), new Position());
        var guest = new AbilityTestFixture.TestPlayer();
        guest.setUid(20001);
        guest.setScene(environment.fixture.scene);
        guest.setWorld(environment.fixture.player.getWorld());
        var field = World.class.getDeclaredField("host");
        field.setAccessible(true);
        field.set(environment.fixture.player.getWorld(), guest);
        assertThrows(
                IllegalStateException.class,
                () -> call(manager, "list", new Class<?>[] {Player.class}, guest));
    }

    @Test
    void sceneFillingDuringSaveDefersTheStoredPlacementUntilRoomIsAvailable() {
        var scene = environment.fixture.scene;
        int previous = emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit;
        try {
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit =
                    scene.getEntities().size() + 1;
            afterSave =
                    () ->
                            scene.addEntity(
                                    new emu.grasscutter.game.entity.EntityGadget(
                                            scene, PROP, new Position(9, 9, 9), new Position()));
            var row = create(true, new Position(1, 2, 3), new Position());
            assertEquals(1, saved.size());
            assertEquals(1, props(scene).size());
            scene.removeEntity(props(scene).get(0));
            restore(manager);
            assertEquals(1, props(scene).size());
            assertEquals(1, props(scene).get(0).getPosition().getX());
            assertEquals(id(row), id(list().get(0)));
        } finally {
            emu.grasscutter.config.Configuration.GAME_OPTIONS.sceneEntityLimit = previous;
        }
    }
}

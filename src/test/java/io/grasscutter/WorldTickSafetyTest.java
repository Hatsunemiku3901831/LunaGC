package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.game.GameServer;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class WorldTickSafetyTest {
    private static void prepareTickServer(GameServer server) throws Exception {
        Field players = GameServer.class.getDeclaredField("players"); players.setAccessible(true);
        players.set(server, new ConcurrentHashMap<Integer, Player>());
        Field scheduler = GameServer.class.getDeclaredField("scheduler"); scheduler.setAccessible(true);
        scheduler.set(server, new emu.grasscutter.server.scheduler.ServerTaskScheduler());
        Field matching = GameServer.class.getDeclaredField("crucibleMatchSystem"); matching.setAccessible(true);
        matching.set(server, new emu.grasscutter.game.activity.crucible.CrucibleMatchSystem(server));
    }
    @Test void sceneCallbacksRunWithoutTheSceneMapMonitor() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var world = fixture.scene.getWorld();
            fixture.player.setWorld(world);
            Field savedAt = World.class.getDeclaredField("lastTimeStore"); savedAt.setAccessible(true);
            savedAt.setLong(world, System.currentTimeMillis());
            var called = new AtomicBoolean();
            var scene = new AbilityTestFixture.TestScene((AbilityTestFixture.TestWorld) world,
                    fixture.scene.getSceneData()) {
                @Override public void onTick() {
                    assertFalse(Thread.holdsLock(world.getScenes()), "Callbacks must not hold collection locks");
                    called.set(true);
                }
            };
            world.getPlayers().add(fixture.player);
            scene.getPlayers().add(fixture.player);
            world.registerScene(scene);
            assertFalse(world.onTick());
            assertTrue(called.get());
        }
    }

    @Test void delayedSceneRemovalCannotRemoveANewerInstanceWithTheSameId() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var world = fixture.scene.getWorld();
            var oldScene = new AbilityTestFixture.TestScene((AbilityTestFixture.TestWorld) world,
                    fixture.scene.getSceneData()) { @Override public void saveGroups() {} };
            var newScene = new AbilityTestFixture.TestScene((AbilityTestFixture.TestWorld) world,
                    fixture.scene.getSceneData());
            world.registerScene(oldScene);
            world.registerScene(newScene);
            world.deregisterScene(oldScene);
            assertSame(newScene, world.getScenes().get(newScene.getId()));
        }
    }

    @Test void worldCallbacksDoNotHoldTheServerWorldCollectionMonitor() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            prepareTickServer(server);
            var worlds = Collections.synchronizedSet(new HashSet<World>());
            Field field = GameServer.class.getDeclaredField("worlds"); field.setAccessible(true); field.set(server, worlds);
            var called = new AtomicBoolean();
            var world = new AbilityTestFixture.TestWorld(server, fixture.player) {
                @Override public boolean onTick() {
                    assertFalse(Thread.holdsLock(worlds), "World callbacks must run outside the collection monitor");
                    called.set(true);
                    return false;
                }
            };
            server.registerWorld(world);
            server.onTick();
            assertTrue(called.get());
            assertTrue(worlds.contains(world));
        }
    }

    @Test void anEmptyWorldResultCannotRemoveAPlayerWhoJoinedDuringTheTick() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            prepareTickServer(server);
            server.getWorlds().clear();
            var world = new AbilityTestFixture.TestWorld(server, fixture.player) {
                @Override public boolean onTick() {
                    getPlayers().add(fixture.player);
                    return true;
                }
            };
            server.registerWorld(world);
            server.onTick();
            assertTrue(server.getWorlds().contains(world), "Recheck membership atomically before removal");
        }
    }

    @Test void worktopPacketsUseAnIteratorSnapshotDuringConcurrentOptionChanges() {
        var worktop = new GadgetWorktop(null);
        worktop.addWorktopOptions(new int[]{1, 2, 3});
        var iterator = worktop.getWorktopOptions().iterator();
        worktop.removeWorktopOption(2);
        worktop.addWorktopOptions(new int[]{4, 5});
        var options = new HashSet<Integer>();
        assertDoesNotThrow(() -> iterator.forEachRemaining(options::add));
        assertEquals(Set.of(1, 2, 3), options);
        var info = SceneGadgetInfo.newBuilder();
        worktop.onBuildProto(info);
        assertEquals(Set.of(1, 3, 4, 5), new HashSet<>(info.getWorktop().getOptionListList()));
    }

    @Test void healthQueriesStayResponsiveWhileTheFirstTickIsBlocked() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            prepareTickServer(server);
            server.getWorlds().clear();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var world = new AbilityTestFixture.TestWorld(server, fixture.player) {
                @Override public boolean onTick() {
                    entered.countDown();
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException exception) { throw new AssertionError(exception); }
                    return false;
                }
            };
            server.registerWorld(world);
            var runner = Executors.newFixedThreadPool(2);
            try {
                var ticking = runner.submit(server::onTick);
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                Field start = GameServer.class.getDeclaredField("firstTickStartedNanos"); start.setAccessible(true);
                start.setLong(server, System.nanoTime() - TimeUnit.SECONDS.toNanos(30));
                var status = runner.submit(server::tickStatus).get(1, TimeUnit.SECONDS);
                assertTrue(status.stalled());
                assertEquals(0, status.completed());
                release.countDown(); ticking.get(2, TimeUnit.SECONDS);
                assertFalse(server.tickStatus().stalled());
                assertEquals(1, server.tickStatus().completed());
            } finally { release.countDown(); runner.shutdownNow(); }
        }
    }

    @Test void emptyHomeWorldsAreRemovedWithoutDiscardingAReplacementForTheSameOwner() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            prepareTickServer(server);
            Field homes = GameServer.class.getDeclaredField("homeWorlds"); homes.setAccessible(true);
            homes.set(server, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<emu.grasscutter.game.home.HomeWorld>());
            var owner = new AbilityTestFixture.TestPlayer() {
                @Override public boolean isOnline() { return true; }
                @Override public int getUid() { return 77; }
            };
            var old = new emu.grasscutter.game.home.HomeWorld(server, owner) {
                @Override public void refreshModuleManager() {}
            };
            var replacement = new emu.grasscutter.game.home.HomeWorld(server, owner) {
                @Override public void refreshModuleManager() {}
            };
            replacement.getPlayers().add(owner);
            server.registerWorld(old);
            server.registerWorld(replacement);
            server.getHomeWorlds().put(owner.getUid(), replacement);
            server.onTick();
            assertFalse(server.getWorlds().contains(old));
            assertTrue(server.getWorlds().contains(replacement));
            assertSame(replacement, server.getHomeWorlds().get(owner.getUid()));
        }
    }
}

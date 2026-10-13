package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.*;

import org.junit.jupiter.api.*;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class SceneTransferConcurrencyTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestWorld world;
    private final List<Thread> workers = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        world = (AbilityTestFixture.TestWorld) fixture.scene.getWorld();
        fixture.player.setUid(16001);
        fixture.player.setWorld(world);
    }

    @AfterEach
    void cleanup() {
        // Baseline regressions can deliberately deadlock two daemon workers in an isolated JVM.
        fixture.close();
    }

    private SceneData data(int id) {
        return new Gson().fromJson("{\"id\":" + id + ",\"type\":\"SCENE_WORLD\"}", SceneData.class);
    }

    private AbilityTestFixture.TestScene scene(int id) {
        var scene =
                new AbilityTestFixture.TestScene(world, data(id)) {
                    @Override
                    public void saveGroups() {}
                };
        scene.setDontDestroyWhenEmpty(true);
        world.registerScene(scene);
        return scene;
    }

    private CompletableFuture<Void> worker(Runnable action) {
        var future = new CompletableFuture<Void>();
        var thread =
                new Thread(
                        () -> {
                            try {
                                action.run();
                                future.complete(null);
                            } catch (Throwable failure) {
                                future.completeExceptionally(failure);
                            }
                        },
                        "scene-transfer-test-" + workers.size());
        thread.setDaemon(true);
        workers.add(thread);
        thread.start();
        return future;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS))
                throw new IllegalStateException("Test barrier timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void oldSceneRemovalRunsWithoutTheDestinationMonitor() {
        var destination = scene(4);
        var previous =
                new AbilityTestFixture.TestScene(world, data(3)) {
                    @Override
                    public void removePlayer(Player player) {
                        assertFalse(
                                Thread.holdsLock(destination),
                                "The destination cannot be locked while leaving the source");
                        super.removePlayer(player);
                    }

                    @Override
                    public void saveGroups() {}
                };
        previous.setDontDestroyWhenEmpty(true);
        previous.addPlayer(fixture.player);
        destination.addPlayer(fixture.player);
        assertSame(destination, fixture.player.getScene());
        assertTrue(previous.getPlayers().isEmpty());
        assertEquals(List.of(fixture.player), destination.getPlayers());
    }

    @Test
    void twoPlayersCanSwapScenesAtTheSameTime() throws Exception {
        var leaving = new CountDownLatch(2);
        class SwapScene extends AbilityTestFixture.TestScene {
            SwapScene(int id) {
                super(
                        SceneTransferConcurrencyTest.this.world,
                        SceneTransferConcurrencyTest.this.data(id));
                setDontDestroyWhenEmpty(true);
            }

            @Override
            public void removePlayer(Player player) {
                leaving.countDown();
                await(leaving);
                super.removePlayer(player);
            }

            @Override
            public void saveGroups() {}
        }
        var left = new SwapScene(3);
        var right = new SwapScene(4);
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(16002);
        other.setWorld(world);
        fixture.player.setScene(null);
        left.addPlayer(fixture.player);
        right.addPlayer(other);
        var first = worker(() -> right.addPlayer(fixture.player));
        var second = worker(() -> left.addPlayer(other));
        try {
            CompletableFuture.allOf(first, second).get(4, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            var bean = ManagementFactory.getThreadMXBean();
            var ids = bean.findMonitorDeadlockedThreads();
            if (ids != null)
                for (var info : bean.getThreadInfo(ids, true, true)) System.err.println(info);
            fail("Two scene swaps did not finish; monitor diagnostics recorded", timeout);
        }
        assertSame(right, fixture.player.getScene());
        assertSame(left, other.getScene());
        assertEquals(List.of(other), left.getPlayers());
        assertEquals(List.of(fixture.player), right.getPlayers());
    }

    @Test
    void concurrentTransfersOfOnePlayerHaveOneFinalSceneMembership() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var startedSecond = new CountDownLatch(1);
        var removals = new AtomicInteger();
        var previous =
                new AbilityTestFixture.TestScene(world, data(3)) {
                    @Override
                    public void removePlayer(Player player) {
                        if (removals.incrementAndGet() == 1) {
                            entered.countDown();
                            await(release);
                        }
                        super.removePlayer(player);
                    }

                    @Override
                    public void saveGroups() {}
                };
        previous.setDontDestroyWhenEmpty(true);
        previous.addPlayer(fixture.player);
        var firstScene = scene(4);
        var secondScene = scene(5);
        var first = worker(() -> firstScene.addPlayer(fixture.player));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var second =
                worker(
                        () -> {
                            startedSecond.countDown();
                            secondScene.addPlayer(fixture.player);
                        });
        try {
            assertTrue(startedSecond.await(2, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse(
                    second.isDone(),
                    "A second transfer must wait for the first transfer of this player");
            assertTrue(secondScene.getPlayers().isEmpty());
        } finally {
            release.countDown();
            CompletableFuture.allOf(first, second).get(4, TimeUnit.SECONDS);
        }
        assertSame(secondScene, fixture.player.getScene());
        assertTrue(previous.getPlayers().isEmpty());
        assertTrue(firstScene.getPlayers().isEmpty());
        assertEquals(List.of(fixture.player), secondScene.getPlayers());
    }

    @Test
    void repeatedAndStaleSceneCallsDoNotClearTheCurrentMembership() {
        var previous = scene(3);
        var destination = scene(4);
        previous.addPlayer(fixture.player);
        destination.addPlayer(fixture.player);
        destination.addPlayer(fixture.player);
        previous.removePlayer(fixture.player);
        assertSame(destination, fixture.player.getScene());
        assertEquals(List.of(fixture.player), destination.getPlayers());
        assertTrue(previous.getPlayers().isEmpty());
    }

    @Test
    void entityAllocationCanProceedWhileAnotherThreadHoldsTheWorldMonitor() throws Exception {
        var result = new CompletableFuture<Integer>();
        synchronized (world) {
            worker(() -> result.complete(world.getNextEntityId(EntityIdType.WEAPON)));
            assertEquals(
                    EntityIdType.WEAPON.getId(),
                    result.get(500, TimeUnit.MILLISECONDS) >>> GameConstants.ENTITY_ID_BIT_SHIFT);
        }
    }

    @Test
    void worldEntryAndSceneEntityAllocationCanFinishConcurrently() throws Exception {
        var inScene = new CountDownLatch(1);
        var entering = new CountDownLatch(1);
        var destination =
                new AbilityTestFixture.TestScene(world, data(3)) {
                    @Override
                    public void addPlayer(Player player) {
                        entering.countDown();
                        await(inScene);
                        super.addPlayer(player);
                    }

                    @Override
                    public void saveGroups() {}
                };
        destination.setDontDestroyWhenEmpty(true);
        world.registerScene(destination);
        fixture.player.setWorld(null);
        fixture.player.setScene(null);
        var first =
                worker(
                        () -> {
                            synchronized (destination) {
                                inScene.countDown();
                                await(entering);
                                world.getNextEntityId(EntityIdType.AVATAR);
                            }
                        });
        var second = worker(() -> world.addPlayer(fixture.player));
        try {
            CompletableFuture.allOf(first, second).get(4, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            var bean = ManagementFactory.getThreadMXBean();
            var ids = bean.findMonitorDeadlockedThreads();
            if (ids != null)
                for (var info : bean.getThreadInfo(ids, true, true)) System.err.println(info);
            fail(
                    "World entry and scene entity allocation did not finish; monitor diagnostics"
                        + " recorded",
                    timeout);
        }
        assertSame(world, fixture.player.getWorld());
        assertSame(destination, fixture.player.getScene());
        assertEquals(List.of(fixture.player), world.getPlayers());
        assertEquals(List.of(fixture.player), destination.getPlayers());
    }

    @Test
    void concurrentAllocationsKeepUniqueSerialsAcrossAllEntityTypes() throws Exception {
        var types = EntityIdType.values();
        int allocationsPerType = 200;
        int expectedAllocations = types.length * allocationsPerType;
        var ready = new CountDownLatch(types.length);
        var start = new CountDownLatch(1);
        var serials = ConcurrentHashMap.<Integer>newKeySet();
        int mask = (1 << GameConstants.ENTITY_ID_BIT_SHIFT) - 1;
        int before = world.getNextEntityId(EntityIdType.GADGET) & mask;
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var type : types)
            futures.add(
                    worker(
                            () -> {
                                ready.countDown();
                                await(start);
                                for (int i = 0; i < allocationsPerType; i++) {
                                    int id = world.getNextEntityId(type);
                                    assertEquals(
                                            type.getId(), id >>> GameConstants.ENTITY_ID_BIT_SHIFT);
                                    assertTrue(
                                            serials.add(id & mask),
                                            "Serial numbers must be shared across entity types");
                                }
                            }));
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(4, TimeUnit.SECONDS);
        assertEquals(expectedAllocations, serials.size());
        assertEquals(before + 1, Collections.min(serials));
        assertEquals(before + expectedAllocations, Collections.max(serials));
    }
}

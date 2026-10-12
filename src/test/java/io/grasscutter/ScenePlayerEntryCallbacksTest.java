package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.world.Scene;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Arrival subscriptions use the real scene transfer API and live player identity. */
class ScenePlayerEntryCallbacksTest extends QuestTestFixture {
    private Scene target, other;

    @BeforeEach
    void away() {
        target = player.getScene();
        target.setDontDestroyWhenEmpty(true);
        boolean enabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        try {
            other =
                    new Scene(
                            player.getWorld(),
                            gson.fromJson("{id:9,type:'SCENE_WORLD'}", SceneData.class));
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = enabled;
        }
        other.setDontDestroyWhenEmpty(true);
        player.setScene(other);
        player.setSceneId(9);
        other.getPlayers().add(player);
    }

    @AfterEach
    void closeScenes() {
        target.getScriptManager().onDestroy();
        other.getScriptManager().onDestroy();
    }

    @Test
    void savedSceneIdDoesNotReleaseACallbackBeforeTheActualTransfer() {
        var calls = new AtomicInteger();
        player.setSceneId(3);
        target.runWhenPlayerEnters(player, calls::incrementAndGet);
        assertEquals(0, calls.get());
        target.addPlayer(player);
        assertEquals(1, calls.get());
        target.addPlayer(player);
        assertEquals(1, calls.get());
    }

    @Test
    void alreadyPresentRegistrationRunsImmediatelyAndOnlyOnce() {
        var calls = new AtomicInteger();
        target.addPlayer(player);
        target.runWhenPlayerEnters(player, calls::incrementAndGet);
        assertEquals(1, calls.get());
        other.addPlayer(player);
        target.addPlayer(player);
        assertEquals(1, calls.get());
    }

    @Test
    void aDifferentSessionWithTheSameUidDoesNotReleaseTheOriginalPlayersCallbacks() {
        var calls = new AtomicInteger();
        target.runWhenPlayerEnters(player, calls::incrementAndGet);
        var otherSession = new TestPlayer();
        otherSession.setUid(player.getUid());
        otherSession.server = player.server;
        otherSession.setWorld(player.getWorld());
        target.addPlayer(otherSession);
        assertEquals(0, calls.get());
        target.removePlayer(otherSession);
        target.addPlayer(player);
        assertEquals(1, calls.get());
    }

    @Test
    void failingSubscriberDoesNotInterruptTheTransferOrOtherSubscribers() {
        var calls = new AtomicInteger();
        target.runWhenPlayerEnters(
                player,
                () -> {
                    throw new IllegalStateException("Controlled arrival failure");
                });
        target.runWhenPlayerEnters(player, calls::incrementAndGet);
        assertDoesNotThrow(() -> target.addPlayer(player));
        assertSame(target, player.getScene());
        assertEquals(1, calls.get());
    }

    @Test
    void callbacksRunOutsideTheSceneMonitorAndAllowNestedRegistration() {
        var calls = new ArrayList<Integer>();
        target.runWhenPlayerEnters(
                player,
                () -> {
                    assertFalse(Thread.holdsLock(target));
                    calls.add(1);
                    target.runWhenPlayerEnters(player, () -> calls.add(2));
                });
        target.runWhenPlayerEnters(player, () -> calls.add(3));
        target.addPlayer(player);
        assertEquals(List.of(1, 2, 3), calls);
    }

    @Test
    void nullAndWrongWorldSubscriptionsDoNotInterruptArrival() {
        var calls = new AtomicInteger();
        target.runWhenPlayerEnters(null, calls::incrementAndGet);
        target.runWhenPlayerEnters(player, null);
        var unrelated = new TestPlayer();
        target.runWhenPlayerEnters(unrelated, calls::incrementAndGet);
        target.addPlayer(player);
        assertEquals(0, calls.get());
    }

    @Test
    void aDestroyedSceneCannotRunLateSubscribers() {
        target.addPlayer(player);
        target.getScriptManager().onDestroy();
        var calls = new AtomicInteger();
        target.runWhenPlayerEnters(player, calls::incrementAndGet);
        assertEquals(0, calls.get());
    }

    @Test
    void concurrentRegistrationAndArrivalDeliverEverySubscriberOnce() throws Exception {
        var calls = new AtomicInteger();
        var start = new CountDownLatch(1);
        var threads = Executors.newFixedThreadPool(8);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int n = 0; n < 24; n++)
                futures.add(
                        threads.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    target.runWhenPlayerEnters(player, calls::incrementAndGet);
                                    return null;
                                }));
            futures.add(
                    threads.submit(
                            () -> {
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                target.addPlayer(player);
                                return null;
                            }));
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(24, calls.get());
            target.addPlayer(player);
            assertEquals(24, calls.get());
        } finally {
            start.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.world.Scene;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Scene API completion must deliver every valid one-shot callback, including during a race. */
class SceneLoadCallbacksTest extends QuestTestFixture {
    @Test
    void registeredCallbacksRunExactlyOnceAcrossRepeatedCompletion() {
        var calls = new AtomicInteger();
        player.getScene().runWhenFinished(calls::incrementAndGet);
        assertEquals(0, calls.get());
        player.getScene().finishLoading();
        player.getScene().finishLoading();
        assertEquals(1, calls.get());
    }

    @Test
    void lateRegistrationRunsImmediately() {
        var calls = new AtomicInteger();
        player.getScene().finishLoading();
        player.getScene().runWhenFinished(calls::incrementAndGet);
        assertEquals(1, calls.get());
    }

    @Test
    void nestedRegistrationDoesNotLoseOrReplayCallbacks() {
        var calls = new ArrayList<Integer>();
        var scene = player.getScene();
        scene.runWhenFinished(
                () -> {
                    calls.add(1);
                    scene.runWhenFinished(() -> calls.add(2));
                });
        scene.runWhenFinished(() -> calls.add(3));
        scene.finishLoading();
        scene.finishLoading();
        assertEquals(List.of(1, 2, 3), calls);
    }

    @Test
    void throwingCallbackDoesNotStrandLaterCallbacks() {
        var scene = player.getScene();
        var calls = new AtomicInteger();
        scene.runWhenFinished(
                () -> {
                    throw new IllegalStateException("Controlled callback failure");
                });
        scene.runWhenFinished(calls::incrementAndGet);
        assertDoesNotThrow(scene::finishLoading);
        assertEquals(1, calls.get());
        scene.finishLoading();
        assertEquals(1, calls.get());
    }

    @Test
    void lateCallbackExceptionDoesNotEscapeTheSceneApi() {
        var scene = player.getScene();
        scene.finishLoading();
        assertDoesNotThrow(
                () ->
                        scene.runWhenFinished(
                                () -> {
                                    throw new IllegalStateException(
                                            "Controlled late callback failure");
                                }));
    }

    @Test
    void nullCallbacksDoNotInterruptCompletion() {
        var scene = player.getScene();
        var calls = new AtomicInteger();
        scene.runWhenFinished(null);
        scene.runWhenFinished(calls::incrementAndGet);
        assertDoesNotThrow(scene::finishLoading);
        assertEquals(1, calls.get());
    }

    @Test
    void completionBetweenTheStateReadAndRegistrationDoesNotLoseTheCallback() throws Exception {
        var interceptedOrRegistered = new CountDownLatch(1);
        var finishRead = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var options = Grasscutter.getConfig().server.game;
        boolean enabled = options.enableScriptInBigWorld;
        options.enableScriptInBigWorld = false;
        Scene scene;
        try {
            scene =
                    new Scene(
                            player.getWorld(),
                            gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class)) {
                        @Override
                        public boolean isFinishedLoading() {
                            boolean captured = super.isFinishedLoading();
                            if (Thread.currentThread()
                                    .getName()
                                    .equals("scene-callback-registration")) {
                                interceptedOrRegistered.countDown();
                                try {
                                    assertTrue(finishRead.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException error) {
                                    throw new AssertionError(error);
                                }
                            }
                            return captured;
                        }
                    };
        } finally {
            options.enableScriptInBigWorld = enabled;
        }
        var registration =
                new FutureTask<Void>(
                        () -> {
                            scene.runWhenFinished(calls::incrementAndGet);
                            interceptedOrRegistered.countDown();
                            return null;
                        });
        var thread = new Thread(registration, "scene-callback-registration");
        thread.start();
        try {
            assertTrue(interceptedOrRegistered.await(5, TimeUnit.SECONDS));
            scene.finishLoading();
        } finally {
            finishRead.countDown();
        }
        registration.get(5, TimeUnit.SECONDS);
        scene.finishLoading();
        assertEquals(1, calls.get());
        scene.getScriptManager().onDestroy();
    }

    @Test
    void concurrentRegistrationsAndCompletionDeliverEveryCallback() throws Exception {
        var start = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(4);
        var tasks = new ArrayList<Future<?>>();
        try {
            for (int index = 0; index < 24; index++)
                tasks.add(
                        executor.submit(
                                () -> {
                                    try {
                                        start.await();
                                    } catch (InterruptedException error) {
                                        throw new AssertionError(error);
                                    }
                                    player.getScene().runWhenFinished(calls::incrementAndGet);
                                }));
            start.countDown();
            player.getScene().finishLoading();
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            assertEquals(24, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }
}

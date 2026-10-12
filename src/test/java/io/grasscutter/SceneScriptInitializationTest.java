package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Real scene loader threads with a controlled metadata-cache barrier. */
class SceneScriptInitializationTest extends QuestTestFixture {
    private CountDownLatch entered, released;
    private final AtomicReference<Thread> loader = new AtomicReference<>();
    private Scene loadingScene;

    private SceneScriptManager loading(boolean fail) throws Exception {
        entered = new CountDownLatch(1);
        released = new CountDownLatch(1);
        var meta = new SceneMeta();
        meta.config = new SceneConfig();
        meta.blocks = new HashMap<>();
        var reference =
                new SoftReference<SceneMeta>(meta) {
                    @Override
                    public SceneMeta get() {
                        loader.set(Thread.currentThread());
                        entered.countDown();
                        try {
                            if (!released.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("Loader barrier timed out");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(error);
                        }
                        if (fail) throw new IllegalStateException("Controlled metadata failure");
                        return super.get();
                    }
                };
        replaceField(
                ScriptLoader.class,
                null,
                "sceneMetaCache",
                new ConcurrentHashMap<>(Map.of(3, reference)));
        loadingScene =
                new Scene(
                        player.getWorld(),
                        gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        return loadingScene.getScriptManager();
    }

    private void finishLoading() throws Exception {
        released.countDown();
        loader.get().join(5000);
        assertFalse(loader.get().isAlive(), "Loader must reach its terminal outcome");
    }

    private void subscribe(SceneScriptManager scripts, Consumer<Boolean> callback)
            throws Exception {
        SceneScriptManager.class
                .getMethod("whenInitialized", Consumer.class)
                .invoke(scripts, callback);
    }

    @AfterEach
    void releaseLoader() throws Exception {
        if (released != null) finishLoading();
        if (loadingScene != null) loadingScene.getScriptManager().onDestroy();
    }

    @Test
    void successfulLoaderPublishesPendingAndLateCallbacksExactlyOnce() throws Exception {
        var scripts = loading(false);
        var outcomes = new CopyOnWriteArrayList<Boolean>();
        subscribe(scripts, outcomes::add);
        assertTrue(outcomes.isEmpty());
        assertFalse(scripts.isInit());
        finishLoading();
        assertTrue(scripts.isInit());
        assertTrue(scripts.isInitAttempted());
        assertEquals(List.of(true), outcomes);
        subscribe(scripts, outcomes::add);
        assertEquals(List.of(true, true), outcomes);
    }

    @Test
    void failedLoaderStillMarksItsInitializationAttemptTerminal() throws Exception {
        var scripts = loading(true);
        finishLoading();
        assertTrue(scripts.isInitAttempted());
        assertFalse(scripts.isInit());
    }

    @Test
    void failureNotifiesPendingAndLateListeners() throws Exception {
        var scripts = loading(true);
        var outcomes = new CopyOnWriteArrayList<Boolean>();
        subscribe(scripts, outcomes::add);
        finishLoading();
        assertEquals(List.of(false), outcomes);
        subscribe(scripts, outcomes::add);
        assertEquals(List.of(false, false), outcomes);
    }

    @Test
    void destroyingDuringBlockedLoadCannotPublishReadyMetadata() throws Exception {
        var scripts = loading(false);
        scripts.onDestroy();
        finishLoading();
        assertFalse(scripts.isInit());
    }

    @Test
    void destroyingDuringLoadCompletesWaitersWithoutWaitingForTheLoader() throws Exception {
        var scripts = loading(false);
        var outcomes = new CopyOnWriteArrayList<Boolean>();
        subscribe(scripts, outcomes::add);
        scripts.onDestroy();
        assertEquals(List.of(false), outcomes);
        finishLoading();
        assertEquals(List.of(false), outcomes);
    }

    @Test
    void disabledBigWorldScriptsAreTerminalWithoutStartingALoader() throws Exception {
        var scripts = player.getScene().getScriptManager();
        assertFalse(scripts.isInit());
        assertTrue(scripts.isInitAttempted());
        var outcomes = new ArrayList<Boolean>();
        subscribe(scripts, outcomes::add);
        assertEquals(List.of(false), outcomes);
    }

    @Test
    void aLateListenerAfterDestroyDoesNotReceiveAReadyScene() throws Exception {
        var scripts = loading(false);
        finishLoading();
        scripts.onDestroy();
        var outcomes = new ArrayList<Boolean>();
        subscribe(scripts, outcomes::add);
        assertEquals(List.of(false), outcomes);
        assertFalse(scripts.isInit());
    }

    @Test
    void aThrowingListenerDoesNotPreventOtherCallbacksOrInitialization() throws Exception {
        var scripts = loading(false);
        var outcomes = new CopyOnWriteArrayList<Boolean>();
        subscribe(
                scripts,
                ready -> {
                    throw new IllegalStateException("Controlled subscriber failure");
                });
        subscribe(scripts, outcomes::add);
        finishLoading();
        assertEquals(List.of(true), outcomes);
        assertTrue(scripts.isInit());
    }
}

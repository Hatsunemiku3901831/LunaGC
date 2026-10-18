package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.trial.TrialAvatarData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.utils.JsonUtils;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/** Trial grants must not invert the Scene -> DungeonManager order used by dungeon exit. */
class DungeonTrialTeamLockTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture fixture;
    private TpsSceneLifecycleTest.RecordingPlayer player;
    private AbilityTestFixture.TestScene scene;
    private DungeonManager dungeon;
    private Object previousDatastore;
    private Runnable removing = () -> {};

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(() -> {
            if (old == null) map.remove(key);
            else map.put(key, old);
        });
    }

    private <T> List<T> load(String name, Class<T> type) throws Exception {
        return JsonUtils.loadToList(EXCEL.resolve(name + "ExcelConfigData.json"), type);
    }

    @BeforeEach
    void setup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        previousDatastore = field.get(null);
        field.set(null, Proxy.newProxyInstance(Datastore.class.getClassLoader(),
                new Class<?>[] {Datastore.class},
                (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        fixture = new AbilityTestFixture();
        player = new TpsSceneLifecycleTest.RecordingPlayer();
        player.server = fixture.scene.getWorld().getServer();
        player.loggedIn = true;
        var world = new AbilityTestFixture.TestWorld(player.server, player);
        player.setWorld(world);
        for (var row : load("AvatarCurve", AvatarCurveData.class)) {
            row.onLoad();
            put(GameData.getAvatarCurveDataMap(), row.getId(), row);
        }
        for (var row : load("AvatarSkillDepot", AvatarSkillDepotData.class)) {
            if (!List.of(501, 701).contains(row.getId())) continue;
            row.onLoad();
            put(GameData.getAvatarSkillDepotDataMap(), row.getId(), row);
        }
        for (var row : load("Avatar", AvatarData.class)) {
            if (!List.of(10000005, 10000007).contains(row.getId())) continue;
            row.onLoad();
            put(GameData.getAvatarDataMap(), row.getId(), row);
        }
        for (var row : load("Weapon", ItemData.class)) {
            row.onLoad();
            put(GameData.getItemDataMap(), row.getId(), row);
        }
        put(GameData.getTrialAvatarDataMap(), 91001, JsonUtils.decode(
                "{trialAvatarId:91001,trialAvatarParamList:[10000007,20]}", TrialAvatarData.class));
        player.getAvatars().addAvatar(new Avatar(10000005));
        player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars().add(10000005);
        scene = new AbilityTestFixture.TestScene(world,
                JsonUtils.decode("{id:3,type:'SCENE_WORLD'}", SceneData.class)) {
            @Override public void saveGroups() {}
            @Override public void removeEntity(GameEntity entity, VisionType type) {
                removing.run();
                super.removeEntity(entity, type);
            }
        };
        scene.setDontDestroyWhenEmpty(true);
        world.registerScene(scene);
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.addPlayer(player);
        dungeon = new DungeonManager(scene, JsonUtils.decode(
                "{id:1,type:'DUNGEON_ELEMENT_CHALLENGE',passCond:0}", DungeonData.class));
        scene.setDungeonManager(dungeon);
    }

    @AfterEach
    void cleanup() throws Exception {
        removing = () -> {};
        if (scene != null) scene.getScriptManager().onDestroy();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        field.set(null, previousDatastore);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (fixture != null) fixture.close();
    }

    private void apply(Integer... ids) {
        try {
            var method = DungeonManager.class.getDeclaredMethod(
                    "applyTrialTeam", Player.class, DungeonTrialTeam.class);
            method.setAccessible(true);
            method.invoke(dungeon, player, new DungeonTrialTeam(List.of(ids),
                    GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY));
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof RuntimeException error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void grantRunsRealPartyRemovalWithoutHoldingDungeonMonitor() {
        var callbacks = new AtomicInteger();
        removing = () -> {
            assertFalse(Thread.holdsLock(dungeon),
                    "Trial setup must not hold DungeonManager while acquiring Scene");
            callbacks.incrementAndGet();
        };
        apply(91001);
        assertTrue(callbacks.get() > 0);
        assertNotEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
        assertEquals(List.of(10000007), player.getTeamManager().getCurrentTeamInfo().getAvatars());
    }

    @Test
    void cleanupRunsRealPartyRemovalWithoutHoldingDungeonMonitor() {
        apply(91001);
        var callbacks = new AtomicInteger();
        removing = () -> {
            assertFalse(Thread.holdsLock(dungeon),
                    "Trial cleanup must not hold DungeonManager while acquiring Scene");
            callbacks.incrementAndGet();
        };
        dungeon.unsetTrialTeam(player);
        assertTrue(callbacks.get() > 0);
        assertEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
        assertEquals(List.of(10000005), player.getTeamManager().getCurrentTeamInfo().getAvatars());
    }

    @Test
    void grantWaitingForSceneDoesNotRetainTheDungeonMonitorNeededByExit() throws Exception {
        var grant = new CompletableFuture<Void>();
        var grantThread = new Thread(() -> {
            try { apply(91001); grant.complete(null); }
            catch (Throwable failure) { grant.completeExceptionally(failure); }
        }, "dungeon-trial-grant-lock-test");
        var probe = Executors.newSingleThreadExecutor();
        try {
            synchronized (scene) {
                grantThread.start();
                awaitSceneBlock(grantThread);
                // The actual exit path already holds Scene before entering unsetTrialTeam.
                // Probe its second monitor on a bounded worker so RED can safely release Scene.
                var acquired = probe.submit(() -> {
                    synchronized (dungeon) { return true; }
                });
                assertTrue(acquired.get(1, TimeUnit.SECONDS),
                        "A pending grant must not prevent the scene owner from taking DungeonManager");
            }
        } finally {
            grantThread.join(5000);
            probe.shutdown();
            assertFalse(grantThread.isAlive(), "Grant must finish once Scene is released");
            assertTrue(probe.awaitTermination(5, TimeUnit.SECONDS));
        }
        grant.get(1, TimeUnit.SECONDS);
        dungeon.unsetTrialTeam(player);
        assertEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
    }

    @Test
    void partiallyFailedGrantStillTracksItsIntroducedAvatarForCleanup() {
        put(GameData.getTrialAvatarDataMap(), 91002, JsonUtils.decode(
                "{trialAvatarId:91002,trialAvatarParamList:[19999999,20]}", TrialAvatarData.class));
        assertThrows(RuntimeException.class, () -> apply(91001, 91002));
        assertNotEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
        dungeon.unsetTrialTeam(player);
        assertEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
        assertEquals(List.of(10000005), player.getTeamManager().getCurrentTeamInfo().getAvatars());
    }

    private void awaitSceneBlock(Thread thread) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < until) {
            var info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockOwnerId() == Thread.currentThread().getId()
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(scene))
                return;
            if (!thread.isAlive()) fail("Grant finished without reaching the Scene monitor");
            Thread.sleep(5);
        }
        fail("Grant did not reach the Scene monitor");
    }
}

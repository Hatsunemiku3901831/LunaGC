package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.utils.JsonUtils;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regression for the Player -> activeTeam -> Player cycle captured during scene departure. */
class SceneAvatarRemovalLockTest {
    private AbilityTestFixture fixture;
    private Scene scene;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        var world = (AbilityTestFixture.TestWorld) fixture.scene.getWorld();
        scene = new AbilityTestFixture.TestScene(world,
                new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class)) {
            @Override public void saveGroups() {}
        };
        scene.setDontDestroyWhenEmpty(true);
        fixture.player.setWorld(world);
        fixture.player.setScene(scene);
        scene.getPlayers().add(fixture.player);
    }

    @AfterEach
    void cleanup() {
        scene.getScriptManager().onDestroy();
        fixture.close();
    }

    private EntityAvatar member(boolean visible, Runnable onInvalidated) {
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public void save() {}
        };
        var entity = new EntityAvatar(scene, avatar) {
            @Override public void initAbilities() {}
            @Override public void invalidateAbilityLifecycle() {
                onInvalidated.run();
                super.invalidateAbilityLifecycle();
            }
        };
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        if (visible) scene.getEntities().put(entity.getId(), entity);
        // This watcher creates an AbilityExecutionContext and reads Player.getScene on removal,
        // matching the callback path in the production thread dump.
        var data = JsonUtils.decode("""
                {abilityName:'Scene_Departure_Lock_Test',abilityMixins:[{
                  $type:'AttachModifierToSelfGlobalValueMixin',
                  globalValueKey:'SGV_departure',modifierNameSteps:[]
                }]}
                """, AbilityData.class);
        entity.getInstancedAbilities().add(new Ability(data, entity, fixture.player));
        return entity;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void departureInvalidatesHiddenAndVisibleAvatarsWithoutHoldingTheTeamList(boolean visible) {
        var team = fixture.player.getTeamManager().getActiveTeam();
        var invalidated = new AtomicInteger();
        Runnable callback = () -> {
            assertFalse(Thread.holdsLock(team),
                    "Ability cleanup must not hold activeTeam while it acquires Player");
            assertEquals(2, team.size(), "Keep the team available until all cleanup completes");
            invalidated.incrementAndGet();
        };
        var first = member(visible, callback);
        var second = member(visible, callback);

        scene.removePlayer(fixture.player);

        assertEquals(2, invalidated.get());
        assertFalse(first.isAbilityLifecycleCurrent(0));
        assertFalse(second.isAbilityLifecycleCurrent(0));
        assertFalse(scene.getEntities().containsKey(first.getId()));
        assertFalse(scene.getEntities().containsKey(second.getId()));
        assertTrue(team.isEmpty());
        assertTrue(scene.getPlayers().isEmpty());
        assertNull(fixture.player.getScene());
    }

    @Test
    void playerLockedTickCanReadTheTeamWhileDepartureRunsAbilityCleanup() throws Exception {
        var enteredCleanup = new CountDownLatch(1);
        var playerLocked = new CountDownLatch(1);
        var tickReadFinished = new CountDownLatch(1);
        var entity = member(true, () -> {
            enteredCleanup.countDown();
            await(playerLocked, "Tick did not acquire Player");
            // Bound the wait before the real watcher reads Player, so the old implementation
            // fails and unwinds instead of leaving two permanently deadlocked test threads.
            await(tickReadFinished, "Player tick is blocked by activeTeam during ability cleanup");
        });
        var workers = Executors.newFixedThreadPool(2);
        try {
            var departure = workers.submit(() -> scene.removePlayer(fixture.player));
            var tick = workers.submit(() -> {
                await(enteredCleanup, "Scene did not start ability cleanup");
                synchronized (fixture.player) {
                    playerLocked.countDown();
                    try {
                        assertSame(entity, fixture.player.getTeamManager().getCurrentAvatarEntity());
                    } finally {
                        tickReadFinished.countDown();
                    }
                }
            });
            departure.get(5, TimeUnit.SECONDS);
            tick.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
            assertFalse(entity.isAbilityLifecycleCurrent(0));
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS), message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message, exception);
        }
    }
}

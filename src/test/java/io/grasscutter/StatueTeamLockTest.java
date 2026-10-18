package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Statue recovery runs under Player; team operations must not introduce a reverse lock edge. */
class StatueTeamLockTest {
    private AbilityTestFixture fixture;
    private SotSManager recovery;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        recovery = new SotSManager(fixture.player, () -> {}, rewards -> {}, item -> {});
    }

    @AfterEach
    void cleanup() {
        fixture.scene.getScriptManager().onDestroy();
        fixture.close();
    }

    private EntityAvatar deadMember(Runnable beforeRevival) throws Exception {
        return deadMember(beforeRevival, () -> {});
    }

    private EntityAvatar deadMember(Runnable beforeRevival, Runnable onAvatarLookup) throws Exception {
        var data = JsonUtils.decode("{id:10000103,name:'StatueLockTest'}", AvatarData.class);
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public AvatarData getAvatarData() { return data; }
            @Override public void save() {}
        };
        var entity = new EntityAvatar(fixture.scene, avatar) {
            @Override public void initAbilities() {}
            @Override public Avatar getAvatar() {
                onAvatarLookup.run();
                return super.getAvatar();
            }
            @Override public boolean revive(float amount) {
                beforeRevival.run();
                return super.revive(amount);
            }
        };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
        var setDead = GameEntity.class.getDeclaredMethod("setDead", boolean.class);
        setDead.setAccessible(true);
        setDead.invoke(entity, true);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    @Test
    void statueRevivalDoesNotHoldTeamListDuringEntityCallbacks() throws Exception {
        var team = fixture.player.getTeamManager().getActiveTeam();
        var member = deadMember(() -> assertFalse(Thread.holdsLock(team),
                "Revival callbacks must not keep activeTeam locked against quest death settlement"));

        recovery.autoRevive();

        assertTrue(member.isAlive());
        assertEquals(31f, member.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(1, team.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wipeTrackerCanBeReadWhileTeamOperationOwnsManager(boolean initialized) throws Exception {
        var manager = fixture.player.getTeamManager();
        var existing = initialized ? manager.getQuestTeamWipe() : null;
        var workers = Executors.newSingleThreadExecutor();
        try {
            // setupAvatarTeam/changeAvatar own this monitor and then read synchronized Player.
            // Recovery and scene death settlement must not acquire it in the reverse order.
            synchronized (manager) {
                var lookup = workers.submit(manager::getQuestTeamWipe);
                var tracker = lookup.get(2, TimeUnit.SECONDS);
                assertNotNull(tracker);
                if (existing != null) assertSame(existing, tracker);
                assertSame(tracker, manager.getQuestTeamWipe());
            }
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void playerLockedStatueRevivalDoesNotWaitForTeamManager() throws Exception {
        var member = deadMember(() -> {});
        var workers = Executors.newSingleThreadExecutor();
        var playerLocked = new CountDownLatch(1);
        try {
            synchronized (fixture.player.getTeamManager()) {
                var revived = workers.submit(() -> {
                    synchronized (fixture.player) {
                        playerLocked.countDown();
                        recovery.autoRevive();
                    }
                });
                assertTrue(playerLocked.await(2, TimeUnit.SECONDS));
                // Bound the wait so the old implementation fails and releases the manager
                // instead of leaving a permanently deadlocked test process.
                revived.get(2, TimeUnit.SECONDS);
            }
            assertTrue(member.isAlive());
            assertEquals(31f, member.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"revive", "heal"})
    void recoveryLookupToleratesRosterChangesWithoutTouchingAnAbsentAvatar(String operation)
            throws Exception {
        var lookupStarted = new CountDownLatch(1);
        var continueLookup = new CountDownLatch(1);
        var pauseLookup = new AtomicBoolean();
        var first = deadMember(() -> fail("An unrelated member must not be revived"), () -> {
            if (!pauseLookup.compareAndSet(true, false)) return;
            lookupStarted.countDown();
            await(continueLookup, "Roster mutation did not finish");
        });
        var second = deadMember(() -> fail("An unrelated member must not be revived"));
        var removed = deadMember(() -> fail("An absent member must not be revived"));
        var manager = fixture.player.getTeamManager();
        manager.getActiveTeam().remove(removed);
        fixture.scene.getEntities().remove(removed.getId());
        var requested = removed.getAvatar();
        fixture.player.packets.clear();
        fixture.scene.packets.clear();

        var workers = Executors.newSingleThreadExecutor();
        try {
            pauseLookup.set(true);
            var recovered = workers.submit(() -> operation.equals("revive")
                    ? manager.reviveAvatar(requested) : manager.healAvatar(requested, 100, 0));
            await(lookupStarted, "Recovery did not start its member lookup");
            // The lookup is already visiting its first member. Adding another member used to
            // invalidate its live iterator before it could compare the second member.
            var added = deadMember(() -> fail("A newly added member must not be revived"));
            continueLookup.countDown();
            assertFalse(recovered.get(3, TimeUnit.SECONDS));
            for (var entity : new EntityAvatar[] {first, second, removed, added}) {
                assertFalse(entity.isAlive());
                assertEquals(0f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            }
            assertEquals(3, manager.getActiveTeam().size());
            assertTrue(fixture.player.packets.isEmpty());
            assertTrue(fixture.scene.packets.isEmpty());
        } finally {
            continueLookup.countDown();
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

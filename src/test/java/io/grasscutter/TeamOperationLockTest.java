package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** Team callbacks must not retain collection or manager monitors needed by a player tick. */
class TeamOperationLockTest {
    private AbilityTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.scene.getPlayers().add(fixture.player);
    }

    @AfterEach
    void cleanup() {
        fixture.scene.getScriptManager().onDestroy();
        fixture.close();
    }

    private EntityAvatar member(Runnable beforeDamage) {
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public void save() {}
        };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100f);
        var entity = new EntityAvatar(fixture.scene, avatar) {
            @Override public void damage(float amount) {
                beforeDamage.run();
                super.damage(amount);
            }
        };
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        // The actual HPDown callback constructs AbilityExecutionContext and reads Player.getScene.
        var data = JsonUtils.decode("""
                {abilityName:'Team_Void_Damage_Lock',abilityMixins:[{
                  $type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',
                  actions:[{$type:'AddGlobalValue',key:'voidEvents',value:1}]
                }]}
                """, AbilityData.class);
        entity.getInstancedAbilities().add(new Ability(data, entity, fixture.player));
        return entity;
    }

    @Test
    void voidDamageRunsHealthCallbacksWithoutHoldingTheTeamList() {
        var team = fixture.player.getTeamManager().getActiveTeam();
        var first = member(() -> assertFalse(Thread.holdsLock(team),
                "Void damage must not retain activeTeam while health callbacks acquire Player"));
        var second = member(() -> assertFalse(Thread.holdsLock(team)));

        fixture.player.getTeamManager().applyVoidDamage();

        for (var entity : new EntityAvatar[] {first, second}) {
            assertEquals(90f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(1f, entity.getGlobalAbilityValues().get("voidEvents"));
        }
        assertEquals(2, team.size());
    }

    @Test
    void playerLockedTickCanReadTheTeamWhileVoidDamageStartsItsHealthCallback() throws Exception {
        var damageStarted = new CountDownLatch(1);
        var playerLocked = new CountDownLatch(1);
        var tickReadFinished = new CountDownLatch(1);
        var entity = member(() -> {
            damageStarted.countDown();
            await(playerLocked, "Tick did not acquire Player");
            // Bound the wait before the production health callback acquires Player. The buggy
            // version unwinds on timeout, instead of leaving permanent deadlocked test threads.
            await(tickReadFinished, "Player tick is blocked by activeTeam during void damage");
        });
        var workers = Executors.newFixedThreadPool(2);
        try {
            var damage = workers.submit(() -> fixture.player.getTeamManager().applyVoidDamage());
            var tick = workers.submit(() -> {
                await(damageStarted, "Void damage did not start");
                synchronized (fixture.player) {
                    playerLocked.countDown();
                    try {
                        assertSame(entity, fixture.player.getTeamManager().getCurrentAvatarEntity());
                    } finally {
                        tickReadFinished.countDown();
                    }
                }
            });
            damage.get(5, TimeUnit.SECONDS);
            tick.get(5, TimeUnit.SECONDS);
            assertEquals(90f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(1f, entity.getGlobalAbilityValues().get("voidEvents"));
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

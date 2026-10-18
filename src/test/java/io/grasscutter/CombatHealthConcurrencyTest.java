package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class CombatHealthConcurrencyTest {
    private AbilityTestFixture fixture;
    private HealthMap health;
    private AbilityTestFixture.TestEntity victim;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        AbilityManager.registerHandlers();
        health = new HealthMap();
        victim = new AbilityTestFixture.TestEntity(fixture.scene) {
            @Override public Int2FloatMap getFightProperties() { return health; }
            @Override public void onAbilityValueUpdate() {
                assertFalse(Thread.holdsLock(health), "HP callbacks must run outside the property settlement lock");
                super.onAbilityValueUpdate();
            }
        };
        victim.setId(43);
        fixture.scene.getEntities().put(43, victim);
        victim.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
        victim.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100f);
        victim.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0f);
        var data = JsonUtils.decode("{abilityName:ConcurrentHealth,abilityMixins:["
                + "{$type:DoActionByEventMixin,onEvent:HPDown,type:Both,"
                + "actions:[{$type:AddGlobalValue,key:damageCallbacks,value:1}]}]}", AbilityData.class);
        victim.getInstancedAbilities().add(new Ability(data, victim, fixture.player));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private void parallel(Runnable first, int firstRead, Runnable second, int secondRead) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> { health.begin(firstRead); first.run(); });
            var b = workers.submit(() -> { health.begin(secondRead); second.run(); });
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void twoConcurrentHitsBothReduceHealthAndRunTheirCallbacks() throws Exception {
        parallel(() -> victim.damage(10, ElementType.Fire), 2,
                () -> victim.damage(20, ElementType.Water), 2);
        assertEquals(70f, victim.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(2f, victim.getGlobalAbilityValues().get("damageCallbacks"));
    }

    @Test
    void concurrentHealingAndDamagePreserveBothAcceptedAmounts() throws Exception {
        victim.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50f);
        victim.getGlobalAbilityValues().clear();
        parallel(() -> victim.damage(10, ElementType.Fire), 2, () -> victim.heal(20), 1);
        assertEquals(60f, victim.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(1f, victim.getGlobalAbilityValues().get("damageCallbacks"));
    }

    @Test
    void concurrentLimboHitsReevaluateTheOneHpFloorFromCurrentHealth() throws Exception {
        var data = JsonUtils.decode("{abilityName:ConcurrentLimbo,modifiers:{Guard:{state:Limbo}}}", AbilityData.class);
        var guard = new Ability(data, victim, fixture.player);
        guard.registerModifier(victim, new AbilityModifierController(guard, data, data.modifiers.get("Guard")));
        parallel(() -> victim.damage(80, ElementType.Fire), 2,
                () -> victim.damage(80, ElementType.Water), 2);
        assertEquals(1f, victim.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertTrue(victim.isAlive());
        assertEquals(2f, victim.getGlobalAbilityValues().get("damageCallbacks"));
    }

    /** Force old unguarded reads to overlap; short atomic settlements need no rendezvous. */
    private static final class HealthMap extends Int2FloatOpenHashMap {
        private final CyclicBarrier reads = new CyclicBarrier(2);
        private final ThreadLocal<Integer> waitAt = ThreadLocal.withInitial(() -> 0);
        private final ThreadLocal<Integer> count = ThreadLocal.withInitial(() -> 0);

        void begin(int read) { waitAt.set(read); count.set(0); }

        @Override public float getOrDefault(int key, float fallback) {
            float value = super.getOrDefault(key, fallback);
            if (key != FightProperty.FIGHT_PROP_CUR_HP.getId() || waitAt.get() == 0) return value;
            if (Thread.holdsLock(this)) {
                waitAt.set(0);
                return value;
            }
            int read = count.get() + 1;
            count.set(read);
            if (read == waitAt.get()) {
                waitAt.set(0);
                try {
                    reads.await(3, TimeUnit.SECONDS);
                } catch (Exception failed) {
                    throw new AssertionError("Both unguarded HP reads should reach the interleaving", failed);
                }
            }
            return value;
        }
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.ability.NyxValue;
import emu.grasscutter.utils.JsonUtils;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import org.junit.jupiter.api.*;

class NyxValueConcurrencyTest {
    private AbilityTestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Ability ability(AbilityTestFixture.TestEntity entity) {
        return new Ability(
                JsonUtils.decode("{abilityName:NyxConcurrency,abilitySpecials:{NyxValueMax:100}}", AbilityData.class),
                entity, fixture.player);
    }

    @Test
    void nyxGainAndGenericGlobalGainCannotOverwriteEachOther() throws Exception {
        var globals = new PausingMap();
        var entity = new AbilityTestFixture.TestEntity(fixture.scene) {
            @Override public Map<String, Float> getGlobalAbilityValues() { return globals; }
        };
        entity.setId(43);
        fixture.scene.getEntities().put(entity.getId(), entity);
        var ability = ability(entity);
        globals.put("NyxValue", 10f);
        var worker = Executors.newSingleThreadExecutor();
        Future<Boolean> gain = null;
        try {
            gain = worker.submit(() -> {
                globals.writer = Thread.currentThread();
                return NyxValue.change(ability, entity, new DynamicFloat(3f), null, null, false);
            });
            assertTrue(globals.entered.await(2, TimeUnit.SECONDS), "Nyx settlement did not reach the controlled interleaving");
            var action = JsonUtils.decode("{$type:AddGlobalValue,key:NyxValue,value:5}", AbilityModifierAction.class);
            assertTrue(AbilityGlobalValues.write(ability, action, entity, 5f, true));
            globals.resume.countDown();
            assertTrue(gain.get(2, TimeUnit.SECONDS));
            assertEquals(18f, globals.get("NyxValue"), "Both accepted gains must survive the same-key interleaving");
        } finally {
            globals.resume.countDown();
            if (gain != null) gain.get(3, TimeUnit.SECONDS);
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void nyxCallbacksDoNotHoldTheGlobalMapMonitor() {
        var notified = new AtomicBoolean();
        var entity = new AbilityTestFixture.TestEntity(fixture.scene) {
            @Override public void onAbilityValueUpdate() {
                assertFalse(Thread.holdsLock(getGlobalAbilityValues()),
                        "A global-value callback can acquire scene, player and modifier locks");
                notified.set(true);
                super.onAbilityValueUpdate();
            }
        };
        entity.setId(44);
        fixture.scene.getEntities().put(entity.getId(), entity);
        assertTrue(NyxValue.change(ability(entity), entity, new DynamicFloat(3f), null, null, false));
        assertTrue(notified.get());
        assertEquals(3f, entity.getNyxValue());
    }

    /** Pause an old get/put or the replacement compute before its atomic update begins. */
    private static final class PausingMap extends ConcurrentHashMap<String, Float> {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicBoolean paused = new AtomicBoolean();
        volatile Thread writer;

        private void pause(Object key) {
            if (!"NyxValue".equals(key) || Thread.currentThread() != writer
                    || !paused.compareAndSet(false, true)) return;
            entered.countDown();
            try {
                assertTrue(resume.await(3, TimeUnit.SECONDS), "The bounded interleaving was not released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }

        @Override public Float getOrDefault(Object key, Float fallback) {
            var result = super.getOrDefault(key, fallback);
            pause(key);
            return result;
        }

        @Override public Float compute(String key, BiFunction<? super String, ? super Float, ? extends Float> update) {
            pause(key);
            return super.compute(key, update);
        }
    }
}

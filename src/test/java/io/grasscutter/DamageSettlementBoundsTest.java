package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.props.*;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.*;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.utils.FileUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

class DamageSettlementBoundsTest {
    AbilityTestFixture fixture;
    Victim target;
    Field pluginsField;
    Object previousPlugins;
    PluginManager plugins;
    final AtomicInteger events = new AtomicInteger();

    static class Victim extends AbilityTestFixture.TestEntity {
        int callbacks;

        Victim(AbilityTestFixture.TestScene scene) {
            super(scene);
        }

        @Override
        public void runLuaCallbacks(EntityDamageEvent event) {
            callbacks++;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        target = new Victim(fixture.scene);
        target.setId(43);
        fixture.scene.getEntities().put(target.getId(), target);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 25);
        var pluginPath = FileUtils.getPluginPath("");
        if (Files.isDirectory(pluginPath)) {
            try (var files = Files.list(pluginPath)) {
                assertTrue(
                        files.noneMatch(p -> p.toString().endsWith(".jar")),
                        "Damage fixture must not load external plugins");
            }
        }
        pluginsField = Grasscutter.class.getDeclaredField("pluginManager");
        pluginsField.setAccessible(true);
        previousPlugins = pluginsField.get(null);
        plugins = new PluginManager();
        pluginsField.set(null, plugins);
        listen(e -> events.incrementAndGet());
        fixture.scene.packets.clear();
    }

    @AfterEach
    void restore() throws Exception {
        pluginsField.set(null, previousPlugins);
        fixture.close();
    }

    void listen(java.util.function.Consumer<EntityDamageEvent> listener) {
        plugins.registerListener(
                new EventHandler<>(EntityDamageEvent.class)
                        .priority(HandlerPriority.NORMAL)
                        .listener(listener::accept));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidEntries() {
        return Stream.of(-20f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
                .flatMap(
                        amount ->
                                Stream.of("simple", "typed", "ability")
                                        .map(
                                                entry ->
                                                        org.junit.jupiter.params.provider.Arguments
                                                                .of(entry, amount)));
    }

    void unchanged() {
        assertEquals(50f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(25f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(target.isAlive());
        assertEquals(ElementType.None, target.getLastAttackType());
        assertEquals(0, target.callbacks);
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidEntries")
    void invalidInputCannotHealCorruptOrTriggerDamageSideEffects(String entry, float amount) {
        switch (entry) {
            case "simple" -> target.damage(amount);
            case "typed" -> target.damage(amount, 0, ElementType.Fire);
            case "ability" -> target.loseHpByAbility(amount, false);
            default -> fail(entry);
        }
        unchanged();
        assertEquals(0, events.get());
    }

    static Stream<Float> invalidPluginDamage() {
        return Stream.of(-5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("invalidPluginDamage")
    void invalidPluginResultIsRejectedBeforeSettlement(float amount) {
        listen(event -> event.setDamage(amount));
        target.damage(10f, ElementType.Fire);
        unchanged();
        assertEquals(1, events.get());
    }

    @Test
    void zeroDamageRetainsTheExistingHitCallbackWithoutChangingHealth() {
        target.damage(0f, ElementType.Fire);
        assertEquals(50f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(25f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(1, target.callbacks);
        assertEquals(1, events.get());
        assertEquals(ElementType.Fire, target.getLastAttackType());
    }

    @Test
    void aPluginCanStillCancelAValidHit() {
        listen(EntityDamageEvent::cancel);
        target.damage(10f, ElementType.Fire);
        unchanged();
        assertEquals(1, events.get());
    }

    @Test
    void aPluginCanStillScaleAValidPositiveHit() {
        listen(event -> event.setDamage(20f));
        target.damage(10f, ElementType.Fire);
        assertEquals(30f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(25f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(1, target.callbacks);
        assertEquals(1, events.get());
        assertFalse(fixture.scene.packets.isEmpty());
    }

    @Test
    void largeFiniteDamageStillCompletesNormalDeathSettlement() {
        target.damage(Float.MAX_VALUE, ElementType.Fire);
        assertEquals(0f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertFalse(target.isAlive());
        assertEquals(1, target.callbacks);
        assertEquals(1, events.get());
    }
}

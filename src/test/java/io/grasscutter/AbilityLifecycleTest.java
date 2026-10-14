package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionTriggerAbility;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Path;
import org.junit.jupiter.api.*;

class AbilityLifecycleTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach void setup() throws Exception {
        fixture = new AbilityTestFixture();
        ability = new Ability(data("Lifecycle_Test"), fixture.entity, fixture.player);
    }
    @AfterEach void close() { fixture.close(); }

    private AbilityData data(String name) {
        return JsonUtils.decode("{\"abilityName\":\"" + name + "\"}", AbilityData.class);
    }
    private AbilityModifierAction increment(String key) {
        return JsonUtils.decode("{\"$type\":\"AddGlobalValue\",\"key\":\"" + key
                + "\",\"value\":1}", AbilityModifierAction.class);
    }
    private void queue(GameEntity target) {
        ability.getManager().executeAction(ability, increment("queued"), ByteString.EMPTY, target);
    }

    /** Hold all workers so assertions observe a real task queued before the lifecycle change. */
    private static class Workers implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Future<?>> blockers = new ArrayList<>();
        Workers() throws Exception {
            var started = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) blockers.add(AbilityManager.eventExecutor.submit(() -> {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            }));
            assertTrue(started.await(3, TimeUnit.SECONDS));
        }
        @Override public void close() throws Exception {
            var queued = new ArrayList<>(((ThreadPoolExecutor) AbilityManager.eventExecutor).getQueue());
            release.countDown();
            for (var task : queued) ((Future<?>) task).get(3, TimeUnit.SECONDS);
            for (var blocker : blockers) blocker.get(3, TimeUnit.SECONDS);
        }
    }

    @Test void queuedActionCannotMutateAnEntityAfterRemoval() throws Exception {
        try (var workers = new Workers()) {
            queue(fixture.entity);
            fixture.scene.removeEntity(fixture.entity);
        }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void queuedActionCannotResumeFromRemovedRootAbility() throws Exception {
        fixture.entity.getInstancedAbilities().add(ability);
        try (var workers = new Workers()) {
            queue(fixture.entity);
            fixture.entity.getInstancedAbilities().set(0, null);
        }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void sameNameReplacementCannotInheritOldQueuedAction() throws Exception {
        fixture.entity.getInstancedAbilities().add(ability);
        try (var workers = new Workers()) {
            queue(fixture.entity);
            fixture.entity.getInstancedAbilities().set(0,
                    new Ability(ability.getData(), fixture.entity, fixture.player));
        }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void queuedActionCannotFollowTheCasterIntoAnotherScene() throws Exception {
        try (var workers = new Workers()) {
            queue(fixture.entity);
            fixture.player.setScene(new AbilityTestFixture.TestScene(
                    (AbilityTestFixture.TestWorld) fixture.scene.getWorld(), fixture.scene.getSceneData()));
        }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void queuedActionCannotResumeAfterTheSameEntityWasRemovedAndReadded() throws Exception {
        try (var workers = new Workers()) {
            queue(fixture.entity);
            fixture.scene.removeEntity(fixture.entity);
            fixture.scene.addEntity(fixture.entity);
        }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
        try (var workers = new Workers()) { queue(fixture.entity); }
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("queued"));
    }
    @Test void queuedActionCannotMutateATargetRemovedWhileItsOwnerRemains() throws Exception {
        var target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        fixture.scene.addEntity(target);
        try (var workers = new Workers()) {
            queue(target);
            fixture.scene.removeEntity(target);
        }
        assertFalse(target.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void replacingAnEntityIdInvalidatesQueuedWorkWithoutRemovingTheReplacement() throws Exception {
        var replacement = new AbilityTestFixture.TestEntity(fixture.scene);
        replacement.setId(fixture.entity.getId());
        try (var workers = new Workers()) {
            queue(fixture.entity);
            fixture.scene.addEntity(replacement);
            fixture.scene.removeEntity(fixture.entity);
        }
        assertSame(replacement, fixture.scene.getEntityById(replacement.getId()));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void initializationBeforeFirstEntityRegistrationRemainsValid() throws Exception {
        var target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        try (var workers = new Workers()) {
            queue(target);
            fixture.scene.addEntity(target);
        }
        assertEquals(1f, target.getGlobalAbilityValues().get("queued"));
    }
    @Test void removedEntitiesCannotAcceptNewQueuedWork() throws Exception {
        fixture.scene.removeEntity(fixture.entity);
        try (var workers = new Workers()) { queue(fixture.entity); }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void asynchronousMixinIsDiscardedAfterRemoval() throws Exception {
        var mixin = JsonUtils.decode("{\"$type\":\"ChangeNyxValueMixin\",\"value\":1}", AbilityMixinData.class);
        try (var workers = new Workers()) {
            ability.getManager().executeMixin(ability, mixin, ByteString.EMPTY);
            fixture.scene.removeEntity(fixture.entity);
        }
        assertEquals(0f, fixture.entity.getNyxValue());
    }
    @Test void asynchronousHealingSequenceIsDiscardedAfterRemoval() throws Exception {
        try (var workers = new Workers()) {
            ability.getManager().executeHealingCallbacks(ability,
                    new AbilityModifierAction[]{increment("healed")}, fixture.entity, "Impale");
            fixture.scene.removeEntity(fixture.entity);
        }
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("healed"));
    }
    @Test void validQueuedActionMixinAndHealingSequenceStillRun() throws Exception {
        var mixin = JsonUtils.decode("{\"$type\":\"ChangeNyxValueMixin\",\"value\":1}", AbilityMixinData.class);
        try (var workers = new Workers()) {
            queue(fixture.entity);
            ability.getManager().executeMixin(ability, mixin, ByteString.EMPTY);
            ability.getManager().executeHealingCallbacks(ability,
                    new AbilityModifierAction[]{increment("healed"), increment("healed")}, fixture.entity, "Impale");
        }
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("queued"));
        assertEquals(1f, fixture.entity.getNyxValue());
        assertEquals(2f, fixture.entity.getGlobalAbilityValues().get("healed"));
        assertNull(AbilityHealContext.currentTag());
    }
    @Test void triggerKeepsTheGuestCasterAndRunsStartOnlyOncePerTrigger() throws Exception {
        var host = new AbilityTestFixture.TestPlayer();
        var guestWorld = new AbilityTestFixture.TestWorld(fixture.scene.getWorld().getServer(), host);
        fixture.player.setWorld(guestWorld);
        var child = JsonUtils.decode("""
                {"abilityName":"Lifecycle_Child","onAdded":[{"$type":"AddGlobalValue","key":"added","value":1}],
                "onAbilityStart":[{"$type":"AddGlobalValue","key":"started","value":1}]}
                """, AbilityData.class);
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        try {
            var trigger = JsonUtils.decode("{\"$type\":\"TriggerAbility\",\"abilityName\":\"Lifecycle_Child\"}", AbilityModifierAction.class);
            try (var workers = new Workers()) {
                assertTrue(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
                assertTrue(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
            }
            var children = fixture.entity.getInstancedAbilities().stream()
                    .filter(a -> a.getData().abilityName.equals(child.abilityName)).toList();
            assertEquals(1, children.size());
            assertSame(fixture.player, children.get(0).getPlayerOwner());
            assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("added"));
            assertEquals(2f, fixture.entity.getGlobalAbilityValues().get("started"));
        } finally {
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
    @Test void triggerWithoutPlayerWorldStillUsesItsRecordedCaster() {
        var child = data("Lifecycle_DetachedWorld");
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        try {
            var trigger = JsonUtils.decode("{\"$type\":\"TriggerAbility\",\"abilityName\":\"Lifecycle_DetachedWorld\"}", AbilityModifierAction.class);
            assertTrue(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
            assertSame(fixture.player, fixture.entity.getInstancedAbilities().get(0).getPlayerOwner());
        } finally {
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
    @Test void malformedTriggerFailsWithoutNullDereferences() {
        var trigger = JsonUtils.decode("{\"$type\":\"TriggerAbility\"}", AbilityModifierAction.class);
        assertFalse(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
        trigger.abilityName = "missing";
        assertFalse(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, null));
    }

    @Test void initializationRunsBeforeStartAndIsNotRepeatedOnTheNextTrigger() {
        var child = JsonUtils.decode("""
                {"abilityName":"Lifecycle_Ordered","onAdded":[{"$type":"SetGlobalValue","key":"ordered","value":0}],
                "onAbilityStart":[{"$type":"AddGlobalValue","key":"ordered","value":1}]}
                """, AbilityData.class);
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        try {
            assertTrue(ability.getManager().triggerAbility(fixture.entity, child.abilityName));
            assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("ordered"));
            assertTrue(ability.getManager().triggerAbility(fixture.entity, child.abilityName));
            assertEquals(2f, fixture.entity.getGlobalAbilityValues().get("ordered"));
        } finally {
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
    @Test void distinctCastersDoNotReuseEachOthersAbilityInstance() {
        var child = data("Lifecycle_Casters");
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        try {
            var second = new AbilityTestFixture.TestPlayer();
            second.setScene(fixture.scene);
            assertTrue(fixture.player.getAbilityManager().triggerAbility(fixture.entity, child.abilityName));
            assertTrue(second.getAbilityManager().triggerAbility(fixture.entity, child.abilityName));
            assertTrue(fixture.player.getAbilityManager().triggerAbility(fixture.entity, child.abilityName));
            assertEquals(2, fixture.entity.getInstancedAbilities().size());
            assertSame(fixture.player, fixture.entity.getInstancedAbilities().get(0).getPlayerOwner());
            assertSame(second, fixture.entity.getInstancedAbilities().get(1).getPlayerOwner());
        } finally {
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
    @Test void simultaneousTriggersCreateOneInstanceAndKeepEveryStart() throws Exception {
        var child = JsonUtils.decode("""
                {"abilityName":"Lifecycle_Concurrent","onAbilityStart":[{"$type":"AddGlobalValue","key":"concurrent","value":1}]}
                """, AbilityData.class);
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 40; i++) tasks.add(executor.submit(() ->
                    assertTrue(ability.getManager().triggerAbility(fixture.entity, child.abilityName))));
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
            assertEquals(1, fixture.entity.getInstancedAbilities().size());
            assertEquals(40f, fixture.entity.getGlobalAbilityValues().get("concurrent"));
        } finally {
            executor.shutdownNow();
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
    @Test void actualSkirkPickableTriggerRestoresEnergyOnEveryStartWithoutResettingState() throws Exception {
        var rows = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_SkirkNew.json"),
                emu.grasscutter.data.ResourceLoader.AbilityConfigData.class);
        var handlerData = rows.stream().map(r -> r.Default)
                .filter(d -> d.abilityName.equals("Avatar_SkirkNew_Pickable_Handler")).findFirst().orElseThrow();
        var pickable = rows.stream().map(r -> r.Default)
                .filter(d -> d.abilityName.equals("Avatar_SkirkNew_Pickable")).findFirst().orElseThrow();
        var trigger = Arrays.stream(pickable.onAbilityStart[0].successActions)
                .filter(a -> a.type == AbilityModifierAction.Type.TriggerAbility).findFirst().orElseThrow();
        var installed = new Ability(handlerData, fixture.entity, fixture.player);
        installed.getAbilitySpecials().put("SkirkNew_Pickable_Energy_Revive", 8f);
        fixture.entity.getInstancedAbilities().add(installed);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 100f);
        fixture.entity.getGlobalAbilityValues().put("_ABILITY_SkirkNew_Pickable_CD", 2f);
        fixture.entity.getGlobalAbilityValues().put("_ABILITY_SkirkNew_ElementalArt_Transform_Flag", 1f);
        assertTrue(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
        assertEquals(8f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        assertTrue(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
        assertEquals(16f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        assertEquals(4f, fixture.entity.getGlobalAbilityValues().get("_ABILITY_SkirkNew_ElementalArt_TimerFreeze_GV"));
        assertEquals(2f, fixture.entity.getGlobalAbilityValues().get("_ABILITY_SkirkNew_Pickable_CD"));
        assertEquals(1, fixture.entity.getInstancedAbilities().size());
    }
    @Test void globalWorldInitializationKeepsItsDynamicSceneSemantics() throws Exception {
        var worldEntity = fixture.scene.getWorld().getEntity();
        var global = new Ability(data("Lifecycle_World"), worldEntity, fixture.player);
        try (var workers = new Workers()) {
            global.getManager().executeAction(global, increment("global"), ByteString.EMPTY, worldEntity);
            fixture.player.setScene(null);
        }
        assertEquals(1f, worldEntity.getGlobalAbilityValues().get("global"));
    }
    @Test void removalBeforeFirstAppearanceAlsoInvalidatesQueuedWork() throws Exception {
        var staged = new AbilityTestFixture.TestEntity(fixture.scene);
        staged.setId(43);
        try (var workers = new Workers()) {
            queue(staged);
            fixture.scene.removeEntity(staged);
            fixture.scene.addEntity(staged);
        }
        assertFalse(staged.getGlobalAbilityValues().containsKey("queued"));
    }
    @Test void synchronousTriggerRejectsAStaleEntityBeforeInstallingAnAbility() {
        var child = data("Lifecycle_RemovedTrigger");
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        try {
            fixture.scene.removeEntity(fixture.entity);
            assertFalse(ability.getManager().triggerAbility(fixture.entity, child.abilityName));
            assertTrue(fixture.entity.getInstancedAbilities().isEmpty());
        } finally {
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
    @Test void unknownTriggersDoNotReportSuccessOrInstallAnInstance() {
        var trigger = JsonUtils.decode("{\"$type\":\"TriggerAbility\",\"abilityName\":\"Lifecycle_NotInResources\"}", AbilityModifierAction.class);
        assertFalse(new ActionTriggerAbility().execute(ability, trigger, ByteString.EMPTY, fixture.entity));
        assertTrue(fixture.entity.getInstancedAbilities().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Map<AbilityModifierAction.Type, AbilityActionHandler> handlers() throws Exception {
        var field = AbilityManager.class.getDeclaredField("actionHandlers");
        field.setAccessible(true);
        return (Map<AbilityModifierAction.Type, AbilityActionHandler>) field.get(null);
    }

    @Test void triggerInitializationCallbacksRunOutsideTheInstanceListMonitor() throws Exception {
        var child = JsonUtils.decode("""
                {"abilityName":"Lifecycle_CallbackLock","onAdded":[{"$type":"AddGlobalValue","key":"lock","value":1}]}
                """, AbilityData.class);
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        var observed = new AtomicBoolean();
        var called = new AtomicBoolean();
        var handlers = handlers();
        var oldHandler = handlers.put(AbilityModifierAction.Type.AddGlobalValue,
                new AbilityActionHandler() {
                    @Override public boolean execute(Ability current, AbilityModifierAction action,
                            ByteString raw, GameEntity target) {
                        called.set(true);
                        observed.set(Thread.holdsLock(target.getInstancedAbilities()));
                        return true;
                    }
                });
        try {
            assertTrue(ability.getManager().triggerAbility(fixture.entity, child.abilityName));
            assertTrue(called.get());
            assertFalse(observed.get(), "Callbacks must not keep the instance registry monitor");
        } finally {
            handlers.put(AbilityModifierAction.Type.AddGlobalValue, oldHandler);
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }

    @Test void aConcurrentStartWaitsForInitializationWithoutBlockingTheCaller() throws Exception {
        var child = JsonUtils.decode("""
                {"abilityName":"Lifecycle_PendingStart","onAdded":[{"$type":"AddGlobalValue","key":"initialize","value":1}],
                "onAbilityStart":[{"$type":"AddGlobalValue","key":"afterInitialization","value":1}]}
                """, AbilityData.class);
        var previous = GameData.getAbilityDataMap().put(child.abilityName, child);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contexts = ConcurrentHashMap.<String>newKeySet();
        var handlers = handlers();
        var oldHandler = handlers.get(AbilityModifierAction.Type.AddGlobalValue);
        handlers.put(AbilityModifierAction.Type.AddGlobalValue, new AbilityActionHandler() {
            @Override public boolean execute(Ability current, AbilityModifierAction action,
                    ByteString raw, GameEntity target) {
                if (action.key.equals("initialize")) {
                    entered.countDown();
                    try { assertTrue(release.await(4, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                }
                if (action.key.equals("afterInitialization"))
                    contexts.add(AbilityManager.isServerOwnedChain() + ":" + AbilityHealContext.currentTag());
                return oldHandler.execute(current, action, raw, target);
            }
        });
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> ability.getManager().triggerAbility(fixture.entity, child.abilityName));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second = workers.submit(() -> {
                var accepted = new AtomicBoolean();
                AbilityManager.runServerOwned(() -> AbilityHealContext.run("Impale", () ->
                        accepted.set(ability.getManager().triggerAbility(fixture.entity, child.abilityName))));
                return accepted.get();
            });
            assertTrue(second.get(1, TimeUnit.SECONDS));
            assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("afterInitialization"));
            release.countDown();
            assertTrue(first.get(2, TimeUnit.SECONDS));
            assertEquals(2f, fixture.entity.getGlobalAbilityValues().get("afterInitialization"));
            assertEquals(Set.of("false:null", "true:Impale"), contexts);
            assertEquals(1, fixture.entity.getInstancedAbilities().size());
        } finally {
            release.countDown();
            workers.shutdown();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
            handlers.put(AbilityModifierAction.Type.AddGlobalValue, oldHandler);
            if (previous == null) GameData.getAbilityDataMap().remove(child.abilityName);
            else GameData.getAbilityDataMap().put(child.abilityName, previous);
        }
    }
}

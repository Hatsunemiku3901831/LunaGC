package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class AbilityReactionMixinTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private AbilityTestFixture.TestEntity target;

    @BeforeEach void setup() throws Exception {
        fixture = new AbilityTestFixture();
        target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        fixture.scene.addEntity(target);
    }

    @AfterEach void close() { fixture.close(); }

    private void install(String mixin) {
        var data = JsonUtils.decode("{\"abilityName\":\"Reaction_Callback_Test\",\"abilityMixins\":["
                + mixin + "]}", AbilityData.class);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    private void invoke(int entityId, int targetId, int modifierId, int localId) {
        fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(entityId)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(1)
                        .setInstancedModifierId(modifierId).setLocalId(localId).setTargetId(targetId))
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_DO_ACTION_BY_ELEMENT_REACTION)
                .build());
    }

    private void drain() throws Exception {
        var barrier = new CyclicBarrier(4);
        var tasks = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++) tasks.add(AbilityManager.eventExecutor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return null;
        }));
        for (var task : tasks) task.get(6, TimeUnit.SECONDS);
    }

    @Test void reactionCallbackUsesTheRequestedEntityAndKeepsActionOrder() throws Exception {
        install("""
                {"$type":"DoActionByElementReactionMixin","actions":[
                 {"$type":"SetGlobalValue","key":"ordered","value":1},
                 {"$type":"AddGlobalValue","key":"ordered","value":2},
                 {"$type":"Predicated","targetPredicates":[{"$type":"ByTargetGlobalValue",
                    "key":"ordered","value":3}],"successActions":[
                    {"$type":"SetGlobalValue","key":"complete","value":1}]}]}
                """);
        invoke(42, 43, 0, 2);
        drain();
        assertEquals(3f, target.getGlobalAbilityValues().get("ordered"));
        assertEquals(1f, target.getGlobalAbilityValues().get("complete"));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("ordered"));
    }

    @Test void existingMixinAlsoRetainsTheInvocationTarget() throws Exception {
        install("{\"$type\":\"ChangeNyxValueMixin\",\"value\":1}");
        invoke(42, 43, 0, 2);
        drain();
        assertEquals(1f, target.getNyxValue());
        assertEquals(0f, fixture.entity.getNyxValue());
    }

    @Test void selfReactionAndGlobalValueCallbacksUseTheSameOrderedQueue() throws Exception {
        for (String type : List.of("DoActionBySelfElementReactionMixin", "DoActionOnGlobalValueChangeMixin")) {
            install("{\"$type\":\"" + type + "\",\"actionQueue\":[null,"
                    + "{\"$type\":\"SetGlobalValue\",\"key\":\"fallback\",\"value\":1},"
                    + "{\"$type\":\"AddGlobalValue\",\"key\":\"fallback\",\"value\":2}]}");
            fixture.player.getAbilityManager().executeMixin(ability, ability.getData().abilityMixins[0], ByteString.EMPTY);
            drain();
            assertEquals(3f, fixture.entity.getGlobalAbilityValues().get("fallback"), type);
        }
    }

    @Test void primaryQueueTakesPrecedenceAndAnEmptyQueueFallsBack() throws Exception {
        install("""
                {"$type":"DoActionByElementReactionMixin","actions":[
                  {"$type":"AddGlobalValue","key":"primary","value":1}],"actionQueue":[
                  {"$type":"AddGlobalValue","key":"fallback","value":1}]}
                """);
        invoke(42, 0, 0, 2);
        drain();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("primary"));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("fallback"));
        ability.getData().abilityMixins[0].actions = new AbilityModifier.AbilityModifierAction[0];
        invoke(42, 0, 0, 2);
        drain();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("fallback"));
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("primary"));
    }

    @Test void callbacksResolveSelfCasterAndTeamWithoutChangingTheOtherEntities() throws Exception {
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.getTeamManager().setEntity(new emu.grasscutter.game.entity.EntityTeam(fixture.player));
        install("""
                {"$type":"DoActionByElementReactionMixin","actions":[
                  {"$type":"SetGlobalValue","target":"Self","key":"self","value":1},
                  {"$type":"SetGlobalValue","target":"Caster","key":"caster","value":2},
                  {"$type":"SetGlobalValue","target":"Team","key":"team","value":3}]}
                """);
        invoke(42, 43, 0, 2);
        drain();
        assertEquals(Map.of("self", 1f), target.getGlobalAbilityValues());
        assertEquals(Map.of("caster", 2f), fixture.entity.getGlobalAbilityValues());
        assertEquals(3f, fixture.player.getTeamManager().getEntity().getGlobalAbilityValues().get("team"));
    }

    @Test void callbackAndActionPredicatesAreEvaluatedBeforeWriting() throws Exception {
        install("""
                {"$type":"DoActionByElementReactionMixin","predicates":[
                  {"$type":"ByTargetGlobalValue","target":"Self","key":"enabled","value":1}],
                 "actions":[{"$type":"SetGlobalValue","key":"denied","value":1,"predicates":[
                    {"$type":"ByTargetGlobalValue","target":"Self","key":"never","value":1}]},
                  {"$type":"SetGlobalValue","key":"allowed","value":1}]}
                """);
        invoke(42, 43, 0, 2);
        drain();
        assertTrue(target.getGlobalAbilityValues().isEmpty());
        target.getGlobalAbilityValues().put("enabled", 1f);
        invoke(42, 43, 0, 2);
        drain();
        assertEquals(1f, target.getGlobalAbilityValues().get("allowed"));
        assertFalse(target.getGlobalAbilityValues().containsKey("denied"));
    }

    private Ability load(String file) throws Exception {
        try (var input = getClass().getResourceAsStream("/ability/" + file)) {
            var data = JsonUtils.decode(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), AbilityData.class);
            return new Ability(data, fixture.entity, fixture.player);
        }
    }

    private int attach(Ability instance, String name, int instanceId) {
        var modifier = new AbilityModifierController(instance, instance.getData(), instance.getData().modifiers.get(name));
        target.getInstancedModifiers().put(instanceId, modifier);
        instance.registerModifier(target, modifier);
        int index = new ArrayList<>(instance.getData().modifiers.keySet()).indexOf(name);
        return 4 + (index << 3);
    }

    @Test void actualNahidaSelfReactionInstallsDefenseDownOnTheModifierEntity() throws Exception {
        ability = load("nahida-reaction.json");
        int localId = attach(ability, "UNIQUE_Avatar_Nahida_Constellation_2_OnMonster", 71);
        assertEquals(List.of("Overdose", "OverdoseElectric", "OverdoseGrass"),
                ability.getData().localIdToMixin.get(localId).rawReactionTypes);
        assertFalse(ability.getModifiers(target).containsKey("UNIQUE_Avatar_Nahida_Constellation_2_DefenseDown"));
        invoke(43, 0, 71, localId);
        drain();
        assertTrue(ability.getModifiers(target).containsKey("UNIQUE_Avatar_Nahida_Constellation_2_DefenseDown"));
        assertFalse(ability.getModifiers().containsKey("UNIQUE_Avatar_Nahida_Constellation_2_DefenseDown"));
    }

    @Test void actualLaumaCallbackInstallsTheHealingModifierOnItsCaster() throws Exception {
        ability = load("lauma-reaction.json");
        int localId = attach(ability, "UNIQUE_Avatar_Lauma_Constellation_1_AvatarHandler", 72);
        assertEquals(List.of("MoonOvergrow"), ability.getData().localIdToMixin.get(localId).reactionTypes);
        invoke(43, 0, 72, localId);
        drain();
        assertTrue(ability.getModifiers().containsKey("UNIQUE_Avatar_Lauma_Constellation_1_HealHP"));
        assertFalse(ability.getModifiers(target).containsKey("UNIQUE_Avatar_Lauma_Constellation_1_HealHP"));
    }

    private static class Workers implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Future<?>> blockers = new ArrayList<>();
        Workers() throws Exception {
            var started = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) blockers.add(AbilityManager.eventExecutor.submit(() -> {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return null;
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

    @Test void queuedCallbackIsDiscardedWhenItsTargetIsRemovedAndReadded() throws Exception {
        install("{\"$type\":\"DoActionByElementReactionMixin\",\"actions\":["
                + "{\"$type\":\"SetGlobalValue\",\"key\":\"late\",\"value\":1}]}");
        try (var workers = new Workers()) {
            invoke(42, 43, 0, 2);
            fixture.scene.removeEntity(target);
            fixture.scene.addEntity(target);
        }
        assertFalse(target.getGlobalAbilityValues().containsKey("late"));
        invoke(42, 43, 0, 2);
        drain();
        assertEquals(1f, target.getGlobalAbilityValues().get("late"));
    }

    @Test void laterActionsStopIfAnEarlierActionRemovesTheTarget() throws Exception {
        install("""
                {"$type":"DoActionByElementReactionMixin","actions":[
                  {"$type":"SetGlobalValue","key":"first","value":1},
                  {"$type":"SetGlobalValue","key":"late","value":1}]}
                """);
        var removed = new AbilityTestFixture.TestEntity(fixture.scene) {
            @Override public void onAbilityValueUpdate() { fixture.scene.removeEntity(this); }
        };
        removed.setId(44);
        fixture.scene.addEntity(removed);
        invoke(42, 44, 0, 2);
        drain();
        assertEquals(1f, removed.getGlobalAbilityValues().get("first"));
        assertFalse(removed.getGlobalAbilityValues().containsKey("late"));
    }

    @Test void serverOwnedSequencesRetainTheStateOnlyRestriction() {
        install("""
                {"$type":"DoActionByElementReactionMixin","actions":[
                  {"$type":"SetGlobalValue","key":"state","value":1},
                  {"$type":"HealHP","amount":20}]}
                """);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        AbilityManager.runServerOwned(() -> assertTrue(ability.getManager().executeMixinActionsNow(
                ability, ability.getData().abilityMixins[0], ByteString.EMPTY, target)));
        assertEquals(1f, target.getGlobalAbilityValues().get("state"));
        assertEquals(50f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test void absentCallbacksAreNoOpsAndMalformedInputsDoNotThrow() {
        install("{\"$type\":\"DoActionByElementReactionMixin\"}");
        var mixin = ability.getData().abilityMixins[0];
        assertTrue(ability.getManager().executeMixinActionsNow(ability, mixin, ByteString.EMPTY, target));
        assertFalse(ability.getManager().executeMixinActionsNow(null, mixin, ByteString.EMPTY, target));
        assertFalse(ability.getManager().executeMixinActionsNow(ability, null, ByteString.EMPTY, target));
        assertFalse(ability.getManager().executeMixinActionsNow(ability, mixin, ByteString.EMPTY, null));
        assertDoesNotThrow(() -> ability.getManager().executeMixin(null, mixin, ByteString.EMPTY));
    }

    @Test void distinctReactionListsSurviveEitherJsonFieldOrderAndSerialization() {
        for (String fields : List.of(
                "\"reactionTypes\":[\"Shock\"],\"IGAMNNAADJB\":[\"Shock\",\"MoonShock\"]",
                "\"IGAMNNAADJB\":[\"Shock\",\"MoonShock\"],\"reactionTypes\":[\"Shock\"]")) {
            var mixin = JsonUtils.decode("{\"$type\":\"DoActionByElementReactionMixin\"," + fields + "}", AbilityMixinData.class);
            assertEquals(List.of("Shock"), mixin.reactionTypes);
            assertEquals(List.of("Shock", "MoonShock"), mixin.rawReactionTypes);
            var restored = JsonUtils.decode(JsonUtils.encode(mixin), AbilityMixinData.class);
            assertEquals(mixin.reactionTypes, restored.reactionTypes);
            assertEquals(mixin.rawReactionTypes, restored.rawReactionTypes);
        }
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

class AnimationModifierTimingTest {
    AbilityTestFixture fixture;
    AbilityData data;
    Ability ability;
    AbilityData previous;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        if (data != null) {
            if (previous == null) GameData.getAbilityDataMap().remove(data.abilityName);
            else GameData.getAbilityDataMap().put(data.abilityName, previous);
        }
        fixture.close();
    }

    String mark(int n) {
        return "{$type:'AddGlobalValue',key:'mark',value:" + n + "}";
    }

    void install(String mixin, boolean parent) {
        data =
                JsonUtils.decode(
                        "{abilityName:'AnimationTiming_Test',"
                                + (parent ? "" : "abilityMixins:[" + mixin + "],")
                                + "modifiers:{Window:{onAdded:["
                                + mark(1)
                                + "],onRemoved:["
                                + mark(-1)
                                + "]}"
                                + (parent ? ",Parent:{modifierMixins:[" + mixin + "]}" : "")
                                + "}}",
                        AbilityData.class);
        previous = GameData.getAbilityDataMap().put(data.abilityName, data);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    String mixin(String type) {
        return "{$type:'"
                + type
                + "',modifierName:'Window',target:'Self',stateIDs:['Attack01'],"
                + "stateID:'Attack01',normalizeStart:0.2,normalizeEnd:0.7,"
                + "normalizedStateIDList:[{stateID:'Attack01',normalizeStart:0.2,normalizeEnd:0.7}]}";
    }

    float value() {
        return fixture.entity.getGlobalAbilityValues().getOrDefault("mark", 0f);
    }

    void client(int id, boolean added) {
        assertDoesNotThrow(
                () ->
                        fixture.player
                                .getAbilityManager()
                                .onAbilityInvoke(
                                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                                .setEntityId(42)
                                                .setArgumentType(
                                                        AbilityInvokeArgumentOuterClass
                                                                .AbilityInvokeArgument
                                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                                .setHead(
                                                        AbilityInvokeEntryHeadOuterClass
                                                                .AbilityInvokeEntryHead.newBuilder()
                                                                .setInstancedAbilityId(1)
                                                                .setInstancedModifierId(id))
                                                .setAbilityData(
                                                        AbilityMetaModifierChangeOuterClass
                                                                .AbilityMetaModifierChange
                                                                .newBuilder()
                                                                .setModifierLocalId(
                                                                        new ArrayList<>(
                                                                                        data
                                                                                                .modifiers
                                                                                                .keySet())
                                                                                .indexOf("Window"))
                                                                .setAction(
                                                                        added
                                                                                ? ModifierActionOuterClass
                                                                                        .ModifierAction
                                                                                        .MODIFIER_ACTION_ADDED
                                                                                : ModifierActionOuterClass
                                                                                        .ModifierAction
                                                                                        .MODIFIER_ACTION_REMOVED)
                                                                .build()
                                                                .toByteString())
                                                .build()));
    }

    void drain() throws Exception {
        var gate = new CyclicBarrier(4);
        var jobs = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            jobs.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                gate.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var job : jobs) job.get(6, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "AttachToStateIDMixin",
                "AttachToNormalizedTimeMixin",
                "AttachToMultiNormalizedTimeMixin"
            })
    void entryAndExitUseClientTimingWithoutEarlyAttachment(String type) {
        install(mixin(type), false);
        fixture.entity.getAbilityGlobalValueWatchers().refresh();
        assertEquals(0, value());
        assertTrue(ability.getModifiers(fixture.entity).isEmpty());
        client(101, true);
        assertEquals(1, value());
        client(101, true);
        assertEquals(1, value());
        client(101, false);
        assertEquals(0, value());
        client(101, false);
        assertEquals(0, value());
        client(102, true);
        assertEquals(1, value());
        client(102, false);
        assertEquals(0, value());
    }

    @Test
    void confirmedLifecycleActionIsNotRepeatedByClientLeafInvoke() throws Exception {
        install(mixin("AttachToNormalizedTimeMixin"), false);
        client(101, true);
        var action = data.modifiers.get("Window").onAdded[0];
        int local =
                data.localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == action)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setArgumentType(
                                        AbilityInvokeArgumentOuterClass.AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_ACTION_EFFECT)
                                .setHead(
                                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead
                                                .newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(101)
                                                .setLocalId(local))
                                .build());
        drain();
        assertEquals(1, value());
        client(101, false);
        assertEquals(0, value());
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setArgumentType(
                                        AbilityInvokeArgumentOuterClass.AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_ACTION_EFFECT)
                                .setHead(
                                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead
                                                .newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(101)
                                                .setLocalId(local))
                                .build());
        drain();
        assertEquals(0, value());
    }

    @Test
    void parentRemovalCleansConfirmedWindow() {
        install(mixin("AttachToStateIDMixin"), true);
        var parent = new AbilityModifierController(ability, data, data.modifiers.get("Parent"));
        ability.registerModifier(fixture.entity, parent);
        client(101, true);
        assertEquals(1, value());
        ability.removeModifier(fixture.entity, "Parent");
        assertEquals(0, value());
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(101));
    }

    @Test
    void abilityRemovalCleansConfirmedWindow() {
        install(mixin("AttachToNormalizedTimeMixin"), false);
        client(101, true);
        assertEquals(1, value());
        ability.removeInstalledModifiers();
        assertEquals(0, value());
    }

    @Test
    void separateInstancesHaveSeparateEntryAndExitEffects() {
        install(mixin("AttachToNormalizedTimeMixin"), false);
        client(101, true);
        client(102, true);
        assertEquals(2, value());
        client(101, false);
        assertEquals(1, value());
        client(102, false);
        assertEquals(0, value());
    }

    @Test
    void removingAnUnrelatedParentDoesNotRemoveTheWindow() {
        install(mixin("AttachToStateIDMixin"), true);
        var first = new AbilityModifierController(ability, data, data.modifiers.get("Parent"));
        var second = new AbilityModifierController(ability, data, data.modifiers.get("Parent"));
        fixture.entity.getInstancedModifiers().put(201, first);
        ability.registerModifier(fixture.entity, first);
        fixture.entity.getInstancedModifiers().put(202, second);
        ability.registerModifier(fixture.entity, second);
        client(101, true);
        assertEquals(1, value());
        fixture.entity.getInstancedModifiers().remove(201);
        ability.unregisterModifier(fixture.entity, first);
        assertEquals(1, value());
        fixture.entity.getInstancedModifiers().remove(202);
        ability.unregisterModifier(fixture.entity, second);
        assertEquals(0, value());
    }

    @Test
    void resourceDiveStreamTracksEnteredAndExitedTeamState() throws Exception {
        var path =
                java.nio.file.Path.of(
                        "resources/BinOutput/Ability/Temp/ConfigAbility_Activity_Absorb_Shoot.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        var rows =
                com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                        .getAsJsonArray();
        for (var row : rows) {
            var ab = row.getAsJsonObject().getAsJsonObject("Default");
            if (!"Ability_Avatar_Dive_Team".equals(ab.get("abilityName").getAsString())) continue;
            var mods = ab.getAsJsonObject("modifiers");
            String child = "Avatar_Diving_DiveStream_Mark_Handler";
            var parent = mods.getAsJsonObject("UNIQUE_Avatar_Diving_Common_Handler");
            String config =
                    java.util.stream.StreamSupport.stream(
                                    parent.getAsJsonArray("modifierMixins").spliterator(), false)
                            .filter(
                                    m ->
                                            m.getAsJsonObject().has("modifierName")
                                                    && child.equals(
                                                            m.getAsJsonObject()
                                                                    .get("modifierName")
                                                                    .getAsString()))
                            .findFirst()
                            .orElseThrow()
                            .toString();
            install(config.replace(child, "Window"), false);
            data.modifiers.get("Window").onAdded =
                    JsonUtils.decode(
                            mods.getAsJsonObject(child).get("onAdded"),
                            AbilityModifier.AbilityModifierAction[].class);
            data.modifiers.get("Window").onRemoved =
                    JsonUtils.decode(
                            mods.getAsJsonObject(child).get("onRemoved"),
                            AbilityModifier.AbilityModifierAction[].class);
            for (var actions :
                    List.of(
                            data.modifiers.get("Window").onAdded,
                            data.modifiers.get("Window").onRemoved))
                for (var action : actions) if ("Team".equals(action.target)) action.target = "Self";
            client(101, true);
            assertEquals(
                    4,
                    fixture.entity
                            .getGlobalAbilityValues()
                            .get("_ABILITY_WhiteBox_Dive_Team_Trigger"));
            client(101, true);
            assertEquals(
                    4,
                    fixture.entity
                            .getGlobalAbilityValues()
                            .get("_ABILITY_WhiteBox_Dive_Team_Trigger"));
            client(101, false);
            assertEquals(
                    5,
                    fixture.entity
                            .getGlobalAbilityValues()
                            .get("_ABILITY_WhiteBox_Dive_Team_Trigger"));
            return;
        }
        fail("Current dive animation resource not found");
    }

    @Test
    void unsupportedCallbacksNeverApplyDamageOrHealing() {
        install(mixin("AttachToNormalizedTimeMixin"), false);
        fixture.entity.setFightProperty(
                emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP, 50);
        data.modifiers.get("Window").onAdded =
                JsonUtils.decode(
                        "[{$type:'HealHP',amount:50}," + mark(1) + "]",
                        AbilityModifier.AbilityModifierAction[].class);
        client(101, true);
        assertEquals(1, value());
        assertEquals(
                50,
                fixture.entity.getFightProperty(
                        emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void resourceWindowFieldsSurviveRoundTrip() throws Exception {
        var raw =
                JsonUtils.decode(mixin("AttachToMultiNormalizedTimeMixin"), AbilityMixinData.class);
        var restored = JsonUtils.decode(JsonUtils.encode(raw), AbilityMixinData.class);
        var states =
                (List<?>) AbilityMixinData.class.getField("normalizedStateIDList").get(restored);
        assertEquals(1, states.size());
        assertEquals("Attack01", states.get(0).getClass().getField("stateID").get(states.get(0)));
        assertEquals(0.2f, states.get(0).getClass().getField("normalizeStart").get(states.get(0)));
        assertEquals(0.7f, states.get(0).getClass().getField("normalizeEnd").get(states.get(0)));
    }

    @Test
    void invalidAnimationWindowDoesNotAcquireCallbacks() {
        install(
                "{$type:'AttachToNormalizedTimeMixin',modifierName:'Window',stateID:'Attack01',normalizeStart:0.8,normalizeEnd:0.2}",
                false);
        client(101, true);
        assertEquals(0, value());
    }

    @Test
    void supportedCallbacksRunBesideVisualActions() {
        install(mixin("AttachToMultiNormalizedTimeMixin"), false);
        data.modifiers.get("Window").onAdded =
                JsonUtils.decode(
                        "[{$type:'FireEffect',effectPattern:'visual'}," + mark(1) + "]",
                        AbilityModifier.AbilityModifierAction[].class);
        data.initialize();
        client(101, true);
        assertEquals(1, value());
        client(101, false);
        assertEquals(0, value());
    }
}

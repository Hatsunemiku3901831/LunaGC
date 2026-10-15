package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

class AbilityGlobalValueDispatchTest {
    AbilityTestFixture fixture;
    AbilityTestFixture.TestEntity bearer;
    Ability ability;
    final AtomicLong now = new AtomicLong();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        bearer =
                new AbilityTestFixture.TestEntity(fixture.scene) {
                    final AbilityPeriodicHealing periodic =
                            new AbilityPeriodicHealing(this, now::get);

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return periodic;
                    }
                };
        bearer.setId(43);
        fixture.scene.getEntities().put(43, bearer);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        configure("{}");
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    void configure(String modifiers) {
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:GlobalDispatch,modifiers:" + modifiers + "}",
                                AbilityData.class),
                        fixture.entity,
                        fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        bearer.getInstancedAbilities().add(ability);
    }

    AbilityModifierAction dispatch(String extra) {
        return JsonUtils.decode(
                "{$type:DoActionByGlobalValue,valueSteps:[0.5,1.5,2.5],"
                    + "FKPKBNOINEG:{$type:AFKDGJBEGHN,name:selector},"
                    + "NGHBADANOHL:[[{\"$type\":AddGlobalValue,key:first,value:1}],[{\"$type\":AddGlobalValue,key:second,value:1}]],"
                    + "IAMAGJDLMAI:[{\"$type\":AddGlobalValue,key:fallback,value:1}]"
                        + extra
                        + "}",
                AbilityModifierAction.class);
    }

    void run(AbilityModifierAction action) {
        ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, bearer);
    }

    float value(GameEntity entity, String key) {
        return entity.getGlobalAbilityValues().getOrDefault(key, 0f);
    }

    @ParameterizedTest
    @CsvSource({
        "0.49,fallback",
        "0.5,first",
        "1.49,first",
        "1.5,second",
        "2.49,second",
        "2.5,fallback"
    })
    void halfOpenIntervalsUseTheOwnerGlobalValue(float selected, String key) {
        fixture.entity.getGlobalAbilityValues().put("selector", selected);
        bearer.getGlobalAbilityValues().put("selector", 999f);
        run(dispatch(""));
        assertEquals(1, value(bearer, key));
        assertEquals(
                1, value(bearer, "first") + value(bearer, "second") + value(bearer, "fallback"));
        assertEquals(0, value(fixture.entity, key));
    }

    @Test
    void missingKeysAndMissingGroupIndexesUseFallbackButEmptyGroupsDoNot() {
        run(dispatch(""));
        assertEquals(1, value(bearer, "fallback"));
        fixture.entity.getGlobalAbilityValues().put("selector", 2f);
        run(dispatch(",NGHBADANOHL:[[]]"));
        assertEquals(2, value(bearer, "fallback"));
        run(dispatch(",NGHBADANOHL:[[],[]]"));
        assertEquals(2, value(bearer, "fallback"));
    }

    @Test
    void fewerThanTwoBoundsUsesFallbackWithoutResolvingTheKeyProvider() {
        run(dispatch(",valueSteps:[],FKPKBNOINEG:{$type:Unsupported}"));
        assertEquals(1, value(bearer, "fallback"));
    }

    @Test
    void overlappingIntervalsChooseTheFirstMatchingAdjacentPair() {
        fixture.entity.getGlobalAbilityValues().put("selector", 1.5f);
        var action =
                dispatch(
                        ",valueSteps:[0,2,1,3],NGHBADANOHL:[[{\"$type\":AddGlobalValue,key:first,value:1}],[],[{\"$type\":AddGlobalValue,key:second,value:1}]]");
        run(action);
        assertEquals(1, value(bearer, "first"));
        assertEquals(0, value(bearer, "second"));
    }

    @ParameterizedTest
    @CsvSource({"2.9,2", "-2.9,-2", "0,0"})
    void dynamicKeyProvidersTruncateTheOwnerCounterBeforeAppending(float input, int suffix) {
        fixture.entity.getGlobalAbilityValues().put("shot", input);
        fixture.entity.getGlobalAbilityValues().put("ammo_" + suffix, 2f);
        bearer.getGlobalAbilityValues().put("shot", 77f);
        run(dispatch(",FKPKBNOINEG:{$type:OOMODMGBDEP,HNEGOANIAHN:shot,JLECIDOAMFG:ammo_}"));
        assertEquals(1, value(bearer, "second"));
    }

    @Test
    void dynamicKeyProvidersUseZeroForAMissingCounter() {
        fixture.entity.getGlobalAbilityValues().put("ammo_0", 1f);
        run(dispatch(",FKPKBNOINEG:{$type:OOMODMGBDEP,HNEGOANIAHN:shot,JLECIDOAMFG:ammo_}"));
        assertEquals(1, value(bearer, "first"));
    }

    @Test
    void childActionsPreserveOrderAndCasterVersusSelfTargets() {
        fixture.entity.getGlobalAbilityValues().put("selector", 1f);
        run(
                dispatch(
                        ",NGHBADANOHL:[[{\"$type\":SetOverrideMapValue,overrideMapKey:amount,value:10},"
                            + "{\"$type\":HealHP,amount:amount},{\"$type\":AddGlobalValue,target:Caster,key:owner,value:1}]]"));
        assertEquals(110, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(1, value(fixture.entity, "owner"));
        assertEquals(0, value(bearer, "owner"));
    }

    @Test
    void parentConditionsAndSourceBoundExpressionsAreApplied() {
        fixture.entity.getGlobalAbilityValues().put("selector", 1f);
        run(dispatch(",predicates:[{$type:ByUnlockTalentParam,talentParam:Locked}]"));
        assertEquals(0, value(bearer, "first"));
        ability.getAbilitySpecials().put("low", 0.5f);
        ability.getAbilitySpecials().put("high", 1.5f);
        bearer.getGlobalAbilityValues().put("low", 99f);
        run(dispatch(",valueSteps:[low,high]"));
        assertEquals(1, value(bearer, "first"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider", "key", "bound", "value", "counter", "counterOverflow"})
    void invalidProvidersBoundsAndValuesDoNotRunFallbackActions(String invalid) {
        fixture.entity.getGlobalAbilityValues().put("selector", 1f);
        var action = dispatch("");
        switch (invalid) {
            case "provider" -> action.globalValueProvider =
                    JsonParser.parseString("{$type:Unsupported}");
            case "key" -> action.globalValueProvider =
                    JsonParser.parseString("{$type:AFKDGJBEGHN,name:123}");
            case "bound" -> action.valueSteps[0] = null;
            case "value" -> fixture.entity.getGlobalAbilityValues().put("selector", Float.NaN);
            default -> {
                action.globalValueProvider =
                        JsonParser.parseString(
                                "{$type:OOMODMGBDEP,HNEGOANIAHN:shot,JLECIDOAMFG:ammo_}");
                fixture.entity
                        .getGlobalAbilityValues()
                        .put("shot", invalid.equals("counter") ? Float.NaN : 2147483648f);
            }
        }
        run(action);
        assertEquals(
                0, value(bearer, "first") + value(bearer, "second") + value(bearer, "fallback"));
    }

    static Stream<String> resourceActions() throws Exception {
        var result = new ArrayList<String>();
        try (var files = Files.walk(Path.of("resources/BinOutput/Ability"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList())
                collect(JsonParser.parseString(Files.readString(path)), result);
        }
        assertEquals(29, result.size());
        return result.stream();
    }

    static void collect(JsonElement value, List<String> found) {
        if (value.isJsonArray()) for (var child : value.getAsJsonArray()) collect(child, found);
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type")
                    && object.get("$type").getAsString().equals("DoActionByGlobalValue"))
                found.add(object.toString());
            for (var entry : object.entrySet()) collect(entry.getValue(), found);
        }
    }

    @ParameterizedTest
    @MethodSource("resourceActions")
    void everyCurrentResourceSelectsAllItsRealGroups(String json) {
        var action = JsonUtils.decode(json, AbilityModifierAction.class);
        var raw = JsonParser.parseString(json).getAsJsonObject();
        var provider = raw.getAsJsonObject("FKPKBNOINEG");
        String key;
        if (provider.get("$type").getAsString().equals("AFKDGJBEGHN"))
            key = provider.get("name").getAsString();
        else {
            fixture.entity
                    .getGlobalAbilityValues()
                    .put(provider.get("HNEGOANIAHN").getAsString(), 2.9f);
            key = provider.get("JLECIDOAMFG").getAsString() + "2";
        }
        var bounds = raw.getAsJsonArray("valueSteps");
        for (int i = 0; i < action.actionSteps.length; i++) {
            float midpoint = (bounds.get(i).getAsFloat() + bounds.get(i + 1).getAsFloat()) / 2f;
            fixture.entity.getGlobalAbilityValues().put(key, midpoint);
            assertSame(
                    action.actionSteps[i],
                    AbilityGlobalValueDispatch.choose(ability, action, bearer).orElseThrow());
        }
    }

    @Test
    void nestedLocalIdsVisitGroupsThenFallbackThenTheFollowingRootAction() {
        var root = dispatch("");
        var data = new AbilityData();
        data.abilityName = "DispatchIds";
        data.onAdded =
                new AbilityModifierAction[] {
                    root,
                    JsonUtils.decode(
                            "{$type:AddGlobalValue,key:next,value:1}", AbilityModifierAction.class)
                };
        data.initialize();
        assertSame(root, data.localIdToAction.get(513));
        assertSame(root.actionSteps[0][0], data.localIdToAction.get(1025));
        assertSame(root.actionSteps[1][0], data.localIdToAction.get(1537));
        assertSame(root.defaultActions[0], data.localIdToAction.get(2049));
        assertSame(data.onAdded[1], data.localIdToAction.get(2561));
    }

    @Test
    void serverPeriodicDispatchOwnsAllBranchesAndReevaluatesTheCurrentGlobalValue() {
        configure(
                "{Pulse:{thinkInterval:1,onThinkInterval:[{$type:DoActionByGlobalValue,valueSteps:[0.5,1.5,2.5],"
                    + "FKPKBNOINEG:{$type:AFKDGJBEGHN,name:selector},NGHBADANOHL:["
                    + "[{$type:HealHP,amount:10}],[{$type:HealHP,amount:20}]]}]}}");
        var controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Pulse"));
        ability.registerModifier(bearer, controller);
        bearer.getInstancedModifiers().put(91, controller);
        fixture.entity.getGlobalAbilityValues().put("selector", 1f);
        now.set(1_000_000_000L);
        bearer.onTick(0);
        assertEquals(110, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        for (var entry : ability.getData().localIdToAction.entrySet()) {
            assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability, 0, entry.getValue()));
            for (int modifierId : new int[] {0, 91})
                ability.getManager()
                        .handleServerInvoke(
                                AbilityInvokeEntry.newBuilder()
                                        .setEntityId(43)
                                        .setHead(
                                                AbilityInvokeEntryHead.newBuilder()
                                                        .setInstancedAbilityId(2)
                                                        .setInstancedModifierId(modifierId)
                                                        .setTargetId(43)
                                                        .setLocalId(entry.getKey()))
                                        .build());
        }
        assertEquals(110, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        fixture.entity.getGlobalAbilityValues().put("selector", 2f);
        now.set(2_000_000_000L);
        bearer.onTick(0);
        assertEquals(130, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
}

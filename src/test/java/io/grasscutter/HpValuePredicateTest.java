package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class HpValuePredicateTest {
    AbilityTestFixture fixture;
    Ability ability;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'HpValue_Test',abilitySpecials:{limit:2}}",
                                AbilityData.class),
                        fixture.entity,
                        fixture.player);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        hp(fixture.entity, 2);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    void hp(GameEntity entity, float value) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, value);
    }

    boolean check(Object threshold, String logic, GameEntity target) {
        var predicate = new HashMap<String, Object>();
        predicate.put("$type", "MIJGFBNEIOG");
        predicate.put("HP", threshold);
        if (logic != null) predicate.put("logic", logic);
        return evaluate(predicate, target);
    }

    boolean evaluate(Map<String, Object> predicate, GameEntity target) {
        return PredicateEvaluator.evaluate(predicate, ability, fixture.entity, target, null);
    }

    EntityAvatar casterAbility() {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public void save() {}
                };
        var caster =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(caster.getId(), caster);
        ability = new Ability(ability.getData(), caster, fixture.player);
        hp(caster, 2);
        assertSame(caster, ability.getCasterEntity());
        return caster;
    }

    @Test
    void strictLesserExcludesTheBoundaryAndHigherHp() {
        assertFalse(check(2, "Lesser", fixture.entity));
        hp(fixture.entity, 3);
        assertFalse(check(2, "Lesser", fixture.entity));
        hp(fixture.entity, 1);
        assertTrue(check(2, "Lesser", fixture.entity));
    }

    @Test
    void defaultComparisonIsStrictGreater() {
        assertFalse(check(2, null, fixture.entity));
        hp(fixture.entity, 1);
        assertFalse(check(2, null, fixture.entity));
        hp(fixture.entity, 3);
        assertTrue(check(2, null, fixture.entity));
    }

    @Test
    void greaterOrEqualIncludesOnlyTheMatchingSide() {
        assertTrue(check(2, "GreaterOrEqual", fixture.entity));
        hp(fixture.entity, 1);
        assertFalse(check(2, "GreaterOrEqual", fixture.entity));
    }

    @Test
    void lesserOrEqualIncludesOnlyTheMatchingSide() {
        assertTrue(check(2, "LesserOrEqual", fixture.entity));
        hp(fixture.entity, 3);
        assertFalse(check(2, "LesserOrEqual", fixture.entity));
    }

    @Test
    void equalityIsExact() {
        assertTrue(check(2, "Equal", fixture.entity));
        hp(fixture.entity, 2.01f);
        assertFalse(check(2, "Equal", fixture.entity));
    }

    @Test
    void absoluteHpDoesNotUseTheHealthRatio() {
        hp(fixture.entity, 50);
        assertFalse(check(2, "Lesser", fixture.entity));
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10000);
        assertFalse(check(2, "Lesser", fixture.entity));
    }

    @Test
    void absoluteHpDoesNotRequireMaximumHealth() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 0);
        assertTrue(check(2, "Equal", fixture.entity));
    }

    @Test
    void nullTargetCannotPass() {
        assertFalse(check(2, "Lesser", null));
    }

    @Test
    void aMissingThresholdIsRejected() {
        assertFalse(evaluate(Map.of("$type", "MIJGFBNEIOG", "logic", "Equal"), fixture.entity));
    }

    @Test
    void aNullThresholdIsRejected() {
        assertFalse(check(null, "Equal", fixture.entity));
    }

    @Test
    void zeroThresholdIsValid() {
        hp(fixture.entity, 0);
        assertTrue(check(0, "Equal", fixture.entity));
        assertFalse(check(0, null, fixture.entity));
    }

    @Test
    void nonFiniteCurrentHpIsRejected() {
        for (float value :
                new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            hp(fixture.entity, value);
            assertFalse(check(2, "Lesser", fixture.entity));
        }
    }

    @Test
    void nonFiniteThresholdIsRejected() {
        for (float value :
                new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertFalse(check(value, "Lesser", fixture.entity));
    }

    @Test
    void abilitySpecialAndExpressionThresholdsAreEvaluated() {
        assertTrue(check("limit", "Equal", fixture.entity));
        assertFalse(check(List.of("limit", 2, "MUL"), "Greater", fixture.entity));
        assertTrue(check(List.of("limit", 2, "MUL"), "Lesser", fixture.entity));
    }

    @Test
    void fixedValueWrapperAndGlobalValueThresholdsAreEvaluated() {
        assertTrue(check(Map.of("__exp_FixedValue", 2), "Equal", fixture.entity));
        fixture.entity.getGlobalAbilityValues().put("threshold", 1f);
        assertFalse(check("%threshold", "Lesser", fixture.entity));
    }

    @Test
    void selectedTargetAndCasterHaveIndependentHealth() {
        casterAbility();
        var other = new AbilityTestFixture.TestEntity(fixture.scene);
        hp(other, 1);
        assertTrue(check(2, "Lesser", other));
        assertFalse(
                evaluate(
                        Map.of(
                                "$type",
                                "MIJGFBNEIOG",
                                "HP",
                                2,
                                "logic",
                                "Lesser",
                                "target",
                                "Caster"),
                        other));
        assertFalse(
                evaluate(
                        Map.of(
                                "$type",
                                "MIJGFBNEIOG",
                                "HP",
                                2,
                                "logic",
                                "Lesser",
                                "forceByCaster",
                                true),
                        other));
    }

    @Test
    void invalidComparisonDoesNotFallBackToEquality() {
        assertFalse(check(2, "Unknown", fixture.entity));
    }

    @Test
    void eventCallbackCannotRunAboveItsAbsoluteHpLimit() {
        var data =
                JsonUtils.decode(
                        """
                {abilityName:'HpValue_Callback',abilityMixins:[{$type:'DoActionByEventMixin',
                 predicates:[{$type:'MIJGFBNEIOG',HP:2,logic:'Lesser'}],
                 actions:[{$type:'SetGlobalValue',key:'called',value:1}]}]}
                """,
                        AbilityData.class);
        var callback = new Ability(data, fixture.entity, fixture.player);
        var mixin = data.abilityMixins[0];
        assertTrue(
                callback.getManager()
                        .executeMixinActionsNow(callback, mixin, ByteString.EMPTY, fixture.entity));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("called"));
        hp(fixture.entity, 1);
        assertTrue(
                callback.getManager()
                        .executeMixinActionsNow(callback, mixin, ByteString.EMPTY, fixture.entity));
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("called"));
    }

    void actual(String avatar) throws Exception {
        var caster = casterAbility();
        var source =
                JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        "/resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                + avatar
                                                + ".json")));
        var found = new ArrayList<JsonObject>();
        collect(source, found);
        assertFalse(found.isEmpty());
        for (var value : found) {
            @SuppressWarnings("unchecked")
            var predicate = (Map<String, Object>) JsonUtils.decode(value, Map.class);
            assertEquals("Lesser", predicate.get("logic"));
            assertEquals("Caster", predicate.get("target"));
            float bound = ((Number) predicate.get("HP")).floatValue();
            hp(caster, bound);
            assertFalse(evaluate(predicate, fixture.entity));
            hp(caster, bound - 0.5f);
            assertTrue(evaluate(predicate, fixture.entity));
        }
    }

    void collect(JsonElement node, List<JsonObject> found) {
        if (node.isJsonObject()) {
            var object = node.getAsJsonObject();
            if (object.has("$type") && "MIJGFBNEIOG".equals(object.get("$type").getAsString()))
                found.add(object);
            for (var entry : object.entrySet()) collect(entry.getValue(), found);
        } else if (node.isJsonArray()) {
            for (var child : node.getAsJsonArray()) collect(child, found);
        }
    }

    @Test
    void actualHutaoProtectionConditionDoesNotPassAtItsBoundary() throws Exception {
        actual("Hutao");
    }

    @Test
    void actualShinobuProtectionConditionDoesNotPassAtItsBoundary() throws Exception {
        actual("Shinobu");
    }
}

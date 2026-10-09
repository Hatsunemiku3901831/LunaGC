package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.*;

class HpDebtPredicateTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 100);
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_DebtPredicate\"}",
                AbilityData.class), fixture.entity, fixture.player);
    }

    @AfterEach
    void restore() { fixture.close(); }

    private boolean check(String json, AbilityModifierAction event) {
        @SuppressWarnings("unchecked")
        var predicate = (Map<String, Object>) JsonUtils.decode(json, Map.class);
        return PredicateEvaluator.evaluate(predicate, ability, fixture.entity, fixture.entity, event);
    }

    private AbilityModifierAction heal(String tag) {
        return JsonUtils.decode("{\"$type\":\"HealHP\",\"healTag\":\"" + tag + "\"}",
                AbilityModifierAction.class);
    }

    @Test
    void nativeDefaultIsStrictGreaterAtTheExactDebtBoundary() {
        String predicate = "{\"$type\":\"LPKOBOLMDBI\",\"value\":\"FIGHT_PROP_MAX_HP\"}";
        assertFalse(check(predicate, null));
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 101);
        assertTrue(check(predicate, null));
    }

    @Test
    void exportedGreaterOrEqualIncludesTheBoundary() {
        assertTrue(check("{\"$type\":\"LPKOBOLMDBI\",\"logic\":\"GreaterOrEqual\",\"value\":100}", null));
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 99);
        assertFalse(check("{\"$type\":\"LPKOBOLMDBI\",\"logic\":\"GreaterOrEqual\",\"value\":100}", null));
    }

    @Test
    void lesserAndEqualityComparisonsUseDebtRatherThanHealth() {
        assertTrue(check("{\"$type\":\"LPKOBOLMDBI\",\"logic\":\"Equal\",\"value\":100}", null));
        assertFalse(check("{\"$type\":\"LPKOBOLMDBI\",\"logic\":\"Lesser\",\"value\":100}", null));
        assertTrue(check("{\"$type\":\"LPKOBOLMDBI\",\"logic\":\"LesserOrEqual\",\"value\":100}", null));
    }

    @Test
    void nonFiniteDebtOrThresholdNeverPasses() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, Float.NaN);
        assertFalse(check("{\"$type\":\"LPKOBOLMDBI\",\"value\":0}", null));
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 100);
        ability.getAbilitySpecials().put("invalid", Float.POSITIVE_INFINITY);
        assertFalse(check("{\"$type\":\"LPKOBOLMDBI\",\"logic\":\"Lesser\",\"value\":\"invalid\"}", null));
    }

    @Test
    void healTagsRequireAnActualHealingEvent() {
        String predicate = "{\"$type\":\"ONJEHDDFFCH\",\"LAIFKEGOFJO\":[\"Impale\",\"Burst\"]}";
        assertTrue(check(predicate, heal("Impale")));
        assertTrue(check(predicate, heal("Burst")));
        assertFalse(check(predicate, heal("Other")));
        assertFalse(check(predicate, null));
        var wrongEvent = heal("Impale");
        wrongEvent.type = AbilityModifierAction.Type.AddHPDebts;
        assertFalse(check(predicate, wrongEvent));
    }

    @Test
    void exportedHealTagAliasIsAccepted() {
        assertTrue(check("{\"$type\":\"ONJEHDDFFCH\",\"__exp_healTags\":[\"Impale\"]}", heal("Impale")));
        assertFalse(check("{\"$type\":\"ONJEHDDFFCH\",\"__exp_healTags\":[\"Impale\"]}", heal("Other")));
    }

    @Test
    void nativeNegationMeansNoneOfTheChildrenCanPass() {
        String predicate = """
                {"$type":"GKGBIPDLMMG","predicates":[
                  {"$type":"ONJEHDDFFCH","LAIFKEGOFJO":["Impale"]},
                  {"$type":"ONJEHDDFFCH","LAIFKEGOFJO":["Burst"]}]}
                """;
        assertFalse(check(predicate, heal("Impale")));
        assertFalse(check(predicate, heal("Burst")));
        assertTrue(check(predicate, heal("Other")));
    }

    @Test
    void anEmptyNonePredicatePassesAndMalformedChildrenAreRejected() {
        assertTrue(check("{\"$type\":\"GKGBIPDLMMG\",\"predicates\":[]}", heal("Other")));
        assertFalse(check("{\"$type\":\"GKGBIPDLMMG\",\"predicates\":[17]}", heal("Other")));
    }

    @Test
    void booleanGlobalValuesCanSelectTheCasterExplicitly() {
        fixture.entity.getGlobalAbilityValues().put("flag", 1f);
        assertTrue(check("{\"$type\":\"ByTargetGlobalValue\",\"key\":\"flag\",\"value\":true}", null));
        assertFalse(check("{\"$type\":\"ByTargetGlobalValue\",\"key\":\"flag\",\"value\":false}", null));
        var other = new AbilityTestFixture.TestEntity(fixture.scene);
        @SuppressWarnings("unchecked")
        var predicate = (Map<String, Object>) JsonUtils.decode("""
                {"$type":"ByTargetGlobalValue","key":"flag","value":true,"forceByCaster":true}
                """, Map.class);
        assertTrue(PredicateEvaluator.evaluate(predicate, ability, fixture.entity, other, null));
    }
}

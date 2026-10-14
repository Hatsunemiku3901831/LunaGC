package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionReviveAvatar;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;

class AbilityDistancePredicateTest {
    AbilityTestFixture fixture;
    LocatedEntity source, target;
    Ability ability;

    static class LocatedEntity extends AbilityTestFixture.TestEntity {
        final Position position = new Position();

        LocatedEntity(Scene scene) {
            super(scene);
        }

        @Override
        public Position getPosition() {
            return position;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        source = new LocatedEntity(fixture.scene);
        source.setId(43);
        target = new LocatedEntity(fixture.scene);
        target.setId(44);
        fixture.scene.getEntities().put(43, source);
        fixture.scene.getEntities().put(44, target);
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'DistancePredicate',abilitySpecials:{Radius:5}}",
                                AbilityData.class),
                        source,
                        fixture.player);
        target.position.set(3, 0, 4);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> predicate(String extra) {
        return (Map<String, Object>)
                JsonUtils.decode("{$type:'CNFMGJJEOPM',value:5" + extra + "}", Map.class);
    }

    boolean evaluate(String extra) {
        return PredicateEvaluator.evaluate(predicate(extra), ability, source, target, null);
    }

    @ParameterizedTest
    @CsvSource({
        "Greater,false",
        "GreaterOrEqual,true",
        "Equal,true",
        "Lesser,false",
        "LesserOrEqual,true"
    })
    void allFiveComparisonRulesRespectTheirBoundary(String logic, boolean expected) {
        assertEquals(expected, evaluate(",logic:'" + logic + "'"));
    }

    @Test
    void greaterThanRejectsAnInsideTargetAndAcceptsAnOutsideTarget() {
        target.position.set(2, 0, 0);
        assertFalse(evaluate(",logic:'Greater'"));
        target.position.set(6, 0, 0);
        assertTrue(evaluate(",logic:'Greater'"));
    }

    @Test
    void lessThanRejectsAnOutsideTargetAndAcceptsAnInsideTarget() {
        target.position.set(6, 0, 0);
        assertFalse(evaluate(",logic:'Lesser'"));
        target.position.set(2, 0, 0);
        assertTrue(evaluate(",logic:'Lesser'"));
    }

    @Test
    void nativeZeroDefaultsSelectHorizontalDistanceAndGreaterComparison() {
        target.position.set(3, 100, 4);
        assertFalse(evaluate(""));
        target.position.set(6, 100, 0);
        assertTrue(evaluate(""));
    }

    @Test
    void horizontalDistanceIgnoresHeight() {
        target.position.set(3, 100, 4);
        assertTrue(evaluate(",logic:'Equal',compareType:'XZ'"));
        assertFalse(evaluate(",logic:'Equal',compareType:'XYZ'"));
    }

    @Test
    void fullDistanceUsesThreeAxes() {
        target.position.set(0, 3, 4);
        assertTrue(evaluate(",logic:'Equal',compareType:'XYZ'"));
        target.position.set(0, 6, 4);
        assertFalse(evaluate(",logic:'LesserOrEqual',compareType:'XYZ'"));
    }

    @Test
    void verticalDistanceUsesAbsoluteHeightDifference() {
        target.position.set(100, -5, 100);
        assertTrue(evaluate(",logic:'Equal',compareType:'Y'"));
        target.position.set(100, -6, 100);
        assertFalse(evaluate(",logic:'LesserOrEqual',compareType:'Y'"));
    }

    @Test
    void canonicalAxisAndMatchingExportAxisAreAccepted() {
        target.position.set(0, 3, 4);
        assertTrue(evaluate(",logic:'Equal',axisType:'XYZ',compareType:'XYZ'"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ",compareType:'FutureAxis'",
                ",compareType:'XY'",
                ",axisType:'Y',compareType:'XZ'",
                ",logic:'NotEqual'",
                ",logic:'FutureComparison'",
                ",NJICAELBCCB:'Top'",
                ",JBOGOHPICML:'EndAttachPoint'",
                ",future:true",
                ",target:'FutureTarget'",
                ",forceByCaster:'true'"
            })
    void unsupportedGeometryDoesNotBecomeTrue(String extra) {
        assertFalse(evaluate(extra));
    }

    @Test
    void dynamicDistanceReadsAbilityParametersAndHasNoZeroFallback() {
        var p = predicate(",logic:'Equal'");
        p.put("value", "Radius");
        assertTrue(PredicateEvaluator.evaluate(p, ability, source, target, null));
        p.put("value", "MissingRadius");
        assertFalse(PredicateEvaluator.evaluate(p, ability, source, target, null));
    }

    @Test
    void canonicalValueWinsOverTheLegacyRatio() {
        assertTrue(evaluate(",logic:'Equal',ratio:100"));
    }

    @Test
    void invalidOrMissingThresholdCannotPassThePredicate() {
        for (var value :
                new Object[] {
                    null, Double.NaN, Double.POSITIVE_INFINITY, Boolean.TRUE, Map.of("future", 5)
                }) {
            var p = predicate(",logic:'Greater'");
            p.put("value", value);
            assertFalse(PredicateEvaluator.evaluate(p, ability, source, target, null));
        }
        var p = predicate(",logic:'Greater'");
        p.remove("value");
        assertFalse(PredicateEvaluator.evaluate(p, ability, source, target, null));
    }

    @Test
    void targetSelectionCanCompareTheSourceToItself() {
        assertFalse(evaluate(",logic:'Greater',target:'Owner'"));
        assertTrue(evaluate(",logic:'LesserOrEqual',target:'Owner'"));
    }

    @Test
    void movedTargetsAreReevaluatedWithoutPositionCaching() {
        assertTrue(evaluate(",logic:'LesserOrEqual'"));
        target.position.set(100, 0, 0);
        assertFalse(evaluate(",logic:'LesserOrEqual'"));
    }

    @Test
    void nonFinitePositionAndInactiveLifetimeCannotPass() {
        target.position.set(Float.NaN, 0, 0);
        assertFalse(evaluate(",logic:'LesserOrEqual'"));
        target.position.set(1, 0, 0);
        target.invalidateAbilityLifecycle();
        assertFalse(evaluate(",logic:'LesserOrEqual'"));
    }

    @Test
    void anotherSceneCannotSupplyDistance() throws Exception {
        try (var elsewhere = new AbilityTestFixture()) {
            var other = new LocatedEntity(elsewhere.scene);
            other.setId(45);
            elsewhere.scene.getEntities().put(45, other);
            assertFalse(
                    PredicateEvaluator.evaluate(
                            predicate(",logic:'LesserOrEqual'"), ability, source, other, null));
        }
    }

    @Test
    void actualRecoveryResourceRevivesOnlyPlayersWithinItsHorizontalRadius() throws Exception {
        fixture.player.setWorld(fixture.scene.getWorld());
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public long getGuid() {
                        return 99111;
                    }

                    @Override
                    public void save() {}
                };
        var position = new Position(12, 100, 0);
        var member =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public Position getPosition() {
                        return position;
                    }
                };
        member.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        member.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        member.checkIfDead();
        fixture.player.getTeamManager().getActiveTeam().add(member);
        fixture.scene.getEntities().put(member.getId(), member);
        var rows =
                JsonUtils.loadToList(
                        Path.of("resources/BinOutput/Ability/Temp/ConfigAbility_Level.json"),
                        ResourceLoader.AbilityConfigData.class);
        var data =
                rows.stream()
                        .map(r -> r.Default)
                        .filter(d -> "LevelAbility_Challenge_RecoverAllState".equals(d.abilityName))
                        .findFirst()
                        .orElseThrow();
        var action = data.modifiers.get("RecoverAllState").onThinkInterval[0];
        var instance = new Ability(data, source, fixture.player);
        assertTrue(new ActionReviveAvatar().execute(instance, action, ByteString.EMPTY, source));
        assertEquals(100, member.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        member.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        member.checkIfDead();
        position.setX(13);
        assertTrue(new ActionReviveAvatar().execute(instance, action, ByteString.EMPTY, source));
        assertEquals(0, member.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void callbackTreeAdmitsSupportedDistanceAndRejectsABoneDependencyBeforePartialActions()
            throws Exception {
        var type = Class.forName("emu.grasscutter.game.ability.AbilityActionSequence");
        var method =
                type.getDeclaredMethod(
                        "callbacks", AbilityData.class, AbilityModifierAction[].class);
        method.setAccessible(true);
        var known =
                JsonUtils.decode(
                        "{$type:'AddGlobalValue',key:'mark',value:1,predicates:[{$type:'CNFMGJJEOPM',value:5,logic:'LesserOrEqual'}]}",
                        AbilityModifierAction.class);
        var unknown =
                JsonUtils.decode(
                        "{$type:'AddGlobalValue',key:'mark',value:1,predicates:[{$type:'CNFMGJJEOPM',value:5,NJICAELBCCB:'Top'}]}",
                        AbilityModifierAction.class);
        assertNotNull(method.invoke(null, ability.getData(), new AbilityModifierAction[] {known}));
        assertNull(method.invoke(null, ability.getData(), new AbilityModifierAction[] {unknown}));
    }

    @Test
    void emptyPartySelectionDoesNotCreateAnUnrelatedCurrentAvatar() {
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
        assertFalse(evaluate(",logic:'LesserOrEqual',target:'CurLocalAvatar'"));
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
    }

    @Test
    void removedSourceCannotSatisfyANearbyDistanceCondition() {
        source.invalidateAbilityLifecycle();
        assertFalse(evaluate(",logic:'LesserOrEqual'"));
    }

    @Test
    void knownEmptyExportPlaceholderKeepsDistanceGatingInsteadOfDisablingIt() {
        assertTrue(evaluate(",logic:'LesserOrEqual',__unk_Q1160719197:''"));
        target.position.set(6, 0, 0);
        assertFalse(evaluate(",logic:'LesserOrEqual',__unk_Q1160719197:''"));
        assertFalse(evaluate(",logic:'LesserOrEqual',__unk_Q1160719197:'FutureBinding'"));
    }
}

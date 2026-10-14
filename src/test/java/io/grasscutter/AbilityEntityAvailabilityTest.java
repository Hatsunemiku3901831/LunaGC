package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

class AbilityEntityAvailabilityTest {
    AbilityTestFixture fixture;
    Ability ability;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Availability_Test'}", AbilityData.class),
                        fixture.entity,
                        fixture.player);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    EntityAvatar member(float health) {
        long guid = 1000 + fixture.player.getTeamManager().getActiveTeam().size();
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public long getGuid() {
                        return guid;
                    }

                    @Override
                    public void save() {}
                };
        var result =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public emu.grasscutter.game.world.Position getPosition() {
                        return new emu.grasscutter.game.world.Position();
                    }
                };
        result.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        result.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, health);
        result.checkIfDead();
        fixture.player.getTeamManager().getActiveTeam().add(result);
        fixture.scene.getEntities().put(result.getId(), result);
        return result;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> predicate(String fields) {
        return JsonUtils.decode("{$type:'CBHEIAINBAI'" + fields + "}", Map.class);
    }

    boolean evaluate(String fields, GameEntity target) {
        return PredicateEvaluator.evaluate(
                predicate(fields), ability, ability.getOwner(), target, null);
    }

    Object callbacks(AbilityModifierAction action) throws Exception {
        var clazz = Class.forName("emu.grasscutter.game.ability.AbilityActionSequence");
        var method =
                clazz.getDeclaredMethod(
                        "callbacks", AbilityData.class, AbilityModifierAction[].class);
        method.setAccessible(true);
        return method.invoke(null, ability.getData(), new AbilityModifierAction[] {action});
    }

    AbilityModifierAction revive(String fields) {
        return JsonUtils.decode(
                "{$type:'ReviveDeadAvatar',amountByTargetMaxHPRatio:1,predicates:["
                        + "{$type:'CBHEIAINBAI'"
                        + fields
                        + "}]}",
                AbilityModifierAction.class);
    }

    @Test
    void currentLivingAvatarIsAvailable() {
        assertFalse(evaluate("", member(100)));
    }

    @Test
    void ordinaryLivingEntityIsAvailable() {
        assertFalse(evaluate("", fixture.entity));
    }

    @Test
    void fallenAvatarIsUnavailableWithEitherFlag() {
        var dead = member(0);
        assertTrue(evaluate("", dead));
        assertTrue(evaluate(",PKFBOEPNANB:true", dead));
    }

    @Test
    void absentEntityIsUnavailable() {
        assertTrue(evaluate("", null));
    }

    @Test
    void defaultIncludesLivingOffStageAvatar() {
        member(100);
        assertTrue(evaluate("", member(100)));
    }

    @Test
    void trueFlagAllowsLivingOffStageAvatar() {
        member(100);
        assertFalse(evaluate(",PKFBOEPNANB:true", member(100)));
    }

    @Test
    void falseFlagMatchesOmittedFlag() {
        member(100);
        assertTrue(evaluate(",PKFBOEPNANB:false", member(100)));
    }

    @Test
    void invalidatedEntityIsUnavailableRegardlessOfOffStagePermission() {
        fixture.entity.invalidateAbilityLifecycle();
        assertTrue(evaluate(",PKFBOEPNANB:true", fixture.entity));
    }

    @Test
    void replacedEntityIsUnavailable() {
        fixture.scene
                .getEntities()
                .put(fixture.entity.getId(), new AbilityTestFixture.TestEntity(fixture.scene));
        assertTrue(evaluate("", fixture.entity));
    }

    @Test
    void ownCasterResolutionIsIndependentOfFallenTarget() {
        var caster = member(100);
        ability = new Ability(ability.getData(), caster, fixture.player);
        assertFalse(evaluate(",target:'Caster',PKFBOEPNANB:true", member(0)));
    }

    @Test
    void missingCurrentAvatarDoesNotCreateOne() {
        assertTrue(evaluate(",target:'CurLocalAvatar'", fixture.entity));
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "'true'", "{}", "[]", "null"})
    void malformedBooleanDoesNotBecomeAPassingCondition(String value) {
        assertFalse(evaluate(",PKFBOEPNANB:" + value, member(0)));
    }

    @Test
    void unknownTargetDoesNotReuseTheSuppliedTarget() {
        assertFalse(evaluate(",target:'FutureTarget'", member(0)));
    }

    @Test
    void extraUnmappedFlagsKeepTheCallbackDormant() throws Exception {
        assertNull(callbacks(revive(",HGAPEJFHAIO:true")));
        assertFalse(evaluate(",HGAPEJFHAIO:true", member(0)));
    }

    @Test
    void callbackValidationAcceptsVerifiedCondition() throws Exception {
        assertNotNull(callbacks(revive(",target:'Target'")));
    }

    @Test
    void realRerirInactiveRevivalExecutesAndOnlyRestoresFallenMembers() throws Exception {
        var config =
                JsonUtils.loadToList(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Rerir.json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(row -> row.Default)
                        .filter(row -> row.abilityName.equals("Monster_Rerir_AvatarRevive"))
                        .findFirst()
                        .orElseThrow();
        ability = new Ability(config, fixture.entity, fixture.player);
        var action = config.modifiers.get("Monster_Rerir_AvatarRevive_Inactive").onKill[0];
        assertTrue(AbilityAvatarRevival.supported(action));
        var dead = member(0);
        var alive = member(73);
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, action, ByteString.EMPTY, fixture.entity);
        assertEquals(100, dead.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(73, alive.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void inactiveLivingMemberIsNotMistakenForADeadAvatar() {
        member(100);
        var inactive = member(73);
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, revive(""), ByteString.EMPTY, fixture.entity);
        assertEquals(73, inactive.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
}

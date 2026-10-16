package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.*;

class AbilityCombatStateTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity target;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        fixture.scene.addEntity(target);
        hp(target, 100);
        hp(fixture.entity, 100);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private void hp(AbilityTestFixture.TestEntity entity, float current) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, current);
    }

    private float hp(AbilityTestFixture.TestEntity entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    private Ability ability(String name, String modifiers) {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"" + name + "\",\"modifiers\":" + modifiers + "}",
                        AbilityData.class);
        return install(data);
    }

    private Ability install(AbilityData data) {
        var ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        return ability;
    }

    private void add(Ability ability, String name, int entityId, int modifierId) throws Exception {
        var change =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setParentAbilityName(AbilityString.newBuilder().setStr(ability.getData().abilityName))
                        .setModifierLocalId(
                                new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name));
        invoke(entityId, modifierId, change.build().toByteString(), fixture.entity.getId(),
                fixture.entity.getInstancedAbilities().indexOf(ability) + 1);
    }

    private void remove(int entityId, int modifierId) throws Exception {
        invoke(
                entityId,
                modifierId,
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build()
                        .toByteString(),
                0, 0);
    }

    private void invoke(int entityId, int modifierId, ByteString data, int sourceId, int rootId)
            throws Exception {
        fixture
                .player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(entityId)
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedModifierId(modifierId)
                                                .setInstancedAbilityId(rootId)
                                                .setTargetId(sourceId))
                                .setAbilityData(data)
                                .build());
    }

    private AbilityModifierAction action(String type, String name) {
        return JsonUtils.decode(
                "{\"$type\":\"" + type + "\",\"modifierName\":\"" + name + "\"}",
                AbilityModifierAction.class);
    }

    @Test
    void nativeInvincibilityWithoutPropertiesStopsDamageAndEndsOnRemoval() throws Exception {
        var ability = ability("Invincible_Test", "{\"Guard\":{\"state\":\"Invincible\"}}");
        add(ability, "Guard", 43, 71);
        fixture.scene.packets.clear();
        target.damage(25);
        assertEquals(100, hp(target));
        assertTrue(
                fixture.scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.EntityFightPropChangeReasonNotify));
        remove(43, 71);
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void aModifierLockStopsEvenALethalHitAndEndsOnRemoval() throws Exception {
        var ability = ability("Lock_Test", "{\"Guard\":{\"state\":\"LockHP\"}}");
        add(ability, "Guard", 43, 71);
        target.damage(200);
        assertEquals(100, hp(target));
        assertFalse(target.isDead());
        remove(43, 71);
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void removingTheLatestClientInstancePreservesAnOlderActiveInstance() throws Exception {
        var ability = ability("Stack_Test", "{\"Guard\":{\"state\":\"Invincible\"}}");
        add(ability, "Guard", 43, 71);
        add(ability, "Guard", 43, 72);
        remove(43, 72);
        target.damage(25);
        assertEquals(100, hp(target));
        remove(43, 71);
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void lateRemovalOfAnOlderInstancePreservesItsReplacement() throws Exception {
        var ability = ability("Replacement_Test", "{\"Guard\":{\"state\":\"Invincible\"}}");
        add(ability, "Guard", 43, 71);
        add(ability, "Guard", 43, 72);
        remove(43, 71);
        target.damage(25);
        assertEquals(100, hp(target));
        remove(43, 72);
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void identicalModifierNamesFromDifferentAbilitiesHaveIndependentLifetimes() throws Exception {
        var first = ability("Source_One", "{\"Guard\":{\"state\":\"Invincible\"}}");
        var second = ability("Source_Two", "{\"Guard\":{\"state\":\"Invincible\"}}");
        add(first, "Guard", 43, 71);
        add(second, "Guard", 43, 72);
        remove(43, 71);
        target.damage(25);
        assertEquals(100, hp(target));
        remove(43, 72);
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void replacingAClientIdWithANonProtectiveModifierImmediatelyRestoresDamage() throws Exception {
        var ability = ability("Reuse_Id", "{\"Guard\":{\"state\":\"Invincible\"},\"Other\":{}}");
        add(ability, "Guard", 43, 71);
        target.damage(25);
        assertEquals(100, hp(target));
        add(ability, "Other", 43, 71);
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void theSameClientIdOnAnotherEntityDoesNotShareProtection() throws Exception {
        var ability = ability("Isolated_Targets", "{\"Guard\":{\"state\":\"Invincible\"}}");
        add(ability, "Guard", 42, 71);
        add(ability, "Guard", 43, 71);
        remove(43, 71);
        fixture.entity.damage(25);
        target.damage(25);
        assertEquals(100, hp(fixture.entity));
        assertEquals(75, hp(target));
    }

    @Test
    void applyingAndRemovingAStateActsOnItsTargetRatherThanItsCaster() {
        var ability = ability("Server_Apply", "{\"Guard\":{\"state\":\"Invincible\"}}");
        assertTrue(
                new ActionApplyModifier()
                        .execute(ability, action("ApplyModifier", "Guard"), ByteString.EMPTY, target));
        target.damage(25);
        fixture.entity.damage(25);
        assertEquals(100, hp(target));
        assertEquals(75, hp(fixture.entity));
        assertTrue(
                new ActionRemoveModifier()
                        .execute(ability, action("RemoveModifier", "Guard"), ByteString.EMPTY, target));
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void anAttachedStateCanBeRemovedWithoutLeavingAStickyFlag() {
        var ability = ability("Server_Attach", "{\"Guard\":{\"state\":\"LockHP\"}}");
        assertTrue(
                new ActionAttachModifier()
                        .execute(ability, action("AttachModifier", "Guard"), ByteString.EMPTY, target));
        target.damage(25);
        assertEquals(100, hp(target));
        assertTrue(
                new ActionRemoveModifier()
                        .execute(ability, action("RemoveModifier", "Guard"), ByteString.EMPTY, target));
        target.damage(25);
        assertEquals(75, hp(target));
    }

    @Test
    void removingAStateDoesNotClearTheSeparatelyConfiguredHpLock() throws Exception {
        target.setLockHP(true);
        var ability = ability("Configured_Lock", "{\"Guard\":{\"state\":\"LockHP\"}}");
        add(ability, "Guard", 43, 71);
        remove(43, 71);
        target.damage(25);
        assertEquals(100, hp(target));
        assertTrue(target.isLockHP());
        target.loseHpByAbility(25, false);
        assertEquals(75, hp(target));
    }

    @Test
    void scriptedHpCostsHonorTheResourceChoiceToRespectAModifierLock() throws Exception {
        var ability = ability("Hp_Cost", "{\"Guard\":{\"state\":\"LockHP\"}}");
        add(ability, "Guard", 43, 71);
        target.loseHpByAbility(25, true);
        assertEquals(100, hp(target));
        target.loseHpByAbility(25, false);
        assertEquals(75, hp(target));
    }

    @Test
    void initialProtectionIsRegisteredAndCanBeRemoved() {
        for (String state : new String[] {"Invincible", "LockHP", "Limbo"}) {
            var data =
                    JsonUtils.decode(
                            "{\"abilityName\":\"Initial_"
                                    + state
                                    + "\",\"onAdded\":["
                                    + "{\"$type\":\"ApplyModifier\",\"modifierName\":\"Guard\"}],\"modifiers\":{\"Guard\":{"
                                    + "\"state\":\""
                                    + state
                                    + "\",\"properties\":{\"Actor_HpThresholdRatio\":0.9}}}}",
                            AbilityData.class);
            hp(fixture.entity, 50);
            var ability = install(data);
            fixture.entity.damage(25);
            assertEquals(50, hp(fixture.entity), state);
            assertTrue(ability.removeModifier(fixture.entity, "Guard"), state);
            fixture.entity.damage(25);
            assertEquals(25, hp(fixture.entity), state);
        }
    }

    @Test
    void limboResolvesTheSourceAbilityParameterAndReflectsItsRuntimeChange() throws Exception {
        var ability =
                ability(
                        "Dynamic_Limbo",
                        "{\"Guard\":{\"state\":\"Limbo\","
                                + "\"properties\":{\"Actor_HpThresholdRatio\":\"floor\"}}}");
        ability.getAbilitySpecials().put("floor", 0.5f);
        add(ability, "Guard", 43, 71);
        hp(target, 60);
        target.damage(5);
        assertEquals(55, hp(target));
        ability.getAbilitySpecials().put("floor", 0.9f);
        target.damage(5);
        assertEquals(55, hp(target));
        remove(43, 71);
        target.damage(5);
        assertEquals(50, hp(target));
    }

    @Test
    void limboWithoutAThresholdOrWithAZeroThresholdStillPreventsDeath() throws Exception {
        var ability =
                ability(
                        "Zero_Limbo",
                        "{\"Absent\":{\"state\":\"Limbo\"},\"Zero\":{\"state\":\"Limbo\","
                                + "\"properties\":{\"Actor_HpThresholdRatio\":0}}}");
        for (String name : new String[] {"Absent", "Zero"}) {
            hp(target, 100);
            add(ability, name, 43, 71);
            target.damage(200);
            assertEquals(1, hp(target), name);
            assertFalse(target.isDead(), name);
            remove(43, 71);
            target.damage(0.5f);
            assertEquals(0.5f, hp(target));
        }
    }

    @Test
    void limboNeverHealsAnEntityWhoseCurrentHpIsBelowOne() throws Exception {
        var ability = ability("Low_Hp_Limbo", "{\"Guard\":{\"state\":\"Limbo\"}}");
        add(ability, "Guard", 43, 71);
        hp(target, 0.5f);
        target.damage(100);
        assertEquals(0.5f, hp(target));
        assertFalse(target.isDead());
    }

    @Test
    void multipleLimboSourcesUseTheHighestActiveThreshold() throws Exception {
        var ability =
                ability(
                        "Multiple_Limbo",
                        "{\"Low\":{\"state\":\"Limbo\",\"properties\":{\"Actor_HpThresholdRatio\":0.2}},"
                                + "\"High\":{\"state\":\"Limbo\",\"properties\":{\"Actor_HpThresholdRatio\":0.8}}}");
        add(ability, "Low", 43, 71);
        add(ability, "High", 43, 72);
        hp(target, 50);
        target.damage(10);
        assertEquals(50, hp(target));
        remove(43, 72);
        target.damage(10);
        assertEquals(40, hp(target));
    }

    @Test
    void currentEffigyShieldAndHutaoProtectionResourcesUseTheSameStateLifecycle() throws Exception {
        try (var stream = getClass().getResourceAsStream("/ability/combat-states.json")) {
            assertNotNull(stream);
            var data =
                    JsonUtils.decode(
                            new String(stream.readAllBytes(), StandardCharsets.UTF_8), AbilityData[].class);
            assertEquals(3, data.length);
            for (var row : data) {
                var ability = install(row);
                String name = row.modifiers.keySet().iterator().next();
                var state = row.modifiers.get(name).state;
                hp(target, 100);
                add(ability, name, 43, 71);
                target.damage(200);
                float expected =
                        state == emu.grasscutter.data.binout.AbilityModifier.State.Limbo ? 1f : 100f;
                assertEquals(expected, hp(target), row.abilityName);
                assertFalse(target.isDead(), row.abilityName);
                remove(43, 71);
                target.damage(0.5f);
                assertEquals(expected - 0.5f, hp(target), row.abilityName);
            }
        }
    }

    @Test
    void initialProtectionMustSatisfyItsActionPredicates() {
        var data = JsonUtils.decode("""
                {"abilityName":"Conditional_Initial","onAdded":[
                 {"$type":"ApplyModifier","modifierName":"Guard","predicates":[
                  {"$type":"ByTargetGlobalValue","key":"allow","value":1}]}],
                 "modifiers":{"Guard":{"state":"Invincible"}}}
                """, AbilityData.class);
        fixture.entity.getGlobalAbilityValues().put("allow", 0f);
        install(data);
        fixture.entity.damage(25);
        assertEquals(75, hp(fixture.entity));
    }

    @Test
    void initialProtectionUsesTheNamedRecipient() {
        fixture.player.setWorld(fixture.scene.getWorld());
        var team = new emu.grasscutter.game.entity.EntityTeam(fixture.player);
        fixture.player.getTeamManager().setEntity(team);
        var data = JsonUtils.decode("""
                {"abilityName":"Targeted_Initial","onAdded":[
                 {"$type":"ApplyModifier","target":"Team","modifierName":"Guard"}],
                 "modifiers":{"Guard":{"state":"LockHP"}}}
                """, AbilityData.class);
        var ability = install(data);
        fixture.entity.damage(25);
        assertEquals(75, hp(fixture.entity));
        assertTrue(ability.getModifiers(team).containsKey("Guard"));
        assertTrue(ability.removeModifier(team, "Guard"));
    }
}

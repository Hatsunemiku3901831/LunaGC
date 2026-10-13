package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class AbilityStateAttachmentTest {
    AbilityTestFixture fixture;
    AbilityData data;
    Ability ability;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    String binding(String states, String extra) {
        return "{$type:'AttachToAbilityStateMixin',modifierName:'Child',abilityStates:"
                + states
                + extra
                + "}";
    }

    void install(String root, String parent, String childExtra) {
        data =
                JsonUtils.decode(
                        "{abilityName:'State_Attachment_Test',abilityMixins:["
                                + root
                                + "],modifiers:{Child:{onAdded:[{$type:'AddGlobalValue',key:'mark',value:1}],"
                                + "onRemoved:[{$type:'AddGlobalValue',key:'mark',value:-1}]"
                                + childExtra
                                + "},Frozen:{state:'ElementFrozen'},Ice:{state:'ElementIce'},Invincible:{state:'Invincible'},"
                                + "Parent:{modifierMixins:["
                                + parent
                                + "]}}}",
                        AbilityData.class);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    void install(String root) {
        install(root, "", "");
    }

    AbilityModifierController add(String name) {
        return add(fixture.entity, name);
    }

    AbilityModifierController add(AbilityTestFixture.TestEntity target, String name) {
        var controller = new AbilityModifierController(ability, data, data.modifiers.get(name));
        ability.registerModifier(target, controller);
        return controller;
    }

    boolean child() {
        return ability.getModifiers().containsKey("Child");
    }

    float mark() {
        return fixture.entity.getGlobalAbilityValues().getOrDefault("mark", 0f);
    }

    void client(int id, String name, boolean added) {
        var modifier = new ArrayList<>(data.modifiers.keySet()).indexOf(name);
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
                                                                .setModifierLocalId(modifier)
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

    @Test
    void rootBindingEntersAndLeavesWithRegisteredState() {
        install(binding("['ElementFrozen']", ""));
        assertFalse(child());
        var state = add("Frozen");
        assertTrue(child());
        assertEquals(1, mark());
        ability.unregisterModifier(fixture.entity, state);
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void matchingAnyConfiguredStateKeepsTheChildUntilTheLastStateLeaves() {
        install(binding("['ElementFrozen','ElementIce']", ""));
        add("Frozen");
        add("Ice");
        assertTrue(child());
        assertEquals(1, mark());
        ability.removeModifier(fixture.entity, "Frozen");
        assertTrue(child());
        assertEquals(1, mark());
        ability.removeModifier(fixture.entity, "Ice");
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void rejectUsesTheOppositeOfAnyMatch() {
        install(binding("['Invincible']", ",reject:true"));
        assertTrue(child());
        assertEquals(1, mark());
        add("Invincible");
        assertFalse(child());
        assertEquals(0, mark());
        ability.removeModifier(fixture.entity, "Invincible");
        assertTrue(child());
        assertEquals(1, mark());
    }

    @Test
    void emptyRejectListHasTheNativeAlwaysTrueMeaning() {
        install(binding("[]", ",reject:true"));
        assertTrue(child());
        assertEquals(1, mark());
    }

    @Test
    void emptyNormalListNeverMatches() {
        install(binding("[]", ""));
        add("Frozen");
        assertFalse(child());
    }

    @Test
    void theExplicitNativeNoneStateHasAZeroMask() {
        install(binding("['None']", ",reject:true"));
        assertTrue(child());
        add("Frozen");
        assertTrue(child());
        assertEquals(1, mark());
    }

    @Test
    void twoBindingsShareTheirChildWithoutRepeatingItsCallbacks() {
        install(binding("['ElementFrozen']", "") + "," + binding("['ElementIce']", ""));
        add("Frozen");
        add("Ice");
        assertEquals(1, mark());
        ability.removeModifier(fixture.entity, "Frozen");
        assertTrue(child());
        ability.removeModifier(fixture.entity, "Ice");
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void parentBindingOnlyLivesWhileItsParentIsAttached() {
        install("", binding("['ElementFrozen']", ""), "");
        add("Frozen");
        assertFalse(child());
        var parent = add("Parent");
        assertTrue(child());
        assertEquals(1, mark());
        ability.unregisterModifier(fixture.entity, parent);
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void repeatedRefreshDoesNotReapplyEitherCallback() {
        install(binding("['ElementFrozen']", ""));
        add("Frozen");
        for (int i = 0; i < 10; i++) fixture.entity.getAbilityGlobalValueWatchers().refresh();
        assertEquals(1, mark());
        ability.removeModifier(fixture.entity, "Frozen");
        for (int i = 0; i < 10; i++) fixture.entity.getAbilityGlobalValueWatchers().refresh();
        assertEquals(0, mark());
    }

    @Test
    void clientStateConfirmationsAndRemovalsRefreshTheBinding() {
        install(binding("['ElementFrozen']", ""));
        client(101, "Frozen", true);
        assertTrue(child());
        assertEquals(1, mark());
        client(102, "Frozen", true);
        assertEquals(1, mark());
        client(101, "Frozen", false);
        assertTrue(child());
        client(102, "Frozen", false);
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void childConfirmationDoesNotRepeatServerCallbacks() {
        install(binding("['ElementFrozen']", ""));
        add("Frozen");
        var child = ability.getModifiers().get("Child");
        client(201, "Child", true);
        client(201, "Child", true);
        assertSame(child, fixture.entity.getInstancedModifiers().get(201));
        assertEquals(1, mark());
        ability.removeModifier(fixture.entity, "Frozen");
        assertFalse(child());
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(201));
        assertEquals(0, mark());
    }

    @Test
    void aPreexistingIndependentChildIsPreserved() {
        install(binding("['ElementFrozen']", ""));
        var independent = add("Child");
        add("Frozen");
        ability.removeModifier(fixture.entity, "Frozen");
        assertSame(independent, ability.getModifiers().get("Child"));
        assertEquals(0, mark());
    }

    @Test
    void anIndependentClientInstanceSurvivesStateExit() {
        install(binding("['ElementFrozen']", ""));
        add("Frozen");
        client(201, "Child", true);
        client(202, "Child", true);
        var independent = fixture.entity.getInstancedModifiers().get(202);
        assertNotSame(fixture.entity.getInstancedModifiers().get(201), independent);
        ability.removeModifier(fixture.entity, "Frozen");
        assertSame(independent, fixture.entity.getInstancedModifiers().get(202));
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(201));
        assertEquals(0, mark());
    }

    @Test
    void aNamedReplacementIsNotRemovedWithTheOldClaim() {
        install(binding("['ElementFrozen']", ""));
        add("Frozen");
        var replacement = add("Child");
        ability.removeModifier(fixture.entity, "Frozen");
        assertSame(replacement, ability.getModifiers().get("Child"));
        assertEquals(0, mark());
    }

    @Test
    void removingTheRootAbilityReleasesItsOwnedChild() {
        install(binding("['ElementFrozen']", ""));
        add("Frozen");
        fixture.entity.getInstancedAbilities().remove(ability);
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void unknownStatesDoNotBecomeTrueWhenRejected() {
        install(binding("['FutureState']", ",reject:true"));
        assertFalse(child());
    }

    @Test
    void malformedListsRemainNonExecutableWithoutBreakingResourceLoading() {
        assertDoesNotThrow(() -> install(binding("{bad:1}", ",reject:true")));
        assertFalse(child());
    }

    @Test
    void malformedRejectValueIsNotCoercedToTrue() {
        install(binding("[]", ",reject:'true'"));
        assertFalse(child());
    }

    @Test
    void unsupportedChildCleanupPreventsPartialApplication() {
        install(binding("['ElementFrozen']", ""), "", ",onRemoved:[{$type:'UnknownFutureAction'}]");
        add("Frozen");
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void disabledBindingDoesNotAttach() {
        install(binding("['ElementFrozen']", ",enable:false"));
        add("Frozen");
        assertFalse(child());
    }

    @Test
    void predicatesAreRecheckedWhenResourceValuesChange() {
        install(
                binding(
                        "['ElementFrozen']",
                        ",predicates:[{$type:'ByTargetGlobalValue',key:'ready',compareType:'MoreThan',value:0}]"));
        add("Frozen");
        assertFalse(child());
        fixture.entity.getGlobalAbilityValues().put("ready", 1f);
        fixture.entity.onAbilityValueUpdate();
        assertTrue(child());
        assertEquals(1, mark());
        fixture.entity.getGlobalAbilityValues().put("ready", 0f);
        fixture.entity.onAbilityValueUpdate();
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void aDifferentBearerStateDoesNotActivateThisRoot() {
        install(binding("['ElementFrozen']", ""));
        var other = new AbilityTestFixture.TestEntity(fixture.scene);
        other.setId(43);
        fixture.scene.getEntities().put(43, other);
        add(other, "Frozen");
        assertFalse(child());
        assertEquals(0, mark());
    }

    @Test
    void lifecycleInvalidationReleasesChildrenWithoutWritingOldCallbacks() {
        install(binding("['ElementFrozen']", ""));
        add("Frozen");
        assertTrue(child());
        fixture.entity.invalidateAbilityLifecycle();
        assertFalse(child());
        assertEquals(1, mark());
    }

    @Test
    void globalValueAndStateBindingsShareOneClaim() {
        install(
                binding("['ElementFrozen']", "")
                        + ",{$type:'AttachModifierToSelfGlobalValueNoInitMixin',globalValueKey:'ready',modifierNameSteps:['Child']}");
        add("Frozen");
        assertEquals(1, mark());
        fixture.entity.getGlobalAbilityValues().put("ready", 1f);
        fixture.entity.onAbilityValueUpdate();
        ability.removeModifier(fixture.entity, "Frozen");
        assertTrue(child());
        assertEquals(1, mark());
        fixture.entity.getGlobalAbilityValues().put("ready", 0f);
        fixture.entity.onAbilityValueUpdate();
        assertFalse(child());
        assertEquals(0, mark());
    }

    void resource(String file, String name) throws Exception {
        var entries =
                com.google.gson.JsonParser.parseString(
                                Files.readString(
                                        Path.of("resources/BinOutput/Ability/Temp/" + file)))
                        .getAsJsonArray();
        for (var entry : entries) {
            var raw = entry.getAsJsonObject().getAsJsonObject("Default");
            if (name.equals(raw.get("abilityName").getAsString())) {
                data = JsonUtils.decode(raw, AbilityData.class);
                ability = new Ability(data, fixture.entity, fixture.player);
                fixture.entity.getInstancedAbilities().add(ability);
                return;
            }
        }
        fail("Resource ability missing: " + name);
    }

    Ability stateAbility(AbilityModifier.State state) {
        var stateData =
                JsonUtils.decode(
                        "{abilityName:'State_Source',modifiers:{Source:{state:'" + state + "'}}}",
                        AbilityData.class);
        var source = new Ability(stateData, fixture.entity, fixture.player);
        source.registerModifier(
                fixture.entity,
                new AbilityModifierController(
                        source, stateData, stateData.modifiers.get("Source")));
        return source;
    }

    @Test
    void actualDragonStatueRejectBindingFollowsInvincibility() throws Exception {
        resource(
                "MonsterAbilities/ConfigAbility_Monster_DragonStatue.json",
                "Monster_DragonStatue_Prison_CreateGadget");
        add("ElementDeadTime_State");
        assertTrue(ability.getModifiers().containsKey("ElementDeadTime"));
        var invincible = stateAbility(AbilityModifier.State.Invincible);
        assertFalse(ability.getModifiers().containsKey("ElementDeadTime"));
        invincible.removeModifier(fixture.entity, "Source");
        assertTrue(ability.getModifiers().containsKey("ElementDeadTime"));
        ability.removeModifier(fixture.entity, "ElementDeadTime_State");
        assertFalse(ability.getModifiers().containsKey("ElementDeadTime"));
    }

    @Test
    void actualCrystalLinkKeepsItsDefenceDebuffForTheResourceExitDelay() throws Exception {
        resource(
                "ActivityAbilities/ConfigAbility_Activity_CrystalLink_Ability.json",
                "ActivityAbility_CrystalLink_FrozenTarget_DefenceDown");
        ability.getAbilitySpecials().put("DefecnceDownRate", 0.3f);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_DEFENSE, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 100);
        add("UNIQUE_Activity_CrystalLink_Decision_TriggerFreezeReaction");
        var frozen = stateAbility(AbilityModifier.State.ElementFrozen);
        var hold = "UNIQUE_Activity_CrystalLink_Frozen_DefecnceDown_Hold";
        var delay = "UNIQUE_Activity_CrystalLink_Frozen_DefecnceDown_Delay";
        assertTrue(ability.getModifiers().containsKey(hold));
        assertEquals(
                -0.3f,
                fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_DEFENSE_PERCENT),
                0.00001f);
        frozen.removeModifier(fixture.entity, "Source");
        assertFalse(ability.getModifiers().containsKey(hold));
        assertTrue(ability.getModifiers().containsKey(delay));
        assertEquals(
                -0.3f,
                fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_DEFENSE_PERCENT),
                0.00001f);
        ability.removeModifier(fixture.entity, delay);
        assertEquals(
                0,
                fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_DEFENSE_PERCENT),
                0.00001f);
    }

    @Test
    void unknownStateControlFieldsKeepTheBindingNonExecutable() {
        install(binding("[]", ",reject:true,elementTypeModifier:{future:1}"));
        assertFalse(child());
    }
}

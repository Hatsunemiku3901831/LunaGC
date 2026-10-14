package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class AbilityBindingOwnershipTest {
    AbilityTestFixture fixture;
    AbilityData data;
    Ability ability;
    static final String GLOBAL =
            "{$type:'AttachModifierToSelfGlobalValueNoInitMixin',globalValueKey:'ready',modifierNameSteps:['Child']}";
    static final String STATE =
            "{$type:'AttachToAbilityStateMixin',abilityStates:['ElementFrozen'],modifierName:'Child'}";

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 100);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    void install(String root, String extra) {
        data =
                JsonUtils.decode(
                        "{abilityName:'Binding_Ownership',abilityMixins:["
                                + root
                                + "],modifiers:{Child:{properties:{Actor_AttackDelta:10},onAdded:[{$type:'AddGlobalValue',key:'mark',value:1}],onRemoved:[{$type:'AddGlobalValue',key:'mark',value:-1}]"
                                + extra
                                + "},Frozen:{state:'ElementFrozen'},Parent:{modifierMixins:["
                                + GLOBAL
                                + "]}}}",
                        AbilityData.class);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    void install() {
        install(GLOBAL, "");
    }

    void ready(float value) {
        fixture.entity.getGlobalAbilityValues().put("ready", value);
        fixture.entity.onAbilityValueUpdate();
    }

    float mark() {
        return fixture.entity.getGlobalAbilityValues().getOrDefault("mark", 0f);
    }

    AbilityModifierController add(String name) {
        var controller = new AbilityModifierController(ability, data, data.modifiers.get(name));
        ability.registerModifier(fixture.entity, controller);
        return controller;
    }

    void client(int id, String name, boolean added) {
        int local = new ArrayList<>(data.modifiers.keySet()).indexOf(name);
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
                                                                .setModifierLocalId(local)
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
    void confirmationKeepsTheControllerThatTheGlobalBindingCreated() {
        install();
        ready(1);
        var owned = ability.getModifiers().get("Child");
        client(101, "Child", true);
        assertSame(owned, fixture.entity.getInstancedModifiers().get(101));
        assertEquals(1, mark());
    }

    @Test
    void duplicateConfirmationDoesNotEndOrReinitializeTheOwnedInstance() {
        install();
        ready(1);
        client(101, "Child", true);
        var owned = fixture.entity.getInstancedModifiers().get(101);
        client(101, "Child", true);
        assertSame(owned, fixture.entity.getInstancedModifiers().get(101));
        assertEquals(1, mark());
        ready(0);
        assertEquals(0, mark());
    }

    @Test
    void anIndependentClientInstanceSurvivesTheGlobalConditionExit() {
        install();
        ready(1);
        client(101, "Child", true);
        client(102, "Child", true);
        var independent = fixture.entity.getInstancedModifiers().get(102);
        ready(0);
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(101));
        assertSame(independent, fixture.entity.getInstancedModifiers().get(102));
        assertEquals(0, mark());
        assertEquals(110, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }

    @Test
    void aNamedReplacementKeepsItsPropertiesWhenTheOriginalClaimLeaves() {
        install();
        ready(1);
        var independent = add("Child");
        ready(0);
        assertSame(independent, ability.getModifiers().get("Child"));
        assertEquals(0, mark());
        assertEquals(110, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }

    @Test
    void aPreexistingChildIsNeverClaimedForRemoval() {
        install();
        var independent = add("Child");
        ready(1);
        ready(0);
        assertSame(independent, ability.getModifiers().get("Child"));
        assertEquals(0, mark());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void globalAndStateClaimsPreserveAnIndependentInstanceInEitherActivationOrder(
            boolean globalFirst) {
        install(GLOBAL + "," + STATE, "");
        if (globalFirst) {
            ready(1);
            add("Frozen");
        } else {
            add("Frozen");
            ready(1);
        }
        client(101, "Child", true);
        client(102, "Child", true);
        var independent = fixture.entity.getInstancedModifiers().get(102);
        ready(0);
        assertEquals(1, mark());
        ability.removeModifier(fixture.entity, "Frozen");
        assertSame(independent, fixture.entity.getInstancedModifiers().get(102));
        assertEquals(0, mark());
    }

    @Test
    void rootRemovalReleasesOnlyItsOwnConfirmedInstance() {
        install();
        ready(1);
        client(101, "Child", true);
        client(102, "Child", true);
        var independent = fixture.entity.getInstancedModifiers().get(102);
        fixture.entity.getInstancedAbilities().remove(ability);
        assertSame(independent, fixture.entity.getInstancedModifiers().get(102));
        assertEquals(0, mark());
    }

    @Test
    void theFinalParentRemovalLeavesTheIndependentClientInstance() {
        install("", "");
        ready(1);
        add("Parent");
        client(101, "Child", true);
        client(102, "Child", true);
        var independent = fixture.entity.getInstancedModifiers().get(102);
        ability.removeModifier(fixture.entity, "Parent");
        assertSame(independent, fixture.entity.getInstancedModifiers().get(102));
        assertEquals(0, mark());
    }

    @Test
    void explicitRemovalBalancesTheOldCallbackBeforeReinitializingItsReplacement() {
        install(
                GLOBAL,
                ",onAdded:[{$type:'SetGlobalValue',key:'mark',value:1}],onRemoved:[{$type:'SetGlobalValue',key:'mark',value:0}]");
        ready(1);
        var old = ability.getModifiers().get("Child");
        ability.removeModifier(fixture.entity, "Child");
        assertNotSame(old, ability.getModifiers().get("Child"));
        assertEquals(1, mark());
    }

    @Test
    void anUnconfirmedExpiryCannotRunCleanupTwiceThroughTheWatcher() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var field =
                emu.grasscutter.game.entity.GameEntity.class.getDeclaredField(
                        "abilityModifierDuration");
        field.setAccessible(true);
        field.set(fixture.entity, new AbilityModifierDuration(fixture.entity, clock::get));
        install(GLOBAL, ",duration:1");
        ready(1);
        fixture.entity.getGlobalAbilityValues().put("ready", 0f);
        clock.set(2_000_000_000L);
        fixture.entity.getAbilityModifierDuration().tick();
        assertEquals(0, mark());
        assertFalse(ability.getModifiers().containsKey("Child"));
    }

    @Test
    void invalidationDoesNotRunCleanupInTheOldEntityLifetime() {
        install();
        ready(1);
        client(101, "Child", true);
        fixture.entity.invalidateAbilityLifecycle();
        assertEquals(1, mark());
        assertFalse(fixture.entity.getInstancedModifiers().containsKey(101));
    }

    @Test
    void unsupportedAddedActionsKeepExistingServerBookkeepingAndOwnedCleanup() {
        install(
                GLOBAL,
                ",onAdded:[{$type:'AddGlobalValue',key:'mark',value:1},{$type:'UnknownFutureAction'}]");
        ready(1);
        client(101, "Child", true);
        ready(0);
        assertEquals(0, mark());
    }

    @Test
    void modifierAttachmentAndOrdinaryCallbacksShareTheConfirmedLifetime() {
        install(
                GLOBAL,
                ",modifierMixins:[{$type:'AttachActionToModifierMixin',onAdded:[{$type:'AddGlobalValue',key:'mark',value:2}],onRemoved:[{$type:'AddGlobalValue',key:'mark',value:-2}]}]");
        ready(1);
        assertEquals(3, mark());
        client(101, "Child", true);
        client(101, "Child", true);
        assertEquals(3, mark());
        ready(0);
        assertEquals(0, mark());
    }

    @Test
    void conditionChangesStopLaterInitializationActions() {
        install(
                GLOBAL,
                ",onAdded:[{$type:'AddGlobalValue',key:'mark',value:1},{$type:'SetGlobalValue',key:'ready',value:0},{$type:'AddGlobalValue',key:'late',value:1}]");
        ready(1);
        assertEquals(0, mark());
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("late"));
    }

    @Test
    void nestedConditionChangesStopTheRestOfTheBranchAndStillCleanUp() {
        install(
                GLOBAL,
                ",onAdded:[{$type:'Predicated',successActions:[{$type:'AddGlobalValue',key:'mark',value:1},{$type:'SetGlobalValue',key:'ready',value:0},{$type:'AddGlobalValue',key:'late',value:1}]}]");
        ready(1);
        assertEquals(0, mark());
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("late"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ordinaryCleanupRunsBeforeTheAttachmentCleanup(boolean confirmed) {
        install(
                GLOBAL,
                ",onAdded:[{$type:'AddGlobalValue',key:'order',value:2}],onRemoved:[{$type:'SetGlobalValue',key:'order',value:10}],modifierMixins:[{$type:'AttachActionToModifierMixin',onAdded:[{$type:'SetGlobalValue',key:'order',value:1}],onRemoved:[{$type:'AddGlobalValue',key:'order',value:1}]}]");
        ready(1);
        assertEquals(3f, fixture.entity.getGlobalAbilityValues().get("order"));
        if (confirmed) client(101, "Child", true);
        ready(0);
        assertEquals(11f, fixture.entity.getGlobalAbilityValues().get("order"));
    }

    @Test
    void clientRemovalDoesNotLeaveTheOldInitializationMarkerOnARecreatedChild() {
        install();
        ready(1);
        client(101, "Child", true);
        client(101, "Child", false);
        assertEquals(1, mark());
        assertNotNull(ability.getModifiers().get("Child"));
        ready(0);
        assertEquals(0, mark());
    }
}

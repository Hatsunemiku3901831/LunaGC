package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

class AbilityHealthEventTest {
    AbilityTestFixture fixture;
    Ability ability;
    final List<Runnable> restore = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        AbilityManager.registerHandlers();
        fixture = new AbilityTestFixture();
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    AbilityData data(String name, String body) {
        return JsonUtils.decode("{abilityName:'" + name + "'," + body + "}", AbilityData.class);
    }

    String mixin(String extra) {
        return "{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',actions:[{$type:'AddGlobalValue',key:'events',value:1}]"
                + (extra.isEmpty() ? "" : "," + extra)
                + "}";
    }

    void root(String extra) {
        ability = install(data("Health_Event", "abilityMixins:[" + mixin(extra) + "]"));
    }

    Ability install(AbilityData data) {
        assertNotNull(data);
        var value = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(value);
        return value;
    }

    void register(AbilityData data) {
        var old = GameData.getAbilityDataMap().put(data.abilityName, data);
        restore.add(
                () -> {
                    if (old == null) GameData.getAbilityDataMap().remove(data.abilityName);
                    else GameData.getAbilityDataMap().put(data.abilityName, old);
                });
    }

    float events() {
        return fixture.entity.getGlobalAbilityValues().getOrDefault("events", 0f);
    }

    void drain() throws Exception {
        var barrier = new CyclicBarrier(4);
        var tasks = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            tasks.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var task : tasks) task.get(6, TimeUnit.SECONDS);
    }

    void invoke() {
        int id =
                ability.getData().localIdToMixin.entrySet().stream()
                        .filter(e -> "HPDown".equals(e.getValue().onEvent))
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setLocalId(id))
                                .build());
    }

    @Test
    void damageRunsTheHpDownCallbackSynchronously() {
        root("");
        fixture.entity.damage(10);
        assertEquals(1, events());
    }

    @Test
    void directHealthReductionAlsoProducesOneEvent() {
        root("");
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 90);
        assertEquals(1, events());
    }

    @Test
    void eachDistinctHealthReductionProducesOneEvent() {
        root("");
        fixture.entity.damage(10);
        fixture.entity.damage(10);
        assertEquals(2, events());
    }

    @Test
    void healingAndUnchangedHealthDoNotInventHpDown() {
        root("");
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 110);
        assertEquals(0, events());
    }

    @Test
    void changesToOtherPropertiesDoNotProduceHpDown() {
        root("");
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 20);
        assertEquals(0, events());
    }

    @Test
    void nonFiniteHealthDoesNotProduceAnEvent() {
        root("");
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.NaN);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 80);
        assertEquals(0, events());
    }

    @Test
    void clientOnlyEventCallbacksRemainClientSelected() {
        root("type:'Client'");
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void serverEventCallbacksCanRunWithoutAClientPacket() {
        root("type:'Server'");
        fixture.entity.damage(10);
        assertEquals(1, events());
    }

    @Test
    void unknownEventKindsAreNotDispatchedAsHpDown() {
        root("onEvent:'Unknown'");
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void unknownConditionsDoNotGrantAutomaticExecution() {
        root("predicates:[{$type:'Unknown'}]");
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void knownConditionsReadHealthAfterTheChange() {
        root("predicates:[{$type:'ByTargetHPRatio',HPRatio:0.5,logic:'Lesser'}]");
        fixture.entity.damage(50);
        assertEquals(0, events());
        fixture.entity.damage(1);
        assertEquals(1, events());
    }

    @Test
    void modifierEventsRunOnlyWhileTheirControllerIsAttached() {
        ability =
                install(
                        data(
                                "Health_Modifier",
                                "modifiers:{Listener:{modifierMixins:[" + mixin("") + "]}}"));
        var modifier =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Listener"));
        ability.registerModifier(fixture.entity, modifier);
        fixture.entity.damage(10);
        assertEquals(1, events());
        ability.removeModifier(fixture.entity, "Listener");
        fixture.entity.damage(10);
        assertEquals(1, events());
    }

    @Test
    void clientAndServerAliasesOfOneControllerDoNotDoubleTheEvent() {
        ability =
                install(
                        data(
                                "Health_Alias",
                                "modifiers:{Listener:{modifierMixins:[" + mixin("") + "]}}"));
        var modifier =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Listener"));
        ability.registerModifier(fixture.entity, modifier);
        fixture.entity.getInstancedModifiers().put(1, modifier);
        fixture.entity.damage(10);
        assertEquals(1, events());
    }

    @Test
    void removedAbilitiesDoNotContinueReceivingEvents() {
        root("");
        fixture.entity.getInstancedAbilities().remove(ability);
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void invalidatedEntityLifetimesDoNotRunAnEvent() {
        root("");
        fixture.entity.invalidateAbilityLifecycle();
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void anEventCanTriggerAValidatedAbilityAndItsModifier() {
        var child =
                data(
                        "Health_Child",
                        "onAbilityStart:[{$type:'ApplyModifier',modifierName:'Buff'}],modifiers:{Buff:{properties:{Actor_CriticalDelta:0.5},duration:1,onAdded:[{$type:'AddGlobalValue',key:'events',value:1}]}}");
        register(child);
        ability =
                install(
                        data(
                                "Health_Parent",
                                "abilityMixins:[{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',actions:[{$type:'TriggerAbility',abilityName:'Health_Child'}]}]"));
        fixture.entity.damage(10);
        assertEquals(1, events());
        assertEquals(0.5f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CRITICAL));
    }

    @Test
    void aMissingTriggeredAbilityDoesNotClaimTheEvent() {
        ability =
                install(
                        data(
                                "Health_Missing",
                                "abilityMixins:[{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',actions:[{$type:'AddGlobalValue',key:'events',value:1},{$type:'TriggerAbility',abilityName:'Missing_Child'}]}]"));
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void unsupportedNestedActionsDoNotAllowPartialEventExecution() {
        register(
                data(
                        "Health_Unsafe",
                        "onAbilityStart:[{$type:'SetAnimatorTrigger',triggerID:'Unknown'}]"));
        ability =
                install(
                        data(
                                "Health_Parent_Unsafe",
                                "abilityMixins:[{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',actions:[{$type:'AddGlobalValue',key:'events',value:1},{$type:'TriggerAbility',abilityName:'Health_Unsafe'}]}]"));
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void recursiveAbilityGraphsAreRejectedBeforeAnyStateChange() {
        register(
                data(
                        "Health_Cycle",
                        "onAbilityStart:[{$type:'TriggerAbility',abilityName:'Health_Cycle'}]"));
        ability =
                install(
                        data(
                                "Health_Parent_Cycle",
                                "abilityMixins:[{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',actions:[{$type:'AddGlobalValue',key:'events',value:1},{$type:'TriggerAbility',abilityName:'Health_Cycle'}]}]"));
        fixture.entity.damage(10);
        assertEquals(0, events());
    }

    @Test
    void uniqueRemovalCanReachAnotherAbilityOnTheSameBearer() {
        var parent =
                install(
                        data(
                                "Health_Protection",
                                "modifiers:{Protection:{isUnique:true,state:'Limbo'}}"));
        parent.registerModifier(
                fixture.entity,
                new AbilityModifierController(
                        parent, parent.getData(), parent.getData().modifiers.get("Protection")));
        var child = install(data("Health_End", "onAbilityStart:[]"));
        var action =
                JsonUtils.decode(
                        "{$type:'RemoveUniqueModifier',modifierName:'Protection'}",
                        AbilityModifierAction.class);
        fixture.player
                .getAbilityManager()
                .executeActionNow(child, action, ByteString.EMPTY, fixture.entity);
        assertFalse(AbilityCombatState.read(fixture.entity).limbo());
    }

    @Test
    void uniqueRemovalDoesNotRemoveAnotherAbilitysNonUniqueModifierWithTheSameName() {
        var parent = install(data("Health_Ordinary", "modifiers:{Protection:{state:'Limbo'}}"));
        parent.registerModifier(
                fixture.entity,
                new AbilityModifierController(
                        parent, parent.getData(), parent.getData().modifiers.get("Protection")));
        var child = install(data("Health_End_Ordinary", "onAbilityStart:[]"));
        var action =
                JsonUtils.decode(
                        "{$type:'RemoveUniqueModifier',modifierName:'Protection'}",
                        AbilityModifierAction.class);
        fixture.player
                .getAbilityManager()
                .executeActionNow(child, action, ByteString.EMPTY, fixture.entity);
        assertTrue(AbilityCombatState.read(fixture.entity).limbo());
    }

    @Test
    void emptyPrimaryActionsUseTheEventQueue() {
        root("actions:[],actionQueue:[{$type:'AddGlobalValue',key:'events',value:1}]");
        fixture.entity.damage(10);
        assertEquals(1, events());
    }

    @Test
    void removingAnAbilityThroughItsCleanupApiRemovesItsProtection() {
        ability =
                install(
                        data(
                                "Health_Removed_Protection",
                                "modifiers:{Protection:{isUnique:true,state:'Limbo'}}"));
        ability.registerModifier(
                fixture.entity,
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Protection")));
        ability.removeInstalledModifiers();
        fixture.entity.getInstancedAbilities().remove(ability);
        assertFalse(AbilityCombatState.read(fixture.entity).limbo());
    }

    @Test
    void aClientConfirmationDoesNotRepeatAnOwnedRootEvent() throws Exception {
        root("");
        fixture.entity.damage(10);
        assertEquals(1, events());
        invoke();
        drain();
        assertEquals(1, events());
    }

    @Test
    void aLateClientConfirmationDoesNotReplayARemovedModifierEvent() throws Exception {
        ability =
                install(
                        data(
                                "Health_Late",
                                "modifiers:{Listener:{modifierMixins:[" + mixin("") + "]}}"));
        ability.registerModifier(
                fixture.entity,
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Listener")));
        fixture.entity.damage(10);
        assertEquals(1, events());
        ability.removeModifier(fixture.entity, "Listener");
        invoke();
        drain();
        assertEquals(1, events());
    }

    @Test
    void aClientOnlyCallbackStillRunsWhenSelected() throws Exception {
        root("type:'Client'");
        fixture.entity.damage(10);
        assertEquals(0, events());
        invoke();
        drain();
        assertEquals(1, events());
    }

    @Test
    void aControlledChildStartCannotBeReplayedByItsClientLeaf() throws Exception {
        register(
                data(
                        "Health_Owned_Child",
                        "onAbilityStart:[{$type:'AddGlobalValue',key:'events',value:1}]"));
        ability =
                install(
                        data(
                                "Health_Owned_Parent",
                                "abilityMixins:[{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Both',actions:[{$type:'TriggerAbility',abilityName:'Health_Owned_Child'}]}]"));
        fixture.entity.damage(10);
        assertEquals(1, events());
        var child = fixture.entity.getInstancedAbilities().get(1);
        int localId = child.getData().localIdToAction.keySet().iterator().next();
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(2)
                                                .setLocalId(localId))
                                .build());
        drain();
        assertEquals(1, events());
    }
}

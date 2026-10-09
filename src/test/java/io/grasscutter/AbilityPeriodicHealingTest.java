package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

class AbilityPeriodicHealingTest {
    private AbilityTestFixture fixture;
    private TimedEntity bearer;
    private final AtomicLong now=new AtomicLong();
    private Ability ability;
    private String modifierName="Pulse";
    private class TimedEntity extends AbilityTestFixture.TestEntity {
        private final AbilityPeriodicHealing healing;
        TimedEntity() { super(fixture.scene); healing=new AbilityPeriodicHealing(this,now::get); }
        @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return healing; }
    }
    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture(); bearer=new TimedEntity(); bearer.setId(43);
        fixture.scene.getEntities().put(43,bearer);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,100);
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String fields) {
        var data=JsonUtils.decode("{\"abilityName\":\"TimedHeal\",\"abilitySpecials\":{\"amount\":10},\"modifiers\":{\"Pulse\":{\"thinkInterval\":5,\"duration\":15.3,\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":\"amount\",\"muteHealEffect\":true,\"target\":\"Self\"}]"+fields+"}}}",AbilityData.class);
        ability=new Ability(data,fixture.entity,fixture.player); fixture.entity.getInstancedAbilities().add(ability);
    }
    private AbilityModifierController attach() {
        var controller=new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get(modifierName));
        ability.registerModifier(bearer,controller); return controller;
    }
    private void tick(double seconds) { now.set((long)(seconds*1_000_000_000d)); bearer.onTick(0); }
    private float hp() { return bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP); }
    private void client(boolean add,int id) throws Exception {
        var data=ability.getData();
        var change=AbilityMetaModifierChange.newBuilder().setAction(add?ModifierAction.MODIFIER_ACTION_ADDED:ModifierAction.MODIFIER_ACTION_REMOVED)
                .setParentAbilityName(AbilityString.newBuilder().setStr(data.abilityName))
                .setModifierLocalId(new java.util.ArrayList<>(data.modifiers.keySet()).indexOf(modifierName));
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(42).setInstancedAbilityId(1).setInstancedModifierId(id))
                .setAbilityData(change.build().toByteString()).build());
    }
    private void invokePeriodic(int id) {
        var action=ability.getData().modifiers.get(modifierName).onThinkInterval[0];
        invokeAction(id,action);
    }
    private void invokeAction(int id,AbilityModifier.AbilityModifierAction action) {
        int localId=ability.getData().localIdToAction.entrySet().stream().filter(e->e.getValue()==action).findFirst().orElseThrow().getKey();
        fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(43).setInstancedAbilityId(1).setInstancedModifierId(id).setLocalId(localId)).build());
    }
    @Test void healingUsesTheConfiguredFiveSecondCadenceAndStopsAtItsDuration() {
        configure(""); attach(); tick(0); tick(4.999); assertEquals(100,hp());
        tick(5); assertEquals(110,hp()); tick(5); assertEquals(110,hp());
        tick(10); assertEquals(120,hp()); tick(15); assertEquals(130,hp()); tick(20); assertEquals(130,hp());
    }
    @Test void removingTheParentImmediatelyStopsFurtherPulses() {
        configure(""); attach(); tick(5); ability.removeModifier(bearer,modifierName);
        tick(10); assertEquals(110,hp());
    }
    @Test void ownerInvalidationStopsAnotherBearersHealing() {
        configure(""); attach(); fixture.entity.invalidateAbilityLifecycle(); tick(5); assertEquals(100,hp());
    }
    @Test void removingAndReaddingTheBearerCannotReviveAnOldTimer() {
        configure(""); attach(); bearer.invalidateAbilityLifecycle(); bearer.activateAbilityLifecycle(); tick(5); assertEquals(100,hp());
    }
    @Test void delayedTicksCoalesceMissedPulsesWithoutAnExcessHealingBurst() {
        configure(""); attach(); tick(14); assertEquals(110,hp()); tick(14.5); assertEquals(110,hp());
        tick(15); assertEquals(120,hp());
    }
    @Test void eachPulseReadsCurrentParametersAndAnExpiredTimerDoesNotRestart() {
        configure(""); attach(); tick(5); ability.getAbilitySpecials().put("amount",20); tick(10);
        assertEquals(130,hp()); tick(30); tick(31); assertEquals(130,hp());
    }
    @Test void multiplePropertylessServerInstancesEachHealOnce() {
        configure(",\"stacking\":\"Multiple\""); attach(); attach(); tick(5); assertEquals(120,hp());
        ability.removeModifier(bearer,modifierName); tick(10); assertEquals(120,hp());
    }
    @Test void zeroDurationLastsUntilRemovalAndAnInvalidIntervalRemainsClientOwned() {
        configure(",\"duration\":0"); attach(); tick(5); tick(30); assertEquals(120,hp());
        ability.removeModifier(bearer,modifierName);
        ability.getData().modifiers.get(modifierName).thinkInterval=emu.grasscutter.data.common.DynamicFloat.ZERO;
        attach(); tick(60); assertEquals(120,hp());
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get(modifierName).onThinkInterval[0]));
    }
    @Test void aLateClientIntervalParameterRetainsTheOriginalAttachmentTime() throws Exception {
        configure(",\"thinkInterval\":\"period\""); attach(); tick(4);
        var action=ability.getData().modifiers.get(modifierName).onThinkInterval[0];
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,action));
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(42)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(1))
                .setAbilityData(AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setStr("period"))
                        .setFloatValue(5).build().toByteString()).build());
        assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,action));
        tick(5); assertEquals(110,hp());
    }
    @Test void aLateBearerGlobalValueCanStartThePendingTimer() {
        configure(",\"thinkInterval\":\"period\""); attach(); tick(4);
        bearer.getGlobalAbilityValues().put("period",5f); bearer.onAbilityValueUpdate();
        tick(5); assertEquals(110,hp());
    }
    @Test void parameterRefreshCannotResurrectAPendingTimerFromAnEarlierLifetime() {
        configure(",\"thinkInterval\":\"period\""); attach();
        bearer.invalidateAbilityLifecycle(); bearer.activateAbilityLifecycle();
        ability.getAbilitySpecials().put("period",5f); ability.refreshModifierTargets(false);
        tick(5); assertEquals(100,hp());
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get(modifierName).onThinkInterval[0]));
    }
    @Test void clientConfirmationPreservesThePendingServersStartTime() throws Exception {
        configure(""); attach(); tick(4); client(true,71); tick(5); assertEquals(110,hp()); tick(10); assertEquals(120,hp());
    }
    @Test void nativeCallbacksCannotDoubleTheServersPeriodicHealing() throws Exception {
        configure(""); client(true,71); invokePeriodic(71); Thread.sleep(40); assertEquals(100,hp());
        tick(5); invokePeriodic(71); Thread.sleep(40); assertEquals(110,hp());
    }
    @Test void lateCallbacksAfterClientRemovalAreIgnoredAndReusedIdsStartAFreshTimer() throws Exception {
        configure(""); bearer.getInstancedAbilities().add(ability); client(true,71); tick(5); client(false,71);
        invokePeriodic(71); Thread.sleep(40); tick(10); assertEquals(110,hp());
        client(true,71); tick(14.999); assertEquals(110,hp()); tick(15); assertEquals(120,hp());
    }
    @Test void payloadDependentMixedCallbacksKeepTheirClientExecutionAuthority() {
        configure(",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10},{\"$type\":\"DamageByAttackValue\"}]");
        attach(); tick(5); assertEquals(100,hp());
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get(modifierName).onThinkInterval[0]));
    }
    @Test void mixedHealingAndBookkeepingRunOncePerCycle() {
        configure(",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10},{\"$type\":\"AddGlobalValue\",\"key\":\"pulses\",\"value\":1}]");
        attach(); tick(5); tick(5); tick(10);
        assertEquals(120,hp()); assertEquals(2f,bearer.getGlobalAbilityValues().get("pulses"));
    }
    @Test void laterActionsReadEarlierWritesOnTheCaster() {
        configure(",\"onThinkInterval\":[{\"$type\":\"SetGlobalValue\",\"key\":\"pulseAmount\",\"value\":25,\"target\":\"Caster\"},{\"$type\":\"HealHP\",\"amount\":\"pulseAmount\",\"target\":\"Self\"}]");
        attach(); tick(5); assertEquals(125,hp());
        assertEquals(25f,fixture.entity.getGlobalAbilityValues().get("pulseAmount"));
        assertFalse(bearer.getGlobalAbilityValues().containsKey("pulseAmount"));
    }
    @Test void conditionalMixedActionsSelectTheCurrentHealthBranch() {
        configure(",\"onThinkInterval\":[{\"$type\":\"Predicated\",\"targetPredicates\":[{\"$type\":\"ByTargetHPRatio\",\"HPRatio\":0.15,\"logic\":\"Lesser\"}],\"successActions\":[{\"$type\":\"HealHP\",\"amount\":60},{\"$type\":\"AddGlobalValue\",\"key\":\"healed\",\"value\":1}],\"failActions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"skipped\",\"value\":1}]}]");
        attach(); tick(5); tick(10); assertEquals(160,hp());
        assertEquals(1f,bearer.getGlobalAbilityValues().get("healed"));
        assertEquals(1f,bearer.getGlobalAbilityValues().get("skipped"));
    }
    @Test void nativeNestedCallbacksAndTheirLateRemovalCannotRepeatAPulse() throws Exception {
        configure(",\"onThinkInterval\":[{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"HealHP\",\"amount\":10},{\"$type\":\"AddGlobalValue\",\"key\":\"pulses\",\"value\":1}]}]");
        bearer.getInstancedAbilities().add(ability); client(true,71); tick(5);
        var wrapper=ability.getData().modifiers.get(modifierName).onThinkInterval[0];
        invokeAction(71,wrapper); invokeAction(71,wrapper.successActions[0]); invokeAction(71,wrapper.successActions[1]);
        client(false,71); invokeAction(71,wrapper.successActions[0]); invokeAction(71,wrapper.successActions[1]);
        Thread.sleep(40); assertEquals(110,hp()); assertEquals(1f,bearer.getGlobalAbilityValues().get("pulses"));
    }
    @Test void conditionsAlsoGuardStateWritesAndNestedActions() {
        configure(",\"onThinkInterval\":[{\"$type\":\"SetGlobalValue\",\"key\":\"root\",\"value\":1,\"predicates\":[{\"$type\":\"ByTargetHPRatio\",\"HPRatio\":0.5,\"logic\":\"Greater\"}]},{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"child\",\"value\":1,\"predicates\":[{\"$type\":\"ByTargetHPRatio\",\"HPRatio\":0.5,\"logic\":\"Greater\"}]}]}]");
        attach(); tick(5); assertFalse(bearer.getGlobalAbilityValues().containsKey("root"));
        assertFalse(bearer.getGlobalAbilityValues().containsKey("child"));
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,600); tick(10);
        assertEquals(1f,bearer.getGlobalAbilityValues().get("root")); assertEquals(1f,bearer.getGlobalAbilityValues().get("child"));
    }
    @Test void aRecastBeforeConfirmationDoesNotAdoptTheSupersededClock() throws Exception {
        configure(""); attach(); tick(4); attach(); now.set(4_500_000_000L); client(true,71);
        tick(5); assertEquals(100,hp()); tick(9); assertEquals(110,hp());
    }
    @Test void nestedCasterTargetsWriteToTheCasterAndNotTheBearer() {
        configure(",\"onThinkInterval\":[{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"casterPulse\",\"value\":1,\"target\":\"Caster\"}]}]");
        attach(); tick(5); assertEquals(1f,fixture.entity.getGlobalAbilityValues().get("casterPulse"));
        assertFalse(bearer.getGlobalAbilityValues().containsKey("casterPulse"));
    }
    @Test void unsupportedNestedActionsLeaveTheEntireChainClientOwned() {
        configure(",\"onThinkInterval\":[{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"HealHP\",\"amount\":10}],\"failActions\":[{\"$type\":\"DamageByAttackValue\"}]}]");
        attach(); tick(5); assertEquals(100,hp());
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get(modifierName).onThinkInterval[0].successActions[0]));
    }
    @Test void unsupportedConditionsDoNotBecomeUnconditionalServerEffects() {
        configure(",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10,\"predicates\":[{\"$type\":\"UnknownClientPredicate\"}]}]");
        attach(); tick(5); assertEquals(100,hp());
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get(modifierName).onThinkInterval[0]));
    }
    @Test void cyclicActionTreesAreRejectedWithoutStartingATimer() {
        configure(",\"onThinkInterval\":[{\"$type\":\"Predicated\"}]");
        var actions=ability.getData().modifiers.get(modifierName).onThinkInterval;
        actions[0].successActions=actions;
        assertDoesNotThrow(this::attach); tick(5);
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,actions[0]));
    }
    @Test void shapeSelectedPeriodicActionsOnlyAffectNearbyAlliedAvatars() {
        configure(",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10,\"otherTargets\":{\"$type\":\"SelectTargetsByShape\",\"shapeName\":\"CircleR6\",\"campTargetType\":\"Alliance\",\"entityTypes\":[\"Avatar\"]}}]");
        var near=avatar(44); var far=avatar(45); near.getPosition().setX(5); far.getPosition().setX(7);
        attach(); tick(5); assertEquals(110,near.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(100,far.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)); assertEquals(100,hp());
    }
    @Test void aZeroRadiusSelectorCannotTurnIntoSceneWideHealing() {
        configure(",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10,\"otherTargets\":{\"$type\":\"SelectTargetsByShape\",\"shapeName\":\"CircleR0\",\"campTargetType\":\"Alliance\",\"entityTypes\":[\"Avatar\"]}}]");
        var far=avatar(44); far.getPosition().setX(100);
        attach(); tick(5); assertEquals(100,far.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get(modifierName).onThinkInterval[0]));
    }
    private void resource(String character,String name,String modifier) throws Exception {
        var configs=JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"+character+".json"),AbilityConfigData.class);
        var data=configs.stream().map(c->c.Default).filter(d->name.equals(d.abilityName)).findFirst().orElseThrow();
        ability=new Ability(data,fixture.entity,fixture.player); fixture.entity.getInstancedAbilities().add(ability); modifierName=modifier;
    }
    private EntityAvatar avatar(int id) {
        var value=new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public void save() {}
        };
        value.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000); value.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        var position=new Position();
        var avatar=new EntityAvatar(fixture.scene,value) {
            @Override public Position getPosition() { return position; }
        };
        avatar.setId(id);
        fixture.scene.getEntities().put(id,avatar); fixture.player.getTeamManager().getActiveTeam().add(avatar); return avatar;
    }
    @Test void qiqisActualModifierUsesItsResourceFormulaAndFollowsTheSelectedAvatar() throws Exception {
        resource("Qiqi","Avatar_Qiqi_ElementalArt_Bullet","Qiqi_ElementalArt_HealHp_Handler");
        ability.getAbilitySpecials().put("ElementalArt_HealHp_Ratio",0.5f); ability.getAbilitySpecials().put("ElementalArt_HealHp_Const",10);
        var first=avatar(44); var second=avatar(45); attach(); tick(4.999);
        assertEquals(100,first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)); tick(5);
        assertEquals(160,first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        fixture.player.getTeamManager().setCurrentCharacterIndex(1); tick(10); tick(15); tick(20);
        assertEquals(220,second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(160,first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void barbarasActualModifierStopsWhenRemovedBeforeItsNominalDuration() throws Exception {
        resource("Barbara","Avatar_Barbara_WetShiled_Heal","WetShiled_Buff");
        ability.getAbilitySpecials().put("HealHP",0.05f); ability.getAbilitySpecials().put("HealHP_Const",10);
        var active=avatar(44); attach(); tick(5); tick(10);
        ability.removeModifier(bearer,modifierName); tick(15); tick(25);
        assertEquals(220,active.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void sigewinnesActualMixedModifierCountsBeforeHealingAndStopsAfterFivePulses() throws Exception {
        resource("Sigewinne","Avatar_Sigewinne_PermanentSkill_3","Avatar_Sigewinne_DiveAid_Predicate_Heal");
        String key="_ABILITY_Sigewinne_PermanentSkill_3_Heal_Count";
        attach(); tick(0.499); assertEquals(100,hp());
        for(int pulse=1;pulse<=5;pulse++) { tick(pulse*0.5); assertEquals(100+100*pulse,hp(),0.001); }
        tick(3); tick(3.5); tick(4); tick(4.5);
        assertEquals(600,hp(),0.001); assertEquals(6f,bearer.getGlobalAbilityValues().get(key));
        ability.removeModifier(bearer,modifierName); assertEquals(0f,bearer.getGlobalAbilityValues().get(key));
        tick(5); assertEquals(600,hp(),0.001);
    }
    @Test void partyWideHealingDoesNotRepeatADuplicateTeamEntity() {
        configure(",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10,\"target\":\"AllPlayerAvatars\"}]");
        var first=avatar(44); var second=avatar(45); fixture.player.getTeamManager().getActiveTeam().add(first);
        attach(); tick(5); assertEquals(110,first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(110,second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
}

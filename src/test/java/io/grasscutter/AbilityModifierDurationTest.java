package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityModifierDurationTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    @BeforeEach void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,100);
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String fields) {
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"TimedState\",\"modifiers\":{\"Buff\":{\"duration\":0.001,\"properties\":{\"Actor_AttackDelta\":20},\"onRemoved\":[{\"$type\":\"AddGlobalValue\",\"key\":\"ended\",\"value\":1}]"+fields+"}}}",AbilityData.class),fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }
    private AbilityModifierController attach() {
        var controller=new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff"));
        ability.registerModifier(fixture.entity,controller); return controller;
    }
    private void afterDeadline() throws Exception { Thread.sleep(15); fixture.entity.onTick(0); }
    private float attack() { return fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK); }
    private float ended() { return fixture.entity.getGlobalAbilityValues().getOrDefault("ended",0f); }
    @Test void aServerStateExpiresRestoresItsPropertyAndRunsItsEndActionOnlyOnce() throws Exception {
        configure(""); attach(); assertEquals(120,attack()); afterDeadline();
        assertEquals(100,attack()); assertFalse(ability.getModifiers().containsKey("Buff"));
        assertEquals(1,ended()); afterDeadline(); assertEquals(1,ended());
    }
    @Test void explicitRemovalRunsTheEndActionWithoutWaitingForTheTimer() {
        configure(",\"duration\":0"); attach(); assertTrue(ability.removeModifier(fixture.entity,"Buff"));
        assertEquals(100,attack()); assertEquals(1,ended());
        assertFalse(ability.removeModifier(fixture.entity,"Buff")); assertEquals(1,ended());
    }
    @Test void expirationRemovesEachPropertylessMultipleInstance() throws Exception {
        configure(",\"properties\":{},\"stacking\":\"Multiple\""); attach(); attach(); afterDeadline();
        assertEquals(2,ended()); assertFalse(ability.getModifiers().containsKey("Buff"));
    }
    @Test void anOldUniqueDeadlineCannotRemoveANewReplacement() throws Exception {
        configure(",\"stacking\":\"Unique\""); attach(); Thread.sleep(15);
        ability.getData().modifiers.get("Buff").duration = new emu.grasscutter.data.common.DynamicFloat(60);
        var current=attach(); fixture.entity.onTick(0);
        assertSame(current,ability.getModifiers().get("Buff")); assertEquals(120,attack()); assertEquals(0,ended());
    }
    @Test void clientConfirmationHandsThePendingStateBackToClientRemoval() throws Exception {
        configure(""); attach();
        var client=new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff"));
        fixture.entity.getInstancedModifiers().put(71,client); ability.registerModifier(fixture.entity,client);
        afterDeadline(); assertSame(client,fixture.entity.getInstancedModifiers().get(71));
        assertEquals(120,attack()); assertEquals(0,ended());
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aPendingTimerKeepsAgingWhileTheSameNameClientInstanceMasksItsProperties(boolean expireWhileMasked) {
        var now = new java.util.concurrent.atomic.AtomicLong();
        var target = new AbilityTestFixture.TestEntity(fixture.scene) {
            final AbilityModifierDuration duration = new AbilityModifierDuration(this, now::get);
            @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
        };
        target.setId(43);
        fixture.scene.getEntities().put(43, target);
        target.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 100);
        configure(",\"duration\":0.5");
        var client = new AbilityModifierController(ability, ability.getData(), ability.getData().modifiers.get("Buff"));
        target.getInstancedModifiers().put(71, client);
        ability.registerModifier(target, client);
        var pending = new AbilityModifierController(ability, ability.getData(), ability.getData().modifiers.get("Buff"));
        ability.registerModifier(target, pending);
        assertEquals(120, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), "same-name properties do not stack");
        target.onTick(0);
        now.set(500_000_000L);
        if (expireWhileMasked) {
            target.onTick(0);
            assertSame(client, target.getInstancedModifiers().get(71));
            assertFalse(ability.getModifiers(target).containsValue(pending), "expiry removes only the server instance");
            assertEquals(1, target.getGlobalAbilityValues().getOrDefault("ended", 0f));
        }
        target.getInstancedModifiers().remove(71);
        ability.unregisterModifier(target, client);
        target.onTick(0);
        assertEquals(100, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        assertFalse(ability.getModifiers(target).containsValue(pending));
        assertEquals(1, target.getGlobalAbilityValues().getOrDefault("ended", 0f));
        target.onTick(0);
        assertEquals(1, target.getGlobalAbilityValues().getOrDefault("ended", 0f), "the expired instance ends once");
    }
    @Test void aPendingMultipleInstanceExpiresWithoutRemovingAnOlderClientInstance() throws Exception {
        configure(",\"stacking\":\"Multiple\"");
        var client=new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff"));
        fixture.entity.getInstancedModifiers().put(71,client); ability.registerModifier(fixture.entity,client);
        attach(); assertEquals(140,attack()); afterDeadline();
        assertSame(client,fixture.entity.getInstancedModifiers().get(71));
        assertEquals(120,attack()); assertEquals(1,ended());
    }
    @Test void ownerInvalidationDiscardsTheTimerWithoutExecutingAnOldEndAction() throws Exception {
        configure(""); attach(); fixture.entity.invalidateAbilityLifecycle();
        fixture.entity.activateAbilityLifecycle(); afterDeadline(); assertEquals(0,ended());
    }
    @Test void aLateDurationParameterCanExpireAnAlreadyAttachedState() throws Exception {
        configure(",\"duration\":\"period\""); attach();
        ability.getAbilitySpecials().put("period",0.001f); ability.refreshModifierTargets(false);
        afterDeadline(); assertEquals(100,attack()); assertEquals(1,ended());
    }
    @Test void endActionsRunInResourceOrderAndCanCreateAFollowUpState() throws Exception {
        configure(",\"onRemoved\":[{\"$type\":\"SetGlobalValue\",\"key\":\"ended\",\"value\":1},{\"$type\":\"AddGlobalValue\",\"key\":\"ended\",\"value\":2},{\"$type\":\"ApplyModifier\",\"modifierName\":\"FollowUp\"}]");
        ability.getData().modifiers.put("FollowUp",JsonUtils.decode("{\"duration\":60,\"properties\":{\"Actor_AttackDelta\":5}}",emu.grasscutter.data.binout.AbilityModifier.class));
        attach(); afterDeadline(); assertEquals(3,ended());
        assertTrue(ability.getModifiers().containsKey("FollowUp")); assertEquals(105,attack());
    }
    @Test void aFinalHealingPulseRunsBeforeExpiryAndNoPulseRunsAfterRemoval() {
        var now=new java.util.concurrent.atomic.AtomicLong();
        var target=new AbilityTestFixture.TestEntity(fixture.scene) {
            final AbilityPeriodicHealing healing=new AbilityPeriodicHealing(this,now::get);
            final AbilityModifierDuration duration=new AbilityModifierDuration(this,now::get);
            @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return healing; }
            @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
        };
        target.setId(43); fixture.scene.getEntities().put(43,target);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        configure(",\"duration\":10,\"thinkInterval\":5,\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10,\"muteHealEffect\":true}]");
        var controller=new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff"));
        ability.registerModifier(target,controller);
        now.set(5_000_000_000L); target.onTick(0);
        assertEquals(110,target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        now.set(10_000_000_000L); target.onTick(0);
        assertEquals(120,target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertFalse(ability.getModifiers(target).containsKey("Buff"));
        assertEquals(1,target.getGlobalAbilityValues().getOrDefault("ended",0f));
        now.set(20_000_000_000L); target.onTick(0);
        assertEquals(120,target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void endActionsResolveCasterTargetsAndCheckTheirPredicates() throws Exception {
        var target=new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43); fixture.scene.getEntities().put(43,target);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,50);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        configure(",\"onRemoved\":[{\"$type\":\"SetGlobalValue\",\"target\":\"Caster\",\"key\":\"ended\",\"value\":2,\"predicates\":[{\"$type\":\"ByTargetHPRatio\",\"HPRatio\":0.7,\"logic\":\"LesserOrEqual\"}]},{\"$type\":\"SetGlobalValue\",\"target\":\"Self\",\"key\":\"ended\",\"value\":3,\"predicates\":[{\"$type\":\"ByTargetHPRatio\",\"HPRatio\":0.7,\"logic\":\"LesserOrEqual\"}]}]");
        ability.registerModifier(target,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff")));
        Thread.sleep(15); target.onTick(0);
        assertEquals(2,ended()); assertEquals(0,target.getGlobalAbilityValues().getOrDefault("ended",0f));
    }
    @Test void partyEndActionsReachEachTeamEntityOnce() throws Exception {
        var members=new java.util.ArrayList<emu.grasscutter.game.entity.EntityAvatar>();
        for(int id:new int[]{44,45}) {
            var value=new emu.grasscutter.game.avatar.Avatar() {
                @Override public emu.grasscutter.game.player.Player getPlayer() { return fixture.player; }
                @Override public void save() {}
            };
            value.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
            value.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
            var member=new emu.grasscutter.game.entity.EntityAvatar(fixture.scene,value);
            member.setId(id); fixture.scene.getEntities().put(id,member);
            members.add(member); fixture.player.getTeamManager().getActiveTeam().add(member);
        }
        fixture.player.getTeamManager().getActiveTeam().add(members.get(0));
        configure(",\"onRemoved\":[{\"$type\":\"AddGlobalValue\",\"target\":\"AllPlayerAvatars\",\"key\":\"ended\",\"value\":1}]");
        attach(); afterDeadline();
        for(var member:members) assertEquals(1,member.getGlobalAbilityValues().getOrDefault("ended",0f));
        assertEquals(0,ended());
    }
}

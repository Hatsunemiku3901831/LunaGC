package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

class AbilityPeriodicModifierTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private final AtomicLong now=new AtomicLong();
    private AbilityTestFixture.TestEntity bearer;
    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture();
        bearer=new AbilityTestFixture.TestEntity(fixture.scene) {
            final AbilityPeriodicHealing periodic=new AbilityPeriodicHealing(this,now::get);
            final AbilityModifierDuration duration=new AbilityModifierDuration(this,now::get);
            @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return periodic; }
            @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
        };
        bearer.setId(43); fixture.scene.getEntities().put(43,bearer);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,100);
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String actions,String childFields) {
        configure(actions,childFields,"");
    }
    private void configure(String actions,String childFields,String rootFields) {
        var data=JsonUtils.decode("{\"abilityName\":\"PeriodicStates\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":5,\"duration\":60,\"onThinkInterval\":"+actions+rootFields+"},\"Child\":{\"stacking\":\"Unique\",\"properties\":{\"Actor_AttackDelta\":20},\"onAdded\":[{\"$type\":\"AddGlobalValue\",\"key\":\"started\",\"value\":1}],\"onRemoved\":[{\"$type\":\"AddGlobalValue\",\"key\":\"ended\",\"value\":1}]"+childFields+"}}}",AbilityData.class);
        ability=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability); bearer.getInstancedAbilities().add(ability);
        ability.registerModifier(bearer,new AbilityModifierController(ability,data,data.modifiers.get("Pulse")));
    }
    private void tick(double seconds) { now.set((long)(seconds*1e9)); bearer.onTick(0); }
    private float value(String key) { return bearer.getGlobalAbilityValues().getOrDefault(key,0f); }
    private float attack() { return bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK); }
    private void client(String name,boolean add,int id) throws Exception {
        var change=AbilityMetaModifierChange.newBuilder().setAction(add?ModifierAction.MODIFIER_ACTION_ADDED:ModifierAction.MODIFIER_ACTION_REMOVED)
                .setParentAbilityName(AbilityString.newBuilder().setStr(ability.getData().abilityName))
                .setModifierLocalId(new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name));
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(42).setInstancedAbilityId(1).setInstancedModifierId(id))
                .setAbilityData(change.build().toByteString()).build());
    }
    private void invoke(int id,AbilityModifier.AbilityModifierAction action) {
        int local=ability.getData().localIdToAction.entrySet().stream().filter(e->e.getValue()==action).findFirst().orElseThrow().getKey();
        fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(43).setInstancedAbilityId(1).setInstancedModifierId(id).setLocalId(local)).build());
    }
    @Test void aPeriodicApplyCreatesTheStateAndRunsItsInitializationOnce() {
        configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]","");
        tick(5); assertEquals(120,attack()); assertEquals(1,value("started"));
        tick(5); assertEquals(1,value("started"));
        tick(10); assertEquals(120,attack()); assertEquals(2,value("started")); assertEquals(1,value("ended"));
    }
    @Test void periodicAttachAndRemoveRunOrderedCallbacksOnTheirConfiguredTargets() {
        configure("[{\"$type\":\"Predicated\",\"targetPredicates\":[{\"$type\":\"ByHasModifier\",\"modifierName\":\"Child\"}],\"successActions\":[{\"$type\":\"RemoveModifier\",\"modifierName\":\"Child\"}],\"failActions\":[{\"$type\":\"AttachModifier\",\"modifierName\":\"Child\"}]}]",
                ",\"onAdded\":[{\"$type\":\"SetGlobalValue\",\"key\":\"heal\",\"value\":25,\"target\":\"Caster\"},{\"$type\":\"HealHP\",\"amount\":\"heal\"}]");
        tick(5); assertEquals(125,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(25f,fixture.entity.getGlobalAbilityValues().get("heal")); assertEquals(120,attack());
        tick(10); assertEquals(100,attack()); assertEquals(1,value("ended"));
    }
    @Test void confirmationAndNativeInitializationCannotRepeatServerCallbacks() throws Exception {
        configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]",""); tick(5);
        var pending=ability.getModifiers(bearer).get("Child"); client("Child",true,71);
        assertSame(pending,bearer.getInstancedModifiers().get(71));
        client("Child",true,71); invoke(71,ability.getData().modifiers.get("Child").onAdded[0]);
        Thread.sleep(40); assertEquals(1,value("started")); assertEquals(120,attack());
    }
    @Test void removingAConfirmedChildRunsItsEndOnceAndIgnoresLateNativeCallbacks() throws Exception {
        configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]",""); tick(5); client("Child",true,71);
        assertEquals(1,value("started"));
        client("Child",false,71); invoke(71,ability.getData().modifiers.get("Child").onRemoved[0]);
        client("Child",false,71); Thread.sleep(40);
        assertEquals(1,value("ended")); assertEquals(100,attack());
    }
    @Test void removingThePeriodicParentStopsTheRemainingActionsInThatPulse() {
        configure("[{\"$type\":\"RemoveModifier\",\"modifierName\":\"Pulse\"},{\"$type\":\"HealHP\",\"amount\":10}]","");
        tick(5); assertFalse(ability.getModifiers(bearer).containsKey("Pulse"));
        assertEquals(100,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void unconfirmedChildrenExpireAndEndWithoutRepeatingInitialization() {
        configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]",",\"duration\":1");
        tick(5); tick(6); tick(7); assertEquals(1,value("started")); assertEquals(1,value("ended")); assertEquals(100,attack());
    }
    @Test void unsupportedChildInitializationOrEndKeepsTheWholePeriodicChainClientOwned() {
        for(String callback:new String[]{"onAdded","onRemoved"}) {
            configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]",",\""+callback+"\":[{\"$type\":\"DamageByAttackValue\"}]");
            now.set(0); tick(5); assertEquals(100,attack()); assertFalse(ability.getModifiers(bearer).containsKey("Child"));
        }
    }
    @Test void recursiveChildInitializationCannotBecomeAnUnboundedServerChain() {
        configure("[{\"$type\":\"AttachModifier\",\"modifierName\":\"Child\"}]",",\"onAdded\":[{\"$type\":\"AttachModifier\",\"modifierName\":\"Child\"}]");
        assertDoesNotThrow(()->tick(5)); assertEquals(100,attack()); assertFalse(ability.getModifiers(bearer).containsKey("Child"));
    }
    @Test void multipleConfirmationsAdoptOnePendingInstanceEachAndPreserveTheOtherTimer() throws Exception {
        configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]",",\"stacking\":\"Multiple\",\"duration\":6");
        tick(5); tick(10); assertEquals(140,attack()); assertEquals(2,value("started"));
        client("Child",true,71); assertEquals(140,attack()); assertEquals(2,value("started"));
        tick(11); assertEquals(140,attack());
        tick(16); assertEquals(140,attack()); // third pulse adds one before the second pending instance expires
        assertEquals(1,value("ended")); assertEquals(3,value("started"));
        client("Child",true,72); assertEquals(140,attack()); assertEquals(3,value("started"));
        client("Child",false,71); assertEquals(120,attack()); assertEquals(2,value("ended"));
        client("Child",false,72); assertEquals(100,attack()); assertEquals(3,value("ended"));
    }
    @Test void aClientOwnedParentsEndIsNotSuppressedByAReferencedDefinition() throws Exception {
        configure("[{\"$type\":\"RemoveModifier\",\"modifierName\":\"Pulse\"}]", "",
                ",\"onRemoved\":[{\"$type\":\"AddGlobalValue\",\"key\":\"rootEnded\",\"value\":1}]");
        client("Pulse",true,72); tick(5);
        invoke(72,ability.getData().modifiers.get("Pulse").onRemoved[0]); Thread.sleep(40);
        assertEquals(1,value("rootEnded"));
    }
    @Test void nativeStatesKeepTheirExistingReapplicationBehavior() throws Exception {
        configure("[{\"$type\":\"HealHP\",\"amount\":10}]",
                ",\"onAdded\":[{\"$type\":\"SetGlobalValue\",\"key\":\"started\",\"value\":1}]");
        client("Child",true,71); assertEquals(1,value("started"));
        var first=bearer.getInstancedModifiers().get(71);
        bearer.getGlobalAbilityValues().put("started",0f);
        client("Child",true,71); assertEquals(1,value("started"));
        assertNotSame(first,bearer.getInstancedModifiers().get(71));
    }
    @Test void partyAttachmentInitializesEachDistinctAvatarOnce() {
        configure("[{\"$type\":\"AttachModifier\",\"modifierName\":\"Child\",\"target\":\"AllPlayerAvatars\"}]","");
        var members=new ArrayList<EntityAvatar>();
        for(int id:new int[]{44,45}) {
            var avatar=new Avatar() {
                @Override public Player getPlayer() { return fixture.player; }
                @Override public void save() {}
            };
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
            var entity=new EntityAvatar(fixture.scene,avatar); entity.setId(id);
            fixture.scene.getEntities().put(id,entity); fixture.player.getTeamManager().getActiveTeam().add(entity); members.add(entity);
        }
        fixture.player.getTeamManager().getActiveTeam().add(members.get(0)); tick(5);
        for(var member:members) assertEquals(1f,member.getGlobalAbilityValues().get("started"));
        assertEquals(0,value("started"));
    }
    @Test void ownerInvalidationCannotRunAConfirmedChildsOldEnd() throws Exception {
        configure("[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]",""); tick(5); client("Child",true,71);
        fixture.entity.invalidateAbilityLifecycle(); client("Child",false,71); assertEquals(0,value("ended"));
    }
    @Test void iansansActualDelayAppliesTheNextStateAndRemovesItself() throws Exception {
        var configs=JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Iansan.json"),AbilityConfigData.class);
        var data=configs.stream().map(c->c.Default).filter(d->"Avatar_Iansan_NyxCycle".equals(d.abilityName)).findFirst().orElseThrow();
        ability=new Ability(data,fixture.entity,fixture.player); fixture.entity.getInstancedAbilities().add(ability);
        String name="Avatar_Iansan_NyxCost_Delay";
        ability.registerModifier(bearer,new AbilityModifierController(ability,data,data.modifiers.get(name)));
        tick(0.009); assertFalse(ability.getModifiers(bearer).containsKey("Avatar_Iansan_NyxCost_Real"));
        tick(0.011); assertFalse(ability.getModifiers(bearer).containsKey(name));
        var current=ability.getModifiers(bearer).get("Avatar_Iansan_NyxCost_Real"); assertNotNull(current);
        tick(1); assertSame(current,ability.getModifiers(bearer).get("Avatar_Iansan_NyxCost_Real"));
    }
    @Test void gorousActualChargeStacksStopAtTheResourceLimitAndClearOnRemoval() throws Exception {
        var configs=JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Gorou.json"),AbilityConfigData.class);
        var data=configs.stream().map(c->c.Default).filter(d->"Avatar_Gorou_ShootAimingArrow_Charge".equals(d.abilityName)).findFirst().orElseThrow();
        ability=new Ability(data,fixture.entity,fixture.player); fixture.entity.getInstancedAbilities().add(ability);
        String name="ShootAimingArrow_Charge_Handler";
        assertNotNull(data.modifiers.get("AimChargeMark").stackingOption);
        ability.registerModifier(bearer,new AbilityModifierController(ability,data,data.modifiers.get(name)));
        for(int i=1;i<=20;i++) tick(i*0.301);
        assertTrue(ability.getModifiers(bearer).containsKey("AimChargeMark"));
        assertEquals(5,value("_Enchanted_Time"));
        for(int id=71;id<=75;id++) {
            client("AimChargeMark",true,id);
            assertTrue(bearer.getInstancedModifiers().get(id).hasServerCallbacks());
        }
        client("AimChargeMark",true,76);
        assertFalse(bearer.getInstancedModifiers().get(76).hasServerCallbacks()); // exactly five pending server slots
        ability.removeModifier(bearer,name); ability.removeModifier(bearer,"AimChargeMark");
        assertFalse(ability.getModifiers(bearer).containsKey("AimChargeMark"));
    }
}

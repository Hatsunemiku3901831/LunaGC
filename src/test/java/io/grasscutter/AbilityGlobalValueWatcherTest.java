package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.*;

class AbilityGlobalValueWatcherTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity target;
    @BeforeEach void setup() throws Exception {
        fixture=new AbilityTestFixture(); target=new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43); fixture.scene.getEntities().put(43,target);
        target.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,200);
    }
    @AfterEach void close() { fixture.close(); }
    private Ability ability(String mixin,boolean root) {
        var data=JsonUtils.decode("{\"abilityName\":\"Watcher\",\"modifiers\":{\"Low\":{\"properties\":{\"Actor_AttackDelta\":10}},\"High\":{\"properties\":{\"Actor_AttackDelta\":30}},\"Listener\":{\"modifierMixins\":["+mixin+"]}}"+(root?",\"abilityMixins\":["+mixin+"]":"")+"}",AbilityData.class);
        var ability=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        return ability;
    }
    private String mixin(String fields) {
        return "{\"$type\":\"AttachModifierToGlobalValueMixin\",\"globalValueKey\":\"GV_mode\",\"modifierNameSteps\":[\"Low\",\"High\"],\"valueSteps\":[0,1,2]"+fields+"}";
    }
    private void listen(Ability ability) {
        ability.registerModifier(target,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Listener")));
    }
    private void update(AbilityTestFixture.TestEntity entity,float value) {
        entity.getGlobalAbilityValues().put("GV_mode",value); entity.onAbilityValueUpdate();
    }
    private float attack() { return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK); }

    @Test void anAttachedListenerSelectsHalfOpenRangesAndRestoresOutsideTheFinalBound() {
        var ability=ability(mixin(""),false); listen(ability); assertEquals(210,attack());
        update(target,1); assertEquals(230,attack());
        update(target,2); assertEquals(200,attack());
        update(target,-1); assertEquals(200,attack());
        update(target,0); assertEquals(210,attack());
    }
    @Test void updatesFromAnotherSourceRefreshTheActualBearer() {
        var ability=ability(mixin(",\"globalValueTarget\":\"Owner\""),false);
        update(fixture.entity,1); listen(ability); assertEquals(230,attack());
        update(fixture.entity,0); assertEquals(210,attack());
        update(target,1); assertEquals(210,attack());
    }
    @Test void repeatedUpdatesDoNotStackOrRepeatOnAddedActions() {
        var ability=ability(mixin(""),false);
        ability.getData().modifiers.get("Low").onAdded=new AbilityModifier.AbilityModifierAction[]{JsonUtils.decode("{\"$type\":\"AddGlobalValue\",\"key\":\"GV_applied\",\"value\":1}",AbilityModifier.AbilityModifierAction.class)};
        listen(ability); for(int i=0;i<5;i++)update(target,0);
        assertEquals(210,attack()); assertEquals(1f,target.getGlobalAbilityValues().get("GV_applied"));
    }
    @Test void removingTheParentRemovesItsDerivedBuffAndSubscription() {
        var ability=ability(mixin(",\"globalValueTarget\":\"Owner\""),false);
        listen(ability); assertEquals(210,attack());
        ability.removeModifier(target,"Listener"); assertEquals(200,attack());
        update(fixture.entity,1); assertEquals(200,attack());
    }
    @Test void canonicalAndLegacyRemoveFlagsRetainDifferentValuesAndCanonicalWins() {
        var json=mixin(",\"DEFOFMOIAAI\":true,\"removeAppliedModifier\":false");
        var ability=ability(json,false); listen(ability); update(target,1); update(target,2);
        assertEquals(240,attack());
        var roundtrip=com.google.gson.JsonParser.parseString(JsonUtils.encode(ability.getData().modifiers.get("Listener").modifierMixins[0])).getAsJsonObject();
        assertFalse(roundtrip.get("removeAppliedModifier").getAsBoolean());
        assertTrue(roundtrip.get("DEFOFMOIAAI").getAsBoolean());
        ability.removeModifier(target,"Listener"); assertEquals(200,attack());
    }
    @Test void invalidBoundsDoNotProduceABuffAndValidParameterUpdatesCanRecover() {
        var ability=ability(mixin("").replace("[0,1,2]","[0,\"threshold\",2]"),false);
        ability.getAbilitySpecials().put("threshold",Float.NaN); listen(ability); assertEquals(200,attack());
        ability.getAbilitySpecials().put("threshold",1f); ability.refreshModifierTargets(true); assertEquals(210,attack());
    }
    @Test void unknownSourcesDoNotFallBackToTheBearer() {
        update(target,1); var ability=ability(mixin(",\"globalValueTarget\":\"UnresolvedTarget\""),false);
        listen(ability); assertEquals(200,attack());
    }
    @Test void entityRemovalCancelsTheDerivedStateAndDoesNotReviveItOnAnOldSourceUpdate() {
        var ability=ability(mixin(",\"globalValueTarget\":\"Owner\""),false); listen(ability); assertEquals(210,attack());
        target.invalidateAbilityLifecycle(); assertEquals(200,attack());
        update(fixture.entity,1); assertEquals(200,attack());
    }
    @Test void rootBindingsInitializeOnListAdditionAndClearWithoutWaitingForAnotherValueWrite() {
        target.getGlobalAbilityValues().put("GV_mode",1f);
        var data=JsonUtils.decode("{\"abilityName\":\"Root\",\"abilityMixins\":["+mixin("")+"],\"modifiers\":{\"Low\":{\"properties\":{\"Actor_AttackDelta\":10}},\"High\":{\"properties\":{\"Actor_AttackDelta\":30}}}}",AbilityData.class);
        var ability=new Ability(data,target,fixture.player);
        target.getInstancedAbilities().add(ability); assertEquals(230,attack());
        target.getInstancedAbilities().remove(ability); assertEquals(200,attack());
        update(target,0); assertEquals(200,attack());
    }
    @Test void twoListenersSharingAChildRetainItUntilTheLastParentLeaves() {
        var ability=ability(mixin(""),false);
        // Distinct parent definitions preserve controller names and separate leases.
        ability.getData().modifiers.put("SecondListener",JsonUtils.decode("{\"modifierMixins\":["+mixin("")+"]}",AbilityModifier.class));
        listen(ability);
        ability.registerModifier(target,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("SecondListener")));
        assertEquals(210,attack()); ability.removeModifier(target,"Listener"); assertEquals(210,attack());
        ability.removeModifier(target,"SecondListener"); assertEquals(200,attack());
    }
    @Test void anIndependentExistingModifierIsNotRemovedWhenTheListenerLeaves() {
        var ability=ability(mixin(""),false);
        ability.registerModifier(target,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Low")));
        listen(ability); assertEquals(210,attack());
        update(target,1); assertEquals(240,attack());
        ability.removeModifier(target,"Listener"); assertEquals(210,attack());
    }
    @Test void aTransitionPublishesOnlyTheCompleteResult() throws Exception {
        var ability=ability(mixin(""),false); listen(ability); fixture.scene.packets.clear();
        update(target,1);
        var updates=fixture.scene.packets.stream().filter(p->p.getOpcode()==emu.grasscutter.net.packet.PacketOpcodes.EntityFightPropUpdateNotify).toList();
        assertEquals(1,updates.size());
        var packet=emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify.parseFrom(updates.get(0).getData());
        assertEquals(230,packet.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_ATTACK.getId()));
    }
    @Test void silentParameterRefreshDoesNotPublishIntermediateOrDerivedProperties() {
        var ability=ability(mixin("").replace("[0,1,2]","[0,\"threshold\",2]"),false);
        ability.getAbilitySpecials().put("threshold",1); update(target,0.5f); listen(ability); fixture.scene.packets.clear();
        ability.getAbilitySpecials().put("threshold",0.25f); ability.refreshModifierTargets(false);
        assertEquals(230,attack()); assertTrue(fixture.scene.packets.isEmpty());
    }
    @Test void anActiveListenerIdentifiesTheFirstHashedValueAndClearingUsesItsDefault() throws Exception {
        var ability=ability(mixin(""),false); listen(ability);
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_mode"));
        var key=emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString.newBuilder().setHash(emu.grasscutter.utils.Utils.abilityHash("GV_mode"));
        var value=emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry.newBuilder().setKey(key).setFloatValue(1).build();
        fixture.player.getAbilityManager().onAbilityInvoke(emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder().setEntityId(43)
            .setArgumentType(emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE).setAbilityData(value.toByteString()).build());
        assertEquals(230,attack());
        fixture.player.getAbilityManager().onAbilityInvoke(emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder().setEntityId(43)
            .setArgumentType(emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE).setAbilityData(value.toByteString()).build());
        assertEquals(210,attack());
    }
    @Test void sourceSubscriptionsDisappearWithTheLastParent() {
        var ability=ability(mixin(",\"globalValueTarget\":\"Owner\""),false); listen(ability);
        assertTrue(fixture.entity.getAbilityGlobalValueWatchers().getReferencedKeys().contains("GV_mode"));
        ability.removeModifier(target,"Listener");
        assertFalse(fixture.entity.getAbilityGlobalValueWatchers().getReferencedKeys().contains("GV_mode"));
    }
    @Test void disabledListenersDoNotAttachModifiers() {
        for(String type:new String[]{"AttachModifierToGlobalValueMixin","AttachModifierToSelfGlobalValueMixin","AttachModifierToSelfGlobalValueNoInitMixin"}) {
            String json=mixin(",\"enable\":false").replace("AttachModifierToGlobalValueMixin",type);
            var ability=ability(json,false); listen(ability); update(target,1); assertEquals(200,attack());
            ability.removeModifier(target,"Listener");
        }
    }
    @Test void teamWritesRefreshABindingEvenWhenThePartyEntityHasNoOwnStats() {
        fixture.player.setWorld(fixture.scene.getWorld());
        var team=new emu.grasscutter.game.entity.EntityTeam(fixture.player); fixture.player.getTeamManager().setEntity(team);
        var ability=ability(mixin(",\"globalValueTarget\":\"Team\""),false); listen(ability); assertEquals(210,attack());
        team.getGlobalAbilityValues().put("GV_mode",1f); team.onAbilityValueUpdate(); assertEquals(230,attack());
        team.getGlobalAbilityValues().remove("GV_mode"); team.onAbilityValueUpdate(); assertEquals(210,attack());
    }
    @Test void concurrentSourceUpdatesConvergeWithoutSharingStateAcrossBearers() throws Exception {
        var ability=ability(mixin(",\"globalValueTarget\":\"Owner\""),false); listen(ability);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var jobs=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<40;i++) { final int value=i%3; jobs.add(pool.submit(()->update(fixture.entity,value))); }
            for(var job:jobs) job.get(5,java.util.concurrent.TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        update(fixture.entity,1); assertEquals(230,attack());
        assertEquals(2,ability.getModifiers(target).size());
    }
    private void clientModifier(Ability ability,String name,int id,boolean add) throws Exception {
        var change=emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange.newBuilder()
            .setAction(add?emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction.MODIFIER_ACTION_ADDED
                          :emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction.MODIFIER_ACTION_REMOVED);
        if(add) change.setModifierLocalId(new java.util.ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name))
            .setParentAbilityName(emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString.newBuilder().setStr(ability.getData().abilityName));
        fixture.player.getAbilityManager().onAbilityInvoke(emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
            .setEntityId(43).setHead(emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead.newBuilder()
                .setTargetId(42).setInstancedAbilityId(1).setInstancedModifierId(id))
            .setArgumentType(emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
            .setAbilityData(change.build().toByteString()).build());
    }
    @Test void clientParentRegistrationAndRemovalFlushTheDeferredWatcher() throws Exception {
        var ability=ability(mixin(""),false);
        clientModifier(ability,"Listener",71,true); assertEquals(210,attack());
        clientModifier(ability,"Listener",71,false); assertEquals(200,attack());
    }
    @Test void clientConfirmationDoesNotRepeatInitializationOfAWatcherOwnedChild() throws Exception {
        var ability=ability(mixin(""),false);
        ability.getData().modifiers.get("Low").onAdded=new AbilityModifier.AbilityModifierAction[]{JsonUtils.decode("{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"GV_applied\",\"value\":1}]}",AbilityModifier.AbilityModifierAction.class)};
        listen(ability); assertEquals(1f,target.getGlobalAbilityValues().get("GV_applied"));
        clientModifier(ability,"Low",72,true);
        assertEquals(1f,target.getGlobalAbilityValues().get("GV_applied")); assertEquals(210,attack());
        update(target,1); assertEquals(230,attack()); assertFalse(target.getInstancedModifiers().containsKey(72));
    }
    @Test void anExpiredChildIsRestoredWhileItsLiveParentStillRequiresIt() {
        var ability=ability(mixin(""),false); listen(ability);
        ability.removeModifier(target,"Low"); assertEquals(210,attack());
        ability.removeModifier(target,"Listener"); assertEquals(200,attack());
    }
    @Test void invalidatingTheSourceOwnerRemovesItsBuffFromAnotherBearer() {
        var ability=ability(mixin(",\"globalValueTarget\":\"Owner\""),false); listen(ability);
        fixture.entity.invalidateAbilityLifecycle(); assertEquals(200,attack());
        update(fixture.entity,1); assertEquals(200,attack());
    }
    @Test void missingRangesUseThePositiveOnlyFallbackAndMissingValuesUseTheConfiguredDefault() {
        var ability=ability(mixin(",\"defaultGlobalValueOnCreate\":1").replace("[0,1,2]","[]"),false); listen(ability);
        assertEquals(210,attack()); update(target,0); assertEquals(200,attack());
        update(target,-1); assertEquals(200,attack()); update(target,5); assertEquals(210,attack());
    }
    @Test void aCachedDynamicAbilityIsNotAnActiveRootButItsAttachedParentCanListen() {
        var ability=ability(mixin(""),true);
        fixture.entity.getInstancedAbilities().remove(ability);
        target.getDynamicAbilities().put(ability.getData().abilityName,ability);
        target.getAbilityGlobalValueWatchers().refresh(); assertEquals(200,attack());
        listen(ability); assertEquals(210,attack());
        ability.removeModifier(target,"Listener"); assertEquals(200,attack());
    }
    @TestFactory java.util.stream.Stream<DynamicTest> frozenResourceRangesMatchIndependentBoundaryCases() throws Exception {
        var fixtures=JsonUtils.decode(new String(getClass().getResourceAsStream("/ability/global-watcher-fixtures.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),AbilityData[].class);
        var cases=com.google.gson.JsonParser.parseString(new String(getClass().getResourceAsStream("/ability/global-watcher-cases.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
        return java.util.stream.IntStream.range(0,fixtures.length).mapToObj(index->DynamicTest.dynamicTest(fixtures[index].abilityName,()-> {
            var ability=new Ability(fixtures[index],fixture.entity,fixture.player); var test=cases.get(index).getAsJsonObject();
            test.getAsJsonObject("overrides").entrySet().forEach(e->ability.getAbilitySpecials().put(e.getKey(),e.getValue().getAsFloat()));
            String owner=test.get("owner").getAsString(); int mixinIndex=test.get("mixinIndex").getAsInt();
            var mixin=owner.equals("ability")?ability.getData().abilityMixins[mixinIndex]:ability.getData().modifiers.get(owner).modifierMixins[mixinIndex];
            for(var element:test.getAsJsonArray("vectors")) {
                var vector=element.getAsJsonObject();
                assertEquals(vector.get("step").getAsInt(),AbilityGlobalValueWatchers.selectStep(ability,target,mixin,vector.get("value").getAsFloat()),vector.toString());
            }
        }));
    }
}

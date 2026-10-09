package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.gson.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class AbilityPropertyModifierTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity target;
    @BeforeEach void setup() throws Exception {
        fixture=new AbilityTestFixture(); target=new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43); fixture.scene.getEntities().put(43,target); stats(target); stats(fixture.entity);
    }
    @AfterEach void close() { fixture.close(); }
    private void stats(GameEntity entity) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP,100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT,0.5f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,150);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,75);
        entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT,0.5f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_ATTACK,30);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,180);
        entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_DEFENSE,200);
        entity.setFightProperty(FightProperty.FIGHT_PROP_DEFENSE_PERCENT,0.1f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_DEFENSE,40);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE,260);
    }
    private float prop(FightProperty p) { return target.getFightProperty(p); }
    private Ability ability(String properties,String stacking) {
        var data=JsonUtils.decode("{\"abilityName\":\"Stats_Test_"+UUID.randomUUID()+"\",\"modifiers\":{\"Buff\":{\"stacking\":\""+stacking+"\",\"properties\":{"+properties+"}},\"Other\":{}}}",AbilityData.class);
        var value=new Ability(data,fixture.entity,fixture.player); fixture.entity.getInstancedAbilities().add(value); return value;
    }
    private void invoke(int entity,int id,AbilityInvokeArgument type,ByteString data) throws Exception {
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(entity)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(42).setInstancedAbilityId(1).setInstancedModifierId(id))
                .setArgumentType(type).setAbilityData(data).build());
    }
    private void add(Ability a,String name,int id) throws Exception {
        invoke(43,id,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE,
                AbilityMetaModifierChange.newBuilder().setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                    .setModifierLocalId(new ArrayList<>(a.getData().modifiers.keySet()).indexOf(name))
                    .setParentAbilityName(AbilityString.newBuilder().setStr(a.getData().abilityName)).build().toByteString());
    }
    private void remove(int id) throws Exception {
        invoke(43,id,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE,
                AbilityMetaModifierChange.newBuilder().setAction(ModifierAction.MODIFIER_ACTION_REMOVED).build().toByteString());
    }
    private void override(Ability a,String key,float value) throws Exception {
        invoke(42,0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM,
                AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setStr(key)).setFloatValue(value).build().toByteString());
    }
    private AbilityModifier.AbilityModifierAction action(String type) {
        return JsonUtils.decode("{\"$type\":\""+type+"\",\"modifierName\":\"Buff\"}",AbilityModifier.AbilityModifierAction.class);
    }
    private static Map<String,String> mappings() throws Exception {
        try(var stream=AbilityPropertyModifierTest.class.getResourceAsStream("/ability/actor-property-mapping.json")) {
            assertNotNull(stream); return JsonUtils.decode(new String(stream.readAllBytes(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<LinkedHashMap<String,String>>(){}.getType());
        }
    }
    static Stream<Arguments> mappedProperties() throws Exception {
        return mappings().entrySet().stream().map(e->Arguments.of(e.getKey(),FightProperty.valueOf(e.getValue())));
    }
    @ParameterizedTest @MethodSource("mappedProperties")
    void everyUpstreamPropertyUpdatesItsBearerAndRevertsOnNativeRemoval(String name,FightProperty property) throws Exception {
        float before=prop(property); var a=ability("\""+name+"\":0.2","Unique");
        add(a,"Buff",71); assertEquals(before+0.2f,prop(property),0.00001f,name);
        assertEquals(before,fixture.entity.getFightProperty(property),0.00001f,"The caster must not receive the target's buff");
        remove(71); assertEquals(before,prop(property),0.00001f,name);
    }
    @Test void attackRatioAndBothFlatAliasesUpdateDerivedAttackTogether() throws Exception {
        var a=ability("\"Actor_AttackRatio\":0.2,\"Actor_AttackDelta\":50,\"Actor_AttackExtraDelta\":10","Unique");
        add(a,"Buff",71); assertEquals(260,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(90,prop(FightProperty.FIGHT_PROP_ATTACK)); assertEquals(0.7f,prop(FightProperty.FIGHT_PROP_ATTACK_PERCENT),0.00001f);
        remove(71); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f); assertEquals(30,prop(FightProperty.FIGHT_PROP_ATTACK));
    }
    @Test void negativeDefenseRatioRestoresTheOriginalDefense() throws Exception {
        var a=ability("\"Actor_DefenceRatio\":-0.3,\"Actor_DefenceDelta\":-10","Unique");
        add(a,"Buff",71); assertEquals(190,prop(FightProperty.FIGHT_PROP_CUR_DEFENSE),0.0001f);
        remove(71); assertEquals(260,prop(FightProperty.FIGHT_PROP_CUR_DEFENSE),0.0001f);
    }
    @Test void nativeMultipleInstancesStackAndRemoveOneAtATime() throws Exception {
        var a=ability("\"Actor_CriticalDelta\":0.2","Multiple"); add(a,"Buff",71); add(a,"Buff",72);
        assertEquals(0.4f,prop(FightProperty.FIGHT_PROP_CRITICAL),0.00001f); remove(71);
        assertEquals(0.2f,prop(FightProperty.FIGHT_PROP_CRITICAL),0.00001f); remove(72); assertEquals(0,prop(FightProperty.FIGHT_PROP_CRITICAL));
    }
    @Test void nativeUniqueDuplicatesAndLateRemovalKeepTheReplacement() throws Exception {
        var a=ability("\"Actor_CriticalDelta\":0.2","Unique"); add(a,"Buff",71); add(a,"Buff",72); remove(71);
        assertEquals(0.2f,prop(FightProperty.FIGHT_PROP_CRITICAL),0.00001f); remove(72); assertEquals(0,prop(FightProperty.FIGHT_PROP_CRITICAL));
    }
    @Test void serverMultipleStacksCanBeRemovedIndividuallyOrByName() {
        var a=ability("\"Actor_ElemMasteryDelta\":50","Multiple"); var data=a.getData().modifiers.get("Buff");
        var first=new AbilityModifierController(a,a.getData(),data); var second=new AbilityModifierController(a,a.getData(),data);
        a.registerModifier(target,first); a.registerModifier(target,second); assertEquals(100,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
        a.unregisterModifier(target,second); assertEquals(50,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
        assertTrue(a.removeModifier(target,"Buff")); assertEquals(0,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
    }
    @Test void clientConfirmationOfAServerStackDoesNotDoubleItsContribution() throws Exception {
        var a=ability("\"Actor_ElemMasteryDelta\":50","Multiple");
        new ActionApplyModifier().execute(a,action("ApplyModifier"),ByteString.EMPTY,target); add(a,"Buff",71);
        assertEquals(50,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY)); remove(71); assertEquals(0,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
    }
    @Test void reusingAnInstanceIdForAnUnrelatedModifierRestoresTheOldContribution() throws Exception {
        var a=ability("\"Actor_FireSubHurtDelta\":-0.2","Multiple"); add(a,"Buff",71); add(a,"Other",71);
        assertEquals(0,prop(FightProperty.FIGHT_PROP_FIRE_SUB_HURT));
    }
    @Test void parameterOverrideRecalculatesAllAttachedStats() throws Exception {
        var a=ability("\"Actor_AttackRatio\":\"amount\",\"Actor_CriticalDelta\":[\"amount\",0.5,\"MUL\"]","Unique");
        a.getAbilitySpecials().put("amount",0.2f); add(a,"Buff",71); override(a,"amount",0.4f);
        assertEquals(220,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f); assertEquals(0.2f,prop(FightProperty.FIGHT_PROP_CRITICAL),0.00001f);
        remove(71); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void aReinitializedMapPublishesHpAndOtherPropertiesInOneCoherentUpdate() throws Exception {
        var a=ability("\"Actor_MaxHPRatio\":\"amount\",\"Actor_AttackRatio\":\"amount\",\"Actor_CriticalDelta\":\"crit\"","Unique");
        a.getAbilitySpecials().put("amount",0.1f); a.getAbilitySpecials().put("crit",0.1f); add(a,"Buff",71); fixture.scene.packets.clear();
        invoke(42,0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_REINIT_OVERRIDEMAP,
                AbilityMetaReInitOverrideMap.newBuilder()
                    .addOverrideMap(AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setStr("amount")).setFloatValue(0.3f))
                    .addOverrideMap(AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setStr("crit")).setFloatValue(0.2f)).build().toByteString());
        var updates=fixture.scene.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.EntityFightPropUpdateNotify).toList(); assertEquals(1,updates.size());
        var p=EntityFightPropUpdateNotify.parseFrom(updates.get(0).getData()); assertEquals(43,p.getEntityId());
        assertEquals(180,p.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_MAX_HP.getId()),0.0001f);
        assertEquals(210,p.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_ATTACK.getId()),0.0001f);
        assertEquals(0.2f,p.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CRITICAL.getId()),0.00001f);
    }
    @Test void aBearerWithoutHpCanStillReceiveOtherCombatProperties() throws Exception {
        target.getFightProperties().clear(); var a=ability("\"Actor_ElemMasteryDelta\":120","Unique"); add(a,"Buff",71);
        assertEquals(120,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY)); remove(71); assertEquals(0,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
    }
    @Test void initialApplicationUsesTheSameRemovableActorPropertyLayer() {
        var data=JsonUtils.decode("{\"abilityName\":\"Initial_Stats\",\"onAdded\":[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Buff\"}],\"modifiers\":{\"Buff\":{\"properties\":{\"Actor_AttackRatio\":0.2}}}}",AbilityData.class);
        var a=new Ability(data,fixture.entity,fixture.player); assertEquals(200,fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertTrue(a.removeModifier(fixture.entity,"Buff")); assertEquals(180,fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void overflowInAnAttackGroupDoesNotCorruptOtherPropertiesAndCanRecover() throws Exception {
        var a=ability("\"Actor_AttackRatio\":\"amount\",\"Actor_CriticalDelta\":0.2","Unique"); a.getAbilitySpecials().put("amount",0.2f); add(a,"Buff",71);
        override(a,"amount",Float.MAX_VALUE); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(0.5f,prop(FightProperty.FIGHT_PROP_ATTACK_PERCENT),0.00001f); assertEquals(0.2f,prop(FightProperty.FIGHT_PROP_CRITICAL),0.00001f);
        override(a,"amount",0.3f); assertEquals(210,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void repeatedRefreshesOfAFightPropertyExpressionDoNotCompoundThePreviousLayer() throws Exception {
        var a=ability("\"Actor_AttackExtraDelta\":[\"FIGHT_PROP_CUR_ATTACK\",0.5,\"MUL\"]","Unique"); add(a,"Buff",71);
        for(int i=0;i<10;i++) a.refreshModifierTargets(false);
        assertEquals(270,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f); remove(71); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    private static class StatAvatar extends Avatar {
        final Player player; float attack=100; final Set<Integer> proud=new HashSet<>();
        StatAvatar(Player p) { player=p; }
        @Override public Player getPlayer() { return player; }
        @Override public int getAvatarId() { return 10000555; }
        @Override public Map<Integer,Integer> getSkillLevelMap() { return Map.of(); }
        @Override public Set<Integer> getProudSkillList() { return proud; }
        @Override public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() { return it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET; }
        @Override public AvatarData getAvatarData() { return new AvatarData() {
            @Override public float getBaseHp(int level) { return 100; }
            @Override public float getBaseAttack(int level) { return attack; }
            @Override public float getBaseDefense(int level) { return 200; }
        }; }
        @Override public void save() {}
    }
    @Test void avatarRecalculationRetainsEachLayerOnceAndUsesTheNewBaseStats() {
        var avatar=new StatAvatar(fixture.player); avatar.recalcStats(false,false); var entity=new EntityAvatar(fixture.scene,avatar);
        fixture.scene.getEntities().put(entity.getId(),entity); fixture.player.getTeamManager().getActiveTeam().add(entity);
        var a=ability("\"Actor_AttackRatio\":0.2,\"Actor_DefenceRatio\":0.1,\"Actor_CriticalDelta\":0.2","Unique");
        new ActionApplyModifier().execute(a,action("ApplyModifier"),ByteString.EMPTY,entity);
        fixture.scene.packets.clear(); avatar.recalcStats(false,false); avatar.recalcStats(false,false);
        assertEquals(120,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(220,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE),0.0001f);
        assertEquals(0.2f,entity.getFightProperty(FightProperty.FIGHT_PROP_CRITICAL),0.00001f); assertTrue(fixture.scene.packets.isEmpty());
        avatar.attack=150; avatar.recalcStats(false,false); assertEquals(180,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        a.removeModifier(entity,"Buff"); assertEquals(150,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void monsterRecalculationRetainsLayersAndExistingAttackScaling() {
        var data=JsonUtils.decode("{\"id\":21010101,\"hpBase\":100,\"attackBase\":100,\"defenseBase\":200,\"affix\":[],\"equips\":[],\"propGrowCurves\":[]}",emu.grasscutter.data.excels.monster.MonsterData.class);
        var entity=new EntityMonster(fixture.scene,data,new emu.grasscutter.game.world.Position(),new emu.grasscutter.game.world.Position(),1) {
            @Override public void initAbilities() {}
        }; fixture.scene.getEntities().put(entity.getId(),entity); entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,250);
        var a=ability("\"Actor_AttackRatio\":0.2,\"Actor_DefenceRatio\":-0.2","Unique");
        new ActionApplyModifier().execute(a,action("ApplyModifier"),ByteString.EMPTY,entity);
        assertEquals(300,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        entity.recalcStats(); entity.recalcStats(); assertEquals(120,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(160,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE),0.0001f);
        a.removeModifier(entity,"Buff"); assertEquals(100,entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }

    @Test void regularDefenseBuffsFeedExtraAttackWithoutIncludingExtraMasteryInItsOwnSource() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY,100);
        var a=ability("\"Actor_DefenceRatio\":0.2,\"Actor_AttackExtraDelta\":[\"FIGHT_PROP_NONEXTRA_DEFENSE\",0.5,\"MUL\"],"
                +"\"Actor_ElemMasteryDelta\":50,\"Actor_ElemMasteryExtraDelta\":[\"FIGHT_PROP_NONEXTRA_ELEMENT_MASTERY\",0.2,\"MUL\"]","Unique");
        add(a,"Buff",71);
        for(int i=0;i<5;i++) a.refreshModifierTargets(false);
        assertEquals(300,prop(FightProperty.FIGHT_PROP_NONEXTRA_DEFENSE),0.0001f);
        assertEquals(330,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(180,prop(FightProperty.FIGHT_PROP_NONEXTRA_ATTACK),0.0001f);
        assertEquals(150,prop(FightProperty.FIGHT_PROP_NONEXTRA_ELEMENT_MASTERY),0.0001f);
        assertEquals(180,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY),0.0001f);
        remove(71); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(100,prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY),0.0001f);
        assertEquals(100,prop(FightProperty.FIGHT_PROP_NONEXTRA_ELEMENT_MASTERY),0.0001f);
    }
    @Test void addingAndRemovingAnotherHpLayerRefreshesHpToAttackConversion() throws Exception {
        var convert=ability("\"Actor_AttackExtraDelta\":[\"FIGHT_PROP_MAX_HP\",0.1,\"MUL\"]","Unique");
        var hp=ability("\"Actor_MaxHPRatio\":0.2","Unique"); add(convert,"Buff",71);
        assertEquals(195,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        add(hp,"Buff",72); assertEquals(197,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        remove(72); assertEquals(195,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        remove(71); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void firstHashedGlobalUpdateAndClearCanResolveAnyKnownAttachedProperty() throws Exception {
        var a=ability("\"Actor_CriticalDelta\":\"GV_Stat_First\"","Unique"); add(a,"Buff",71);
        var key=AbilityString.newBuilder().setHash(Utils.abilityHash("GV_Stat_First"));
        invoke(43,0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE,
                AbilityScalarValueEntry.newBuilder().setKey(key).setFloatValue(0.2f).build().toByteString());
        assertEquals(0.2f,prop(FightProperty.FIGHT_PROP_CRITICAL),0.00001f);
        invoke(43,0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE,
                AbilityScalarValueEntry.newBuilder().setKey(key).build().toByteString());
        assertEquals(0,prop(FightProperty.FIGHT_PROP_CRITICAL));
    }
    @Test void overflowInAnExtraLayerPreservesValidRegularContributions() throws Exception {
        var a=ability("\"Actor_AttackRatio\":0.2,\"Actor_AttackExtraDelta\":\"extra\"","Unique");
        a.getAbilitySpecials().put("extra",Float.MAX_VALUE); add(a,"Buff",71);
        var second=ability("\"Actor_AttackExtraDelta\":\"extra\"","Unique");
        second.getAbilitySpecials().put("extra",Float.MAX_VALUE); add(second,"Buff",72);
        assertEquals(200,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
        assertEquals(200,prop(FightProperty.FIGHT_PROP_NONEXTRA_ATTACK),0.0001f);
        remove(72);
        override(a,"extra",Float.POSITIVE_INFINITY);
        assertEquals(Float.MAX_VALUE,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),"Invalid client parameters must keep the previous finite value");
        override(a,"extra",0f);
        assertEquals(200,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void unknownNumericPropertiesRoundTripWithoutMappingToAnUnrelatedStat() throws Exception {
        var a=ability("\"Actor_AttackRatio\":null,\"Vendor_Unknown\":[2,\"amount\",\"MUL\"]","Unique");
        var encoded=JsonParser.parseString(JsonUtils.encode(a.getData())).getAsJsonObject();
        assertTrue(encoded.getAsJsonObject("modifiers").getAsJsonObject("Buff").getAsJsonObject("properties").has("Vendor_Unknown"));
        add(a,"Buff",71); assertEquals(180,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }
    @Test void aLargeButFiniteHpLayerRestoresItsOriginalMaximumAndFraction() throws Exception {
        var a=ability("\"Actor_MaxHPRatio\":1e30","Unique"); add(a,"Buff",71);
        assertTrue(Float.isFinite(prop(FightProperty.FIGHT_PROP_MAX_HP))); remove(71);
        assertEquals(150,prop(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(75,prop(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        assertEquals(0.5f,prop(FightProperty.FIGHT_PROP_HP_PERCENT),0.00001f);
    }
    @Test void externalAdditionsDuringAnActiveBuffAreKeptWhenTheBuffIsRemoved() throws Exception {
        var a=ability("\"Actor_AttackRatio\":0.2","Unique"); add(a,"Buff",71);
        target.addFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,40); a.refreshModifierTargets(false);
        assertEquals(240,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f); remove(71);
        assertEquals(220,prop(FightProperty.FIGHT_PROP_CUR_ATTACK),0.0001f);
    }

    private static class ResourceCase {
        String modifier;
        Map<String,Float> inputs;
        Map<String,Float> expected;
    }
    @Test void currentResourcePropertySubtreesProduceTheIndependentlyCalculatedStatsAndCanBeRemoved() throws Exception {
        AbilityData[] data; ResourceCase[] cases;
        try(var definitions=getClass().getResourceAsStream("/ability/actor-stat-modifiers.json");
            var scenarios=getClass().getResourceAsStream("/ability/actor-stat-cases.json")) {
            assertNotNull(definitions); assertNotNull(scenarios);
            data=JsonUtils.decode(new String(definitions.readAllBytes(),StandardCharsets.UTF_8),AbilityData[].class);
            cases=JsonUtils.decode(new String(scenarios.readAllBytes(),StandardCharsets.UTF_8),ResourceCase[].class);
        }
        assertEquals(25,data.length); assertEquals(data.length,cases.length);
        for(int i=0;i<data.length;i++) {
            var before=new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap(target.getFightProperties());
            var a=new Ability(data[i],fixture.entity,fixture.player);
            cases[i].inputs.forEach((key,value)->a.getAbilitySpecials().put(key,value));
            a.registerModifier(target,new AbilityModifierController(a,data[i],data[i].modifiers.get(cases[i].modifier)));
            for(var expected:cases[i].expected.entrySet()) assertEquals(expected.getValue(),
                    prop(FightProperty.valueOf(expected.getKey())),0.0001f,data[i].abilityName+" / "+expected.getKey());
            assertTrue(a.removeModifier(target,cases[i].modifier));
            for(var expected:cases[i].expected.entrySet()) assertEquals(before.getOrDefault(FightProperty.valueOf(expected.getKey()).getId(),0f),
                    prop(FightProperty.valueOf(expected.getKey())),0.0001f,data[i].abilityName+" removal / "+expected.getKey());
        }
    }
}

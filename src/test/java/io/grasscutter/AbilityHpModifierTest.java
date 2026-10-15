package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;

class AbilityHpModifierTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity target;

    @BeforeEach void setup() throws Exception {
        fixture = new AbilityTestFixture();
        target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        fixture.scene.addEntity(target);
        stats(target);
        stats(fixture.entity);
    }
    @AfterEach void close() { fixture.close(); }

    private void stats(AbilityTestFixture.TestEntity entity) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_HP, 20);
        entity.setFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT, 0.5f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 170);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 85);
    }
    private float hp(FightProperty prop) { return target.getFightProperty(prop); }
    private Ability ability(String name, String modifiers) {
        var data = JsonUtils.decode("{\"abilityName\":\""+name+"\",\"modifiers\":"+modifiers+"}", AbilityData.class);
        var result = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(result);
        return result;
    }
    private Ability ratio(String name, String value, String stacking) {
        return ability(name,"{\"Buff\":{\"stacking\":\""+stacking+"\",\"properties\":{\"Actor_MaxHPRatio\":"+value+"}},\"Other\":{}}");
    }
    private void add(Ability ability, String name, int id) throws Exception {
        var data = AbilityMetaModifierChange.newBuilder().setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                .setModifierLocalId(new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name))
                .setParentAbilityName(AbilityString.newBuilder().setStr(ability.getData().abilityName));
        invoke(43,id,data.build().toByteString(),AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE);
    }
    private void remove(int id) throws Exception {
        invoke(43,id,AbilityMetaModifierChange.newBuilder().setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                .build().toByteString(),AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE,0);
    }
    private void invoke(int entityId, int modifierId, ByteString data, AbilityInvokeArgument type) throws Exception {
        invoke(entityId, modifierId, data, type, 1);
    }
    private void invoke(int entityId, int modifierId, ByteString data, AbilityInvokeArgument type, int root) throws Exception {
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(entityId)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(42).setInstancedAbilityId(root)
                        .setInstancedModifierId(modifierId)).setArgumentType(type).setAbilityData(data).build());
    }
    private AbilityModifier.AbilityModifierAction action(String type) {
        return JsonUtils.decode("{\"$type\":\""+type+"\",\"modifierName\":\"Buff\"}",AbilityModifier.AbilityModifierAction.class);
    }

    @Test void nativeHpRatioAddsToBasePercentageAndPreservesTheCurrentFraction() throws Exception {
        var ability=ratio("Hp_One","0.2","Unique");
        add(ability,"Buff",71);
        assertEquals(0.7f,hp(FightProperty.FIGHT_PROP_HP_PERCENT),0.00001f);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(95,hp(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        assertEquals(170,fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        remove(71);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(85,hp(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
    }
    @Test void differentSourcesAddTheirRatiosWithoutCompounding() throws Exception {
        var first=ratio("Hp_First","0.2","Unique");
        var second=ratio("Hp_Second","0.3","Unique");
        add(first,"Buff",71); add(second,"Buff",72);
        assertEquals(220,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(71);
        assertEquals(200,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(72);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void uniqueClientInstancesDoNotDuplicateABuffAndLateRemovalKeepsTheReplacement() throws Exception {
        var ability=ratio("Hp_Unique","0.2","Unique");
        add(ability,"Buff",71); add(ability,"Buff",72);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(71);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(72);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void multipleClientInstancesStackAndEachRemovalDropsOneContribution() throws Exception {
        var ability=ratio("Hp_Multiple","0.2","Multiple");
        add(ability,"Buff",71); add(ability,"Buff",72);
        assertEquals(210,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(72);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(71);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void serverApplicationAndRemovalUseTheSameHpLayer() {
        var ability=ratio("Hp_Server","0.2","Unique");
        assertTrue(new ActionApplyModifier().execute(ability,action("ApplyModifier"),ByteString.EMPTY,target));
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertTrue(new ActionRemoveModifier().execute(ability,action("RemoveModifier"),ByteString.EMPTY,target));
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void aParameterOverrideRefreshesTheAttachedTarget() throws Exception {
        var ability=ratio("Hp_Param","\"amount\"","Unique");
        ability.getAbilitySpecials().put("amount",0.2f);
        add(ability,"Buff",71);
        invoke(42,0,AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setStr("amount"))
                .setFloatValue(0.4f).build().toByteString(),AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM);
        assertEquals(210,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(105,hp(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
    }

    @Test void aReinitializedParameterMapPublishesOneCoherentHpUpdate() throws Exception {
        var ability=ratio("Hp_Param_Map","[\"amount\",\"level\",\"MUL\"]","Unique");
        ability.getAbilitySpecials().put("amount",0.1f);
        ability.getAbilitySpecials().put("level",2f);
        add(ability,"Buff",71);
        fixture.scene.packets.clear();
        var changes=AbilityMetaReInitOverrideMap.newBuilder()
                .addOverrideMap(AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setStr("amount")).setFloatValue(0.2f))
                .addOverrideMap(AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setStr("level")).setFloatValue(3f));
        invoke(42,0,changes.build().toByteString(),AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_REINIT_OVERRIDEMAP);
        var updates=fixture.scene.packets.stream().filter(p -> p.getOpcode()==PacketOpcodes.EntityFightPropUpdateNotify).toList();
        assertEquals(1,updates.size(),"Publish after all parameters are updated");
        var packet=EntityFightPropUpdateNotify.parseFrom(updates.get(0).getData());
        assertEquals(43,packet.getEntityId());
        assertEquals(3,packet.getFightPropMapCount());
        assertEquals(1.1f,packet.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_HP_PERCENT.getId()),0.00001f);
        assertEquals(230,packet.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_MAX_HP.getId()),0.0001f);
        assertEquals(115,packet.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()),0.0001f);
    }

    @Test void anInvalidMaximumFallsBackToTheBaselineAndCanRecover() throws Exception {
        var ability=ratio("Hp_Invalid_Max","\"amount\"","Unique");
        ability.getAbilitySpecials().put("amount",0.2f);
        add(ability,"Buff",71);
        for(float invalid:new float[]{-2f,Float.MAX_VALUE,Float.NaN}) {
            ability.getAbilitySpecials().put("amount",invalid);
            ability.refreshModifierTargets(true);
            assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
            assertEquals(85,hp(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
            assertEquals(0.5f,hp(FightProperty.FIGHT_PROP_HP_PERCENT),0.00001f);
            ability.getAbilitySpecials().put("amount",0.2f);
            ability.refreshModifierTargets(true);
            assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        }
        remove(71);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }

    @Test void multipleServerApplicationsStackAndAnExplicitRemovalClearsThem() {
        var ability=ratio("Hp_Server_Multiple","0.2","Multiple");
        var apply=new ActionApplyModifier();
        apply.execute(ability,action("ApplyModifier"),ByteString.EMPTY,target);
        apply.execute(ability,action("ApplyModifier"),ByteString.EMPTY,target);
        assertEquals(210,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        new ActionRemoveModifier().execute(ability,action("RemoveModifier"),ByteString.EMPTY,target);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void clientConfirmationDoesNotCountTheServerRequestedBuffTwice() throws Exception {
        var ability=ratio("Hp_Confirm","0.2","Multiple");
        new ActionApplyModifier().execute(ability,action("ApplyModifier"),ByteString.EMPTY,target);
        add(ability,"Buff",71);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        remove(71);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void clientConfirmationReusingAnInstanceIdDoesNotPublishATemporaryDoubleBuff() throws Exception {
        var ability=ratio("Hp_Confirm_Replacement","0.2","Multiple");
        add(ability,"Other",71);
        new ActionApplyModifier().execute(ability,action("ApplyModifier"),ByteString.EMPTY,target);
        fixture.scene.packets.clear();
        add(ability,"Buff",71);
        var updates=fixture.scene.packets.stream().filter(p -> p.getOpcode()==PacketOpcodes.EntityFightPropUpdateNotify).toList();
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        for(var update:updates) {
            var packet=EntityFightPropUpdateNotify.parseFrom(update.getData());
            assertEquals(190,packet.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_MAX_HP.getId()),0.0001f,
                    "Replacing an instance must not expose both the pending server buff and its client confirmation");
        }
    }
    @Test void replacingAnInstanceWithAnUnrelatedModifierRestoresItsContribution() throws Exception {
        var ability=ratio("Hp_Replace","0.2","Unique");
        add(ability,"Buff",71);
        add(ability,"Other",71);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(85,hp(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
    }
    @Test void aMaxHpBuffNeverResurrectsAnEntityWithZeroCurrentHp() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,0);
        var ability=ratio("Hp_Dead","0.2","Unique");
        add(ability,"Buff",71);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(0,hp(FightProperty.FIGHT_PROP_CUR_HP));
        remove(71);
        assertEquals(0,hp(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void hpDebtRemainsAnAbsoluteUnpaidAmount() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS,40);
        var ability=ratio("Hp_Debt","0.2","Unique");
        add(ability,"Buff",71);
        assertEquals(40,hp(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        remove(71);
        assertEquals(40,hp(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
    }
    @Test void negativeRatiosCanReduceMaxHpAndStillPreserveItsFraction() throws Exception {
        var ability=ratio("Hp_Negative","-0.2","Unique");
        add(ability,"Buff",71);
        assertEquals(150,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(75,hp(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        remove(71);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }
    @Test void attachingAnHpBuffAlsoUsesTheSamePropertyLayer() {
        var ability=ratio("Hp_Attach","0.2","Unique");
        new ActionAttachModifier().execute(ability,action("AttachModifier"),ByteString.EMPTY,target);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        new ActionRemoveModifier().execute(ability,action("RemoveModifier"),ByteString.EMPTY,target);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }

    private static class StatAvatar extends Avatar {
        final Player player;
        final Set<Integer> proud=new HashSet<>();
        float baseHp=100;
        StatAvatar(Player player) { this.player=player; }
        @Override public Player getPlayer() { return player; }
        @Override public int getAvatarId() { return 10000555; }
        @Override public Map<Integer,Integer> getSkillLevelMap() { return Map.of(); }
        @Override public Set<Integer> getProudSkillList() { return proud; }
        @Override public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() { return it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET; }
        @Override public AvatarData getAvatarData() { return new AvatarData() {
            @Override public float getBaseHp(int level) { return baseHp; }
            @Override public float getBaseAttack(int level) { return 10; }
            @Override public float getBaseDefense(int level) { return 10; }
        }; }
        @Override public void save() {}
    }
    @Test void avatarStatRecalculationReappliesTheBuffOnceUsingItsNewBaseHp() {
        var avatar=new StatAvatar(fixture.player);
        avatar.recalcStats(false,false);
        var entity=new EntityAvatar(fixture.scene,avatar);
        fixture.scene.getEntities().put(entity.getId(),entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,50);
        var ability=ratio("Hp_Recalc","0.2","Unique");
        new ActionApplyModifier().execute(ability,action("ApplyModifier"),ByteString.EMPTY,entity);
        assertEquals(120,avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        fixture.scene.packets.clear();
        avatar.recalcStats(false,false);
        avatar.recalcStats(false,false);
        assertEquals(120,avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(60,avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        assertTrue(fixture.scene.packets.isEmpty(),"Silent recalculation must not publish borrowed-avatar updates");
        avatar.baseHp=150;
        avatar.recalcStats(false,false);
        assertEquals(180,avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(90,avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        new ActionRemoveModifier().execute(ability,action("RemoveModifier"),ByteString.EMPTY,entity);
        assertEquals(150,avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }

    @Test void aMonsterKeepsItsExistingScalingAndReappliesItsBuffAfterRecalculation() {
        var data=JsonUtils.decode("{\"id\":21010101,\"hpBase\":100,\"attackBase\":10,\"defenseBase\":10,"
                + "\"affix\":[],\"equips\":[],\"propGrowCurves\":[]}",emu.grasscutter.data.excels.monster.MonsterData.class);
        var monster=new emu.grasscutter.game.entity.EntityMonster(fixture.scene,data,
                new emu.grasscutter.game.world.Position(),new emu.grasscutter.game.world.Position(),1) {
            @Override public void initAbilities() {}
        };
        fixture.scene.getEntities().put(monster.getId(),monster);
        monster.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,250);
        monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,125);
        var ability=ratio("Hp_Monster","0.2","Unique");
        new ActionApplyModifier().execute(ability,action("ApplyModifier"),ByteString.EMPTY,monster);
        assertEquals(300,monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(150,monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        monster.recalcStats();
        monster.recalcStats();
        assertEquals(120,monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertEquals(60,monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
        new ActionRemoveModifier().execute(ability,action("RemoveModifier"),ByteString.EMPTY,monster);
        assertEquals(100,monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }

    @Test void currentYelanFurinaAndFoodResourcesResolveTheirNativeRatioExpressions() throws Exception {
        try(var stream=getClass().getResourceAsStream("/ability/hp-modifiers.json")) {
            assertNotNull(stream);
            var rows=JsonUtils.decode(new String(stream.readAllBytes(),StandardCharsets.UTF_8),AbilityData[].class);
            assertEquals(3,rows.length);
            float[] ratios={0.3f,0.35f,0.5f};
            for(int i=0;i<rows.length;i++) {
                var ability=new Ability(rows[i],fixture.entity,fixture.player);
                ability.getAbilitySpecials().put("Hp_Ratio",0.1f);
                ability.getAbilitySpecials().put("Buff_Level",3f);
                ability.getAbilitySpecials().put("AvatarChangeHP_Count_Overflow",100f);
                ability.getAbilitySpecials().put("OverflowCount2MaxHPRatio",0.0035f);
                fixture.entity.getInstancedAbilities().add(ability);
                String name=rows[i].modifiers.keySet().iterator().next();
                add(ability,name,71);
                assertEquals(170+100*ratios[i],hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f,rows[i].abilityName);
                assertEquals(0.5f+ratios[i],hp(FightProperty.FIGHT_PROP_HP_PERCENT),0.0001f);
                remove(71);
                assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
            }
        }
    }

    @Test void initialHpBuffsFollowTheSameRemovableRegistrationPath() {
        var data=JsonUtils.decode("""
                {"abilityName":"Initial_Hp","onAdded":[{"$type":"ApplyModifier","modifierName":"Buff"}],
                 "modifiers":{"Buff":{"properties":{"Actor_MaxHPRatio":0.2}}}}
                """,AbilityData.class);
        var ability=new Ability(data,fixture.entity,fixture.player);
        assertEquals(190,fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertTrue(ability.removeModifier(fixture.entity,"Buff"));
        assertEquals(170,fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }

    @Test void repeatedRegistrationOfTheSameServerControllerDoesNotCreateASecondStack() {
        var ability=ratio("Hp_Idempotent","0.2","Multiple");
        var controller=new emu.grasscutter.game.ability.AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff"));
        ability.registerModifier(target,controller);
        ability.registerModifier(target,controller);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertTrue(ability.removeModifier(target,"Buff"));
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
    }

    @Test void unregisteringOneServerStackLeavesItsOlderContributionAndNamedState() {
        var ability=ratio("Hp_Unregister","0.2","Multiple");
        var data=ability.getData().modifiers.get("Buff");
        var first=new emu.grasscutter.game.ability.AbilityModifierController(ability,ability.getData(),data);
        var second=new emu.grasscutter.game.ability.AbilityModifierController(ability,ability.getData(),data);
        ability.registerModifier(target,first);
        ability.registerModifier(target,second);
        ability.unregisterModifier(target,second);
        assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertSame(first,ability.getModifiers(target).get("Buff"));
        ability.unregisterModifier(target,first);
        assertEquals(170,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        assertFalse(ability.getModifiers(target).containsKey("Buff"));
    }

    @Test void equalEntityIdsInDifferentWorldsHaveIndependentPropertyLayers() throws Exception {
        var first=ratio("Hp_World_One","0.2","Unique");
        new ActionApplyModifier().execute(first,action("ApplyModifier"),ByteString.EMPTY,target);
        try(var other=new AbilityTestFixture()) {
            var entity=new AbilityTestFixture.TestEntity(other.scene);
            entity.setId(43);
            other.scene.getEntities().put(43,entity);
            stats(entity);
            var data=JsonUtils.decode("{\"abilityName\":\"Hp_World_Two\",\"modifiers\":{\"Buff\":{\"properties\":{\"Actor_MaxHPRatio\":0.4}}}}",AbilityData.class);
            var second=new Ability(data,other.entity,other.player);
            new ActionApplyModifier().execute(second,action("ApplyModifier"),ByteString.EMPTY,entity);
            assertEquals(210,entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
            assertEquals(190,hp(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
            new ActionRemoveModifier().execute(first,action("RemoveModifier"),ByteString.EMPTY,target);
            assertEquals(210,entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),0.0001f);
        }
    }
}

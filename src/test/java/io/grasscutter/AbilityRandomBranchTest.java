package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.gson.JsonParser;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityRandomBranchTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity bearer;
    private Ability ability;
    private final AtomicLong now=new AtomicLong();
    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture();
        bearer=new AbilityTestFixture.TestEntity(fixture.scene) {
            final AbilityPeriodicHealing periodic=new AbilityPeriodicHealing(this,now::get);
            final AbilityModifierDuration duration=new AbilityModifierDuration(this,now::get);
            @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return periodic; }
            @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
        };
        bearer.setId(43); fixture.scene.getEntities().put(43,bearer);
        for(var entity:new GameEntity[]{bearer,fixture.entity}) {
            entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
            entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        }
        configure("{}");
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String modifiers) {
        ability=new Ability(JsonUtils.decode("{\"abilityName\":\"RandomBranches\",\"modifiers\":"+modifiers+"}",AbilityData.class),fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability); bearer.getInstancedAbilities().add(ability);
    }
    private AbilityModifierAction random(String chance,String fields) {
        return JsonUtils.decode("{\"$type\":\"Randomed\",\"chance\":"+chance+",\"successActions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"success\",\"value\":1}],\"failActions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"failure\",\"value\":1}]"+fields+"}",AbilityModifierAction.class);
    }
    private boolean run(AbilityModifierAction action) { return new ActionRandomed().execute(ability,action,ByteString.EMPTY,bearer); }
    private float value(GameEntity entity,String key) { return entity.getGlobalAbilityValues().getOrDefault(key,0f); }
    private void attach(String name) { ability.registerModifier(bearer,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get(name))); }
    private void tick(int seconds) { now.set(seconds*1_000_000_000L); bearer.onTick(0); }
    private void invoke(AbilityModifierAction action) {
        int local=ability.getData().localIdToAction.entrySet().stream().filter(e->e.getValue()==action).findFirst().orElseThrow().getKey();
        fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(43).setInstancedAbilityId(1).setLocalId(local)).build());
    }

    @Test void zeroAndOneSelectExactlyTheirConfiguredBranches() {
        assertTrue(run(random("0",""))); assertEquals(0,value(bearer,"success")); assertEquals(1,value(bearer,"failure"));
        assertTrue(run(random("1",""))); assertEquals(1,value(bearer,"success")); assertEquals(1,value(bearer,"failure"));
    }
    @ParameterizedTest @ValueSource(strings={"-0.1","1.1","[1,0,\"DIV\"]","\"bad\""})
    void invalidProbabilityRunsNeitherBranch(String chance) {
        ability.getAbilitySpecials().put("bad",Float.NaN);
        assertFalse(run(random(chance,""))); assertEquals(0,value(bearer,"success")); assertEquals(0,value(bearer,"failure"));
    }
    @Test void aFalseResourceConditionDoesNotDrawOrRunTheFailureBranch() {
        assertTrue(run(random("1",",\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]")));
        assertEquals(0,value(bearer,"success")); assertEquals(0,value(bearer,"failure"));
    }
    @Test void aDynamicChanceReadsCurrentParametersAndGlobals() {
        fixture.entity.getGlobalAbilityValues().put("GV_chance",0f);
        var action=random("\"%GV_chance\"",""); assertTrue(run(action));
        fixture.entity.getGlobalAbilityValues().put("GV_chance",1f); assertTrue(run(action));
        assertEquals(1,value(bearer,"success")); assertEquals(1,value(bearer,"failure"));
    }
    @Test void eachIntermediateProbabilityDrawRunsOnlyOneBranch() {
        var action=random("0.5",""); for(int i=0;i<100;i++) assertTrue(run(action));
        assertEquals(100,value(bearer,"success")+value(bearer,"failure"));
    }
    @Test void childrenResolveCasterAndKeepSelfOnTheCallbackBearer() {
        assertTrue(run(random("1",",\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"owner\",\"value\":5,\"target\":\"Caster\"},{\"$type\":\"SetGlobalValue\",\"key\":\"bearer\",\"value\":2,\"target\":\"Self\"}]")));
        assertEquals(5,value(fixture.entity,"owner")); assertEquals(0,value(bearer,"owner"));
        assertEquals(2,value(bearer,"bearer")); assertEquals(0,value(fixture.entity,"bearer"));
    }
    @Test void childActionsKeepTheirOrderAndTheirOwnConditions() {
        assertTrue(run(random("1",",\"successActions\":[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"heal\",\"value\":20},{\"$type\":\"HealHP\",\"amount\":\"heal\"},{\"$type\":\"HealHP\",\"amount\":20,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]}]")));
        assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void aNativeRandomHealCanAddressEveryActualPartyMember() {
        var first=avatar(44); var second=avatar(45);
        fixture.player.getTeamManager().getActiveTeam().add(first); fixture.player.getTeamManager().getActiveTeam().add(second);
        assertTrue(run(random("1",",\"successActions\":[{\"$type\":\"HealHP\",\"amount\":10,\"target\":\"AllPlayerAvatars\"}]")));
        assertEquals(110,first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)); assertEquals(110,second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(100,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void conditionalBranchesPreserveCasterAndSelectedTargetRecipients() {
        var block=JsonUtils.decode("{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"caster\",\"value\":1,\"target\":\"Caster\"},{\"$type\":\"SetGlobalValue\",\"key\":\"selected\",\"value\":2,\"target\":\"Target\"}]}",AbilityModifierAction.class);
        assertTrue(new ActionPredicated().execute(ability,block,ByteString.EMPTY,bearer));
        assertEquals(1,value(fixture.entity,"caster")); assertEquals(0,value(bearer,"caster")); assertEquals(2,value(bearer,"selected"));
    }
    @Test void aFalseConditionalParentPredicateRunsNoBranch() {
        var block=JsonUtils.decode("{\"$type\":\"Predicated\",\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}],\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"unexpected\",\"value\":1}]}",AbilityModifierAction.class);
        assertTrue(new ActionPredicated().execute(ability,block,ByteString.EMPTY,bearer)); assertEquals(0,value(bearer,"unexpected"));
    }
    @Test void periodicRandomTreesRunOnceAndClaimBothBranchesAgainstNativeEchoes() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Randomed\",\"chance\":1,\"successActions\":[{\"$type\":\"HealHP\",\"amount\":10}],\"failActions\":[{\"$type\":\"HealHP\",\"amount\":30}]}]}}"); attach("Pulse");
        var action=ability.getData().modifiers.get("Pulse").onThinkInterval[0];
        assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,action)); tick(1);
        assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        invoke(action); invoke(action.successActions[0]); invoke(action.failActions[0]);
        assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        tick(2); assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void anUnknownUntakenBranchStillPreventsServerOwnership() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Randomed\",\"chance\":1,\"successActions\":[{\"$type\":\"HealHP\",\"amount\":10}],\"failActions\":[{\"$type\":\"TriggerBullet\"}]}]}}"); attach("Pulse");
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get("Pulse").onThinkInterval[0]));
        tick(1); assertEquals(100,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void nestedRandomAndConditionalNodesRetainChildTargetsAndOrdering() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Randomed\",\"chance\":1,\"successActions\":[{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"Randomed\",\"chance\":0,\"failActions\":[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"heal\",\"value\":10},{\"$type\":\"HealHP\",\"amount\":\"heal\",\"target\":\"Caster\"}]}]}]}]}}"); attach("Pulse"); tick(1);
        assertEquals(110,fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)); assertEquals(100,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void randomModifierInitializationAndRemovalCallbacksAreOwned() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}]},\"Child\":{\"stacking\":\"Unique\",\"onAdded\":[{\"$type\":\"Randomed\",\"chance\":1,\"successActions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"started\",\"value\":1}]}],\"onRemoved\":[{\"$type\":\"Randomed\",\"chance\":0,\"failActions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"ended\",\"value\":1}]}]}}"); attach("Pulse"); tick(1);
        assertEquals(1,value(bearer,"started")); ability.removeModifier(bearer,"Child"); assertEquals(1,value(bearer,"ended"));
        assertEquals(2,fixture.player.packets.stream().filter(p->p.getOpcode()==emu.grasscutter.net.packet.PacketOpcodes.ServerGlobalValueChangeNotify).count());
    }
    @Test void aRemovedCallbackBearerCannotReceiveFurtherRandomEffects() {
        fixture.scene.removeEntity(bearer); assertFalse(run(random("1","")));
        assertEquals(0,value(bearer,"success"));
    }
    @Test void blindServerBookkeepingStillCannotExecuteAHealingChild() {
        AbilityManager.runServerOwned(()->assertTrue(run(random("1",",\"successActions\":[{\"$type\":\"HealHP\",\"amount\":20},{\"$type\":\"SetGlobalValue\",\"key\":\"bookkeeping\",\"value\":1}]"))));
        assertEquals(100,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)); assertEquals(1,value(bearer,"bookkeeping")); assertTrue(fixture.player.packets.isEmpty());
    }
    @Test void durinsActualLockedRandomTalentCannotRewriteItsDamageLimit() throws Exception {
        var file=Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Durin.json");
        var rows=JsonUtils.loadToList(file,AbilityConfigData.class); ability=new Ability(rows.get(23).Default,fixture.entity,fixture.player);
        var node=JsonParser.parseString(Files.readString(file)).getAsJsonArray().get(23).getAsJsonObject().getAsJsonObject("Default").getAsJsonObject("modifiers")
                .getAsJsonObject("UNIQUE_Avatar_Durin_DamageExtra_White_Buff").getAsJsonArray("modifierMixins").get(0).getAsJsonObject().getAsJsonArray("successActions").get(0);
        var action=JsonUtils.decode(node.toString(),AbilityModifierAction.class);
        ability.getAbilitySpecials().put("Talent_4_RandomValue",1f);
        var key=action.successActions[0].overrideMapKey; float before=ability.getAbilitySpecials().getFloat(key);
        assertTrue(run(action)); assertEquals(before,ability.getAbilitySpecials().getFloat(key));
    }
    @Test void cyclicNativeBranchesStopAtTheDepthLimitAndDoNotPoisonLaterCallbacks() {
        var action=random("1",""); var count=action.successActions[0];
        action.successActions=new AbilityModifierAction[]{count,action};
        assertTrue(run(action)); assertEquals(32,value(bearer,"success"));
        assertTrue(run(random("1",""))); assertEquals(33,value(bearer,"success"));
    }
    @Test void selectedConditionalTargetsDoNotRedirectCasterOrSelfChildren() {
        var selected=new AbilityTestFixture.TestEntity(fixture.scene); selected.setId(44); fixture.scene.getEntities().put(44,selected);
        // Only the selected entity carries the matching key, so the successful branch is unambiguous.
        selected.getGlobalAbilityValues().put("eligible",1f);
        var block=JsonUtils.decode("{\"$type\":\"Predicated\",\"otherTargets\":{\"$type\":\"SelectTargetsByShape\",\"shapeName\":\"CircleR10\",\"campTargetType\":\"All\"},\"targetPredicates\":[{\"$type\":\"ByTargetGlobalValue\",\"key\":\"eligible\",\"value\":1}],\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"selected\",\"value\":2,\"target\":\"Target\"},{\"$type\":\"SetGlobalValue\",\"key\":\"self\",\"value\":3,\"target\":\"Self\"},{\"$type\":\"SetGlobalValue\",\"key\":\"caster\",\"value\":4,\"target\":\"Caster\"}]}",AbilityModifierAction.class);
        assertTrue(new ActionPredicated().execute(ability,block,ByteString.EMPTY,bearer));
        assertEquals(2,value(selected,"selected")); assertEquals(0,value(bearer,"selected"));
        assertEquals(3,value(bearer,"self")); assertEquals(0,value(selected,"self"));
        assertEquals(4,value(fixture.entity,"caster")); assertEquals(0,value(selected,"caster"));
    }
    private EntityAvatar avatar(int id) {
        var avatar=new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public void save() {}
            @Override public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() { return it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET; }
        };
        var entity=new EntityAvatar(fixture.scene,avatar); entity.setId(id); fixture.scene.getEntities().put(id,entity);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000); entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        return entity;
    }
}

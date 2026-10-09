package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.gson.JsonParser;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityRandomQueueTest {
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
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        configure("{}");
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String modifiers) {
        ability=new Ability(JsonUtils.decode("{\"abilityName\":\"RandomQueues\",\"modifiers\":"+modifiers+"}",AbilityData.class),fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability); bearer.getInstancedAbilities().add(ability);
    }
    private AbilityModifierAction queue(String fields) {
        return JsonUtils.decode("{\"$type\":\"RandomAction\",\"randomCount\":1,\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]"+fields+"}",AbilityModifierAction.class);
    }
    private void run(AbilityModifierAction action) { ability.getManager().executeActionNow(ability,action,ByteString.EMPTY,bearer); }
    private float value(GameEntity entity,String key) { return entity.getGlobalAbilityValues().getOrDefault(key,0f); }
    private void attach() { ability.registerModifier(bearer,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Pulse"))); }
    private void tick(int seconds) { now.set(seconds*1_000_000_000L); bearer.onTick(0); }

    @Test void aSingleEligibleGroupRunsEveryChildInOrder() {
        run(queue(",\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"amount\",\"value\":10},{\"$type\":\"HealHP\",\"amount\":\"amount\"},{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]"));
        assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)); assertEquals(1,value(bearer,"count"));
    }
    @Test void groupConditionsFilterBeforeChoosingAndUseTheInvocationTarget() {
        fixture.entity.getGlobalAbilityValues().put("eligible",0f); bearer.getGlobalAbilityValues().put("eligible",1f);
        run(queue(",\"randomQueue\":[{\"randomPredicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}],\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"wrong\",\"value\":1}]},{\"randomPredicates\":[{\"$type\":\"ByTargetGlobalValue\",\"key\":\"eligible\",\"value\":1}],\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]"));
        assertEquals(0,value(bearer,"wrong")); assertEquals(1,value(bearer,"count"));
    }
    @Test void everyCallChoosesOneGroupAndDoesNotReuseAnEarlierChoice() {
        var action=queue(",\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"a\",\"value\":1}]},{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"b\",\"value\":1}]}]");
        for(int i=1;i<=40;i++) { run(action); assertEquals(i,value(bearer,"a")+value(bearer,"b")); }
    }
    @Test void parentAndChildConditionsAreIndependent() {
        run(queue(",\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]")); assertEquals(0,value(bearer,"count"));
        run(queue(",\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"blocked\",\"value\":1,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]},{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]"));
        assertEquals(0,value(bearer,"blocked")); assertEquals(1,value(bearer,"count"));
    }
    @Test void zeroMissingEmptyAndIneligibleQueuesHaveNoEffects() {
        run(queue(",\"randomCount\":0")); run(JsonUtils.decode("{\"$type\":\"RandomAction\",\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]}",AbilityModifierAction.class));
        run(queue(",\"randomQueue\":null")); run(queue(",\"randomQueue\":[]")); run(queue(",\"randomQueue\":[null,{\"randomPredicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]}]"));
        assertEquals(0,value(bearer,"count")); assertTrue(fixture.player.packets.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"-1","2","1.5","null","\"1\"","true","[1]","4294967296"})
    void unsupportedOrMalformedCountsDoNotBecomeOne(String count) {
        run(queue(",\"randomCount\":"+count)); assertEquals(0,value(bearer,"count"));
    }
    @Test void resourceAliasesRoundTripAndExplicitFieldsWinInEitherOrder() {
        var alias=JsonUtils.decode("{\"$type\":\"RandomAction\",\"GJKEHHFNGLP\":1,\"AHHFBGACEJF\":[{\"EBOIAJJCFAG\":[],\"FMBINFHJBGG\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]}",AbilityModifierAction.class);
        run(alias); assertEquals(1,value(bearer,"count"));
        var encoded=JsonParser.parseString(JsonUtils.encode(alias)).getAsJsonObject(); assertEquals(1,encoded.get("randomCount").getAsInt()); assertEquals(1,encoded.getAsJsonArray("randomQueue").size());
        run(queue(",\"GJKEHHFNGLP\":0,\"AHHFBGACEJF\":[]")); assertEquals(2,value(bearer,"count"));
        run(JsonUtils.decode("{\"$type\":\"RandomAction\",\"AHHFBGACEJF\":[],\"GJKEHHFNGLP\":0,\"randomCount\":1,\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]}]}",AbilityModifierAction.class)); assertEquals(3,value(bearer,"count"));
    }
    @Test void explicitGroupFieldsWinOverObfuscatedCopies() {
        run(queue(",\"randomQueue\":[{\"randomPredicates\":[],\"EBOIAJJCFAG\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}],\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}],\"FMBINFHJBGG\":[]}]")); assertEquals(1,value(bearer,"count"));
    }
    @Test void childCasterAndSelfTargetsArePreserved() {
        run(queue(",\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"AddGlobalValue\",\"key\":\"caster\",\"value\":1,\"target\":\"Caster\"},{\"$type\":\"AddGlobalValue\",\"key\":\"self\",\"value\":1,\"target\":\"Self\"}]}]"));
        assertEquals(1,value(fixture.entity,"caster")); assertEquals(0,value(bearer,"caster")); assertEquals(1,value(bearer,"self"));
    }
    @Test void completePeriodicTreesOwnEveryGroupIncludingUnselectedChildren() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"RandomAction\",\"randomCount\":1,\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"HealHP\",\"amount\":10}]},{\"randomPredicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}],\"actionQueue\":[{\"$type\":\"HealHP\",\"amount\":20}]}]}]}}"); attach(); tick(1);
        assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(3,ability.getData().localIdToAction.size());
        for(var entry:ability.getData().localIdToAction.entrySet()) {
            assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,entry.getValue()));
            fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(43).setInstancedAbilityId(1).setLocalId(entry.getKey())).build());
        }
        assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @ParameterizedTest @ValueSource(strings={"{\"randomPredicates\":[{\"$type\":\"UnknownQueueCondition\"}],\"actionQueue\":[{\"$type\":\"HealHP\",\"amount\":10}]}","{\"randomPredicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}],\"actionQueue\":[{\"$type\":\"TriggerBullet\"}]}"})
    void unknownGroupsPreventOwnershipEvenIfTheyCannotBeSelected(String group) {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"RandomAction\",\"randomCount\":1,\"randomQueue\":["+group+"]}]}}"); attach();
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get("Pulse").onThinkInterval[0]));
    }
    @Test void unsupportedCountsCannotClaimACompletePeriodicBody() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"RandomAction\",\"randomCount\":2,\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"HealHP\",\"amount\":10}]}]}]}}"); attach();
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get("Pulse").onThinkInterval[0]));
    }
    @Test void nestedQueuesAndRepeatsRetainTheirSequence() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Repeated\",\"repeatTimes\":2,\"actions\":[{\"$type\":\"RandomAction\",\"randomCount\":1,\"randomQueue\":[{\"actionQueue\":[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"amount\",\"value\":10},{\"$type\":\"HealHP\",\"amount\":\"amount\"}]}]}]}]}}"); attach(); tick(1); assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void removingTheBearerStopsFurtherQueueEffects() { fixture.scene.removeEntity(bearer); run(queue("")); assertEquals(0,value(bearer,"count")); }
    @Test void actualChascaQueuesOnlyPickTheAvailableElementAndHonorTheLockedConstellation() throws Exception {
        bearer.getGlobalAbilityValues().put("_ABILITY_Chasca_MultiAim_ElemTypeIce",1f);
        var rows=JsonParser.parseString(Files.readString(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Chasca.json"))).getAsJsonArray();
        run(JsonUtils.decode(rows.get(16).getAsJsonObject().getAsJsonObject("Default").getAsJsonObject("modifiers").getAsJsonObject("Chasca_NyxInstant_AttackPrepare").getAsJsonArray("onAdded").get(4).toString(),AbilityModifierAction.class));
        assertEquals(4,value(bearer,"_ABILITY_Chasca_NyxInstant_Shoot_Color"));
        var actions=rows.get(22).getAsJsonObject().getAsJsonObject("Default").getAsJsonObject("modifiers").getAsJsonObject("Avatar_Chasca_MultiAimAttack_Aiming_Pre").getAsJsonArray("onAdded").get(3).getAsJsonObject().getAsJsonArray("successActions").get(5).getAsJsonObject().getAsJsonArray("successActions");
        run(JsonUtils.decode(actions.get(0).toString(),AbilityModifierAction.class)); assertEquals(4,value(bearer,"_ABILITY_Chasca_MultiAim_Bullet03_Color"));
        run(JsonUtils.decode(actions.get(1).toString(),AbilityModifierAction.class)); assertEquals(0,value(bearer,"_ABILITY_Chasca_MultiAim_Bullet02_Color"));
    }
    @Test void actualWandererAliasQueueAddsOnlyAnUnobtainedElement() throws Exception {
        for(var element:new String[]{"Fire","Water","Ice"}) bearer.getGlobalAbilityValues().put("_ABILITY_Wanderer_ElementalArt_MixType_"+element,1f);
        var rows=JsonParser.parseString(Files.readString(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Wanderer.json"))).getAsJsonArray();
        var node=rows.get(34).getAsJsonObject().getAsJsonObject("Default").getAsJsonArray("abilityMixins").get(1).getAsJsonObject().getAsJsonArray("AKJJPILKIGP").get(0).getAsJsonObject().getAsJsonArray("successActions").get(0);
        var action=JsonUtils.decode(node.toString(),AbilityModifierAction.class); run(action); assertEquals(1,value(bearer,"_ABILITY_Wanderer_ElementalArt_MixType_Electric"));
        int packets=fixture.player.packets.size(); run(action); assertEquals(packets,fixture.player.packets.size());
    }
}

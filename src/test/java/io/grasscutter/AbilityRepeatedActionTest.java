package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.gson.JsonParser;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionClearGlobalValue;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityRepeatedActionTest {
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
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000); bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        configure("{}");
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String modifiers) {
        ability=new Ability(JsonUtils.decode("{\"abilityName\":\"RepeatedActions\",\"modifiers\":"+modifiers+"}",AbilityData.class),fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability); bearer.getInstancedAbilities().add(ability);
    }
    private AbilityModifierAction repeated(String fields) {
        return JsonUtils.decode("{\"$type\":\"Repeated\",\"actions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1}]"+fields+"}",AbilityModifierAction.class);
    }
    private void run(AbilityModifierAction action) { ability.getManager().executeActionNow(ability,action,ByteString.EMPTY,bearer); }
    private float value(GameEntity entity,String key) { return entity.getGlobalAbilityValues().getOrDefault(key,0f); }
    private void attach() { ability.registerModifier(bearer,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Pulse"))); }
    private void tick(int seconds) { now.set(seconds*1_000_000_000L); bearer.onTick(0); }
    private AbilityModifierAction clear(String fields) {
        return JsonUtils.decode("{\"$type\":\"ClearGlobalValue\",\"key\":\"count\""+fields+"}",AbilityModifierAction.class);
    }
    @Test void integerCountsExecuteTheWholeOrderedActionListEachTime() {
        run(repeated(",\"repeatTimes\":3"));
        assertEquals(3,value(bearer,"count"));
        // The parameter write must precede the heal in every iteration.
        run(repeated(",\"repeatTimes\":2,\"actions\":[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"amount\",\"value\":10},{\"$type\":\"HealHP\",\"amount\":\"amount\"}]"));
        assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void dynamicCountsReadAbilityConstantsAndOwnerGlobals() {
        ability.getAbilitySpecials().put("base",2f); fixture.entity.getGlobalAbilityValues().put("GV_extra",1f);
        run(repeated(",\"repeatTimes\":[\"base\",\"%GV_extra\",\"ADD\"]")); assertEquals(3,value(bearer,"count"));
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","257","[1,0,\"DIV\"]","\"bad\""})
    void invalidCountsDoNotExecutePartialActions(String count) {
        ability.getAbilitySpecials().put("bad",Float.POSITIVE_INFINITY);
        run(repeated(",\"repeatTimes\":"+count)); assertEquals(0,value(bearer,"count"));
    }
    @Test void zeroMissingAndEmptyBodiesAreNoOps() {
        run(repeated("")); run(repeated(",\"repeatTimes\":0")); run(repeated(",\"repeatTimes\":3,\"actions\":null"));
        assertEquals(0,value(bearer,"count")); assertTrue(fixture.player.packets.isEmpty());
    }
    @Test void obfuscatedCountsArePreservedAndExplicitCountsWinInBothJsonOrders() {
        var alias=repeated(",\"GFJCFFFEDCF\":2"); run(alias); assertEquals(2,value(bearer,"count"));
        assertEquals(2,JsonParser.parseString(JsonUtils.encode(alias)).getAsJsonObject().get("repeatTimes").getAsInt());
        run(repeated(",\"repeatTimes\":3,\"GFJCFFFEDCF\":9")); assertEquals(5,value(bearer,"count"));
        run(repeated(",\"GFJCFFFEDCF\":9,\"repeatTimes\":3")); assertEquals(8,value(bearer,"count"));
    }
    @Test void aFalseParentConditionRunsNoIterations() {
        run(repeated(",\"repeatTimes\":3,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]")); assertEquals(0,value(bearer,"count"));
    }
    @Test void childrenKeepTheirCasterTargetsAndTheirOwnConditions() {
        run(repeated(",\"repeatTimes\":2,\"actions\":[{\"$type\":\"AddGlobalValue\",\"key\":\"ownerCount\",\"value\":1,\"target\":\"Caster\"},{\"$type\":\"AddGlobalValue\",\"key\":\"count\",\"value\":1,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]}]"));
        assertEquals(2,value(fixture.entity,"ownerCount")); assertEquals(0,value(bearer,"ownerCount")); assertEquals(0,value(bearer,"count"));
    }
    @Test void knownPeriodicRepeatsOwnTheirChildrenAgainstNativeEchoes() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Repeated\",\"repeatTimes\":3,\"actions\":[{\"$type\":\"HealHP\",\"amount\":10}]}]}}"); attach();
        var action=ability.getData().modifiers.get("Pulse").onThinkInterval[0];
        assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,action)); tick(1);
        assertEquals(130,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        for(var node:new AbilityModifierAction[]{action,action.actions[0]}) {
            int id=ability.getData().localIdToAction.entrySet().stream().filter(e->e.getValue()==node).findFirst().orElseThrow().getKey();
            fixture.player.getAbilityManager().handleServerInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                    .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(43).setInstancedAbilityId(1).setLocalId(id)).build());
        }
        assertEquals(130,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void anUnknownBodyPreventsOwnershipEvenWhenTheCountIsZero() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Repeated\",\"repeatTimes\":0,\"actions\":[{\"$type\":\"TriggerBullet\"}]}]}}"); attach();
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get("Pulse").onThinkInterval[0]));
    }
    @Test void nestedRepeatRandomAndConditionalNodesKeepTheirActionOrder() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"Repeated\",\"repeatTimes\":2,\"actions\":[{\"$type\":\"Randomed\",\"chance\":1,\"successActions\":[{\"$type\":\"Predicated\",\"successActions\":[{\"$type\":\"Repeated\",\"repeatTimes\":3,\"actions\":[{\"$type\":\"HealHP\",\"amount\":10}]}]}]}]}]}}"); attach(); tick(1);
        assertEquals(160,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void aRemovedBearerStopsNativeRepeatEffects() {
        fixture.scene.removeEntity(bearer); run(repeated(",\"repeatTimes\":3")); assertEquals(0,value(bearer,"count"));
    }
    @Test void excessiveNestedWorkStopsAndTheNextCallbackGetsAFreshBudget() {
        var inner=repeated(",\"repeatTimes\":256"); var outer=repeated(",\"repeatTimes\":256"); outer.actions=new AbilityModifierAction[]{inner};
        AbilityManager.runServerOwned(()->run(outer));
        float partial=value(bearer,"count"); assertTrue(partial>0 && partial<65536);
        run(repeated(",\"repeatTimes\":2")); assertEquals(partial+2,value(bearer,"count"));
    }
    @Test void clearHonorsConditionsAndCannotMutateAnOldEntityLifetime() {
        bearer.getGlobalAbilityValues().put("count",7f);
        assertTrue(new ActionClearGlobalValue().execute(ability,clear(",\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]"),ByteString.EMPTY,bearer)); assertEquals(7,value(bearer,"count"));
        fixture.scene.removeEntity(bearer); assertFalse(new ActionClearGlobalValue().execute(ability,clear(""),ByteString.EMPTY,bearer)); assertEquals(7,value(bearer,"count"));
    }
    @Test void clearIsIdempotentAndGuessedServerWritesDoNotSendZeroToTheClient() {
        bearer.getGlobalAbilityValues().put("count",7f);
        AbilityManager.runServerOwned(()->assertTrue(new ActionClearGlobalValue().execute(ability,clear(""),ByteString.EMPTY,bearer)));
        assertFalse(bearer.getGlobalAbilityValues().containsKey("count")); assertTrue(fixture.player.packets.isEmpty());
        assertTrue(new ActionClearGlobalValue().execute(ability,clear(""),ByteString.EMPTY,bearer)); assertTrue(fixture.player.packets.isEmpty());
        bearer.getGlobalAbilityValues().put("count",7f); assertTrue(new ActionClearGlobalValue().execute(ability,clear(""),ByteString.EMPTY,bearer));
        assertEquals(1,fixture.player.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify).count());
    }
    @Test void clearRejectsBlankKeysAndNullInputsWithoutThrowing() {
        var action=clear(""); action.key=" ";
        assertFalse(new ActionClearGlobalValue().execute(ability,action,ByteString.EMPTY,bearer));
        assertFalse(new ActionClearGlobalValue().execute(null,clear(""),ByteString.EMPTY,bearer));
        assertFalse(new ActionClearGlobalValue().execute(ability,clear(""),ByteString.EMPTY,null));
    }
    @Test void feiyansActualFullWeightNodeAddsAllThreeConfiguredCharges() throws Exception {
        var data=resource("Feiyan",12); ability=new Ability(data,fixture.entity,fixture.player);
        var node=data.modifiers.get("AddGlobalValueToGadget_Full").onAdded[0]; run(node);
        assertEquals(3,value(bearer,"_ABILITY_Feiyan_ExtraAttack_Weight_Bullet_Count"));
    }
    @Test void ittosActualFinalNormalAttackAddsTwoStacksAndRespectsTheFiveStackLimit() throws Exception {
        var data=resource("Itto",3); ability=new Ability(data,fixture.entity,fixture.player);
        var node=data.modifiers.get("Avatar_Itto_LastNormalAttack_AddSymbol").onAdded[0]; run(node);
        assertEquals(2,value(bearer,"_ABILITY_Itto_Symbol"));
        bearer.getGlobalAbilityValues().put("_ABILITY_Itto_Symbol",4f); run(node); assertEquals(5,value(bearer,"_ABILITY_Itto_Symbol"));
    }
    private AbilityData resource(String avatar,int index) throws Exception {
        return JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"+avatar+".json"),AbilityConfigData.class).get(index).Default;
    }
}

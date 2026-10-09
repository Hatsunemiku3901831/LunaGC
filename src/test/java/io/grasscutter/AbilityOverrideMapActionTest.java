package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityActionSetRandomOverrideMapValueOuterClass.AbilityActionSetRandomOverrideMapValue;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

class AbilityOverrideMapActionTest {
    private AbilityTestFixture fixture;
    private TimedEntity bearer;
    private CountingAbility ability;
    private final AtomicLong now=new AtomicLong();
    private class TimedEntity extends AbilityTestFixture.TestEntity {
        private final AbilityPeriodicHealing periodic;
        private final AbilityModifierDuration duration;
        TimedEntity() {
            super(fixture.scene); setId(43);
            periodic=new AbilityPeriodicHealing(this,now::get);
            duration=new AbilityModifierDuration(this,now::get);
            fixture.scene.addEntity(this);
        }
        @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return periodic; }
        @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
    }
    private class CountingAbility extends Ability {
        int refreshes;
        CountingAbility(AbilityData data,GameEntity owner) { super(data,owner,fixture.player); }
        @Override public void refreshModifierTargets(boolean notify) { refreshes++; super.refreshModifierTargets(notify); }
    }
    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture(); bearer=new TimedEntity();
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE,1500);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        configure("{}");
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String modifiers) {
        var data=JsonUtils.decode("{\"abilityName\":\"Parameters\",\"abilitySpecials\":{\"rate\":7},\"modifiers\":"+modifiers+"}",AbilityData.class);
        ability=new CountingAbility(data,fixture.entity); fixture.entity.getInstancedAbilities().add(ability);
    }
    private AbilityModifierAction fixed(String fields) {
        return JsonUtils.decode("{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"rate\""+fields+"}",AbilityModifierAction.class);
    }
    private boolean fixed(AbilityModifierAction action) {
        return new ActionSetOverrideMapValue().execute(ability,action,ByteString.EMPTY,bearer);
    }
    private AbilityModifierAction random(String fields) {
        return JsonUtils.decode("{\"$type\":\"SetRandomOverrideMapValue\",\"overrideMapKey\":\"rate\""+fields+"}",AbilityModifierAction.class);
    }
    private boolean random(AbilityModifierAction action,float value) {
        var payload=AbilityActionSetRandomOverrideMapValue.newBuilder().setRandomValue(value).build().toByteString();
        return new ActionSetRandomOverrideMapValue().execute(ability,action,payload,bearer);
    }
    private void attach(String name) { ability.registerModifier(bearer,
            new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get(name))); }
    private void tick(double seconds) { now.set((long)(seconds*1_000_000_000d)); bearer.onTick(0); }
    private float value() { return ability.getAbilitySpecials().getFloat("rate"); }

    @Test void aWrittenValueWinsOverASeparateRatioRegardlessOfJsonOrder() {
        assertTrue(fixed(fixed(",\"value\":4,\"ratio\":0"))); assertEquals(4,value());
        assertTrue(fixed(fixed(",\"ratio\":0,\"value\":5"))); assertEquals(5,value());
    }
    @Test void sourceGlobalsAndFightPropertiesParticipateInExpressions() {
        fixture.entity.getGlobalAbilityValues().put("GV_scale",2f);
        assertTrue(fixed(fixed(",\"value\":[\"FIGHT_PROP_CUR_DEFENSE\",1000,\"DIV\",\"%GV_scale\",\"MUL\"]")));
        assertEquals(3,value());
    }
    @Test void configuredLimitsClampBothEndsAndDisabledLimitsPreserveTheValue() {
        assertTrue(fixed(fixed(",\"value\":20,\"useLimitRange\":true,\"minValue\":2,\"maxValue\":8"))); assertEquals(8,value());
        assertTrue(fixed(fixed(",\"value\":-1,\"useLimitRange\":true,\"minValue\":2,\"maxValue\":8"))); assertEquals(2,value());
        assertTrue(fixed(fixed(",\"value\":20,\"useLimitRange\":false,\"minValue\":2,\"maxValue\":8"))); assertEquals(20,value());
    }
    @Test void lockedConditionsCannotRewriteAParameter() {
        assertTrue(fixed(fixed(",\"value\":5,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]")));
        assertEquals(7,value()); assertEquals(0,ability.refreshes);
    }
    @Test void invalidKeysRangesAndExpressionsPreserveTheExistingParameter() {
        var action=fixed(",\"value\":4"); action.overrideMapKey="";
        assertFalse(fixed(action)); action.overrideMapKey=null; assertFalse(fixed(action));
        assertFalse(fixed(fixed(",\"value\":4,\"useLimitRange\":true,\"minValue\":9,\"maxValue\":2")));
        ability.getAbilitySpecials().put("bad",Float.POSITIVE_INFINITY);
        assertFalse(fixed(fixed(",\"value\":\"bad\""))); assertEquals(7,value());
    }
    @Test void zeroWritesAreRealAndIdenticalValuesDoNotRefreshAgain() {
        assertTrue(fixed(fixed(",\"value\":0"))); assertEquals(0,value()); assertEquals(1,ability.refreshes);
        assertTrue(fixed(fixed(",\"value\":0"))); assertEquals(1,ability.refreshes);
    }
    @Test void aParameterWriteRefreshesAnAlreadyAttachedPropertyOnAnotherBearer() {
        configure("{\"Buff\":{\"properties\":{\"Actor_AttackDelta\":\"rate\"}}}"); attach("Buff");
        assertEquals(107,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        assertTrue(fixed(fixed(",\"value\":20"))); assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }
    @Test void aPendingIntervalStartsAtItsOriginalAttachmentWhenTheActionSuppliesTheParameter() {
        configure("{\"Pulse\":{\"thinkInterval\":\"period\",\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10}]}}");
        attach("Pulse"); tick(1);
        var action=fixed(",\"value\":2"); action.overrideMapKey="period"; assertTrue(fixed(action));
        tick(2); assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }
    @Test void fixedParameterSequencesBecomeServerOwnedButRandomPayloadActionsRemainNative() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"rate\",\"value\":3},"
                +"{\"$type\":\"HealHP\",\"amount\":\"rate\"}]}}"); attach("Pulse"); tick(1);
        assertEquals(103,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        var actions=ability.getData().modifiers.get("Pulse").onThinkInterval;
        assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,actions[0]));
        ability.removeModifier(bearer,"Pulse"); actions[0]=random(",\"valueRangeMin\":0,\"valueRangeMax\":1");
        attach("Pulse"); tick(2); assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,actions[0]));
    }
    @Test void aNamedWriteChangesOnlyTheExistingNamedAbilityOfTheRecipient() {
        var named=new CountingAbility(JsonUtils.decode("{\"abilityName\":\"Named\"}",AbilityData.class),bearer);
        bearer.getInstancedAbilities().add(named);
        assertTrue(fixed(fixed(",\"abilityName\":\"Named\",\"value\":3")));
        assertEquals(3,named.getAbilitySpecials().getFloat("rate")); assertEquals(7,value()); assertEquals(1,named.refreshes);
        assertFalse(fixed(fixed(",\"abilityName\":\"Missing\",\"value\":9"))); assertEquals(7,value());
    }
    @Test void randomValuesRejectNonFiniteInputsAndOutOfRangeValues() {
        var action=random(",\"valueRangeMin\":1,\"valueRangeMax\":3");
        assertFalse(random(action,Float.NaN)); assertFalse(random(action,Float.POSITIVE_INFINITY));
        assertFalse(random(action,0)); assertFalse(random(action,4)); assertEquals(7,value());
        assertTrue(random(action,1)); assertEquals(1,value()); assertTrue(random(action,3)); assertEquals(3,value());
    }
    @Test void absentRandomMinimumUsesZeroAndDynamicBoundsSeeSourceGlobals() {
        assertTrue(random(random(",\"valueRangeMax\":4"),2)); assertEquals(2,value());
        fixture.entity.getGlobalAbilityValues().put("GV_min",1f); fixture.entity.getGlobalAbilityValues().put("GV_max",3f);
        assertTrue(random(random(",\"valueRangeMin\":\"%GV_min\",\"valueRangeMax\":\"%GV_max\""),2));
        assertEquals(2,value());
    }
    @Test void randomConditionsMalformedPayloadsAndInvertedRangesCannotWrite() {
        var action=random(",\"valueRangeMin\":1,\"valueRangeMax\":3,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]");
        assertTrue(random(action,2)); assertEquals(7,value());
        assertFalse(random(random(",\"valueRangeMin\":3,\"valueRangeMax\":1"),2));
        assertFalse(new ActionSetRandomOverrideMapValue().execute(ability,random(""),ByteString.copyFrom(new byte[]{15}),bearer));
    }
    @Test void aRandomParameterAlsoRefreshesAnAttachedProperty() {
        configure("{\"Buff\":{\"properties\":{\"Actor_AttackDelta\":\"rate\"}}}"); attach("Buff");
        assertTrue(random(random(",\"valueRangeMin\":1,\"valueRangeMax\":30"),20));
        assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }
    @Test void invalidRandomBoundExpressionsCannotBecomeZeroBounds() {
        ability.getAbilitySpecials().put("bad",Float.POSITIVE_INFINITY);
        assertFalse(random(random(",\"valueRangeMin\":\"bad\",\"valueRangeMax\":3"),2));
        assertFalse(random(random(",\"valueRangeMin\":0,\"valueRangeMax\":[1,0,\"DIV\"]"),0));
        assertEquals(7,value());
    }
    @Test void anExistingDynamicAbilityReceivesNamedWritesAndRemovedLifetimesRejectThem() {
        var named=new CountingAbility(JsonUtils.decode("{\"abilityName\":\"Dynamic\"}",AbilityData.class),bearer);
        bearer.getDynamicAbilities().put("Dynamic",named);
        assertTrue(fixed(fixed(",\"abilityName\":\"Dynamic\",\"value\":2")));
        assertEquals(2,named.getAbilitySpecials().getFloat("rate"));
        fixture.scene.removeEntity(bearer);
        assertFalse(fixed(fixed(",\"abilityName\":\"Dynamic\",\"value\":3")));
        assertEquals(2,named.getAbilitySpecials().getFloat("rate"));
    }
    @Test void albedosActualTwoStepFormulaUpdatesItsDependentProperty() throws Exception {
        var configs=JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Albedo.json"),AbilityConfigData.class);
        var data=configs.stream().map(c->c.Default).filter(d->d.modifiers!=null
                &&d.modifiers.containsKey("UNIQUE_Avatar_Albedo_HexenzirkelSkill_OG_BuffHandler_OnSelf")).findFirst().orElseThrow();
        var actual=new CountingAbility(data,fixture.entity); actual.getAbilitySpecials().put("Ratio_OG",0.5f);
        ability=actual; var actions=data.modifiers.get("UNIQUE_Avatar_Albedo_HexenzirkelSkill_OG_BuffHandler_OnSelf").onThinkInterval;
        assertTrue(fixed(actions[0])); assertTrue(fixed(actions[1]));
        assertEquals(1.5f,actual.getAbilitySpecials().getFloat("Defence_InCalc_OG"));
        assertEquals(0.75f,actual.getAbilitySpecials().getFloat("Hexenzirkel_DamageUp_Ratio_OG"));
    }
    @Test void marionettesActualBackgroundActionWritesToTheNamedDriver() throws Exception {
        var configs=JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_MarionetteNew.json"),AbilityConfigData.class);
        var data=configs.stream().map(c->c.Default).filter(d->d.modifiers!=null
                &&d.modifiers.containsKey("SetMarionetteFrostValueBackGround")).findFirst().orElseThrow();
        ability=new CountingAbility(data,fixture.entity);
        float original=ability.getAbilitySpecials().getFloat("BackgroundRatio");
        var named=new CountingAbility(JsonUtils.decode("{\"abilityName\":\"Avatar_Marionette_FrostValue_Driver\"}",AbilityData.class),bearer);
        bearer.getInstancedAbilities().add(named);
        assertTrue(fixed(data.modifiers.get("SetMarionetteFrostValueBackGround").onAdded[0]));
        assertEquals(1,named.getAbilitySpecials().getFloat("BackgroundRatio"));
        assertTrue(fixed(data.modifiers.get("SetMarionetteFrostValueBackGround").onRemoved[0]));
        assertEquals(3,named.getAbilitySpecials().getFloat("BackgroundRatio"));
        assertEquals(original,ability.getAbilitySpecials().getFloat("BackgroundRatio"));
        bearer.getInstancedAbilities().remove(named);
        assertFalse(fixed(data.modifiers.get("SetMarionetteFrostValueBackGround").onAdded[0]));
        assertEquals(original,ability.getAbilitySpecials().getFloat("BackgroundRatio"));
    }
}

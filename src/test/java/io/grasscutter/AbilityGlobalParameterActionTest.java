package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.ServerGlobalValueChangeNotifyOuterClass.ServerGlobalValueChangeNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityGlobalParameterActionTest {
    private AbilityTestFixture fixture;
    private ObservedEntity bearer;
    private Ability ability;
    private final AtomicLong now=new AtomicLong();
    private class ObservedEntity extends AbilityTestFixture.TestEntity {
        int updates;
        final AbilityPeriodicHealing periodic=new AbilityPeriodicHealing(this,now::get);
        final AbilityModifierDuration duration=new AbilityModifierDuration(this,now::get);
        ObservedEntity() { super(fixture.scene); setId(43); fixture.scene.getEntities().put(43,this); }
        @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return periodic; }
        @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
        @Override public void onAbilityValueUpdate() { updates++; super.onAbilityValueUpdate(); }
    }
    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture(); bearer=new ObservedEntity();
        bearer.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
        configure("{}");
    }
    @AfterEach void close() { fixture.close(); }
    private void configure(String modifiers) {
        ability=new Ability(JsonUtils.decode("{\"abilityName\":\"GlobalParameters\",\"abilitySpecials\":{\"rate\":7},\"modifiers\":"+modifiers+"}",AbilityData.class),fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }
    private AbilityModifierAction action(String type,String fields) {
        return JsonUtils.decode("{\"$type\":\""+type+"\",\"key\":\"GV_rate\",\"srcTarget\":\"Caster\",\"srcKey\":\"GV_source\",\"dstKey\":\"GV_rate\",\"globalValueKey\":\"GV_source\",\"overrideMapKey\":\"rate\""+fields+"}",AbilityModifierAction.class);
    }
    private boolean run(String type,String fields) { return run(action(type,fields),bearer); }
    private boolean run(AbilityModifierAction action,GameEntity target) {
        AbilityActionHandler handler=switch(action.type) {
            case SetGlobalValue,SetGlobalValueV2 -> new ActionSetGlobalValue();
            case AddGlobalValue -> new ActionAddGlobalValue();
            case CopyGlobalValue -> new ActionCopyGlobalValue();
            case SetGlobalValueToOverrideMap -> new ActionSetGlobalValueToOverrideMap();
            default -> throw new IllegalArgumentException();
        };
        return handler.execute(ability,action,ByteString.EMPTY,target);
    }
    private float global() { return bearer.getGlobalAbilityValues().getOrDefault("GV_rate",0f); }
    private void source(float value) { fixture.entity.getGlobalAbilityValues().put("GV_source",value); bearer.getGlobalAbilityValues().put("GV_source",value); }
    private void attach(String name) { ability.registerModifier(bearer,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get(name))); }

    @ParameterizedTest @ValueSource(strings={"SetGlobalValue","SetGlobalValueV2","AddGlobalValue","CopyGlobalValue","SetGlobalValueToOverrideMap"})
    void allResourceWriteConditionsAreHonored(String type) {
        source(20); bearer.getGlobalAbilityValues().put("GV_rate",7f);
        assertTrue(run(type,",\"value\":20,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]"));
        assertEquals(7,global()); assertEquals(7,ability.getAbilitySpecials().getFloat("rate"));
        assertEquals(0,bearer.updates); assertTrue(fixture.player.packets.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"SetGlobalValue","SetGlobalValueV2","AddGlobalValue"})
    void invalidArithmeticCannotEraseAnExistingValue(String type) {
        bearer.getGlobalAbilityValues().put("GV_rate",7f);
        assertFalse(run(type,",\"value\":[1,0,\"DIV\"]")); assertEquals(7,global());
        assertFalse(run(type,",\"value\":3,\"useLimitRange\":true,\"minValue\":9,\"maxValue\":2")); assertEquals(7,global());
        assertFalse(run(type,",\"value\":3,\"useLimitRange\":true,\"minValue\":[1,0,\"DIV\"],\"maxValue\":9"));
        assertEquals(7,global()); assertEquals(0,bearer.updates); assertTrue(fixture.player.packets.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"SetGlobalValue","SetGlobalValueV2","AddGlobalValue","CopyGlobalValue","SetGlobalValueToOverrideMap"})
    void blankKeysAreRejectedWithoutChangingState(String type) {
        source(20); var action=action(type,",\"value\":3");
        action.key=" "; action.dstKey=" "; action.overrideMapKey=" ";
        assertFalse(run(action,bearer)); assertFalse(bearer.getGlobalAbilityValues().containsKey(" "));
        assertEquals(7,ability.getAbilitySpecials().getFloat("rate")); assertEquals(0,bearer.updates);
    }
    @Test void literalZeroCreatesAKeyButIdenticalWritesDoNotRepeatNotifications() throws Exception {
        assertTrue(run("SetGlobalValue",",\"value\":0")); assertTrue(bearer.getGlobalAbilityValues().containsKey("GV_rate"));
        assertEquals(1,bearer.updates); assertEquals(1,fixture.player.packets.size());
        var packet=fixture.player.packets.get(0); assertEquals(PacketOpcodes.ServerGlobalValueChangeNotify,packet.getOpcode());
        var payload=ServerGlobalValueChangeNotify.parseFrom(packet.getData()); assertEquals(43,payload.getEntityId()); assertEquals(0,payload.getValue());
        assertTrue(run("SetGlobalValue",",\"value\":0")); assertTrue(run("AddGlobalValue",",\"value\":0"));
        assertEquals(1,bearer.updates); assertEquals(1,fixture.player.packets.size());
    }
    @Test void overflowPreservesStateWhileBoundedAdditionClampsWithoutOverflowing() {
        bearer.getGlobalAbilityValues().put("GV_rate",Float.MAX_VALUE); ability.getAbilitySpecials().put("huge",Float.MAX_VALUE);
        assertFalse(run("AddGlobalValue",",\"value\":\"huge\"")); assertEquals(Float.MAX_VALUE,global());
        assertEquals(0,bearer.updates);
        assertTrue(run("AddGlobalValue",",\"value\":\"huge\",\"useLimitRange\":true,\"minValue\":0,\"maxValue\":100")); assertEquals(100,global());
    }
    @Test void concurrentIncrementsStillDoNotLoseUpdates() throws Exception {
        var pool=Executors.newFixedThreadPool(4); var start=new CyclicBarrier(4);
        var increment=action("AddGlobalValue",",\"value\":1");
        try {
            var work=new ArrayList<Future<?>>();
            for(int i=0;i<4;i++) work.add(pool.submit(()->{start.await(5,TimeUnit.SECONDS); for(int j=0;j<50;j++)assertTrue(run(increment,bearer)); return null;}));
            for(var future:work) future.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(200,global());
    }
    @Test void copyingRejectsNonFiniteInputAndKeepsTheSourceUnchanged() {
        bearer.getGlobalAbilityValues().put("GV_rate",7f); source(Float.NaN);
        assertFalse(run("CopyGlobalValue","")); assertEquals(7,global());
        source(12); assertTrue(run("CopyGlobalValue","")); assertEquals(12,global());
        assertEquals(12,fixture.entity.getGlobalAbilityValues().get("GV_source"));
        assertTrue(run("CopyGlobalValue","")); assertEquals(1,bearer.updates); assertEquals(1,fixture.player.packets.size());
    }
    @Test void convertingAGlobalToAParameterIsAReadAndRefreshesAttachedProperties() {
        configure("{\"Buff\":{\"properties\":{\"Actor_AttackDelta\":\"rate\"}}}"); attach("Buff"); source(20);
        assertTrue(run("SetGlobalValueToOverrideMap","")); assertEquals(20,ability.getAbilitySpecials().getFloat("rate"));
        assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK)); assertEquals(20,bearer.getGlobalAbilityValues().get("GV_source"));
        assertEquals(0,bearer.updates); assertTrue(fixture.player.packets.isEmpty());
        source(Float.POSITIVE_INFINITY); assertFalse(run("SetGlobalValueToOverrideMap","")); assertEquals(20,ability.getAbilitySpecials().getFloat("rate"));
    }
    @Test void aNamedConversionWritesTheRecipientsAbilityWithoutChangingItsSourceGlobal() {
        var recipient=new Ability(JsonUtils.decode("{\"abilityName\":\"Recipient\"}",AbilityData.class),bearer,fixture.player);
        bearer.getInstancedAbilities().add(recipient); source(12);
        assertTrue(run("SetGlobalValueToOverrideMap",",\"abilityName\":\"Recipient\"")); assertEquals(12,recipient.getAbilitySpecials().getFloat("rate"));
        assertEquals(7,ability.getAbilitySpecials().getFloat("rate")); bearer.getInstancedAbilities().remove(recipient);
        assertFalse(run("SetGlobalValueToOverrideMap",",\"abilityName\":\"Recipient\"")); assertEquals(7,ability.getAbilitySpecials().getFloat("rate"));
    }
    @Test void aSummonCanReadItsOwnersGlobalAndMissingOwnerCannotRewriteParameters() {
        source(13);
        var summon=new EntityClientGadget(fixture.scene,fixture.player,EvtCreateGadgetNotify.newBuilder().setEntityId(44).setOwnerEntityId(42).build());
        fixture.scene.getEntities().put(44,summon); summon.getGlobalAbilityValues().put("GV_source",99f);
        var action=action("SetGlobalValueToOverrideMap",",\"isFromOwner\":true");
        assertTrue(run(action,summon)); assertEquals(13,ability.getAbilitySpecials().getFloat("rate"));
        fixture.scene.getEntities().remove(42); assertTrue(run(action,summon)); assertEquals(13,ability.getAbilitySpecials().getFloat("rate"));
    }
    @Test void deterministicConversionSequencesBecomeServerOwnedAndExecuteInResourceOrder() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"SetGlobalValueToOverrideMap\",\"globalValueKey\":\"GV_source\",\"overrideMapKey\":\"rate\"},{\"$type\":\"HealHP\",\"amount\":\"rate\"}]}}"); source(20); attach("Pulse");
        var step=ability.getData().modifiers.get("Pulse").onThinkInterval[0];
        assertTrue(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,step));
        now.set(1_000_000_000L); bearer.onTick(0); assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(20,ability.getAbilitySpecials().getFloat("rate")); assertTrue(fixture.player.packets.isEmpty());
    }
    @Test void unknownFormulaSequencesRemainNative() {
        configure("{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"SetGlobalValueToOverrideMap\",\"globalValueKey\":\"GV_source\",\"overrideMapKey\":\"rate\",\"abilityFormula\":\"UnknownFormula\"}]}}"); source(20); attach("Pulse");
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,ability.getData().modifiers.get("Pulse").onThinkInterval[0]));
        now.set(1_000_000_000L); bearer.onTick(0); assertEquals(7,ability.getAbilitySpecials().getFloat("rate"));
    }
    @Test void lisasActualRandomCounterRemainsNativeInsteadOfBecomingAFixedOne() throws Exception {
        var data=resource("Lisa",24); var modifier=data.modifiers.get("CountAttack");
        assertEquals(AbilityModifierAction.Type.AddGlobalValue,modifier.onAdded[0].type);
        modifier.thinkInterval=emu.grasscutter.data.common.DynamicFloat.ONE;
        modifier.onThinkInterval=modifier.onAdded;
        ability=new Ability(data,fixture.entity,fixture.player); fixture.entity.getInstancedAbilities().add(ability);
        ability.registerModifier(bearer,new AbilityModifierController(ability,data,modifier));
        assertFalse(bearer.getAbilityPeriodicHealing().ownsAction(ability,0,modifier.onThinkInterval[0]));
        now.set(1_000_000_000L); bearer.onTick(0); assertFalse(bearer.getGlobalAbilityValues().containsKey("_Ball_Attack_COUNT"));
    }
    @Test void kinichsActualLockedTalentCopyDoesNotInstallItsBulletParameter() throws Exception {
        var data=resource("Kinich",16); ability=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        var copy=data.onAdded[4]; assertEquals(AbilityModifierAction.Type.CopyGlobalValue,copy.type);
        fixture.entity.getGlobalAbilityValues().put(copy.srcKey,3f); assertTrue(run(copy,bearer));
        assertFalse(bearer.getGlobalAbilityValues().containsKey(copy.dstKey));
    }
    @Test void kazuhasActualPlungeAltitudeIsCopiedToTheAbilityParameter() throws Exception {
        var data=resource("Kazuha",18); ability=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        var copy=data.modifiers.get("FallingAnthem_FallingDamageImmune_02").onAdded[0];
        bearer.getGlobalAbilityValues().put(copy.globalValueKey,8f); assertTrue(run(copy,bearer));
        assertEquals(8,ability.getAbilitySpecials().getFloat(copy.overrideMapKey)); assertTrue(fixture.player.packets.isEmpty());
    }
    private AbilityData resource(String avatar,int index) throws Exception {
        return JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"+avatar+".json"),AbilityConfigData.class).get(index).Default;
    }
}

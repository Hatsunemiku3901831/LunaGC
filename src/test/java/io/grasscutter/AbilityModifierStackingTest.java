package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.*;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;

class AbilityModifierStackingTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private AbilityTestFixture.TestEntity bearer;
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
        bearer.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
    }
    @AfterEach void close() { fixture.close(); }
    private Ability configure(String childFields) { return configure("Stacked",childFields); }
    private Ability configure(String name,String childFields) {
        var data=JsonUtils.decode("""
                {"abilityName":"%s","modifiers":{
                  "Pulse":{"thinkInterval":1,"duration":100,"onThinkInterval":[{"$type":"ApplyModifier","modifierName":"Child"}]},
                  "Child":{"stacking":"MultipleRefreshNoRemove","duration":10,
                    "stackingOption":{"maxModifierNumForMultipleType":3},
                    "properties":{"Actor_AttackDelta":20},
                    "onAdded":[{"$type":"AddGlobalValue","key":"started","value":1}],
                    "onRemoved":[{"$type":"AddGlobalValue","key":"ended","value":1}]%s}
                }}
                """.formatted(name,childFields),AbilityData.class);
        var result=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(result); bearer.getInstancedAbilities().add(result);
        result.registerModifier(bearer,new AbilityModifierController(result,data,data.modifiers.get("Pulse")));
        ability=result; return result;
    }
    private void tick(double seconds) { now.set((long)(seconds*1e9)); bearer.onTick(0); }
    private float attack() { return bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK); }
    private float value(String name) { return bearer.getGlobalAbilityValues().getOrDefault(name,0f); }
    private void client(boolean add,int id) throws Exception {
        var change=AbilityMetaModifierChange.newBuilder()
                .setAction(add?ModifierAction.MODIFIER_ACTION_ADDED:ModifierAction.MODIFIER_ACTION_REMOVED)
                .setParentAbilityName(AbilityString.newBuilder().setStr(ability.getData().abilityName))
                .setModifierLocalId(new ArrayList<>(ability.getData().modifiers.keySet()).indexOf("Child"));
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(42).setInstancedAbilityId(1).setInstancedModifierId(id))
                .setAbilityData(change.build().toByteString()).build());
    }
    @Test void aFullStackRefreshesTheOldestWithoutAddingOrRemovingItsCallbacks() {
        configure(""); tick(1); tick(2); tick(3); tick(4);
        assertEquals(160,attack()); assertEquals(3,value("started")); assertEquals(0,value("ended"));
        ability.removeModifier(bearer,"Pulse");
        tick(11); assertEquals(160,attack()); assertEquals(0,value("ended"));
        tick(12); assertEquals(140,attack()); tick(13); assertEquals(120,attack());
        tick(14); assertEquals(100,attack()); assertEquals(3,value("ended"));
    }
    @Test void eachRefreshRotatesTheOldestSlotAndStaleDeadlinesCannotDeleteIt() {
        configure(""); for(int i=1;i<=8;i++) tick(i);
        assertEquals(160,attack()); assertEquals(3,value("started"));
        ability.removeModifier(bearer,"Pulse");
        tick(15); assertEquals(160,attack()); tick(16); assertEquals(140,attack());
        tick(17); assertEquals(120,attack()); tick(18); assertEquals(100,attack());
        assertEquals(3,value("ended"));
    }
    @Test void dynamicAbilityLimitsAreResolvedBeforeCreatingEachStack() {
        configure(",\"stackingOption\":{\"abilitySpecialName\":\"cap\",\"maxModifierNumForMultipleType\":0}");
        tick(1); assertEquals(100,attack());
        ability.getAbilitySpecials().put("cap",2f); tick(2); tick(3); tick(4);
        assertEquals(140,attack()); assertEquals(2,value("started"));
    }
    @Test void theVerifiedObfuscatedLimitFieldsHaveTheSameBehavior() {
        configure(",\"stackingOption\":{\"PFEHHABPHDB\":\"\",\"KAANKELFPLA\":2}");
        tick(1); tick(2); tick(3); assertEquals(140,attack()); assertEquals(2,value("started"));
    }
    @Test void confirmedStacksKeepTheirIdsAndDoNotRepeatInitializationOnRefresh() throws Exception {
        configure(",\"stackingOption\":{\"maxModifierNumForMultipleType\":2}");
        tick(1); client(true,71); var first=bearer.getInstancedModifiers().get(71);
        tick(2); client(true,72); tick(3); client(true,71);
        assertSame(first,bearer.getInstancedModifiers().get(71)); assertEquals(140,attack());
        assertEquals(2,value("started")); assertEquals(0,value("ended"));
        ability.removeModifier(bearer,"Pulse"); tick(20); assertEquals(140,attack());
        client(false,71); assertEquals(120,attack()); client(false,72); assertEquals(100,attack());
        assertEquals(2,value("ended"));
    }
    @Test void explicitRemovalClearsEverySlotAndFutureApplicationsStartFromZero() {
        configure(""); tick(1); tick(2); tick(3); tick(4);
        ability.removeModifier(bearer,"Child"); assertEquals(100,attack()); assertEquals(3,value("ended"));
        tick(5); assertEquals(120,attack()); assertEquals(4,value("started"));
    }
    @Test void equalModifierNamesInDifferentAbilitiesHaveIndependentLimits() {
        var first=configure("First",",\"stackingOption\":{\"maxModifierNumForMultipleType\":2}");
        var second=configure("Second",",\"stackingOption\":{\"maxModifierNumForMultipleType\":2}");
        tick(1); tick(2); tick(3); assertEquals(180,attack()); assertEquals(4,value("started"));
        first.removeModifier(bearer,"Child"); assertEquals(140,attack());
        assertTrue(second.getModifiers(bearer).containsKey("Child"));
    }
    @Test void malformedOrUnknownStackingOptionsLeaveTheWholeChainToTheClient() {
        for(String option:new String[]{"{}","{\"maxModifierNumForMultipleType\":0}",
                "{\"maxModifierNumForMultipleType\":-1}","{\"maxModifierNumForMultipleType\":1.5}",
                "{\"maxModifierNumForMultipleType\":3,\"unknown\":true}",
                "{\"maxModifierNumForMultipleType\":3,\"enableMixedUnique\":true}",
                "{\"maxModifierNumForMultipleType\":3,\"KAANKELFPLA\":2}"}) {
            configure(",\"stackingOption\":"+option);
            now.set(0); tick(1); assertEquals(100,attack());
            assertFalse(ability.getModifiers(bearer).containsKey("Child"));
            ability.removeModifier(bearer,"Pulse");
        }
    }
    @Test void refreshingAStackExtendsItsPeriodicLifetimeWithoutResettingPulseCadence() {
        configure(",\"stackingOption\":{\"maxModifierNumForMultipleType\":1},\"thinkInterval\":0.75,\"onThinkInterval\":[{\"$type\":\"HealHP\",\"amount\":10}]");
        tick(1); tick(1.75); assertEquals(110,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        tick(2); tick(2.5); assertEquals(120,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        ability.removeModifier(bearer,"Pulse");
        tick(11.25); assertEquals(130,bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        tick(12); assertEquals(100,attack()); assertEquals(1,value("ended"));
    }
    @Test void anExpiryAlreadyWaitingOnTheRegistryCannotRemoveARefreshedGeneration() throws Exception {
        configure(",\"stackingOption\":{\"maxModifierNumForMultipleType\":1},\"duration\":1.5");
        tick(1); now.set(3_000_000_000L);
        var failure=new AtomicReference<Throwable>();
        var expiry=new Thread(()-> {
            try { bearer.getAbilityModifierDuration().tick(); }
            catch(Throwable error) { failure.set(error); }
        });
        try {
            synchronized(bearer.getInstancedModifiers()) {
                expiry.start();
                long deadline=System.nanoTime()+1_000_000_000L;
                while(expiry.getState()!=Thread.State.BLOCKED && System.nanoTime()<deadline) Thread.sleep(1);
                assertEquals(Thread.State.BLOCKED,expiry.getState());
                bearer.getAbilityPeriodicHealing().tick();
            }
            expiry.join(2000); assertFalse(expiry.isAlive()); assertNull(failure.get());
            assertEquals(120,attack()); assertEquals(0,value("ended"));
            ability.removeModifier(bearer,"Pulse"); tick(4.51);
            assertEquals(100,attack()); assertEquals(1,value("ended"));
        } finally { expiry.join(2000); }
    }
    @Test void parentGuardsRunWithoutHoldingAnotherEntitysRegistry() throws Exception {
        configure("");
        var entry=Class.forName("emu.grasscutter.game.ability.AbilityModifierStacking").getDeclaredMethod(
                "apply",Ability.class,AbilityModifier.class,emu.grasscutter.game.entity.GameEntity.class,BooleanSupplier.class);
        entry.setAccessible(true);
        BooleanSupplier guard=()-> {
            assertFalse(Thread.holdsLock(bearer.getInstancedModifiers()),
                    "The parent guard may acquire a different entity registry");
            return false;
        };
        assertDoesNotThrow(()->entry.invoke(null,ability,ability.getData().modifiers.get("Child"),bearer,guard));
        assertEquals(100,attack()); assertFalse(ability.getModifiers(bearer).containsKey("Child"));
    }
}

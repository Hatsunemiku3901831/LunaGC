package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.*;

class AbilitySelfGlobalValueWatcherTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity target;
    @BeforeEach void setup() throws Exception {
        fixture=new AbilityTestFixture(); target=new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43); fixture.scene.getEntities().put(43,target);
        target.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK,100);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK,200);
    }
    @AfterEach void close() { fixture.close(); }
    private Ability ability(boolean noInit,String fields) {
        String type=noInit?"AttachModifierToSelfGlobalValueNoInitMixin":"AttachModifierToSelfGlobalValueMixin";
        String mixin="{\"$type\":\""+type+"\",\"globalValueKey\":\"GV_mode\",\"modifierNameSteps\":[\"Low\",\"High\"],\"valueSteps\":[0,1,2]"+fields+"}";
        var data=JsonUtils.decode("{\"abilityName\":\"SelfWatcher\",\"modifiers\":{\"Low\":{\"properties\":{\"Actor_AttackDelta\":10}},\"High\":{\"properties\":{\"Actor_AttackDelta\":30}},\"Listener\":{\"modifierMixins\":["+mixin+"]}}}",AbilityData.class);
        var ability=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        return ability;
    }
    private void listen(Ability ability) {
        ability.registerModifier(target,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Listener")));
    }
    private void update(float value) { target.getGlobalAbilityValues().put("GV_mode",value); target.onAbilityValueUpdate(); }
    private float attack() { return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK); }
    private long initializations() { return fixture.scene.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify).count(); }

    @Test void selfInitializesTheActualBearerAndSelectsItsDefaultRange() {
        var ability=ability(false,",\"defaultGlobalValueOnCreate\":1"); listen(ability);
        assertEquals(1f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(230,attack());
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("GV_mode")); assertEquals(1,initializations());
    }
    @Test void selfPreservesAnExistingValueAndDoesNotSendAnInitialization() {
        update(0); var ability=ability(false,",\"defaultGlobalValueOnCreate\":1"); fixture.scene.packets.clear(); listen(ability);
        assertEquals(0f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(210,attack()); assertEquals(0,initializations());
    }
    @Test void refreshesDoNotOverwriteGameplayProgressOrRepeatInitialization() {
        var ability=ability(false,",\"defaultGlobalValueOnCreate\":0"); listen(ability); update(1);
        for(int i=0;i<8;i++)target.getAbilityGlobalValueWatchers().refresh();
        assertEquals(1f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(230,attack()); assertEquals(1,initializations());
    }
    @Test void noInitObservesZeroAndSubsequentWritesWithoutCreatingTheKey() {
        var ability=ability(true,""); listen(ability);
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_mode")); assertEquals(210,attack()); assertEquals(0,initializations());
        update(1); assertEquals(230,attack()); update(2); assertEquals(200,attack());
        ability.removeModifier(target,"Listener"); update(0); assertEquals(200,attack());
    }
    @Test void noInitDoesNotUseAnInitializationDefaultOrAnExternalTarget() {
        update(1); fixture.entity.getGlobalAbilityValues().put("GV_mode",0f);
        var ability=ability(true,",\"defaultGlobalValueOnCreate\":5,\"globalValueTarget\":\"Owner\""); listen(ability);
        assertEquals(230,attack()); target.getGlobalAbilityValues().remove("GV_mode"); target.onAbilityValueUpdate();
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_mode")); assertEquals(210,attack()); assertEquals(0,initializations());
    }
    @Test void clearingAKeyDoesNotReinitializeALiveSelfListenerButReattachingDoes() {
        var ability=ability(false,",\"defaultGlobalValueOnCreate\":1"); listen(ability);
        target.getGlobalAbilityValues().remove("GV_mode"); target.onAbilityValueUpdate();
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_mode")); assertEquals(1,initializations());
        ability.removeModifier(target,"Listener"); listen(ability);
        assertEquals(1f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(2,initializations());
    }
    @Test void aDynamicInitialValueIsEvaluatedWithTheBearersProperties() {
        var ability=ability(false,",\"defaultGlobalValueOnCreate\":\"initial\""); ability.getAbilitySpecials().put("initial",1);
        listen(ability); assertEquals(1f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(230,attack());
    }
    @Test void invalidDynamicInitializationUsesTheExistingFiniteFallback() {
        var ability=ability(false,",\"defaultGlobalValueOnCreate\":\"initial\""); ability.getAbilitySpecials().put("initial",Float.POSITIVE_INFINITY);
        listen(ability); assertEquals(0f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(210,attack()); assertEquals(1,initializations());
    }
    @Test void silentInitializationDoesNotPublishTheValueOrBuff() {
        var ability=ability(false,",\"defaultGlobalValueOnCreate\":1,\"enable\":false"); listen(ability);
        fixture.scene.packets.clear();
        ability.getData().modifiers.get("Listener").modifierMixins[0].enable=true;
        target.getAbilityGlobalValueWatchers().refresh(false);
        assertEquals(1f,target.getGlobalAbilityValues().get("GV_mode")); assertEquals(230,attack()); assertTrue(fixture.scene.packets.isEmpty());
    }
    @Test void aConfiguredSourceInitializesWithoutWritingTheBearer() {
        var observer=ability(false,",\"globalValueTarget\":\"Owner\",\"defaultGlobalValueOnCreate\":1");
        listen(observer); assertEquals(1f,fixture.entity.getGlobalAbilityValues().get("GV_mode")); assertEquals(230,attack());
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_mode"));
        fixture.entity.getGlobalAbilityValues().put("GV_mode",0f); fixture.entity.onAbilityValueUpdate(); assertEquals(210,attack());
    }
    @Test void rootInitializationRefreshesAnAlreadySubscribedBearer() {
        var observer=ability(false,",\"globalValueTarget\":\"Owner\"");
        observer.getData().modifiers.get("Listener").modifierMixins[0].type=AbilityMixinData.Type.AttachModifierToGlobalValueMixin;
        listen(observer); assertEquals(210,attack()); assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("GV_mode"));
        var initializer=ability(false,",\"defaultGlobalValueOnCreate\":1");
        fixture.entity.getInstancedAbilities().remove(initializer);
        initializer.getData().abilityMixins=initializer.getData().modifiers.get("Listener").modifierMixins;
        fixture.entity.getInstancedAbilities().add(initializer);
        assertEquals(1f,fixture.entity.getGlobalAbilityValues().get("GV_mode")); assertEquals(230,attack());
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_mode"));
    }
    @Test void replacingTheTeamSourceInitializesTheNewSourceAndReleasesTheOldSubscription() {
        fixture.player.setWorld(fixture.scene.getWorld());
        var first=new emu.grasscutter.game.entity.EntityTeam(fixture.player); fixture.player.getTeamManager().setEntity(first);
        var ability=ability(false,",\"globalValueTarget\":\"Team\",\"defaultGlobalValueOnCreate\":1"); listen(ability);
        assertEquals(1f,first.getGlobalAbilityValues().get("GV_mode"));
        var second=new emu.grasscutter.game.entity.EntityTeam(fixture.player); fixture.player.getTeamManager().setEntity(second);
        target.getAbilityGlobalValueWatchers().refresh();
        assertEquals(1f,second.getGlobalAbilityValues().get("GV_mode"));
        assertFalse(first.getAbilityGlobalValueWatchers().getReferencedKeys().contains("GV_mode"));
        second.getGlobalAbilityValues().put("GV_mode",0f); second.onAbilityValueUpdate(); assertEquals(210,attack());
        first.getGlobalAbilityValues().put("GV_mode",1f); first.onAbilityValueUpdate(); assertEquals(210,attack());
    }
}

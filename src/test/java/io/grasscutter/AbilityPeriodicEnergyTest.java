package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

class AbilityPeriodicEnergyTest {
    private AbilityTestFixture fixture;
    private TimedAvatar first, second;
    private Ability ability;
    private String modifierName="Pulse";
    private final AtomicLong now=new AtomicLong();
    private boolean previousEnergyUsage;

    private class TimedCarrier extends AbilityTestFixture.TestEntity {
        private final AbilityPeriodicHealing periodic;
        private final AbilityModifierDuration duration;
        TimedCarrier() {
            super(fixture.scene); setId(45);
            periodic=new AbilityPeriodicHealing(this,now::get);
            duration=new AbilityModifierDuration(this,now::get);
            fixture.scene.addEntity(this);
        }
        @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return periodic; }
        @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
    }

    private class TimedAvatar extends EntityAvatar {
        private final AbilityPeriodicHealing periodic;
        private final AbilityModifierDuration duration;
        private final Position position=new Position();
        TimedAvatar(int id) {
            super(fixture.scene,new Avatar() {
                @Override public Player getPlayer() { return fixture.player; }
                @Override public AvatarSkillDepotData getSkillDepot() {
                    return new AvatarSkillDepotData() {
                        @Override public ElementType getElementType() { return ElementType.Fire; }
                    };
                }
                @Override public void save() {}
                @Override public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() {
                    return new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
                }
            });
            setId((EntityIdType.AVATAR.getId()<<emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT)|id);
            setFightProperty(FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY,60);
            setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY,0);
            setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,100);
            setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,100);
            periodic=new AbilityPeriodicHealing(this,now::get);
            duration=new AbilityModifierDuration(this,now::get);
        }
        @Override public void initAbilities() {}
        @Override public AbilityPeriodicHealing getAbilityPeriodicHealing() { return periodic; }
        @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
        @Override public Position getPosition() { return position; }
        @Override public SceneEntityInfo toProto() { return SceneEntityInfo.newBuilder().setEntityId(getId()).build(); }
        void die() { setDead(true); }
    }
    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture();
        previousEnergyUsage=Configuration.GAME_OPTIONS.energyUsage;
        Configuration.GAME_OPTIONS.energyUsage=true;
        first=new TimedAvatar(43); second=new TimedAvatar(44);
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.getTeamManager().setEntity(new emu.grasscutter.game.entity.EntityTeam(fixture.player) {
            @Override public void initAbilities() {}
        });
        fixture.player.getTeamManager().getActiveTeam().addAll(java.util.List.of(first,second));
        fixture.scene.getPlayers().add(fixture.player);
        fixture.scene.addEntity(first);
        fixture.scene.packets.clear();
    }
    @AfterEach void close() { Configuration.GAME_OPTIONS.energyUsage=previousEnergyUsage; fixture.close(); }
    private void configure(boolean offstage) {
        var data=JsonUtils.decode("""
                {"abilityName":"EnergyPulse","modifiers":{"Pulse":{"thinkInterval":2,"duration":8,
                "onThinkInterval":[{"$type":"ReviveElemEnergy","value":3,"doOffStage":%s,"target":"Self"}]}}}
                """.formatted(offstage),AbilityData.class);
        data.initialize();
        ability=new Ability(data,first,fixture.player);
    }
    private void resource(String character,String name,String modifier) throws Exception {
        var configs=JsonUtils.loadToClass(Path.of(System.getenv().getOrDefault("LUNAGC_RESOURCES_PATH","resources"),
                "BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"+character+".json"),AbilityConfigData[].class);
        var data=java.util.Arrays.stream(configs).map(c->c.Default).filter(d->name.equals(d.abilityName)).findFirst().orElseThrow();
        modifierName=modifier; ability=new Ability(data,first,fixture.player);
    }
    private AbilityModifierController attach(emu.grasscutter.game.entity.GameEntity bearer) {
        if(!first.getInstancedAbilities().contains(ability)) first.getInstancedAbilities().add(ability);
        var controller=new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get(modifierName));
        ability.registerModifier(bearer,controller); return controller;
    }
    private void tick(double seconds) { now.set((long)(seconds*1_000_000_000d)); fixture.scene.onTick(); }
    private float energy(TimedAvatar avatar) { return avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY); }
    private void select(TimedAvatar from,TimedAvatar to,int index) {
        fixture.player.getTeamManager().setCurrentCharacterIndex(index);
        fixture.scene.replaceEntity(from,to);
    }
    @Test void sceneTicksGrantEnergyExactlyOnceAtEachConfiguredInterval() {
        configure(true); attach(first); tick(1.999); assertEquals(0,energy(first));
        tick(2); tick(2); assertEquals(3,energy(first)); tick(4); assertEquals(6,energy(first));
    }
    @Test void anActualPartySwitchPreservesTheOffstageClockAndSwitchingBackDoesNotRestartIt() {
        configure(true); attach(first); tick(1); long version=first.getAbilityLifecycleVersion();
        select(first,second,1); assertEquals(version,first.getAbilityLifecycleVersion());
        tick(2); assertEquals(3,energy(first)); assertEquals(0,energy(second));
        tick(3); select(second,first,0); tick(4); assertEquals(6,energy(first));
    }
    @Test void actionsWithoutTheOffstageFlagStayDormantUntilTheAvatarReturns() {
        configure(false); attach(first); select(first,second,1);
        tick(2); tick(4); assertEquals(0,energy(first));
        select(second,first,0); tick(6); assertEquals(3,energy(first));
    }
    @Test void durationStillExpiresWhileTheAvatarIsOffstage() {
        configure(true); var controller=attach(first); select(first,second,1);
        tick(2); tick(4); tick(6); tick(8); assertEquals(12,energy(first));
        tick(8.1); assertNotSame(controller,first.getAppliedAbilityModifiers()
                .getOrDefault(ability,java.util.Map.of()).get(modifierName));
        select(second,first,0); tick(10); assertEquals(12,energy(first));
    }
    @Test void removingAnOffstagePartyMemberCancelsItsTimerEvenBeforeAnotherAppearance() {
        configure(true); attach(first); select(first,second,1); tick(2);
        fixture.player.getTeamManager().getActiveTeam().remove(first); fixture.scene.removeEntity(first);
        fixture.player.getTeamManager().getActiveTeam().add(first); select(second,first,1);
        tick(4); assertEquals(3,energy(first));
    }
    @Test void aReplacementOutsideThePartyInvalidatesTheOldLifetime() {
        configure(true); attach(first); fixture.player.getTeamManager().getActiveTeam().remove(first);
        select(first,second,0); tick(2); assertEquals(0,energy(first));
        assertFalse(first.isAbilityLifecycleCurrent(0));
    }
    @Test void leavingTheSceneCannotContinueOffstageEffects() {
        configure(true); attach(first); select(first,second,1);
        fixture.player.setScene(null); tick(2); first.onTick(0); assertEquals(0,energy(first));
    }
    @Test void aDeadOffstageAvatarStopsItsTimerAndCannotResumeOldPulses() {
        configure(true); attach(first); select(first,second,1); first.die();
        tick(2); assertFalse(first.isAbilityLifecycleCurrent(0)); assertEquals(0,energy(first));
    }
    @Test void aReusedEntityIdCannotReceiveAnOldAvatarsEnergy() {
        configure(true); attach(first); select(first,second,1);
        var replacement=new AbilityTestFixture.TestEntity(fixture.scene); replacement.setId(first.getId());
        fixture.scene.addEntity(replacement); tick(2); first.onTick(0); assertEquals(0,energy(first));
    }
    @Test void aLateNativeCallbackForTheOwnedPulseCannotGrantEnergyTwice() {
        configure(true); attach(first); tick(2);
        var action=ability.getData().modifiers.get(modifierName).onThinkInterval[0];
        assertTrue(first.getAbilityPeriodicHealing().ownsAction(ability,0,action));
        int localId=ability.getData().localIdToAction.entrySet().stream().filter(e->e.getValue()==action)
                .findFirst().orElseThrow().getKey();
        ability.getManager().handleServerInvoke(emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry
                .newBuilder().setEntityId(first.getId())
                .setHead(emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead.newBuilder()
                        .setTargetId(first.getId()).setInstancedAbilityId(1).setLocalId(localId)).build());
        assertEquals(3,energy(first));
        select(first,second,1); tick(4);
        ability.getManager().handleServerInvoke(emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry
                .newBuilder().setEntityId(first.getId())
                .setHead(emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead.newBuilder()
                        .setTargetId(first.getId()).setInstancedAbilityId(1).setLocalId(localId)).build());
        assertEquals(6,energy(first));
    }
    @Test void ayatosActualOffstageModifierResolvesLateTalentParametersAndKeepsItsClock() throws Exception {
        resource("Ayato","Avatar_Ayato_PermanentSkill_2_ReviveEnergy","Avatar_Ayato_ReviveEnergy_OffStage");
        attach(first); select(first,second,1); tick(1);
        ability.getAbilitySpecials().put("Interval",1); ability.getAbilitySpecials().put("Energy",2);
        first.onAbilityValueUpdate(); tick(2); tick(3); assertEquals(4,energy(first));
    }
    @Test void dorisActualPulseSelectsOnlyConnectedNearbyTargets() throws Exception {
        resource("Dori","Avatar_Dori_ChargingStation","ReviveEnergy_Handler");
        ability.getAbilitySpecials().put("ReviveElemEnergy",2);
        first.getGlobalAbilityValues().put("_ABILITY_Dori_Is_Connecting",1f);
        second.getPosition().setX(9); fixture.scene.addEntity(second);
        var carrier=new TimedCarrier(); attach(carrier);
        var action=ability.getData().modifiers.get(modifierName).onThinkInterval[0];
        assertTrue(carrier.getAbilityPeriodicHealing().ownsAction(ability,0,action),"resource ownership");
        assertTrue(AbilityTargetSelector.select(action.otherTargets,ability,carrier).contains(first),"shape selector");
        tick(2); assertEquals(2,energy(first),"connected caster"); assertEquals(0,energy(second),"disconnected ally");
        second.getGlobalAbilityValues().put("_ABILITY_Dori_Is_Connecting",1f);
        tick(4); assertEquals(2,energy(second),"connected ally");
        second.getPosition().setX(11); tick(6); assertEquals(2,energy(second),"out of range ally");
    }
    @Test void pyroTravelersActualCounterGrantsExactlyTenPulsesAcrossAPartySwitch() throws Exception {
        resource("Player_Fire","Avatar_PlayerFire_SpecialConstellation_TheAbyssXiuhcoatl",
                "UNIQUE_Avatar_Player_Fire_SpecialConstellation_4_RecoverEnergy");
        attach(first); select(first,second,1);
        for(int i=1;i<=12;i++) tick(i);
        assertEquals(40,energy(first)); assertEquals(0,energy(second));
    }
    @Test void unknownEnergyPredicatesKeepTheWholePulseClientOwned() {
        configure(true); var action=ability.getData().modifiers.get(modifierName).onThinkInterval[0];
        action.predicates=java.util.List.of(java.util.Map.of("$type","KJBEKOGFBKG","ratio",0.5));
        attach(first); tick(2); assertEquals(0,energy(first));
        assertFalse(first.getAbilityPeriodicHealing().ownsAction(ability,0,action));
    }
}

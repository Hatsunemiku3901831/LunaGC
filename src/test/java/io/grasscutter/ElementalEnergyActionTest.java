package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionReviveElemEnergy;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class ElementalEnergyActionTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private EntityAvatar target;
    private boolean previousEnergyUsage;
    private final AtomicInteger saves=new AtomicInteger();

    @BeforeEach void scene() throws Exception {
        fixture=new AbilityTestFixture();
        previousEnergyUsage=Configuration.GAME_OPTIONS.energyUsage;
        Configuration.GAME_OPTIONS.energyUsage=true;
        var depot=new AvatarSkillDepotData() {
            @Override public ElementType getElementType() { return ElementType.Fire; }
        };
        var avatar=new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public AvatarSkillDepotData getSkillDepot() { return depot; }
            @Override public void save() { saves.incrementAndGet(); }
        };
        target=new EntityAvatar(fixture.scene,avatar);
        target.setId(43); fixture.scene.getEntities().put(43,target);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY,60);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY,10);
        target.setFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY,3);
        ability=new Ability(JsonUtils.decode("{\"abilityName\":\"Energy\",\"abilitySpecials\":{\"gain\":8}}",
                AbilityData.class),fixture.entity,fixture.player);
        fixture.scene.packets.clear(); saves.set(0);
    }
    @AfterEach void close() {
        Configuration.GAME_OPTIONS.energyUsage=previousEnergyUsage;
        fixture.close();
    }
    private boolean grant(String fields) {
        var action=JsonUtils.decode("{\"$type\":\"ReviveElemEnergy\""+fields+"}",AbilityModifierAction.class);
        return new ActionReviveElemEnergy().execute(ability,action,ByteString.EMPTY,target);
    }
    private float energy() { return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY); }
    private EntityFightPropChangeReasonNotify change() throws Exception {
        var packets=fixture.scene.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.EntityFightPropChangeReasonNotify).toList();
        assertEquals(1,packets.size());
        return EntityFightPropChangeReasonNotify.parseFrom(packets.get(0).getData());
    }
    @Test void fixedGainUsesTheWrittenValueAndReportsTheGainWithoutRechargeMultiplication() throws Exception {
        assertTrue(grant(",\"value\":\"gain\",\"ratio\":0"));
        assertEquals(18,energy()); assertEquals(8,change().getPropDelta()); assertEquals(1,saves.get());
        assertEquals(PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,change().getReason());
        assertEquals(ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_ABILITY,change().getChangeEnergyReson());
    }
    @Test void aGainAtTheCapReportsOnlyTheEnergyActuallyAdded() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY,59);
        assertTrue(grant(",\"value\":8"));
        assertEquals(60,energy()); assertEquals(1,change().getPropDelta());
        var update=fixture.scene.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.EntityFightPropUpdateNotify).findFirst().orElseThrow();
        var proto=EntityFightPropUpdateNotify.parseFrom(update.getData());
        assertEquals(60f,proto.getFightPropMapMap().get(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY.getId()));
    }
    @Test void aNegativeGrantCannotTakeEnergyBelowZero() throws Exception {
        assertTrue(grant(",\"value\":-100"));
        assertEquals(0,energy()); assertEquals(-10,change().getPropDelta());
    }
    @Test void absentOrZeroGainsDoNotSaveOrSendChanges() {
        assertTrue(grant("")); assertTrue(grant(",\"value\":0,\"ratio\":5"));
        assertEquals(10,energy()); assertTrue(fixture.scene.packets.isEmpty()); assertEquals(0,saves.get());
    }
    @Test void aFullAvatarDoesNotEmitAChangeForClippedEnergy() {
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY,60);
        assertTrue(grant(",\"value\":8"));
        assertEquals(60,energy()); assertTrue(fixture.scene.packets.isEmpty()); assertEquals(0,saves.get());
    }
    @Test void aLockedTalentCannotGrantElementalEnergy() {
        assertTrue(grant(",\"value\":8,\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"MissingTalent\"}]"));
        assertEquals(10,energy()); assertTrue(fixture.scene.packets.isEmpty()); assertEquals(0,saves.get());
    }
    @Test void nonFiniteGainsCannotPoisonTheStoredEnergy() {
        ability.getAbilitySpecials().put("gain",Float.POSITIVE_INFINITY);
        // DynamicFloat already normalizes a non-finite expression to the zero fallback.
        assertTrue(grant(",\"value\":\"gain\""));
        assertEquals(10,energy()); assertTrue(fixture.scene.packets.isEmpty()); assertEquals(0,saves.get());
    }
    @Test void disabledEnergyUsagePreservesTheExistingEnergyWithoutFalseNotifications() {
        Configuration.GAME_OPTIONS.energyUsage=false;
        assertTrue(grant(",\"value\":8"));
        assertEquals(10,energy()); assertTrue(fixture.scene.packets.isEmpty()); assertEquals(0,saves.get());
    }
    @Test void simultaneousGainsPreserveEveryAcceptedChange() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY,100);
        var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<80;i++) futures.add(pool.submit(()->grant(",\"value\":1")));
            for(var future:futures) assertTrue(future.get(5,TimeUnit.SECONDS));
            assertEquals(90,energy()); assertEquals(80,saves.get());
            float total=0;
            for(var packet:fixture.scene.packets) if(packet.getOpcode()==PacketOpcodes.EntityFightPropChangeReasonNotify)
                total+=EntityFightPropChangeReasonNotify.parseFrom(packet.getData()).getPropDelta();
            assertEquals(80,total);
        } finally { pool.shutdownNow(); }
    }
    @Test void aDirectGainEqualToTheCurrentEnergyStillAddsThatAmount() {
        assertTrue(target.addEnergy(10)); assertEquals(20,energy()); assertEquals(1,saves.get());
        assertEquals(1,fixture.scene.packets.size());
    }
    @Test void particlesApplyRechargeButReportOnlyTheClippedDelta() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY,59);
        target.addEnergy(8,PropChangeReason.PropChangeReason_PROP_CHANGE_ENERGY_BALL);
        assertEquals(60,energy()); assertEquals(1,change().getPropDelta());
        assertEquals(PropChangeReason.PropChangeReason_PROP_CHANGE_ENERGY_BALL,change().getReason());
    }
    @Test void negativeFlatEntityGainsClampAtZero() throws Exception {
        target.addEnergy(-100,PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,true);
        assertEquals(0,energy()); assertEquals(-10,change().getPropDelta());
    }
    @Test void invalidParticleAmountsDoNotWriteNaNOrSendChanges() {
        target.addEnergy(Float.NaN,PropChangeReason.PropChangeReason_PROP_CHANGE_ENERGY_BALL);
        assertEquals(10,energy()); assertTrue(fixture.scene.packets.isEmpty()); assertEquals(0,saves.get());
    }
}

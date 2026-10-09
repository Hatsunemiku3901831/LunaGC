package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ServerGlobalValueChangeNotifyOuterClass.ServerGlobalValueChangeNotify;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class SpecialEnergyActionTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach void setup() throws Exception {
        fixture = new AbilityTestFixture();
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Energy_Test\","
                + "\"abilitySpecials\":{\"Gain\":8,\"NyxValueMax\":90}}", AbilityData.class),
                fixture.entity, fixture.player);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 100);
    }
    @AfterEach void restore() { fixture.close(); }
    private void action(String json) {
        ability.getManager().executeActionNow(ability, JsonUtils.decode(json, AbilityModifierAction.class),
                ByteString.EMPTY, fixture.entity);
    }

    @Test void globalAdditionPreservesExplicitValueAndAbsentAdditionDoesNotInventOne() {
        action("{\"$type\":\"AddGlobalValue\",\"key\":\"Counter\",\"value\":8,\"ratio\":0}");
        action("{\"$type\":\"AddGlobalValue\",\"key\":\"Counter\"}");
        assertEquals(8, fixture.entity.getGlobalAbilityValues().get("Counter"));
    }

    @Test void ordinaryElementalEnergyActionsAndParticleUseRemainFunctional() {
        var depot = new emu.grasscutter.data.excels.avatar.AvatarSkillDepotData() {
            @Override public emu.grasscutter.game.props.ElementType getElementType() {
                return emu.grasscutter.game.props.ElementType.Fire;
            }
        };
        var avatar = new emu.grasscutter.game.avatar.Avatar() {
            @Override public emu.grasscutter.game.player.Player getPlayer() { return fixture.player; }
            @Override public emu.grasscutter.data.excels.avatar.AvatarSkillDepotData getSkillDepot() { return depot; }
            @Override public void save() {}
        };
        var entity = new emu.grasscutter.game.entity.EntityAvatar(fixture.scene, avatar);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY, 60);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 1f);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        var revive = JsonUtils.decode("{\"$type\":\"ReviveElemEnergy\",\"value\":1,\"ratio\":0}", AbilityModifierAction.class);
        ability.getManager().executeActionNow(ability, revive, ByteString.EMPTY, entity);
        assertEquals(1, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        var particle = JsonUtils.decode("{\"id\":2017,\"materialType\":\"MATERIAL_ADSORBATE\","
                + "\"itemUse\":[{\"useOp\":\"ITEM_USE_ADD_ALL_ENERGY\",\"useParam\":[\"2\"]}]}",
                emu.grasscutter.data.excels.ItemData.class);
        particle.onLoad();
        assertTrue(particle.isUseOnGain());
        assertTrue(particle.getItemUseActions().get(0).useItem(new emu.grasscutter.game.props.ItemUseAction.UseItemParams(
                fixture.player, emu.grasscutter.game.props.ItemUseTarget.ITEM_USE_TARGET_CUR_TEAM)));
        assertEquals(3, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
    }

    @Test void specialEnergyReadsBothSpecialsAndGlobalsWithoutZeroRatioOverwritingValue() {
        fixture.entity.getGlobalAbilityValues().put("Bonus", 2f);
        action("{\"$type\":\"ReviveSpecialEnergy\",\"value\":[\"Gain\",\"Bonus\",\"ADD\"],\"ratio\":0}");
        assertEquals(10, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        assertTrue(fixture.scene.packets.stream().anyMatch(packet ->
                packet.getOpcode() == PacketOpcodes.EntityFightPropUpdateNotify));
    }
    @Test void specialEnergyClampsAtBothEndsAndAbsentGainStaysZero() {
        action("{\"$type\":\"ReviveSpecialEnergy\",\"value\":200}");
        assertEquals(100, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        action("{\"$type\":\"ReviveSpecialEnergy\",\"value\":-200}");
        action("{\"$type\":\"ReviveSpecialEnergy\"}");
        assertEquals(0, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }
    @Test void conditionalConstellationGainIsNotGrantedWhenTalentIsAbsent() {
        action("{\"$type\":\"ReviveSpecialEnergy\",\"value\":10,\"predicates\":["
                + "{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"SkirkNew_Constellation_2\"}]}");
        assertEquals(0, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }
    @Test void nyxUsesResourceBoundsAndSupportsClearing() throws Exception {
        action("{\"$type\":\"AddNyxValue\",\"value\":100}");
        assertEquals(90, fixture.entity.getNyxValue());
        var packet = fixture.scene.packets.stream().filter(p -> p.getOpcode() == PacketOpcodes.ServerGlobalValueChangeNotify)
                .findFirst().orElseThrow();
        assertEquals(90, ServerGlobalValueChangeNotify.parseFrom(packet.getData()).getValue());
        action("{\"$type\":\"SetNyxValue\"}");
        assertEquals(0, fixture.entity.getNyxValue());
    }
    @Test void nyxMixinEvaluatesFreezeExpressionAndDoesNotFallbackFromExplicitZero() {
        fixture.entity.getGlobalAbilityValues().put("NyxValue", 10f);
        fixture.entity.getGlobalAbilityValues().put("NyxFreeze", 0f);
        var mixin = JsonUtils.decode("{\"$type\":\"ChangeNyxValueMixin\",\"minValue\":0,\"maxValue\":\"NyxValueMax\","
                + "\"value\":[0,0.5,\"NyxFreeze\",\"MUL\",\"SUB\"]}", AbilityMixinData.class);
        var handler = AbilityManager.mixinHandlers.get(mixin.type);
        assertNotNull(handler);
        assertTrue(handler.execute(ability, mixin, ByteString.EMPTY, fixture.entity));
        assertEquals(10, fixture.entity.getNyxValue());
        fixture.entity.getGlobalAbilityValues().put("NyxFreeze", 1f);
        handler.execute(ability, mixin, ByteString.EMPTY, fixture.entity);
        assertEquals(9.5f, fixture.entity.getNyxValue());
        action("{\"$type\":\"AddNyxValue\",\"value\":-100}");
        assertEquals(0, fixture.entity.getNyxValue());
    }
    @Test void concurrentNyxIncrementsAreNotLost() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var pending = new ArrayList<Future<?>>();
            for (int i = 0; i < 80; i++) pending.add(pool.submit(() ->
                    action("{\"$type\":\"AddNyxValue\",\"value\":1}")));
            for (var task : pending) task.get(5, TimeUnit.SECONDS);
            assertEquals(80, fixture.entity.getNyxValue());
        } finally { pool.shutdownNow(); }
    }
}

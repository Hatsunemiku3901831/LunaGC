package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify;
import java.util.*;
import org.junit.jupiter.api.*;

class TowerBlessingEffectsTest {
    PhlogistonMutationTest env;
    TowerBlessingStateTest resources;
    TowerBlessingEffects effects;

    @BeforeAll
    static void resources() throws Exception {
        TowerBlessingStateTest.resources();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        resources = new TowerBlessingStateTest();
        resources.setup();
        effects = env.fixture.player.getTowerManager().getBlessingEffects();
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, 100);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 100);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 100);
        env.fixture.player.packets.clear();
    }

    @AfterEach
    void close() {
        effects.clear();
        resources.restore();
        env.close();
    }

    float prop(FightProperty property) {
        return env.caster.getFightProperty(property);
    }

    void activate(Integer... ids) {
        effects.activate(env.fixture.scene, List.of(ids));
    }

    emu.grasscutter.game.entity.EntityMonster monster() {
        return new emu.grasscutter.game.entity.EntityMonster(
                env.fixture.scene,
                new emu.grasscutter.data.excels.monster.MonsterData(),
                new emu.grasscutter.game.world.Position(),
                new emu.grasscutter.game.world.Position(),
                1) {
            @Override
            public void recalcStats() {}

            @Override
            public void initAbilities() {}
        };
    }

    ServerBuffChangeNotify packet() throws Exception {
        var packets = env.fixture.player.packets;
        return ServerBuffChangeNotify.parseFrom(
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ServerBuffChangeNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    @Test
    void repeatedCardsStackAndRefreshDoesNotDoubleTheirProperties() {
        activate(2, 2);
        assertEquals(.16f, prop(FightProperty.FIGHT_PROP_CRITICAL), .00001);
        assertEquals(.30f, prop(FightProperty.FIGHT_PROP_CRITICAL_HURT), .00001);
        for (int i = 0; i < 5; i++) effects.refresh();
        assertEquals(.16f, prop(FightProperty.FIGHT_PROP_CRITICAL), .00001);
        effects.clear();
        assertEquals(0, prop(FightProperty.FIGHT_PROP_CRITICAL), .00001);
    }

    @Test
    void addingAnotherCardPreservesExistingMaxHpAndHealth() {
        activate(7);
        assertEquals(125, prop(FightProperty.FIGHT_PROP_MAX_HP), .001);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 120);
        activate(7, 2);
        assertEquals(120, prop(FightProperty.FIGHT_PROP_CUR_HP), .001);
        assertEquals(125, prop(FightProperty.FIGHT_PROP_MAX_HP), .001);
    }

    @Test
    void hpConditionalCardsFollowEachAvatarHealth() {
        activate(25);
        assertEquals(130, prop(FightProperty.FIGHT_PROP_CUR_ATTACK), .001);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 40);
        effects.refresh();
        assertEquals(100, prop(FightProperty.FIGHT_PROP_CUR_ATTACK), .001);
        activate(26);
        assertEquals(160, prop(FightProperty.FIGHT_PROP_CUR_ATTACK), .001);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 80);
        effects.refresh();
        assertEquals(100, prop(FightProperty.FIGHT_PROP_CUR_ATTACK), .001);
    }

    @Test
    void nativeServerBuffConfirmationDoesNotCollideWithOrdinaryModifierIds() throws Exception {
        activate(2);
        var buff = packet().getServerBuffList(0);
        assertEquals(2, buff.getServerBuffType());
        var data = GameData.getAbilityData("Atk_Dungeon");
        int index = new ArrayList<>(data.modifiers.keySet()).indexOf("SERVER_Critical_Dungeon");
        var invoke =
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(env.caster.getId())
                        .setHead(
                                AbilityInvokeEntryHead.newBuilder()
                                        .setServerBuffUid(buff.getServerBuffUid())
                                        .setFPDAONBEBHKValue(1)
                                        .setInstancedModifierId(1))
                        .setArgumentType(
                                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                        .setAbilityData(
                                AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange.newBuilder()
                                        .setAction(ModifierActionOuterClass.ModifierAction.MODIFIER_ACTION_ADDED)
                                        .setModifierLocalId(index)
                                        .build()
                                        .toByteString())
                        .build();
        assertTrue(effects.accept(invoke));
        assertTrue(effects.accept(invoke));
        assertEquals(.08f, prop(FightProperty.FIGHT_PROP_CRITICAL), .00001);
        assertTrue(env.caster.getInstancedModifiers().isEmpty());
        effects.clear();
        assertFalse(effects.accept(invoke));
    }

    @Test
    void unselectedSiblingModifiersAndForeignRecipientsAreRejected() throws Exception {
        activate(2);
        var buff = packet().getServerBuffList(0);
        var data = GameData.getAbilityData("Atk_Dungeon");
        int index = new ArrayList<>(data.modifiers.keySet()).indexOf("SERVER_Atk_Dungeon_Ratio");
        var invoke =
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(env.caster.getId())
                        .setHead(
                                AbilityInvokeEntryHead.newBuilder()
                                        .setServerBuffUid(buff.getServerBuffUid())
                                        .setFPDAONBEBHKValue(1))
                        .setArgumentType(
                                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                        .setAbilityData(
                                AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange.newBuilder()
                                        .setAction(ModifierActionOuterClass.ModifierAction.MODIFIER_ACTION_ADDED)
                                        .setModifierLocalId(index)
                                        .build()
                                        .toByteString())
                        .build();
        assertFalse(effects.accept(invoke));
        assertFalse(effects.accept(invoke.toBuilder().setEntityId(999).build()));
        assertEquals(100, prop(FightProperty.FIGHT_PROP_CUR_ATTACK), .001);
    }

    @Test
    void killingHealsTheAttackerOnceAndNeverPaysForAnUnownedKill() {
        activate(31);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 20);
        var killed = monster();
        effects.onKill(killed, 999);
        assertEquals(20, prop(FightProperty.FIGHT_PROP_CUR_HP), .001);
        effects.onKill(killed, env.caster.getId());
        assertEquals(28, prop(FightProperty.FIGHT_PROP_CUR_HP), .001);
        effects.onKill(killed, env.caster.getId());
        assertEquals(28, prop(FightProperty.FIGHT_PROP_CUR_HP), .001);
    }

    @Test
    void burstHealingUsesTheExportedCallbackAndIgnoresTheImmediateReplay() {
        activate(34);
        env.caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 20);
        effects.onBurst(env.caster);
        assertEquals(50, prop(FightProperty.FIGHT_PROP_CUR_HP), .001);
        effects.onBurst(env.caster);
        assertEquals(50, prop(FightProperty.FIGHT_PROP_CUR_HP), .001);
    }

    @Test
    void successfulKillCooldownProcResetsOnlyTheSkillAndHonorsItsTwentySecondGate() throws Exception {
        var depot =
                emu.grasscutter.utils.JsonUtils.decode(
                        "{id:9901,skills:[10012,10013],energySkill:10014,talents:[]}",
                        emu.grasscutter.data.excels.avatar.AvatarSkillDepotData.class);
        var depotField = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField("skillDepot");
        depotField.setAccessible(true);
        depotField.set(env.caster.getAvatar(), depot);
        for (int id : List.of(10013, 10014))
            env.environment.put(
                    GameData.getAvatarSkillDataMap(),
                    id,
                    emu.grasscutter.utils.JsonUtils.decode(
                            "{id:" + id + ",maxChargeNum:1}",
                            emu.grasscutter.data.excels.avatar.AvatarSkillData.class));
        var random =
                GameData.getAbilityData("Support_Dungeon")
                        .modifiers
                        .get("ResetCDWhenKill_Dungeon_Random")
                        .onAdded[0];
        var previous = random.chance;
        random.chance = new emu.grasscutter.data.common.DynamicFloat(1f);
        try {
            activate(35);
            var cooldown = env.fixture.player.getAbilityManager().getSkillCooldown();
            assertTrue(cooldown.start(env.caster.getAvatar(), 10013, 10));
            assertTrue(cooldown.start(env.caster.getAvatar(), 10014, 10));
            effects.onKill(monster(), env.caster.getId());
            assertEquals(0, cooldown.state(env.caster.getAvatar(), 10013, 1).getFullCdTimeListCount());
            assertEquals(1, cooldown.state(env.caster.getAvatar(), 10014, 1).getFullCdTimeListCount());
            assertTrue(cooldown.start(env.caster.getAvatar(), 10013, 10));
            effects.onKill(monster(), env.caster.getId());
            assertEquals(1, cooldown.state(env.caster.getAvatar(), 10013, 1).getFullCdTimeListCount());
        } finally {
            random.chance = previous;
        }
    }
}

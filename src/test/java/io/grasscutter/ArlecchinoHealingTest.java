package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatTypeArgumentOuterClass.CombatTypeArgument;
import emu.grasscutter.net.proto.EvtBeingHealedNotifyOuterClass.EvtBeingHealedNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.junit.jupiter.api.*;

class ArlecchinoHealingTest {
    private AbilityTestFixture fixture;
    private EntityAvatar arlecchino;
    private Ability burst;
    private AbilityModifierAction heal;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        arlecchino = avatar(10000096, 10, 40);
        var rows = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Arlecchino.json"),
                AbilityConfigData.class);
        var data = rows.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Arlecchino_ElementalBurst_Attack"))
                .findFirst().orElseThrow();
        burst = new Ability(data, arlecchino, fixture.player);
        burst.getAbilitySpecials().put("HealHp_Debt_Ratio", 1.5f);
        burst.getAbilitySpecials().put("HealHp_Atk_Ratio", 1.5f);
        heal = data.modifiers.get("Avatar_Arlecchino_ElementalBurst_HealDelay").onRemoved[0];
        snapshot(burst, arlecchino);
        fixture.scene.packets.clear();
        fixture.player.packets.clear();
    }

    @AfterEach
    void restore() { fixture.close(); }

    private EntityAvatar avatar(int id, float hp, float debt) {
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public int getAvatarId() { return id; }
            @Override public long getGuid() { return id; }
            @Override public IntSet getTalentIdList() { return IntSets.EMPTY_SET; }
            @Override public void save() {}
        };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, debt);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 10);
        var entity = new EntityAvatar(fixture.scene, avatar);
        entity.setConvertToHpDebt(true);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private void snapshot(Ability ability, EntityAvatar target) {
        for (var action : ability.getData().onAbilityStart) {
            if (action.type == AbilityModifierAction.Type.SetOverrideMapValue)
                ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, target);
        }
    }

    private void execute(Ability ability, AbilityModifierAction action, EntityAvatar target) {
        ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, target);
    }

    private float hp(EntityAvatar entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    private float debt(EntityAvatar entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
    }

    @Test
    void theResourceHealTagSurvivesLoading() {
        assertEquals("Arlecchino_ElementalBurst_Heal", heal.healTag);
    }

    @Test
    void actualBurstPaysBondBeforeRestoringHealthAndKeepsTheCombatRestriction() {
        execute(burst, heal, arlecchino);
        // Resource: 1.5 * 40 Bond + 1.5 * 10 ATK = 75; 40 repays Bond, 35 restores HP.
        assertEquals(45f, hp(arlecchino));
        assertEquals(0f, debt(arlecchino));
        assertTrue(arlecchino.isConvertToHpDebt());
        assertTrue(fixture.player.packets.stream().anyMatch(
                packet -> packet.getOpcode() == PacketOpcodes.AvatarFightPropUpdateNotify));
        assertFalse(arlecchino.getGlobalAbilityValues().containsKey("_ABILITY_Clorinde_Dodge_HealFlag"));
    }

    @Test
    void burstReportsTheRequestedAmountAndActualHpAddition() throws Exception {
        execute(burst, heal, arlecchino);
        EvtBeingHealedNotify event = null;
        for (var packet : fixture.scene.packets) {
            if (packet.getOpcode() != PacketOpcodes.CombatInvocationsNotify) continue;
            for (var invocation : CombatInvocationsNotify.parseFrom(packet.getData()).getInvokeListList()) {
                if (invocation.getArgumentType() == CombatTypeArgument.CombatTypeArgument_COMBAT_BEING_HEALED_NTF)
                    event = EvtBeingHealedNotify.parseFrom(invocation.getCombatData());
            }
        }
        assertNotNull(event);
        assertEquals(arlecchino.getId(), event.getSourceId());
        assertEquals(arlecchino.getId(), event.getTargetId());
        assertEquals(75f, event.getHealAmount());
        assertEquals(35f, event.getRealHealAmount());
    }

    @Test
    void aFullHealthBurstStillRepaysBond() {
        arlecchino.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        execute(burst, heal, arlecchino);
        assertEquals(100f, hp(arlecchino));
        assertEquals(0f, debt(arlecchino));
    }

    @Test
    void anOrdinaryHealCannotBypassTheCombatRestriction() {
        var action = JsonUtils.decode("{\"$type\":\"HealHP\",\"amount\":100}", AbilityModifierAction.class);
        execute(burst, action, arlecchino);
        assertEquals(10f, hp(arlecchino));
        assertEquals(40f, debt(arlecchino));
    }

    @Test
    void theBurstTagDoesNotHealAnotherAvatarWithThisRestriction() {
        var other = avatar(10000096, 10, 40);
        execute(burst, heal, other);
        assertEquals(10f, hp(other));
        assertEquals(40f, debt(other));
    }

    @Test
    void aDeadAvatarIsNotRevivedOrModified() {
        arlecchino.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        arlecchino.checkIfDead();
        fixture.scene.packets.clear();
        execute(burst, heal, arlecchino);
        assertEquals(0f, hp(arlecchino));
        assertEquals(0f, debt(arlecchino));
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void twoAvatarsKeepSeparateBurstSnapshots() throws Exception {
        var other = avatar(10000096, 10, 10);
        var data = JsonUtils.decode(JsonUtils.encode(burst.getData()), AbilityData.class);
        var second = new Ability(data, other, fixture.player);
        second.getAbilitySpecials().put("HealHp_Debt_Ratio", 1.5f);
        second.getAbilitySpecials().put("HealHp_Atk_Ratio", 1.5f);
        snapshot(second, other);
        execute(second, second.getData().modifiers.get("Avatar_Arlecchino_ElementalBurst_HealDelay").onRemoved[0], other);
        execute(burst, heal, arlecchino);
        assertEquals(30f, hp(other));
        assertEquals(45f, hp(arlecchino));
        assertEquals(0f, debt(other));
        assertEquals(0f, debt(arlecchino));
    }

    @Test
    void skillAndBurstHitPacketsDoNotDeductAnExtraNormalAttackBondPayment() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        for (var name : new String[]{"ElementalArt", "ElementalBurst", "ExtraAttack", "NormalAttack"}) {
            fixture.scene.handleAttack(AttackResult.newBuilder()
                    .setAttackerId(arlecchino.getId()).setDefenseId(fixture.entity.getId())
                    .setAnimEventId(name).setDamage(1).setElementType(1).build());
        }
        assertEquals(40f, debt(arlecchino));
    }

    @Test
    void namedCasterDebtRatioAddsToTheExistingFormula() {
        var action = JsonUtils.decode("""
                {"$type":"HealHP","amount":10,"amountByCasterCurrentHPDebtsRatio":0.5}
                """, AbilityModifierAction.class);
        arlecchino.setConvertToHpDebt(false);
        var recipient = avatar(10000001, 10, 0);
        recipient.setConvertToHpDebt(false);
        execute(burst, action, recipient);
        assertEquals(40f, hp(recipient));
        assertEquals(40f, debt(arlecchino));
    }

    @Test
    void mutedBurstStillRepaysBondAndRestoresHealthWithoutAHealEffect() throws Exception {
        heal.muteHealEffect = true;
        execute(burst, heal, arlecchino);
        assertEquals(45f, hp(arlecchino));
        assertEquals(0f, debt(arlecchino));
        for (var packet : fixture.scene.packets) {
            if (packet.getOpcode() != PacketOpcodes.CombatInvocationsNotify) continue;
            assertTrue(CombatInvocationsNotify.parseFrom(packet.getData()).getInvokeListList().stream()
                    .noneMatch(entry -> entry.getArgumentType()
                            == CombatTypeArgument.CombatTypeArgument_COMBAT_BEING_HEALED_NTF));
        }
    }

    @Test
    void theSameTagOnAnotherAbilityDoesNotBypassCombatHealingRestrictions() {
        var ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_OtherAbility\"}", AbilityData.class),
                arlecchino, fixture.player);
        var action = JsonUtils.decode("""
                {"$type":"HealHP","amount":100,"healTag":"Arlecchino_ElementalBurst_Heal"}
                """, AbilityModifierAction.class);
        execute(ability, action, arlecchino);
        assertEquals(10f, hp(arlecchino));
        assertEquals(40f, debt(arlecchino));
    }

    @Test
    void nonFiniteBurstFormulaDoesNotChangeHealthBondOrGlobals() {
        burst.getAbilitySpecials().put("HealHp_Debt_Ratio", Float.NaN);
        execute(burst, heal, arlecchino);
        assertEquals(10f, hp(arlecchino));
        assertEquals(40f, debt(arlecchino));
        assertTrue(arlecchino.getGlobalAbilityValues().isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void aLegacyRestrictionDoesNotRepayBondOnAForeignClorindeHealTag() {
        var clorinde = avatar(10000098, 10, 40);
        var action = JsonUtils.decode("""
                {"$type":"HealHP","amount":10,"healTag":"Clorinde_ElementalArt_Heal"}
                """, AbilityModifierAction.class);
        execute(burst, action, clorinde);
        assertEquals(10f, hp(clorinde));
        assertEquals(40f, debt(clorinde));
        assertEquals(0f, clorinde.getGlobalAbilityValues().get("_ABILITY_Clorinde_Dodge_HealFlag"));
        assertFalse(arlecchino.getGlobalAbilityValues().containsKey("_ABILITY_Clorinde_Dodge_HealFlag"));
    }

    @Test
    void aBareSwitchMixinDoesNotLeaveArlecchinosRestrictionPermanentlyEnabled() {
        arlecchino.setConvertToHpDebt(false);
        burst.getManager().executeMixin(burst,
                JsonUtils.decode("{\"$type\":\"SwitchHealToHPDebtsMixin\"}", AbilityMixinData.class),
                ByteString.EMPTY);
        assertFalse(arlecchino.isConvertToHpDebt());
    }

    @Test
    void theInstalledResourceRestrictionAllowsBurstAndExpiresOnRemoval() throws Exception {
        var rows = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Arlecchino.json"),
                AbilityConfigData.class);
        var data = rows.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Arlecchino_HealToHpDebts"))
                .findFirst().orElseThrow();
        var restriction = new Ability(data, arlecchino, fixture.player);
        var name = "Avatar_Arlecchino_HealToHpDebts_State";
        arlecchino.setConvertToHpDebt(false);
        restriction.registerModifier(arlecchino, new emu.grasscutter.game.ability.AbilityModifierController(
                restriction, data, data.modifiers.get(name)));
        var ordinary = JsonUtils.decode("{\"$type\":\"HealHP\",\"amount\":50}", AbilityModifierAction.class);
        execute(burst, ordinary, arlecchino);
        assertEquals(10f, hp(arlecchino));
        assertEquals(40f, debt(arlecchino));
        execute(burst, heal, arlecchino);
        assertEquals(45f, hp(arlecchino));
        restriction.removeModifier(arlecchino, name);
        execute(burst, ordinary, arlecchino);
        assertEquals(95f, hp(arlecchino));
    }
}

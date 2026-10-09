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
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.junit.jupiter.api.*;

class HpDebtActionTest {
    private AbilityTestFixture fixture;
    private EntityAvatar target;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public IntSet getTalentIdList() { return IntSets.EMPTY_SET; }
            @Override public void save() {}
        };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        target = new EntityAvatar(fixture.scene, avatar);
        ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_Debt\"}", AbilityData.class),
                target, fixture.player);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 40);
        fixture.scene.packets.clear();
    }

    @AfterEach
    void restore() { fixture.close(); }

    private void action(String type, String fields) {
        var action = JsonUtils.decode("{\"$type\":\"" + type + "\"," + fields + "}", AbilityModifierAction.class);
        ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, target);
    }

    private float debt() { return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS); }

    @Test
    void addRejectsNegativeAmounts() {
        action("AddHPDebts", "\"value\":-10");
        assertEquals(40f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void reduceRejectsNegativeAmounts() {
        action("ReduceHPDebts", "\"value\":-10");
        assertEquals(40f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void bothActionsRespectTheirPredicates() {
        for (String type : new String[]{"AddHPDebts", "ReduceHPDebts"}) {
            action(type, """
                    "value":10,"predicates":[{"$type":"ByTargetHPRatio","HPRatio":0.1,"logic":"Lesser"}]
                    """);
        }
        assertEquals(40f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void nonFiniteAmountsCannotCorruptTheDebt() {
        ability.getAbilitySpecials().put("invalid", Float.NaN);
        for (String type : new String[]{"AddHPDebts", "ReduceHPDebts"}) action(type, "\"value\":\"invalid\"");
        assertEquals(40f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void increaseAndPaymentPublishOnlyActualChanges() {
        action("AddHPDebts", "\"value\":1000");
        assertEquals(200f, debt());
        fixture.scene.packets.clear();
        action("AddHPDebts", "\"value\":10");
        assertTrue(fixture.scene.packets.isEmpty());
        action("ReduceHPDebts", "\"value\":1000");
        assertEquals(0f, debt());
        fixture.scene.packets.clear();
        action("ReduceHPDebts", "\"value\":10");
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void zeroWritesAreNoOps() {
        action("AddHPDebts", "\"value\":0");
        action("ReduceHPDebts", "\"value\":0");
        assertEquals(40f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void bothActionsRejectDeadAvatars() {
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        target.checkIfDead();
        fixture.scene.packets.clear();
        action("AddHPDebts", "\"value\":10");
        action("ReduceHPDebts", "\"value\":10");
        assertEquals(0f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void reduceEvaluatesCurrentFightProperties() {
        action("ReduceHPDebts", "\"value\":[\"FIGHT_PROP_CUR_HP_DEBTS\",0.075,\"MUL\"]");
        assertEquals(37f, debt());
    }

    @Test
    void actualNormalAttackCallbackConsumesItsResourceRatioOnce() throws Exception {
        var rows = JsonUtils.loadToList(Path.of(
                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Arlecchino.json"),
                AbilityConfigData.class);
        var data = rows.stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Avatar_Arlecchino_HealToHpDebts"))
                .findFirst().orElseThrow();
        ability = new Ability(data, target, fixture.player);
        ability.getAbilitySpecials().put("Cur_HPDebts_Reduce_Ratio", 0.075f);
        for (var action : data.modifiers.get("Avatar_Arlecchino_FireAttack_ReduceHPDebts").onAdded) {
            if (action.type == AbilityModifierAction.Type.SetOverrideMapValue
                    || action.type == AbilityModifierAction.Type.ReduceHPDebts)
                ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, target);
        }
        assertEquals(37f, debt());
    }

    @Test
    void missingPlayerDoesNotPreventResourceBasedDebtAddition() {
        ability = new Ability(ability.getData(), fixture.entity, null);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        action("AddHPDebts", "\"value\":[\"FIGHT_PROP_MAX_HP\",0.2,\"MUL\"]");
        assertEquals(60f, debt());
    }

    @Test
    void explicitValueWinsOverASeparateRatio() {
        action("AddHPDebts", "\"value\":10,\"ratio\":0.5");
        assertEquals(50f, debt());
        action("ReduceHPDebts", "\"value\":20,\"ratio\":0.5");
        assertEquals(30f, debt());
    }

    @Test
    void unhandledMixinDoesNotEnableHealConversion() {
        var mixin = JsonUtils.decode("{\"$type\":\"HPDebtsMixin\"}", AbilityMixinData.class);
        // HPDebtsMixin has no registered handler. Comparing two null handlers used to mistake it
        // for SwitchHealToHPDebtsMixin, silently applying a different mechanic.
        var avatar = new Avatar() {
            @Override public int getAvatarId() { return 10000096; }
            @Override public Player getPlayer() { return fixture.player; }
            @Override public IntSet getTalentIdList() { return IntSets.EMPTY_SET; }
            @Override public void save() {}
        };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        var entity = new EntityAvatar(fixture.scene, avatar);
        var ownAbility = new Ability(ability.getData(), entity, fixture.player);
        ownAbility.getManager().executeMixin(ownAbility, mixin, ByteString.EMPTY);
        assertFalse(entity.isConvertToHpDebt());
    }

    @Test
    void concurrentAdditionsAndPaymentsShareTheHealingMonitor() throws Exception {
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10000);
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 500);
        var add = JsonUtils.decode("{\"$type\":\"AddHPDebts\",\"value\":1}", AbilityModifierAction.class);
        var repay = JsonUtils.decode("{\"$type\":\"ReduceHPDebts\",\"value\":1}", AbilityModifierAction.class);
        var workers = Executors.newFixedThreadPool(8);
        var barrier = new CyclicBarrier(8);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                final boolean adding = i < 4;
                futures.add(workers.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    for (int j = 0; j < 100; j++) {
                        if (!adding && j % 2 == 1) target.heal(1);
                        else ability.getManager().executeActionNow(ability, adding ? add : repay, ByteString.EMPTY, target);
                    }
                    return null;
                }));
            }
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertEquals(500f, debt());
        assertEquals(50f, target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void calculationAndMutationHoldTheSameEntityMonitor() {
        var checking = new boolean[]{false};
        var unlocked = new boolean[]{false};
        target = new EntityAvatar(fixture.scene, target.getAvatar()) {
            @Override public float getFightProperty(FightProperty property) {
                if (checking[0] && property == FightProperty.FIGHT_PROP_CUR_HP_DEBTS
                        && !Thread.holdsLock(this)) unlocked[0] = true;
                return super.getFightProperty(property);
            }
            @Override public void setFightProperty(FightProperty property, float value) {
                if (checking[0] && property == FightProperty.FIGHT_PROP_CUR_HP_DEBTS
                        && !Thread.holdsLock(this)) unlocked[0] = true;
                super.setFightProperty(property, value);
            }
        };
        ability = new Ability(ability.getData(), target, fixture.player);
        checking[0] = true;
        action("AddHPDebts", "\"value\":10");
        action("ReduceHPDebts", "\"value\":[\"FIGHT_PROP_CUR_HP_DEBTS\",0.2,\"MUL\"]");
        checking[0] = false;
        assertFalse(unlocked[0], "Debt formula and mutation must share heal()'s monitor");
        assertEquals(40f, debt());
    }

    @Test
    void malformedHealthOrMaximumDoesNotPermitDebtChanges() {
        for (float hp : new float[]{Float.NaN, Float.POSITIVE_INFINITY}) {
            target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
            action("AddHPDebts", "\"value\":10");
            action("ReduceHPDebts", "\"value\":10");
            assertEquals(40f, debt());
        }
        target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        target.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 0);
        action("AddHPDebts", "\"value\":10");
        action("ReduceHPDebts", "\"value\":10");
        assertEquals(40f, debt());
        assertTrue(fixture.scene.packets.isEmpty());
    }
}

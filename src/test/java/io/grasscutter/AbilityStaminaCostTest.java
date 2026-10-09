package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.managers.stamina.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

class AbilityStaminaCostTest {
    private AbilityTestFixture fixture;
    private EntityAvatar entity;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        entity = AbilityPlayModeTest.avatar(fixture, fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Ability ability(String expression, String stacking, GameEntity owner) {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"Cost_"
                                + UUID.randomUUID()
                                + "\",\"modifiers\":{\"Cost\":{\"stacking\":\""
                                + stacking
                                + "\",\"properties\":{\"Actor_CostStaminaRatio\":"
                                + expression
                                + "}}}}",
                        AbilityData.class);
        return new Ability(data, owner, fixture.player);
    }

    private AbilityModifierController add(Ability ability, Integer id) {
        var controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Cost"));
        synchronized (entity.getInstancedModifiers()) {
            if (id != null) entity.getInstancedModifiers().put(id.intValue(), controller);
            ability.registerModifier(entity, controller);
        }
        return controller;
    }

    private int cost() {
        return AbilityStaminaCost.adjust(fixture.player, -100, true);
    }

    @ParameterizedTest
    @CsvSource({"0,-100", "-0.5,-50", "-0.9,-10", "-1,0", "-2,0", "-999,0", "0.5,-150"})
    void resourceRatiosReduceOrIncreaseOnlyTheCurrentCharactersCost(String value, int expected) {
        add(ability(value, "Unique", entity), null);
        assertEquals(expected, cost());
        assertEquals(100, AbilityStaminaCost.adjust(fixture.player, 100, true));
        assertEquals(-100, AbilityStaminaCost.adjust(fixture.player, -100, false));
    }

    @Test
    void uniqueNativeAndNamedRegistryEntriesDoNotApplyTheSameLayerTwice() {
        var a = ability("-0.25", "Unique", entity);
        add(a, 71);
        add(a, 72);
        assertEquals(-75, cost());
        a.removeModifier(entity, "Cost");
        assertEquals(-100, cost());
    }

    @Test
    void multipleInstancesStackAndEachRemovalRestoresOnlyItsOwnContribution() {
        var a = ability("-0.25", "Multiple", entity);
        var first = add(a, 71);
        add(a, 72);
        assertEquals(-50, cost());
        entity.getInstancedModifiers().remove(71);
        a.unregisterModifier(entity, first);
        assertEquals(-75, cost());
        a.removeModifier(entity, "Cost");
        assertEquals(-100, cost());
    }

    @Test
    void attachedEffectsUseTheRecipientsGlobalsAndTheCastersAbilityConstants() {
        var a = ability("\"%RecipientRatio\"", "Unique", fixture.entity);
        fixture.entity.getGlobalAbilityValues().put("RecipientRatio", 0.5f);
        entity.getGlobalAbilityValues().put("RecipientRatio", -0.5f);
        add(a, null);
        assertEquals(-50, cost());
        a.getAbilitySpecials().put("RecipientRatio", -0.25f);
        assertEquals(-75, cost());
        a.removeModifier(entity, "Cost");
        assertEquals(-100, cost());
    }

    @Test
    void anUnresolvedOrNonFiniteLayerCannotGrantAnExemption() {
        var a = ability("\"missing\"", "Unique", entity);
        add(a, null);
        add(ability("-2", "Unique", entity), null);
        assertEquals(-100, cost());
        a.getAbilitySpecials().put("missing", Float.NaN);
        assertEquals(-100, cost());
        a.getAbilitySpecials().put("missing", Float.POSITIVE_INFINITY);
        assertEquals(-100, cost());
    }

    @Test
    void anExpiredCasterCannotRestoreAnOldEffectAfterReAdd() {
        add(ability("-2", "Unique", fixture.entity), null);
        assertEquals(0, cost());
        fixture.entity.invalidateAbilityLifecycle();
        assertEquals(-100, cost());
        fixture.entity.activateAbilityLifecycle();
        assertEquals(-100, cost());
    }

    @Test
    void aRemovedTargetCannotCarryItsOldCostLayerIntoTheNextLifetime() {
        var a = ability("-2", "Unique", entity);
        add(a, null);
        assertEquals(0, cost());
        entity.invalidateAbilityLifecycle();
        assertEquals(-100, cost());
        entity.activateAbilityLifecycle();
        assertEquals(-100, cost());
        a.removeModifier(entity, "Cost");
        add(a, null);
        assertEquals(0, cost());
    }

    @Test
    void anIdReusedByAnotherAvatarDoesNotInheritAnExemption() {
        add(ability("-2", "Unique", entity), null);
        var replacement = AbilityPlayModeTest.avatar(fixture, fixture.player);
        replacement.setId(entity.getId());
        fixture.scene.getEntities().put(replacement.getId(), replacement);
        fixture.player.getTeamManager().getActiveTeam().clear();
        fixture.player.getTeamManager().getActiveTeam().add(replacement);
        assertEquals(-100, cost());
    }

    @Test
    void nyxGaugeAndModeNamesAloneDoNotExemptOrdinaryStamina() {
        entity.getGlobalAbilityValues().put("_ABILITY_NyxInstant_Active", 1f);
        entity.getGlobalAbilityValues().put("_ABILITY_Mualani_IsNyxState", 1f);
        entity.getGlobalAbilityValues().put("NyxValue", 100f);
        entity.getAbilityPlayMode()
                .change(AbilityPlayMode.Type.Normal, AbilityPlayMode.Type.NyxState);
        assertEquals(-100, cost());
    }

    @Test
    void aFirstHashedNativeUpdateResolvesTheCostLayersDynamicGlobal() throws Exception {
        add(ability("\"%New_Cost_Global\"", "Unique", entity), null);
        assertEquals(-100, cost());
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(entity.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                                .setAbilityData(
                                        AbilityScalarValueEntry.newBuilder()
                                                .setKey(
                                                        AbilityString.newBuilder()
                                                                .setHash(
                                                                        Utils.abilityHash(
                                                                                "New_Cost_Global")))
                                                .setFloatValue(-0.5f)
                                                .build()
                                                .toByteString())
                                .build());
        assertEquals(-50, cost());
    }

    @Test
    void theRealRelativeUpdatePathScalesOnceAndLeavesTheOriginalConsumptionReusable() {
        var manager =
                new StaminaManager(fixture.player) {
                    @Override
                    public int getCurrentCharacterStamina() {
                        return 1000;
                    }

                    @Override
                    public int getMaxCharacterStamina() {
                        return 1000;
                    }

                    @Override
                    public int getCurrentVehicleStamina() {
                        return 1000;
                    }

                    @Override
                    public int getMaxVehicleStamina() {
                        return 1000;
                    }

                    @Override
                    public int setStamina(
                            GameSession session, String reason, int value, boolean character) {
                        return value;
                    }
                };
        var consumption = new Consumption(ConsumptionType.FIGHT, -100);
        add(ability("-0.5", "Unique", entity), null);
        assertEquals(950, manager.updateStaminaRelative(null, consumption, true));
        assertEquals(950, manager.updateStaminaRelative(null, consumption, true));
        assertEquals(-100, consumption.amount);
        assertEquals(900, manager.updateStaminaRelative(null, consumption, false));
        assertEquals(700, manager.updateStaminaAbsolute(null, "set", 700, true));
    }

    @Test
    void all176RealPropertiesAreRetainedAndThe48ExplicitExemptionsSuppressCost() throws Exception {
        AbilityModeResourceCorpus.load();
        assertEquals(176, AbilityModeResourceCorpus.costs.size());
        int exemptions = 0;
        for (var row : AbilityModeResourceCorpus.costs) {
            var data = new JsonObject();
            data.addProperty("abilityName", "ResourceCost");
            if (row.ability() != null && row.ability().has("abilitySpecials"))
                data.add("abilitySpecials", row.ability().get("abilitySpecials"));
            var modifiers = new JsonObject();
            modifiers.add("Cost", row.data());
            data.add("modifiers", modifiers);
            var decoded = JsonUtils.decode(data.toString(), AbilityData.class);
            assertNotNull(
                    decoded.modifiers
                            .get("Cost")
                            .properties
                            .getValues()
                            .get("Actor_CostStaminaRatio"),
                    row.path());
            var raw = row.data().getAsJsonObject("properties").get("Actor_CostStaminaRatio");
            if (raw.isJsonPrimitive()
                    && raw.getAsJsonPrimitive().isNumber()
                    && raw.getAsDouble() <= -1) {
                var a = new Ability(decoded, entity, fixture.player);
                add(a, null);
                assertEquals(0, cost(), row.file() + row.path());
                a.removeModifier(entity, "Cost");
                assertEquals(-100, cost());
                exemptions++;
            }
        }
        assertEquals(48, exemptions);
    }

    @Test
    void citlalisActualNyxShieldStateDoesNotRemoveOrdinaryStaminaCost() throws Exception {
        AbilityModeResourceCorpus.load();
        var rows =
                AbilityModeResourceCorpus.nyx.stream()
                        .filter(
                                r ->
                                        r.file()
                                                .getFileName()
                                                .toString()
                                                .equals("ConfigAbility_Avatar_Citlali.json"))
                        .toList();
        assertFalse(rows.isEmpty());
        for (var row : rows) {
            var data = new JsonObject();
            data.addProperty("abilityName", "CitlaliNyx");
            var modifiers = new JsonObject();
            modifiers.add("Cost", row.data());
            data.add("modifiers", modifiers);
            var a =
                    new Ability(
                            JsonUtils.decode(data.toString(), AbilityData.class),
                            entity,
                            fixture.player);
            add(a, null);
            assertEquals(-100, cost());
            a.removeModifier(entity, "Cost");
        }
    }
}

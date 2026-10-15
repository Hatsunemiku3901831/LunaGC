package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionHealHP;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actual 400102 resource graph: the recipient heals, the current avatar owns the five-second visual
 * child.
 */
class NestedFoodBuffTest {
    static final String NAME = "ReHP_SpecialEffects";
    static final String ROOT = "SERVER_ReHP_SpecialEffects_Modifier_lv2_1";
    static final String EFFECT = "UNIQUE_SERVER_ReHP_SpecialEffects_Modifier_lv2_1_EffHandler";
    static final String HEAL = "UNIQUE_SERVER_ReHP_SpecialEffects_Modifier_lv2_1_HealHP";
    static final String LOOP = "UNIQUE_SERVER_ReHP_SpecialEffects_Modifier_lv2_1_LoopEff";
    static JsonObject source;
    static emu.grasscutter.data.excels.ItemData food;
    FoodBuffEffectsTest env;
    AbilityData data;
    final AtomicLong nanos = new AtomicLong();

    @BeforeAll
    static void resources() throws Exception {
        FoodBuffEffectsTest.resources();
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/ItemAbilities.json")))
                        .getAsJsonArray()) {
            var ability = row.getAsJsonObject().getAsJsonObject("Default");
            if (NAME.equals(ability.get("abilityName").getAsString())) source = ability;
        }
        assertNotNull(source);
        food =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                emu.grasscutter.data.excels.ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 108566)
                        .findFirst()
                        .orElseThrow();
        food.onLoad();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new FoodBuffEffectsTest();
        env.setup();
        install(source.deepCopy());
        clock(env.first);
    }

    @AfterEach
    void close() {
        env.close();
    }

    void install(JsonObject graph) {
        data = JsonUtils.decode(graph, AbilityData.class);
        env.env.environment.put(GameData.getAbilityDataMap(), NAME, data);
    }

    JsonObject forced(float chance) {
        var graph = source.deepCopy();
        graph.getAsJsonObject("modifiers")
                .getAsJsonObject(HEAL)
                .getAsJsonArray("onAdded")
                .get(0)
                .getAsJsonObject()
                .addProperty("chance", chance);
        return graph;
    }

    void clock(GameEntity entity) throws Exception {
        var field = GameEntity.class.getDeclaredField("abilityModifierDuration");
        field.setAccessible(true);
        field.set(entity, new AbilityModifierDuration(entity, nanos::get));
        var periodic = GameEntity.class.getDeclaredField("abilityPeriodicHealing");
        periodic.setAccessible(true);
        periodic.set(entity, new AbilityPeriodicHealing(entity, nanos::get));
    }

    List<AbilityModifierController> controllers(EntityAvatar entity) {
        return entity.getAppliedAbilityModifiers().values().stream()
                .flatMap(x -> x.values().stream())
                .toList();
    }

    long active(EntityAvatar entity, String name) {
        return controllers(entity).stream()
                .filter(x -> x.getAbility().getData() == data && x.getName().equals(name))
                .count();
    }

    @Test
    void actualResourceIsAcceptedWithItsTwoPercentOneHpOutcome() {
        assertEquals(0.02, data.modifiers.get(HEAL).onAdded[0].chance.get(), 0.000001);
        assertEquals(1, data.modifiers.get(HEAL).onAdded[0].successActions[0].amount.get());
        assertTrue(env.use(400102, "", env.first));
        assertTrue(env.hp(env.first) == 1000 || env.hp(env.first) == 1001);
        assertTrue(env.manager.hasBuff(10));
    }

    @Test
    void failedRandomRollStillConsumesARealEffectfulRecipeWithoutHealing() {
        install(forced(0));
        assertTrue(env.use(400102, "", env.first));
        assertEquals(1000, env.hp(env.first));
        assertEquals(1, active(env.first, EFFECT));
    }

    @Test
    void offStageRecipientGetsOneHpWhileTheOnFieldAvatarGetsTheVisual() throws Exception {
        install(forced(1));
        var other = env.second();
        clock(other);
        env.env.fixture.scene.getEntities().remove(other.getId());
        assertTrue(env.use(400102, "", other));
        assertEquals(1001, env.hp(other));
        assertEquals(1000, env.hp(env.first));
        assertEquals(1, active(env.first, EFFECT));
        assertEquals(0, active(other, EFFECT));
    }

    @Test
    void visualChildKeepsItsFiveSecondsAfterTheOneSecondRootExpires() {
        install(forced(0));
        assertTrue(env.use(400102, "", env.first));
        env.tick(2);
        nanos.set(2_000_000_000L);
        env.first.getAbilityModifierDuration().tick();
        assertFalse(env.manager.hasBuff(10));
        assertEquals(1, active(env.first, EFFECT));
        nanos.set(4_999_000_000L);
        env.first.getAbilityModifierDuration().tick();
        assertEquals(1, active(env.first, EFFECT));
        nanos.set(5_000_000_000L);
        env.first.getAbilityModifierDuration().tick();
        assertEquals(0, active(env.first, EFFECT));
    }

    @Test
    void anInstantHealingChildLeavesNoPermanentController() {
        install(forced(1));
        assertTrue(env.use(400102, "", env.first));
        assertEquals(1001, env.hp(env.first));
        assertEquals(0, active(env.first, HEAL));
        nanos.set(6_000_000_000L);
        env.first.getAbilityModifierDuration().tick();
        assertEquals(
                0,
                controllers(env.first).stream()
                        .filter(x -> x.getAbility().getData() == data)
                        .count());
    }

    @Test
    void nestedNativeHealingIsClaimedEvenAfterTheRootBuffExpires() {
        install(forced(1));
        assertTrue(env.use(400102, "", env.first));
        var action = data.modifiers.get(HEAL).onAdded[0].successActions[0];
        var nativeAbility = new Ability(data, env.first, env.env.fixture.player);
        assertTrue(env.manager.ownsHealing(nativeAbility, action));
        assertFalse(env.manager.ownsHealing(nativeAbility, data.modifiers.get(ROOT).onAdded[0]));
        assertFalse(env.manager.ownsHealing(nativeAbility, data.modifiers.get(LOOP).onRemoved[0]));
        new ActionHealHP().execute(nativeAbility, action, ByteString.EMPTY, env.first);
        assertEquals(1001, env.hp(env.first));
        env.tick(2);
        new ActionHealHP().execute(nativeAbility, action, ByteString.EMPTY, env.first);
        assertEquals(1001, env.hp(env.first));
    }

    @Test
    void aMissingChildIsRejectedBeforeInstallingAnyVisual() {
        var graph = forced(1);
        graph.getAsJsonObject("modifiers").remove(HEAL);
        install(graph);
        assertFalse(env.use(400102, "", env.first));
        assertEquals(0, active(env.first, EFFECT));
    }

    @Test
    void aHealingBranchCannotBeRedirectedToTheCurrentAvatar() {
        var graph = forced(1);
        graph.getAsJsonObject("modifiers")
                .getAsJsonObject(ROOT)
                .getAsJsonArray("onAdded")
                .get(1)
                .getAsJsonObject()
                .addProperty("target", "CurLocalAvatar");
        install(graph);
        assertFalse(env.use(400102, "", env.first));
        assertEquals(0, active(env.first, EFFECT));
        assertEquals(1000, env.hp(env.first));
    }

    @Test
    void aClientStateMixinCannotHideAnUnimplementedHealingBranch() {
        var graph = forced(1);
        graph.getAsJsonObject("modifiers")
                .getAsJsonObject(LOOP)
                .getAsJsonArray("onAdded")
                .add(
                        JsonParser.parseString(
                                "{\"$type\":\"HealHP\",\"amount\":20,\"target\":\"Self\"}"));
        install(graph);
        assertFalse(env.use(400102, "", env.first));
        assertEquals(1000, env.hp(env.first));
        assertEquals(0, active(env.first, EFFECT));
    }

    @Test
    void aVisualRootCannotBypassValidationOfHealingInsideAClientStateMixin() {
        var graph = forced(1);
        graph.getAsJsonObject("modifiers")
                .getAsJsonObject(ROOT)
                .getAsJsonArray("onAdded")
                .remove(1);
        graph.getAsJsonObject("modifiers")
                .getAsJsonObject(LOOP)
                .getAsJsonArray("onAdded")
                .add(
                        JsonParser.parseString(
                                "{\"$type\":\"HealHP\",\"amount\":20,\"target\":\"Self\"}"));
        install(graph);
        assertFalse(env.use(400102, "", env.first));
        assertFalse(env.manager.hasBuff(10));
        assertEquals(1000, env.hp(env.first));
    }

    @Test
    void cyclicAndUnsupportedGraphsAreRejectedBeforeAnyEffect() {
        var graph = forced(1);
        graph.getAsJsonObject("modifiers")
                .getAsJsonObject(HEAL)
                .getAsJsonArray("onAdded")
                .add(
                        JsonParser.parseString(
                                "{\"$type\":\"ApplyModifier\",\"modifierName\":\""
                                        + ROOT
                                        + "\",\"target\":\"Self\"}"));
        install(graph);
        assertFalse(env.use(400102, "", env.first));
        assertEquals(1000, env.hp(env.first));
        assertEquals(0, active(env.first, EFFECT));
    }

    @Test
    void anIndependentChildRunsItsRemovalHealingOnceAtItsOwnDeadline() {
        var graph = forced(0);
        var child = graph.getAsJsonObject("modifiers").getAsJsonObject(HEAL);
        child.addProperty("duration", 3);
        child.add(
                "onRemoved",
                JsonParser.parseString(
                        "[{\"$type\":\"HealHP\",\"amount\":7,\"target\":\"Self\"}]"));
        install(graph);
        assertTrue(env.use(400102, "", env.first));
        env.tick(2);
        assertFalse(env.manager.hasBuff(10));
        assertEquals(1000, env.hp(env.first));
        nanos.set(3_000_000_000L);
        env.first.getAbilityModifierDuration().tick();
        assertEquals(1007, env.hp(env.first));
        var action = data.modifiers.get(HEAL).onRemoved[0];
        new ActionHealHP()
                .execute(
                        new Ability(data, env.first, env.env.fixture.player),
                        action,
                        ByteString.EMPTY,
                        env.first);
        assertEquals(1007, env.hp(env.first));
        nanos.set(6_000_000_000L);
        env.first.getAbilityModifierDuration().tick();
        assertEquals(1007, env.hp(env.first));
    }

    @Test
    void nestedPeriodicHealingUsesItsChildLifetimeAfterTheRootDisappears() {
        var graph = forced(0);
        var child = graph.getAsJsonObject("modifiers").getAsJsonObject(HEAL);
        child.addProperty("duration", 3);
        child.addProperty("thinkInterval", 1);
        child.add(
                "onThinkInterval",
                JsonParser.parseString(
                        "[{\"$type\":\"HealHP\",\"amount\":2,\"target\":\"Self\"}]"));
        install(graph);
        assertTrue(env.use(400102, "", env.first));
        for (int second = 1; second <= 4; second++) {
            nanos.set(second * 1_000_000_000L);
            env.tick(second);
            env.first.getAbilityPeriodicHealing().tick();
            env.first.getAbilityModifierDuration().tick();
            assertEquals(1000 + 2 * Math.min(second, 3), env.hp(env.first));
        }
    }

    @Test
    void realInventoryRecipeConsumesOneItemEvenWhenTheHealRollFails() throws Exception {
        install(forced(0));
        env.env.environment.put(GameData.getItemDataMap(), food.getId(), food);
        var item = env.stock(food.getId());
        var player = env.env.fixture.player;
        var system =
                new emu.grasscutter.game.systems.InventorySystem(
                        env.env.fixture.scene.getWorld().getServer(), env.now::get, ignored -> {});
        assertSame(item, system.useItem(player, 50001, item.getGuid(), 1, 0, false));
        assertEquals(1, item.getCount());
        assertEquals(1000, env.hp(env.first));
        assertNull(player.getPendingItemUse());
        env.tick(4.99);
        assertTrue(
                env.manager.hasBuff(10),
                "The item overrides the root's one-second default with five seconds");
        env.tick(5.001);
        assertFalse(env.manager.hasBuff(10));
    }

    @Test
    void rootRemovalCallbacksRunOnceWithoutReplayingHealing() {
        var graph = forced(0);
        var root = graph.getAsJsonObject("modifiers").getAsJsonObject(ROOT);
        root.getAsJsonArray("onAdded")
                .add(
                        JsonParser.parseString(
                                "{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"endCount\",\"value\":0,\"target\":\"Self\"}"));
        root.add(
                "onRemoved",
                JsonParser.parseString(
                        "[{\"$type\":\"SetOverrideMapValue\",\"overrideMapKey\":\"endCount\",\"value\":[\"endCount\",1,\"ADD\"],\"target\":\"Self\"}]"));
        install(graph);
        assertTrue(env.use(400102, "", env.first));
        var ability =
                controllers(env.first).stream()
                        .filter(x -> x.getName().equals(EFFECT))
                        .findFirst()
                        .orElseThrow()
                        .getAbility();
        assertTrue(env.manager.removeBuff(10));
        assertEquals(1f, ability.getAbilitySpecials().get("endCount"));
        assertFalse(env.manager.removeBuff(10));
        env.tick(2);
        assertEquals(1f, ability.getAbilitySpecials().get("endCount"));
        assertEquals(1000, env.hp(env.first));
    }

    @Test
    void allExistingResourceHealingRecipesRemainSupportedExceptTheUnpairedCondition()
            throws Exception {
        var recipes =
                JsonUtils.loadToList(
                                Path.of("resources/BinOutput/Ability/Temp/ItemAbilities.json"),
                                emu.grasscutter.data.ResourceLoader.AbilityConfigData.class)
                        .stream()
                        .map(x -> x.Default)
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        x -> x.abilityName, x -> x, (a, b) -> a));
        var buffs =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/BuffExcelConfigData.json"),
                                emu.grasscutter.data.excels.BuffData.class)
                        .stream()
                        .filter(
                                x ->
                                        x.getAbilityName() != null
                                                && x.getAbilityName().startsWith("ReHP"))
                        .toList();
        assertEquals(40, buffs.size());
        for (var buff : buffs) {
            var recipe = recipes.get(buff.getAbilityName());
            assertNotNull(recipe);
            var modifier = recipe.modifiers.get(buff.getModifierName());
            if (buff.getId() == 108101) {
                assertFalse(
                        FoodBuffHealing.hasHealing(recipe, modifier),
                        "This row contains LoseHP, not healing");
                assertFalse(FoodBuffHealing.supported(recipe, modifier));
            } else {
                assertTrue(FoodBuffHealing.hasHealing(recipe, modifier));
                assertTrue(
                        FoodBuffHealing.supported(recipe, modifier),
                        "Unexpected recipe support: " + buff.getId());
            }
        }
    }
}

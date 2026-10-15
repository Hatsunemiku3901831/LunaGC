package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.IntFunction;

class StatueRewardPolicyTest {
    StatueOfferingTest env;

    @BeforeAll
    static void resources() throws Exception {
        StatueOfferingTest.resources();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new StatueOfferingTest();
        env.setup();
        env.statue();
    }

    @AfterEach
    void close() throws Exception {
        env.restoreStatueData();
        env.cleanup();
    }

    void configure(String options) throws Exception {
        env.replaceField(
                ConfigContainer.Game.class,
                Grasscutter.getConfig().server.game,
                "gameOptions",
                JsonUtils.decode(
                        "{statueRewards:" + options + "}", ConfigContainer.GameOptions.class));
    }

    void reference() throws Exception {
        configure("{referenceMultipliers:true}");
    }

    void offer(int area, int item, int count) {
        env.stock(item, count);
        env.manager.levelUpSotS(area, 3, count);
    }

    @SuppressWarnings("unchecked")
    IntFunction<List<ItemParamData>> schedule(int city) throws Exception {
        return (IntFunction<List<ItemParamData>>)
                Class.forName("emu.grasscutter.game.managers.StatueOfferRewardHelper")
                        .getMethod("forCity", int.class)
                        .invoke(null, city);
    }

    int count(List<ItemParamData> items, int id) {
        return items.stream().filter(x -> x.getId() == id).mapToInt(ItemParamData::getCount).sum();
    }

    @Test
    void referenceMondstadtSplitsBoostedTotalsAndKeepsResourceStamina() throws Exception {
        reference();
        offer(1, 107001, 1);
        assertEquals(0, env.response().getRetcode());
        assertEquals(550, env.reward(201));
        assertEquals(720, env.reward(102));
        assertEquals(100, env.reward(305));
        assertEquals(
                10700,
                env.player.getProperty(emu.grasscutter.game.props.PlayerProperty.PROP_MAX_STAMINA));
    }

    @Test
    void defaultConfigurationKeepsOriginalPerLevelRewards() throws Exception {
        configure("{}");
        offer(1, 107001, 1);
        assertEquals(10, env.reward(201));
        assertEquals(80, env.reward(102));
        assertEquals(5, env.reward(305));
    }

    @Test
    void fontaineKeyStaysOnItsOriginalRewardLevel() throws Exception {
        reference();
        offer(33, 107023, 20);
        assertEquals(0, env.response().getRetcode());
        assertEquals(1800, env.reward(201));
        assertEquals(1200, env.reward(102));
        assertEquals(200, env.reward(302));
        assertEquals(1, env.reward(107025));
    }

    @Test
    void cityEightUsesCurrentRealRewardsIncludingUnboostedFate() throws Exception {
        reference();
        offer(55, 107035, 20);
        assertEquals(0, env.response().getRetcode());
        assertEquals(2500, env.reward(201));
        assertEquals(200, env.reward(306));
        assertEquals(1, env.reward(224));
    }

    @Test
    void customMultiplierCanRunWithoutTheReferenceProfile() throws Exception {
        configure("{cityItemMultipliers:{'1':{'102':2}}}");
        offer(1, 107001, 1);
        assertEquals(480, env.reward(102));
        assertEquals(10, env.reward(201));
    }

    @Test
    void disablingRedistributionScalesOnlyTheReachedRewardLevel() throws Exception {
        configure("{referenceMultipliers:true,evenlyDistribute:false}");
        offer(1, 107001, 1);
        assertEquals(240, env.reward(102));
        assertEquals(50, env.reward(305));
        assertEquals(550, env.reward(201));
    }

    @Test
    void explicitOneDisablesOnlyTheSelectedReferenceBoost() throws Exception {
        configure("{referenceMultipliers:true,cityItemMultipliers:{'1':{'201':1}}}");
        offer(1, 107001, 1);
        assertEquals(10, env.reward(201));
        assertEquals(720, env.reward(102));
    }

    @Test
    void keysAndConstellationMemoriesIgnoreCustomMultipliers() throws Exception {
        configure("{referenceMultipliers:true,cityItemMultipliers:{'8':{'916':100,'107037':100}}}");
        var rewards = schedule(8);
        var raw = new HashMap<Integer, List<ItemParamData>>();
        configure("{}");
        var originals = schedule(8);
        for (int level = 2; level <= 10; level++) raw.put(level, originals.apply(level));
        for (int level = 2; level <= 10; level++) {
            assertEquals(count(raw.get(level), 916), count(rewards.apply(level), 916));
            assertEquals(count(raw.get(level), 107037), count(rewards.apply(level), 107037));
        }
    }

    @Test
    void invalidMultiplierRejectsBeforePayment() throws Exception {
        env.stock(107001, 1);
        for (int multiplier : new int[] {0, -1, Integer.MAX_VALUE}) {
            configure("{cityItemMultipliers:{'1':{'201':" + multiplier + "}}}");
            env.manager.levelUpSotS(1, 3, 1);
            assertNotEquals(0, env.response().getRetcode());
            assertEquals(1, env.manager.getCityInfo(1).getLevel());
            assertTrue(env.granted.isEmpty());
            assertEquals(1, env.player.getInventory().getItemCountById(107001));
        }
    }

    @Test
    void missingLaterRewardCannotSilentlyShrinkTheEvenSplit() throws Exception {
        reference();
        env.put(GameData.getRewardDataMap(), 210320, null);
        offer(1, 107001, 1);
        assertNotEquals(0, env.response().getRetcode());
        assertEquals(1, env.player.getInventory().getItemCountById(107001));
        assertTrue(env.granted.isEmpty());
    }

    @Test
    void resourceChangesAreVisibleToNewSchedules() throws Exception {
        reference();
        assertEquals(550, count(schedule(1).apply(2), 201));
        var original = GameData.getRewardDataMap().get(210312);
        var changed = new RewardData();
        changed.rewardId = original.getId();
        changed.rewardItemList =
                original.getRewardItemList().stream()
                        .map(
                                x ->
                                        new ItemParamData(
                                                x.getId(), x.getId() == 201 ? 20 : x.getCount()))
                        .toList();
        env.put(GameData.getRewardDataMap(), changed.getId(), changed);
        assertEquals(612, count(schedule(1).apply(2), 201));
    }

    @Test
    void crossLevelOfferingUsesTheSameDistributionAsIndividualLevels() throws Exception {
        reference();
        offer(1, 107001, 3);
        assertEquals(3, env.manager.getCityInfo(1).getLevel());
        assertEquals(1100, env.reward(201));
        assertEquals(1440, env.reward(102));
        assertEquals(200, env.reward(305));
        assertEquals(0, env.response().getRetcode());
    }

    @Test
    void unresolvedReceiptKeepsTheAwardSnapshotAfterPolicyChange() throws Exception {
        reference();
        env.grantThrows = true;
        offer(1, 107001, 1);
        var receipt = env.manager.getCityInfo(1).getPendingOffer();
        assertNotNull(receipt);
        assertEquals(
                550,
                receipt.getRewards().stream()
                        .filter(x -> x.getId() == 201)
                        .findFirst()
                        .orElseThrow()
                        .getCount());
        configure("{}");
        env.manager.levelUpSotS(1, 3, 1);
        assertSame(receipt, env.manager.getCityInfo(1).getPendingOffer());
        assertEquals(550, env.reward(201));
    }

    @Test
    void allEightCitiesKeepTheAuditedBoostedTotals() throws Exception {
        reference();
        int[] gems = {4950, 4950, 13500, 13500, 16200, 22500, 22500, 22500};
        int[] sigils = {305, 307, 304, 303, 302, 301, 308, 306};
        int[] fates = {0, 0, 0, 0, 0, 15, 30, 1};
        for (int city = 1; city <= 8; city++) {
            var rewards = schedule(city);
            var all = new ArrayList<ItemParamData>();
            for (int level = 2; level <= 10; level++) all.addAll(rewards.apply(level));
            assertEquals(gems[city - 1], count(all, 201), "city " + city);
            assertEquals(city <= 4 ? 6480 : 10800, count(all, 102), "city " + city);
            assertEquals(city <= 4 ? 900 : 1800, count(all, sigils[city - 1]), "city " + city);
            assertEquals(fates[city - 1], count(all, 224), "city " + city);
        }
    }

    @Test
    void indivisibleFateTotalsAreConservedWithRemaindersAtEarlierLevels() throws Exception {
        reference();
        var natlan = schedule(6);
        var nodkrai = schedule(7);
        for (int level = 2; level <= 10; level++) {
            assertEquals(level <= 7 ? 2 : 1, count(natlan.apply(level), 224));
            assertEquals(level <= 4 ? 4 : 3, count(nodkrai.apply(level), 224));
        }
    }

    @Test
    void missingCityEightRewardCannotTriggerTheReferencePlaceholder() throws Exception {
        reference();
        env.put(GameData.getRewardDataMap(), 210382, null);
        offer(55, 107035, 20);
        assertNotEquals(0, env.response().getRetcode());
        assertEquals(20, env.player.getInventory().getItemCountById(107035));
        assertTrue(env.granted.isEmpty());
    }

    @Test
    void boostedStackLimitIsCheckedBeforeTheOculusIsConsumed() throws Exception {
        reference();
        var limited =
                JsonUtils.decode(
                        "{id:305,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_EXCHANGE',stackLimit:50}",
                        emu.grasscutter.data.excels.ItemData.class);
        limited.onLoad();
        env.put(GameData.getItemDataMap(), 305, limited);
        offer(1, 107001, 1);
        assertNotEquals(0, env.response().getRetcode());
        assertEquals(1, env.player.getInventory().getItemCountById(107001));
        assertNull(env.manager.getCityInfo(1).getPendingOffer());
        assertTrue(env.granted.isEmpty());
    }
}

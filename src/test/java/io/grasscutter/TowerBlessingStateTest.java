package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.tower.*;
import emu.grasscutter.utils.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class TowerBlessingStateTest {
    static List<TowerBuffData> cards;
    static List<BuffData> buffs;
    static List<AbilityData> abilities;
    static List<TowerLevelData> levels;
    final Map<Integer, TowerBuffData> oldCards = new HashMap<>();
    final Map<Integer, BuffData> oldBuffs = new HashMap<>();
    final Map<String, AbilityData> oldAbilities = new HashMap<>();
    TowerBlessingState state;

    @BeforeAll
    static void resources() throws Exception {
        cards =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/TowerBuffExcelConfigData.json"), TowerBuffData.class);
        buffs =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/BuffExcelConfigData.json"), BuffData.class)
                        .stream()
                        .filter(buff -> buff.getId() >= 701001 && buff.getId() <= 701036)
                        .toList();
        buffs.forEach(BuffData::onLoad);
        var names = new HashSet<String>();
        buffs.forEach(buff -> names.add(buff.getAbilityName()));
        abilities =
                JsonUtils.loadToList(
                                Path.of("resources/BinOutput/Ability/Temp/ItemAbilities.json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(config -> config.Default)
                        .filter(data -> names.contains(data.abilityName))
                        .toList();
        levels =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/TowerLevelExcelConfigData.json"),
                        TowerLevelData.class);
    }

    @BeforeEach
    void setup() {
        oldCards.putAll(GameData.getTowerBuffDataMap());
        oldBuffs.putAll(GameData.getBuffDataMap());
        oldAbilities.putAll(GameData.getAbilityDataMap());
        cards.forEach(data -> GameData.getTowerBuffDataMap().put(data.getId(), data));
        buffs.forEach(data -> GameData.getBuffDataMap().put(data.getId(), data));
        abilities.forEach(data -> GameData.getAbilityDataMap().put(data.abilityName, data));
        state = new TowerBlessingState();
    }

    @AfterEach
    void restore() {
        GameData.getTowerBuffDataMap().clear();
        GameData.getTowerBuffDataMap().putAll(oldCards);
        GameData.getBuffDataMap().clear();
        GameData.getBuffDataMap().putAll(oldBuffs);
        GameData.getAbilityDataMap().clear();
        GameData.getAbilityDataMap().putAll(oldAbilities);
    }

    TowerLevelData pool(String first, String second, String third) {
        return JsonUtils.decode(
                "{towerBuffConfigStrList:['" + first + "','" + second + "','" + third + "']}",
                TowerLevelData.class);
    }

    void select(int chamber, int card) {
        state.prepare(1053, chamber, 123, 20000, pool(card + ":100", "17:100", "18:100"));
        assertTrue(state.choose(1053, chamber, card));
    }

    @Test
    void everyExportedChamberProducesThreeValidDistinctCards() {
        assertEquals(36, cards.size());
        assertEquals(36, buffs.size());
        for (var level : levels) {
            var offers =
                    new TowerBlessingState()
                            .prepare(1000 + level.getLevelGroupId(), level.getLevelIndex(), 123, 20000, level);
            assertEquals(3, offers.size(), "level " + level.getId());
            assertEquals(3, new HashSet<>(offers).size());
            for (int i = 0; i < 3; i++) {
                int id = offers.get(i);
                assertTrue(TowerBlessingState.available(id));
                assertTrue(
                        Arrays.stream(level.getTowerBuffConfigStrList().get(i).split(","))
                                .anyMatch(entry -> entry.startsWith(id + ":")));
            }
        }
    }

    @Test
    void repeatedEntriesAndNewAttemptsCannotRerollTheSameDay() {
        var level = levels.get(156);
        var first = state.prepare(1053, 1, 123, 20000, level);
        assertEquals(first, state.prepare(1053, 1, 123, 20000, level));
        state.reset(1053);
        assertEquals(first, state.prepare(1053, 1, 123, 20000, level));
    }

    @Test
    void offersChangeWithTheDailyReset() {
        var first = state.prepare(1053, 1, 123, 20000, levels.get(156));
        assertTrue(
                java.util.stream.LongStream.range(20001, 20020)
                        .anyMatch(day -> !first.equals(state.prepare(1053, 1, 123, day, levels.get(156)))));
        assertEquals(
                1,
                GameCalendar.resetDay(Instant.parse("2026-10-22T20:00:00Z")).toEpochDay()
                        - GameCalendar.resetDay(Instant.parse("2026-10-22T19:59:59Z")).toEpochDay());
    }

    @Test
    void onlyOneOfferedCardMayBeChosenPerChamber() {
        var offers = state.prepare(1053, 1, 123, 20000, levels.get(156));
        assertFalse(state.choose(1053, 1, 999));
        assertFalse(state.choose(1052, 1, offers.get(0)));
        assertFalse(state.choose(1053, 2, offers.get(0)));
        assertTrue(state.choose(1053, 1, offers.get(0)));
        assertTrue(state.choose(1053, 1, offers.get(0)));
        assertFalse(state.choose(1053, 1, offers.get(1)));
        assertTrue(state.prepare(1053, 1, 123, 20001, levels.get(156)).isEmpty());
    }

    @Test
    void floorCardsCarryAcrossChambersAndStack() {
        select(1, 2);
        select(2, 2);
        select(3, 1);
        assertEquals(List.of(2, 2, 1), state.active(1053, 3));
        assertTrue(state.active(1052, 3).isEmpty());
        state.reset(1053);
        assertTrue(state.active(1053, 1).isEmpty());
    }

    @Test
    void chamberAndImmediateCardsDoNotLeakIntoTheNextChamber() {
        select(1, 28);
        assertEquals(List.of(28), state.active(1053, 1));
        assertTrue(state.active(1053, 2).isEmpty());
        select(2, 19);
        assertTrue(state.active(1053, 2).isEmpty());
        assertTrue(state.active(1053, 3).isEmpty());
    }

    @Test
    void savingAndLoadingPreserveChoicesAndOffers() {
        select(1, 2);
        var offers = state.prepare(1053, 2, 123, 20000, levels.get(157));
        var restored = JsonUtils.decode(JsonUtils.encode(state), TowerBlessingState.class);
        assertEquals(List.of(2), restored.active(1053, 2));
        assertEquals(offers, restored.prepare(1053, 2, 123, 20000, levels.get(157)));
        assertTrue(
                JsonUtils.decode("{blessings:null}", TowerData.class)
                        .getBlessings()
                        .active(1053, 1)
                        .isEmpty());
    }

    @Test
    void malformedAndUnavailablePoolEntriesAreNeverOffered() {
        assertEquals(
                List.of(2, 18, 19),
                state.prepare(1053, 1, 123, 20000, pool("bad,1:0,999:100,2:100", "18:100", "19:100")));
        state.reset(1053);
        assertTrue(state.prepare(1053, 1, 123, 20000, pool("1:-3", "18:100", "19:100")).isEmpty());
    }
}

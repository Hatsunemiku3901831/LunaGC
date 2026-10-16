package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

class DungeonDropReferenceCatalogTest {
    static final Map<Integer, ItemData> oldItems = new HashMap<>();
    static final Map<Integer, DisplayItemData> oldDisplays = new HashMap<>();
    static final Map<Integer, DungeonData> dungeons = new HashMap<>();
    static List<DungeonDrop> bundled;

    @BeforeAll
    static void resources() throws Exception {
        Grasscutter.getConfig();
        oldItems.putAll(GameData.getItemDataMap());
        oldDisplays.putAll(GameData.getDisplayItemDataMap());
        for (var type : List.of("Material", "Reliquary", "Weapon"))
            for (var item :
                    JsonUtils.loadToList(
                            Path.of("resources/ExcelBinOutput/" + type + "ExcelConfigData.json"),
                            ItemData.class)) GameData.getItemDataMap().put(item.getId(), item);
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/DisplayItemExcelConfigData.json"),
                        DisplayItemData.class))
            GameData.getDisplayItemDataMap().put(row.getId(), row);
        var previews = new HashMap<Integer, RewardPreviewData>();
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/RewardPreviewExcelConfigData.json"),
                        RewardPreviewData.class)) previews.put(row.getId(), row);
        var field = DungeonData.class.getDeclaredField("rewardPreviewData");
        field.setAccessible(true);
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/DungeonExcelConfigData.json"),
                        DungeonData.class)) {
            field.set(row, previews.get(row.getPassRewardPreviewID()));
            dungeons.put(row.getId(), row);
        }
        bundled = DataLoader.loadList("DungeonDropReference.json", DungeonDrop.class);
    }

    @AfterAll
    static void restore() {
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getDisplayItemDataMap().clear();
        GameData.getDisplayItemDataMap().putAll(oldDisplays);
    }

    @Test
    void everyBundledCandidateMatchesTheCurrentResources() {
        assertEquals(244, bundled.size());
        for (var row : bundled)
            assertDoesNotThrow(
                    () ->
                            DungeonDropReferenceCatalog.validate(
                                    dungeons.get(row.getDungeonId()), row.getDrops()),
                    "Dungeon " + row.getDungeonId());
    }

    @Test
    void manifestAccountsForAllPinnedReferenceRows() throws Exception {
        var audit =
                JsonUtils.loadToClass(
                        Path.of("docs/data/dungeon-reference-audit-2026-10-17.json"),
                        com.google.gson.JsonObject.class);
        assertEquals(266, audit.get("sourceCount").getAsInt());
        assertEquals(244, audit.get("acceptedCount").getAsInt());
        var accepted = new HashSet<Integer>();
        audit.getAsJsonArray("acceptedIds").forEach(x -> assertTrue(accepted.add(x.getAsInt())));
        assertEquals(
                accepted,
                bundled.stream()
                        .map(DungeonDrop::getDungeonId)
                        .collect(java.util.stream.Collectors.toSet()));
        var excluded = new HashSet<Integer>();
        audit.getAsJsonArray("excluded")
                .forEach(
                        x -> {
                            assertTrue(
                                    excluded.add(x.getAsJsonObject().get("dungeonId").getAsInt()));
                            assertFalse(x.getAsJsonObject().getAsJsonArray("reasons").isEmpty());
                        });
        assertEquals(
                Set.of(
                        4480, 4484, 4665, 4683, 4684, 4685, 4686, 4687, 4688, 4689, 4690, 5000,
                        5001, 5002, 5008, 5018, 5022, 5050, 5060, 5064, 5100, 5101),
                excluded);
        assertTrue(Collections.disjoint(accepted, excluded));
    }

    List<DungeonDropEntry> groups(String body) throws IOException {
        return JsonUtils.loadToList(
                        new StringReader("[{dungeonId:4654,drops:" + body + "}]"),
                        DungeonDrop.class)
                .get(0)
                .getDrops();
    }

    @ParameterizedTest
    @ValueSource(strings = {"114001", "20002", "999999999", "128"})
    void evenUnselectedInvalidCandidatesAreRejected(String itemId) throws Exception {
        var entries =
                groups("[{counts:[1],items:[104358," + itemId + "],itemProbabilities:[1,0]}]");
        assertThrows(
                IllegalArgumentException.class,
                () -> DungeonDropReferenceCatalog.validate(dungeons.get(4654), entries));
    }

    @Test
    void aRelicFromAnotherSetIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DungeonDropReferenceCatalog.validate(
                                dungeons.get(4668), groups("[{counts:[1],items:[97513]}]")));
    }

    @Test
    void aCurrencyOnlyReferenceCannotReplaceTheProgressionReward() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DungeonDropReferenceCatalog.validate(
                                dungeons.get(4654), groups("[{counts:[1],items:[202]}]")));
    }

    @Test
    void theReferenceCatalogPublishesIndependentlyOfTheOrdinaryCatalog() throws Exception {
        var ordinary = GameData.getDungeonDropDataMap();
        assertEquals(
                244,
                DungeonDropReferenceCatalog.reload(new StringReader(JsonUtils.encode(bundled))));
        assertSame(ordinary, GameData.getDungeonDropDataMap());
        assertNotNull(DungeonDropReferenceCatalog.forDungeon(dungeons.get(4668)));
        assertNull(DungeonDropReferenceCatalog.forDungeon(dungeons.get(5064)));
    }

    @Test
    void aMalformedReloadKeepsThePreviousReferenceSnapshot() throws Exception {
        DungeonDropReferenceCatalog.reload(new StringReader(JsonUtils.encode(bundled)));
        var before = DungeonDropReferenceCatalog.forDungeon(dungeons.get(4668));
        assertThrows(
                IOException.class, () -> DungeonDropReferenceCatalog.reload(new StringReader("[")));
        assertSame(before, DungeonDropReferenceCatalog.forDungeon(dungeons.get(4668)));
    }
}

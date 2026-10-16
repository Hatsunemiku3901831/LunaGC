package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.commands.ReloadCommand;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

class DungeonDropCatalogTest {
    Int2ObjectMap<List<DungeonDropEntry>> before;

    @BeforeAll
    static void configuration() {
        Grasscutter.getConfig();
        DataLoader.checkAllFiles();
    }

    @BeforeEach
    void snapshot() {
        before = new Int2ObjectOpenHashMap<>(GameData.getDungeonDropDataMap());
        GameData.getDungeonDropDataMap().clear();
    }

    @AfterEach
    void restore() {
        GameData.getDungeonDropDataMap().clear();
        GameData.getDungeonDropDataMap().putAll(before);
    }

    @Test
    void dungeonSystemStartupLoadsTheConfiguredRewardTables() throws Exception {
        var expected = DataLoader.loadList("DungeonDrop.json", DungeonDrop.class);
        assertEquals(116, expected.size());
        new DungeonSystem(null);
        assertEquals(expected.size(), GameData.getDungeonDropDataMap().size());
        for (var row : expected) {
            var loaded = GameData.getDungeonDropDataMap().get(row.getDungeonId());
            assertNotNull(loaded);
            assertEquals(row.getDrops().size(), loaded.size());
            for (int i = 0; i < loaded.size(); i++) {
                assertEquals(row.getDrops().get(i).getCounts(), loaded.get(i).getCounts());
                assertEquals(row.getDrops().get(i).getItems(), loaded.get(i).getItems());
                assertEquals(
                        row.getDrops().get(i).getProbabilities(), loaded.get(i).getProbabilities());
                assertEquals(
                        row.getDrops().get(i).getItemProbabilities(),
                        loaded.get(i).getItemProbabilities());
            }
        }
    }

    String table(int id) {
        return "{dungeonId:"
                + id
                + ",drops:[{counts:[0,2],probabilities:[10,20,70],"
                + "items:[202,104003],itemProbabilities:[7,3],mpDouble:true}]}";
    }

    @Test
    void successfulReloadReplacesTheCatalogWhileEarlierReadersKeepTheirSnapshot() throws Exception {
        assertEquals(1, DungeonDropCatalog.reload(new StringReader("[" + table(1) + "]")));
        var old = GameData.getDungeonDropDataMap();
        assertEquals(1, DungeonDropCatalog.reload(new StringReader("[" + table(2) + "]")));
        assertEquals(Set.of(1), old.keySet());
        assertEquals(Set.of(2), GameData.getDungeonDropDataMap().keySet());
        assertNotSame(old, GameData.getDungeonDropDataMap());
        var group = GameData.getDungeonDropDataMap().get(2).get(0);
        assertEquals(List.of(0, 2), group.getCounts());
        assertEquals(List.of(10, 20, 70), group.getProbabilities());
        assertEquals(List.of(7, 3), group.getItemProbabilities());
        assertTrue(group.isMpDouble());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "[]",
                "[null]",
                "[",
                "[{dungeonId:0,drops:[{counts:[1],items:[202]}]}]",
                "[{dungeonId:1,drops:[]}]",
                "[{dungeonId:1,drops:[{counts:[],items:[202]}]}]",
                "[{dungeonId:1,drops:[{counts:[-1],items:[202]}]}]",
                "[{dungeonId:1,drops:[{counts:[2,1],items:[202]}]}]",
                "[{dungeonId:1,drops:[{counts:[2147483647],items:[202]}]}]",
                "[{dungeonId:1,drops:[{counts:[null],items:[202]}]}]",
                "[{dungeonId:1,drops:[{counts:[1],items:[0]}]}]",
                "[{dungeonId:1,drops:[{counts:[1,2],items:[202],probabilities:[1]}]}]",
                "[{dungeonId:1,drops:[{counts:[1],items:[202],probabilities:[0]}]}]",
                "[{dungeonId:1,drops:[{counts:[1,2],items:[202],probabilities:[-1,3]}]}]",
                "[{dungeonId:1,drops:[{counts:[1],items:[202],probabilities:[2147483647]}]}]",
                "[{dungeonId:1,drops:[{counts:[1],items:[202,104003],itemProbabilities:[1]}]}]"
            })
    void invalidStructureKeepsTheWholePreviousCatalog(String input) throws Exception {
        DungeonDropCatalog.reload(new StringReader("[" + table(9) + "]"));
        var old = GameData.getDungeonDropDataMap();
        assertThrows(IOException.class, () -> DungeonDropCatalog.reload(new StringReader(input)));
        assertSame(old, GameData.getDungeonDropDataMap());
        assertEquals(Set.of(9), old.keySet());
        assertEquals(List.of(202, 104003), old.get(9).get(0).getItems());
    }

    @Test
    void aLaterInvalidRowCannotInstallAnEarlierValidRow() throws Exception {
        DungeonDropCatalog.reload(new StringReader("[" + table(9) + "]"));
        var old = GameData.getDungeonDropDataMap();
        assertThrows(
                IOException.class,
                () ->
                        DungeonDropCatalog.reload(
                                new StringReader("[" + table(1) + ",{dungeonId:2,drops:[]}]")));
        assertSame(old, GameData.getDungeonDropDataMap());
        assertEquals(Set.of(9), old.keySet());
    }

    @Test
    void duplicateDungeonIdsDoNotSilentlyOverrideOneAnother() throws Exception {
        DungeonDropCatalog.reload(new StringReader("[" + table(9) + "]"));
        var old = GameData.getDungeonDropDataMap();
        assertThrows(
                IOException.class,
                () ->
                        DungeonDropCatalog.reload(
                                new StringReader("[" + table(1) + "," + table(1) + "]")));
        assertSame(old, GameData.getDungeonDropDataMap());
    }

    @Test
    void aReadFailurePreservesThePreviousCatalog() throws Exception {
        DungeonDropCatalog.reload(new StringReader("[" + table(9) + "]"));
        var old = GameData.getDungeonDropDataMap();
        assertThrows(
                IOException.class,
                () ->
                        DungeonDropCatalog.reload(
                                new Reader() {
                                    @Override
                                    public int read(char[] buffer, int offset, int count)
                                            throws IOException {
                                        throw new IOException("Controlled read failure");
                                    }

                                    @Override
                                    public void close() {}
                                }));
        assertSame(old, GameData.getDungeonDropDataMap());
    }

    @Test
    void theDedicatedReloadCommandOnlyNeedsTheDropConfiguration() {
        assertDoesNotThrow(() -> new ReloadCommand().execute(null, null, List.of("DuNgEon-DrOps")));
        assertEquals(116, GameData.getDungeonDropDataMap().size());
    }

    @Test
    void everyBundledDropItemExistsInTheCurrentResources() throws Exception {
        new DungeonSystem(null);
        var definitions = new HashSet<Integer>();
        for (var name : List.of("Material", "Weapon", "Reliquary"))
            JsonUtils.loadToList(
                            Path.of("resources/ExcelBinOutput/" + name + "ExcelConfigData.json"),
                            ItemData.class)
                    .forEach(row -> definitions.add(row.getId()));
        var missing = new TreeSet<Integer>();
        GameData.getDungeonDropDataMap()
                .values()
                .forEach(
                        groups ->
                                groups.forEach(
                                        group ->
                                                group.getItems().stream()
                                                        .filter(id -> !definitions.contains(id))
                                                        .forEach(missing::add)));
        assertEquals(Set.of(), missing);
    }
}

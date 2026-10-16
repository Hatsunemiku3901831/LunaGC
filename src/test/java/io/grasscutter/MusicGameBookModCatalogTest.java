package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.MusicGameBookModCatalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

class MusicGameBookModCatalogTest {
    @TempDir Path directory;

    MusicGameBookModCatalog load(String json) throws Exception {
        var path = directory.resolve("mods.json");
        Files.writeString(path, json);
        return MusicGameBookModCatalog.load(path);
    }

    @Test
    void currentResourcesAllowBothHardAndMixedCategoriesButRejectTwoRangeMods() {
        var catalog =
                MusicGameBookModCatalog.load(
                        Path.of("resources/ExcelBinOutput/MusicGameBookModExcelConfigData.json"));
        assertEquals(List.of(2, 3, 4), catalog.select(List.of(4, 3, 2)));
        assertEquals(List.of(1, 3, 4), catalog.select(List.of(1, 4, 3)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(5)));
    }

    @Test
    void resourceChangesCanAddModsAndChangeTheActualExclusiveGroups() throws Exception {
        var catalog =
                load(
                        "[{id:11,KKLNILPFAIP:7},{id:12,KKLNILPFAIP:7},"
                                + "{id:23,KKLNILPFAIP:8},{id:24,KKLNILPFAIP:0},{id:25}]");
        assertEquals(List.of(11, 23, 24, 25), catalog.select(List.of(25, 23, 24, 11)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(11, 12)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(1)));
    }

    @Test
    void selectionsAreImmutableAndDoNotRetainTheCallersCollection() throws Exception {
        var catalog = load("[{id:1},{id:2}]");
        var requested = new ArrayList<>(List.of(2, 1));
        var selected = catalog.select(requested);
        requested.clear();
        assertEquals(List.of(1, 2), selected);
        assertThrows(UnsupportedOperationException.class, () -> selected.add(3));
        assertEquals(List.of(), catalog.select(List.of()));
    }

    @Test
    void missingAndDuplicateChoicesAreRejectedBeforeCreatingASelection() throws Exception {
        var catalog = load("[{id:1},{id:2}]");
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(Arrays.asList(1, null)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of(-1)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "{}",
                "[{}]",
                "[{id:0}]",
                "[{id:-1}]",
                "[{id:1.5}]",
                "[{id:2147483648}]",
                "[{id:1},{id:1}]",
                "[{id:1,KKLNILPFAIP:-1}]",
                "[{id:1,KKLNILPFAIP:1.5}]",
                "[{id:1,KKLNILPFAIP:2147483648}]",
                "[{id:1,KKLNILPFAIP:null}]"
            })
    void incompleteOrInvalidResourceDefinitionsDoNotCreateAPartialCatalog(String json) {
        assertThrows(IllegalStateException.class, () -> load(json));
    }

    @Test
    void missingResourceFileIsNotAnImplicitEmptySelectionCatalog() {
        assertThrows(
                IllegalStateException.class,
                () -> MusicGameBookModCatalog.load(directory.resolve("missing.json")));
    }
}

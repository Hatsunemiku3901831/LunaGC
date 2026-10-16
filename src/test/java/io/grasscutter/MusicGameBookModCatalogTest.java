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

    void categories(String json) throws Exception {
        Files.writeString(directory.resolve("MusicGameBookModGroupExcelConfigData.json"), json);
    }

    @Test
    void theCurrentLowerDifficultyCategoryDisablesOnlyItsOfficialScores() {
        var catalog =
                MusicGameBookModCatalog.load(
                        Path.of("resources/ExcelBinOutput/MusicGameBookModExcelConfigData.json"));
        assertTrue(catalog.recordsOfficialScore(List.of()));
        assertFalse(catalog.recordsOfficialScore(List.of(1)));
        assertFalse(catalog.recordsOfficialScore(List.of(1, 3, 4)));
        assertTrue(catalog.recordsOfficialScore(List.of(2, 3, 4)));
    }

    @Test
    void categoryMembershipDeterminesTheRuleWithoutHardcodingAModOrCategoryId() throws Exception {
        var catalog = load("[{id:11},{id:12},{id:13}]");
        categories(
                "[{id:41,nameTextMapHash:1759822631,GHLEGPMMNEG:[11,12]},"
                        + "{id:42,nameTextMapHash:889317207,GHLEGPMMNEG:[13]}]");
        assertFalse(catalog.recordsOfficialScore(List.of(11)));
        assertFalse(catalog.recordsOfficialScore(List.of(12, 13)));
        assertTrue(catalog.recordsOfficialScore(List.of(13)));
    }

    @Test
    void newRunsReadTheCurrentCategoryMembership() throws Exception {
        var catalog = load("[{id:11},{id:12}]");
        categories(
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[11]},"
                        + "{id:2,nameTextMapHash:889317207,GHLEGPMMNEG:[12]}]");
        assertFalse(catalog.recordsOfficialScore(List.of(11)));
        categories(
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[12]},"
                        + "{id:2,nameTextMapHash:889317207,GHLEGPMMNEG:[11]}]");
        assertTrue(catalog.recordsOfficialScore(List.of(11)));
        assertFalse(catalog.recordsOfficialScore(List.of(12)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "{}",
                "[{}]",
                "[{id:1,nameTextMapHash:889317207,GHLEGPMMNEG:[1,2]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[1]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[1,2,99]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[1,1,2]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[]},{id:2,nameTextMapHash:889317207,GHLEGPMMNEG:[1,2]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[1]},{id:1,nameTextMapHash:889317207,GHLEGPMMNEG:[2]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[1]},{id:2,nameTextMapHash:1759822631,GHLEGPMMNEG:[2]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:[1,2]},{id:2,nameTextMapHash:889317207,GHLEGPMMNEG:[2]}]",
                "[{id:0,nameTextMapHash:1759822631,GHLEGPMMNEG:[1,2]}]",
                "[{id:1.5,nameTextMapHash:1759822631,GHLEGPMMNEG:[1,2]}]",
                "[{id:1,nameTextMapHash:4294967296,GHLEGPMMNEG:[1,2]}]",
                "[{id:1,nameTextMapHash:null,GHLEGPMMNEG:[1,2]}]",
                "[{id:1,nameTextMapHash:1759822631,GHLEGPMMNEG:null}]"
            })
    void ambiguousMissingAndInvalidCategoriesCannotAdmitAModdedRecord(String json)
            throws Exception {
        var catalog = load("[{id:1},{id:2}]");
        categories(json);
        assertThrows(IllegalStateException.class, () -> catalog.recordsOfficialScore(List.of(1)));
    }

    @Test
    void selectedModsRequireACategoryExportButEmptyChoicesDoNot() throws Exception {
        var catalog = load("[{id:1}]");
        assertTrue(catalog.recordsOfficialScore(List.of()));
        assertThrows(IllegalStateException.class, () -> catalog.recordsOfficialScore(List.of(1)));
        assertThrows(
                IllegalArgumentException.class, () -> catalog.recordsOfficialScore(List.of(2)));
    }
}

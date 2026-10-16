package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.MusicGameBookThemeCatalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;

class MusicGameBookThemeCatalogTest {
    @TempDir Path directory;

    MusicGameBookThemeCatalog load(String themes, String constants) throws Exception {
        var themePath = directory.resolve("themes.json");
        var constPath = directory.resolve("constants.json");
        Files.writeString(themePath, themes);
        Files.writeString(constPath, constants);
        return MusicGameBookThemeCatalog.load(themePath, constPath);
    }

    @Test
    void activeResourcesContainTheImplicitDefaultAndAllCouponThemes() {
        var catalog =
                MusicGameBookThemeCatalog.load(
                        Path.of("resources/ExcelBinOutput/MusicGameThemeExcelConfigData.json"),
                        Path.of(
                                "resources/ExcelBinOutput/MusicGameBookConstValueExcelConfigData.json"));
        assertEquals(8, catalog.defaultTheme());
        for (int id = 1; id <= 26; id++) assertTrue(catalog.contains(id));
        assertFalse(catalog.contains(0));
        assertFalse(catalog.contains(27));
    }

    @Test
    void changingTheResourceDefaultChangesTheCatalog() throws Exception {
        var catalog =
                load(
                        "[{themeId:7},{themeId:9}]",
                        "[{name:'MGB_CONST_VALUE_DEFAULT_THEME_ID',value:['9']}] ");
        assertEquals(9, catalog.defaultTheme());
        assertTrue(catalog.contains(7));
        assertFalse(catalog.contains(8));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[{themeId:0}]",
                "[{themeId:-1}]",
                "[{themeId:1.5}]",
                "[{themeId:2147483648}]",
                "[{themeId:1},{themeId:1}]",
                "{}"
            })
    void invalidOrDuplicateThemeDefinitionsRejectTheCatalog(String themes) {
        assertThrows(
                IllegalStateException.class,
                () -> load(themes, "[{name:'MGB_CONST_VALUE_DEFAULT_THEME_ID',value:['1']}]"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "[{name:'MGB_CONST_VALUE_DEFAULT_THEME_ID',value:['99']}]",
                "[{name:'MGB_CONST_VALUE_DEFAULT_THEME_ID',value:['1']},{name:'MGB_CONST_VALUE_DEFAULT_THEME_ID',value:['1']}]",
                "[{name:'MGB_CONST_VALUE_DEFAULT_THEME_ID',value:['1.5']}]"
            })
    void missingUnknownOrAmbiguousDefaultsRejectTheCatalog(String constants) {
        assertThrows(IllegalStateException.class, () -> load("[{themeId:1}]", constants));
    }
}

package emu.grasscutter.game.player;

import com.google.gson.*;

import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Theme identity and the implicit default come from the active resource export. */
public final class MusicGameBookThemeCatalog {
    private final Set<Integer> themes;
    private final int defaultTheme;

    private MusicGameBookThemeCatalog(Set<Integer> themes, int defaultTheme) {
        this.themes = Set.copyOf(themes);
        this.defaultTheme = defaultTheme;
    }

    public boolean contains(int id) {
        return themes.contains(id);
    }

    public int defaultTheme() {
        return defaultTheme;
    }

    public static MusicGameBookThemeCatalog current() {
        return load(
                FileUtils.getResourcePath("ExcelBinOutput/MusicGameThemeExcelConfigData.json"),
                FileUtils.getResourcePath(
                        "ExcelBinOutput/MusicGameBookConstValueExcelConfigData.json"));
    }

    public static MusicGameBookThemeCatalog load(Path themesPath, Path constantsPath) {
        try {
            var themes = new HashSet<Integer>();
            for (var value :
                    JsonParser.parseString(Files.readString(themesPath)).getAsJsonArray()) {
                int id = value.getAsJsonObject().get("themeId").getAsBigDecimal().intValueExact();
                if (id <= 0 || !themes.add(id))
                    throw new IllegalArgumentException("Invalid music theme ID");
            }
            Integer defaultId = null;
            for (var value :
                    JsonParser.parseString(Files.readString(constantsPath)).getAsJsonArray()) {
                var row = value.getAsJsonObject();
                if (!row.has("name")
                        || !row.get("name")
                                .getAsString()
                                .equals("MGB_CONST_VALUE_DEFAULT_THEME_ID")) continue;
                if (defaultId != null)
                    throw new IllegalArgumentException("Duplicate default music theme");
                defaultId =
                        Integer.valueOf(row.getAsJsonArray("value").get(0).getAsString().trim());
            }
            if (defaultId == null || !themes.contains(defaultId))
                throw new IllegalArgumentException("Default music theme is missing");
            return new MusicGameBookThemeCatalog(themes, defaultId);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Music game book theme resources are invalid", invalid);
        }
    }
}

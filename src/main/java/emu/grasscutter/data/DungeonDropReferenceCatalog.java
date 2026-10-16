package emu.grasscutter.data;

import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonDropEntry;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.utils.FileUtils;

import it.unimi.dsi.fastutil.ints.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Optional reference distributions; the ordinary catalog remains independent. */
public final class DungeonDropReferenceCatalog {
    private DungeonDropReferenceCatalog() {}

    private static volatile Int2ObjectMap<List<DungeonDropEntry>> snapshot =
            new Int2ObjectOpenHashMap<>();
    private static final DungeonDropCatalog.FileCatalog FILES =
            new DungeonDropCatalog.FileCatalog(
                    () -> FileUtils.getDataPath("DungeonDropReference.json"),
                    path -> Files.newBufferedReader(path, StandardCharsets.UTF_8),
                    System::nanoTime,
                    rows -> snapshot = new Int2ObjectOpenHashMap<>(rows),
                    "DungeonDropReference.json");

    public static int reload() throws IOException {
        return FILES.reload();
    }

    public static int reload(Reader reader) throws IOException {
        return FILES.reload(reader);
    }

    public static List<DungeonDropEntry> forDungeon(DungeonData dungeon) {
        try {
            FILES.ensureLoaded();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot load optional reference dungeon rewards", failure);
        }
        var groups = snapshot.get(dungeon.getId());
        if (groups != null) validate(dungeon, groups);
        return groups;
    }

    /**
     * Validate every candidate against current metadata before rolling, including zero-weight IDs.
     */
    static void validate(DungeonData dungeon, List<DungeonDropEntry> groups) {
        var subtype = dungeon.getSubType();
        boolean relic = subtype == DungeonSubType.DUNGEON_SUB_RELIQUARY;
        require(
                relic
                        || subtype == DungeonSubType.DUNGEON_SUB_TALENT
                        || subtype == DungeonSubType.DUNGEON_SUB_WEAPON,
                dungeon,
                "unsupported subtype");
        var preview = dungeon.getRewardPreviewData();
        require(
                preview != null && preview.getRawPreviewItems() != null,
                dungeon,
                "missing original preview");
        var allowedItems = new HashSet<Integer>();
        var allowedSets = new HashSet<Integer>();
        for (var row : preview.getRawPreviewItems()) {
            allowedItems.add(row.getId());
            var display = GameData.getDisplayItemDataMap().get(row.getId());
            if (display != null && display.getParam() > 0 && display.getRankLevel() > 0)
                allowedSets.add(display.getParam());
        }
        boolean progression = false;
        for (var group : groups) {
            for (int id : group.getItems()) {
                var item = GameData.getItemDataMap().get(id);
                require(item != null && item.getId() == id, dungeon, "missing item " + id);
                var type = item.getItemType();
                if (type == ItemType.ITEM_VIRTUAL) {
                    require(
                            id == 102 || id == 105 || id == 202,
                            dungeon,
                            "unsupported currency " + id);
                } else if (type == ItemType.ITEM_RELIQUARY) {
                    require(
                            relic && allowedSets.contains(item.getSetId()),
                            dungeon,
                            "unexpected relic " + id);
                    progression = true;
                } else if (type == ItemType.ITEM_MATERIAL) {
                    require(
                            !relic && allowedItems.contains(id),
                            dungeon,
                            "unexpected material " + id);
                    progression = true;
                } else {
                    throw new IllegalArgumentException("Unsupported reference item " + id);
                }
            }
        }
        require(progression, dungeon, "missing progression reward");
    }

    private static void require(boolean valid, DungeonData dungeon, String reason) {
        if (!valid)
            throw new IllegalArgumentException(
                    "Reference dungeon " + dungeon.getId() + ": " + reason);
    }
}

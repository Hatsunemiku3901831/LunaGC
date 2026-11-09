package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarCurveData;
import emu.grasscutter.data.excels.avatar.AvatarPromoteData;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Approved birth-scope inputs through the real resource loader, not full resource readiness. */
class D02BirthPromoteCurveResourceLoadTest {
    private static Path input;
    private static JsonObject projection;
    private static final Map<String, String> HASHES = Map.of(
            "AvatarPromoteExcelConfigData.json", "2e6ffe163edc4ed592fcf59f8ae6934eac1249c7f9bb461ecb9dea1d0dfda54a",
            "WeaponPromoteExcelConfigData.json", "b209a9963982aa59e85e35ad6f0af01c7028ba11848f30bf4c8c2869abbfeaad",
            "AvatarCurveExcelConfigData.json", "54b85b5857f6b36c8554b0ef8aefca3d7389bedfd42eaad5924868f8eb0be6c1");
    private static final List<String> TYPES = List.of("GROW_CURVE_HP_S4", "GROW_CURVE_ATTACK_S4", "GROW_CURVE_HP_S5", "GROW_CURVE_ATTACK_S5");
    @TempDir Path temp;

    @BeforeAll
    static void pinInputs() throws Exception {
        Grasscutter.getLogger();
        String reportPath = System.getProperty("d02.promote.report");
        String inputPath = System.getProperty("d02.promote.input");
        assertNotNull(reportPath);
        assertNotNull(inputPath);
        byte[] report = Files.readAllBytes(Path.of(reportPath));
        assertEquals("546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1", sha(report));
        projection = JsonParser.parseString(new String(report, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("javaProjection");
        input = Path.of(inputPath);
        for (var entry : HASHES.entrySet()) assertEquals(entry.getValue(), sha(Files.readAllBytes(input.resolve(entry.getKey()))));
    }

    @Test
    void approvedRowsLoadAndFilterThroughRealLoader() throws Exception {
        boolean loadedAll = ResourceLoader.isLoadedAll();
        var globalCurves = new Int2ObjectOpenHashMap<>(GameData.getAvatarCurveDataMap());
        var globalAvatars = new Int2ObjectOpenHashMap<>(GameData.getMapByResourceDef(AvatarPromoteData.class));
        var globalWeapons = new Int2ObjectOpenHashMap<>(GameData.getWeaponPromoteDataMap());
        var curves = new Int2ObjectOpenHashMap<AvatarCurveData>();
        var avatars = new Int2ObjectOpenHashMap<AvatarPromoteData>();
        var weapons = new Int2ObjectOpenHashMap<WeaponPromoteData>();
        JsonArray sourceCurves = projection.getAsJsonArray("completeAvatarCurveInputRows");
        JsonArray sourcePromotes = projection.getAsJsonArray("completeBirthPromoteInputRows");
        JsonArray curveInput = read("AvatarCurveExcelConfigData.json");
        assertEquals(100, curveInput.size());
        for (int i = 0; i < 100; i++) assertEquals(sourceCurves.get(i).getAsJsonObject().get("javaFields"), curveInput.get(i));
        for (var e : sourcePromotes) {
            var row = e.getAsJsonObject();
            var table = row.get("table").getAsString();
            var data = read(table + "ExcelConfigData.json");
            assertEquals(1, data.size());
            assertEquals(row.get("javaFields"), data.get(0));
            assertEquals(table.equals("AvatarPromote") ? 4 : 3, data.get(0).getAsJsonObject().getAsJsonArray("costItems").size());
            assertEquals(table.equals("AvatarPromote") ? 4 : 5, data.get(0).getAsJsonObject().getAsJsonArray("addProps").size());
        }
        ResourceLoader.loadFromResource(AvatarCurveData.class, input.resolve("AvatarCurveExcelConfigData.json"), curves);
        ResourceLoader.loadFromResource(AvatarPromoteData.class, input.resolve("AvatarPromoteExcelConfigData.json"), avatars);
        ResourceLoader.loadFromResource(WeaponPromoteData.class, input.resolve("WeaponPromoteExcelConfigData.json"), weapons);
        assertEquals(100, curves.size());
        for (int level = 1; level <= 100; level++) {
            var data = curves.get(level);
            assertNotNull(data);
            assertEquals(level, data.getLevel());
            assertEquals(Set.copyOf(TYPES), data.getCurveInfos().keySet());
            var expected = curveInput.get(level - 1).getAsJsonObject().getAsJsonArray("curveInfos");
            for (int i = 0; i < 4; i++) {
                var info = expected.get(i).getAsJsonObject();
                assertEquals(TYPES.get(i), info.get("type").getAsString());
                assertEquals("ARITH_MULTI", info.get("arith").getAsString());
                assertEquals(Float.floatToRawIntBits(info.get("value").getAsFloat()), Float.floatToRawIntBits(data.getCurveInfos().get(TYPES.get(i))));
            }
        }
        assertEquals(Set.of(12 << 8), avatars.keySet());
        assertEquals(Set.of(11101 << 8), weapons.keySet());
        var avatar = avatars.get(12 << 8);
        var weapon = weapons.get(11101 << 8);
        assertEquals(12, avatar.getAvatarPromoteId());
        assertEquals(0, avatar.getPromoteLevel());
        assertEquals(11101, weapon.getWeaponPromoteId());
        assertEquals(0, weapon.getPromoteLevel());
        assertEquals(0, avatar.getAddProps().length);
        assertEquals(0, avatar.getCostItems().length);
        assertEquals(0, weapon.getAddProps().length);
        assertEquals(0, weapon.getCostItems().length);
        assertEquals(globalCurves, GameData.getAvatarCurveDataMap());
        assertEquals(globalAvatars, GameData.getMapByResourceDef(AvatarPromoteData.class));
        assertEquals(globalWeapons, GameData.getWeaponPromoteDataMap());
        assertEquals(loadedAll, ResourceLoader.isLoadedAll());
        for (var entry : HASHES.entrySet()) assertEquals(entry.getValue(), sha(Files.readAllBytes(input.resolve(entry.getKey()))));
    }

    @Test
    void corruptRowsAreRejectedButMissingCurveIsNotLoaderValidated() throws Exception {
        boolean loadedAll = ResourceLoader.isLoadedAll();
        var avatars = new Int2ObjectOpenHashMap<AvatarPromoteData>();
        JsonObject damaged = read("AvatarPromoteExcelConfigData.json").get(0).getAsJsonObject().deepCopy();
        damaged.remove("addProps");
        Path path = Files.writeString(temp.resolve("broken.json"), "[" + damaged + "]");
        ResourceLoader.loadFromResource(AvatarPromoteData.class, path, avatars);
        assertTrue(avatars.isEmpty(), "onLoad must reject missing mandatory array");
        var curves = new Int2ObjectOpenHashMap<AvatarCurveData>();
        JsonObject missing = read("AvatarCurveExcelConfigData.json").get(0).getAsJsonObject().deepCopy();
        missing.getAsJsonArray("curveInfos").remove(0);
        Files.writeString(path, "[" + missing + "]");
        ResourceLoader.loadFromResource(AvatarCurveData.class, path, curves);
        assertEquals(1, curves.size());
        assertNull(curves.get(1).getCurveInfos().get(TYPES.get(0)), "loader does not validate required curve; projection must reject it");
        assertEquals(loadedAll, ResourceLoader.isLoadedAll());
    }

    private static JsonArray read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(input.resolve(name))).getAsJsonArray();
    }

    private static String sha(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }
}

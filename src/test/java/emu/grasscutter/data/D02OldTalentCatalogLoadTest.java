package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The current checkout's complete OpenConfig catalog, limited to the saved Talent keys. */
class D02OldTalentCatalogLoadTest {
    @Test
    void productionCatalogLoaderRetainsAllRequiredTalentDefinitions() throws Exception {
        var manifest = Path.of(System.getProperty("d02.talent.overlay"));
        var raw = Files.readAllBytes(manifest);
        assertEquals("3bbd7497d6b916e41862214cbbe47d3ebb17df1c659e80484a3de64df7897cf7", sha(raw));
        var report = JsonUtils.decode(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
        var checkout = Path.of(System.getProperty("d02.talent.repo"));
        var expectedFiles = new HashSet<String>();
        for (var element : report.getAsJsonArray("files")) {
            var row = element.getAsJsonObject();
            String name = row.get("path").getAsString();
            assertTrue(expectedFiles.add(name));
            var bytes = Files.readAllBytes(checkout.resolve(name));
            assertEquals(row.get("bytes").getAsInt(), bytes.length);
            assertEquals(row.get("sha256").getAsString(), sha(bytes));
        }
        assertEquals(142, expectedFiles.size());
        var actualFiles = new HashSet<String>();
        for (var element : report.getAsJsonArray("directoryOrder")) {
            var directory = checkout.resolve("resources/BinOutput/Talent").resolve(element.getAsString());
            try (var files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> actualFiles.add(checkout.relativize(p).toString().replace('\\', '/')));
            }
        }
        assertEquals(expectedFiles, actualFiles);
        assertEquals(0, report.getAsJsonArray("missing").size());
        assertEquals(0, report.getAsJsonArray("errors").size());
        var keys = new TreeSet<>(report.getAsJsonObject("hits").keySet());
        assertEquals(720, keys.size());
        for (String key : keys) assertEquals(1, report.getAsJsonObject("hits").getAsJsonArray(key).size());

        Grasscutter.getLogger();
        assertEquals(checkout.resolve("resources").normalize(), FileUtils.getResourcePath(""));
        assertFalse(Files.exists(checkout.resolve("local/data/OpenConfig.json")));
        assertFalse(Files.exists(checkout.resolve("src/main/resources/defaults/data/OpenConfig.json")));
        var cache = FileUtils.getDataPath("OpenConfig.json");
        assertFalse(Files.exists(cache));
        var avatarOnly = OpenConfigLoader.load(cache, FileUtils.getResourcePath("BinOutput/Talent/AvatarTalents"));
        var before = new HashMap<>(GameData.getOpenConfigEntries());
        boolean loadedAll = ResourceLoader.isLoadedAll();
        try {
            var loader = ResourceLoader.class.getDeclaredMethod("loadOpenConfig");
            loader.setAccessible(true);
            loader.invoke(null);
            for (String key : keys) {
                var expected = avatarOnly.get(key);
                var actual = GameData.getOpenConfigEntries().get(key);
                assertNotNull(expected, key);
                assertNotNull(actual, key);
                assertEquals(JsonUtils.encode(expected), JsonUtils.encode(actual), key);
            }
            assertFalse(GameData.getOpenConfigEntries().containsKey(""));
            assertFalse(GameData.getOpenConfigEntries().containsKey(null));
            assertEquals(loadedAll, ResourceLoader.isLoadedAll());
            System.out.println("OLD_TALENT_FULL_CATALOG checked=720 unresolved=0 changed=0 cacheAbsent=true");
        } finally {
            GameData.getOpenConfigEntries().clear();
            GameData.getOpenConfigEntries().putAll(before);
        }
    }

    private static String sha(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }
}

package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.avatar.AvatarPromoteData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class D02OldSavedPromoteResourceLoadTest {
    @TempDir Path temp;

    @Test
    void loadsAllSavedPhaseRowsWithExactFieldsAndOrder() throws Exception {
        Path source = Path.of(System.getProperty("d02.oldPromote.source")).resolve(
                "d02-resume-20261111-asc/c-old-promote-fields/first.json");
        byte[] raw = Files.readAllBytes(source);
        assertEquals(
                "38c8d66b08ba8743b49a0bafaab88713494a82a3b84a3de94f3da22f84170a54",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var report = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        var rows = report.getAsJsonArray("rows");
        assertEquals(121, rows.size());
        var input = new JsonArray();
        var keys = new HashSet<Integer>();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            var fields = row.getAsJsonObject("projection").getAsJsonObject("javaFields");
            int group = row.get("group").getAsInt();
            int phase = row.get("phase").getAsInt();
            assertEquals(6, phase);
            assertEquals(group, fields.get("avatarPromoteId").getAsInt());
            assertEquals(phase, fields.get("promoteLevel").getAsInt());
            assertTrue(keys.add((group << 8) + phase));
            input.add(fields);
        }
        Path resource = Files.writeString(temp.resolve("AvatarPromoteExcelConfigData.json"), input.toString());

        Grasscutter.getLogger();
        boolean loadedAll = ResourceLoader.isLoadedAll();
        var loaded = new Int2ObjectOpenHashMap<AvatarPromoteData>();
        ResourceLoader.loadFromResource(AvatarPromoteData.class, resource, loaded);
        assertEquals(keys, loaded.keySet());
        int props = 0;
        int positiveCosts = 0;
        int zeroIds = 0;
        for (var element : input) {
            var row = element.getAsJsonObject();
            int group = row.get("avatarPromoteId").getAsInt();
            int phase = row.get("promoteLevel").getAsInt();
            var actual = loaded.get((group << 8) + phase);
            assertEquals(group, actual.getAvatarPromoteId());
            assertEquals(phase, actual.getPromoteLevel());
            var expectedProps = row.getAsJsonArray("addProps");
            assertEquals(expectedProps.size(), actual.getAddProps().length);
            for (int i = 0; i < expectedProps.size(); i++) {
                var prop = expectedProps.get(i).getAsJsonObject();
                var loadedProp = actual.getAddProps()[i];
                assertEquals(prop.get("propType").getAsString(), loadedProp.getProp().name());
                assertEquals(
                        Float.floatToRawIntBits(prop.get("value").getAsFloat()),
                        Float.floatToRawIntBits(loadedProp.getValue()));
                props++;
            }
            int itemIndex = 0;
            for (var costElement : row.getAsJsonArray("costItems")) {
                var cost = costElement.getAsJsonObject();
                int id = cost.get("id").getAsInt();
                int count = cost.get("count").getAsInt();
                if (id == 0) {
                    assertEquals(20, count);
                    zeroIds++;
                    continue;
                }
                var loadedCost = actual.getCostItems()[itemIndex++];
                assertEquals(id, loadedCost.getId());
                assertEquals(count, loadedCost.getCount());
                positiveCosts++;
            }
            assertEquals(itemIndex, actual.getCostItems().length);
        }
        assertEquals(484, props);
        assertEquals(483, positiveCosts);
        assertEquals(1, zeroIds);
        assertEquals(loadedAll, ResourceLoader.isLoadedAll());
    }
}

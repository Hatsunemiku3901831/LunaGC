package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.common.FightPropData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.avatar.AvatarPromoteData;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loads the old Traveler's actual phase row through the production resource loader. */
class D02OldTravelerPromoteResourceLoadTest {
    @TempDir Path temp;

    @Test
    void loadsSavedPhaseSixWithExactPropertiesAndMaterialFiltering() throws Exception {
        Path sources = Path.of(System.getProperty("d02.oldPromote.source"));
        var save = read(
                sources.resolve("d02-resume-20261110b/old-account-snapshot/run-20261110-2/selected-dependencies.private.json"),
                "3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7");
        Path promoteRoot = sources.resolve("d02-resume-20261108/r3-promote");
        var report = read(
                promoteRoot.resolve("main-third.json"),
                "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        int avatarId = 10000007;
        var saved = only(save.getAsJsonArray("avatars"), "avatarId", avatarId, "fields");
        int phase = saved.get("promoteLevel").getAsInt();
        assertEquals(6, phase);
        assertEquals(100, saved.get("level").getAsInt());

        var groups = report.getAsJsonObject("consumers").getAsJsonArray("avatarPromoteGroups");
        int group = only(groups, "avatarId", avatarId).get("avatarPromoteId").getAsInt();
        assertEquals(12, group);
        var tables = report.getAsJsonArray("tables");
        var rows = only(tables, "name", "AvatarPromote").getAsJsonArray("rows");
        var selected = StreamSupport.stream(rows.spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(value -> value.get("group").getAsInt() == group && value.get("phase").getAsInt() == phase)
                .toList();
        assertEquals(1, selected.size());
        var row = selected.get(0);
        var span = row.getAsJsonObject("source");
        byte[] payload = Files.readAllBytes(promoteRoot.resolve("AvatarPromote.cached-m_bytes.bin"));
        assertEquals("4fadbdbb37babef2ffd8051d78b4cf0fa9476efc6dae8d6ff4d2178e8d832449", sha(payload));
        assertEquals(2670, span.get("begin").getAsInt());
        assertEquals(2776, span.get("end").getAsInt());
        assertEquals(span.get("sha256").getAsString(), sha(Arrays.copyOfRange(payload, 2670, 2776)));

        var fields = row.getAsJsonArray("fields");
        var input = new JsonObject();
        input.add("avatarPromoteId", only(fields, "fieldIndex", 83717).get("value"));
        input.add("promoteLevel", only(fields, "fieldIndex", 83716).get("value"));
        var props = new JsonArray();
        for (var element : only(fields, "fieldIndex", 83712).getAsJsonArray("value")) {
            var pair = element.getAsJsonArray();
            var prop = new JsonObject();
            var type = only(pair, "fieldIndex", 261827).getAsJsonObject("value");
            prop.add("propType", type.getAsJsonArray("symbols").get(0));
            var value = only(pair, "fieldIndex", 261828).getAsJsonObject("value");
            prop.add("value", value.get("float32"));
            props.add(prop);
        }
        input.add("addProps", props);
        var costs = new JsonArray();
        for (var element : only(fields, "fieldIndex", 83713).getAsJsonArray("value")) {
            var pair = element.getAsJsonArray();
            var cost = new JsonObject();
            cost.add("id", only(pair, "fieldIndex", 81537).get("value"));
            cost.add("count", only(pair, "fieldIndex", 81538).get("value"));
            costs.add(cost);
        }
        assertEquals(4, costs.size());
        assertEquals(0, costs.get(1).getAsJsonObject().get("id").getAsInt());
        assertEquals(20, costs.get(1).getAsJsonObject().get("count").getAsInt());
        input.add("costItems", costs);

        Grasscutter.getLogger();
        boolean loadedAll = ResourceLoader.isLoadedAll();
        var loaded = new Int2ObjectOpenHashMap<AvatarPromoteData>();
        Path resource = Files.writeString(temp.resolve("AvatarPromoteExcelConfigData.json"), "[" + input + "]");
        ResourceLoader.loadFromResource(AvatarPromoteData.class, resource, loaded);
        int key = (group << 8) + phase;
        assertEquals(Set.of(key), loaded.keySet());
        var data = loaded.get(key);
        assertEquals(group, data.getAvatarPromoteId());
        assertEquals(phase, data.getPromoteLevel());
        assertArrayEquals(
                new FightProperty[] {
                    FightProperty.FIGHT_PROP_BASE_HP,
                    FightProperty.FIGHT_PROP_BASE_DEFENSE,
                    FightProperty.FIGHT_PROP_BASE_ATTACK,
                    FightProperty.FIGHT_PROP_ATTACK_PERCENT
                },
                Arrays.stream(data.getAddProps()).map(FightPropData::getProp).toArray(FightProperty[]::new));
        assertArrayEquals(
                new int[] {0x454be5f3, 0x434cc000, 0x427edf70, 0x3e75c28f},
                Arrays.stream(data.getAddProps()).mapToInt(prop -> Float.floatToRawIntBits(prop.getValue())).toArray());
        assertArrayEquals(new int[] {104104, 100024, 112007},
                Arrays.stream(data.getCostItems()).mapToInt(ItemParamData::getId).toArray());
        assertArrayEquals(new int[] {6, 60, 24},
                Arrays.stream(data.getCostItems()).mapToInt(ItemParamData::getCount).toArray());
        assertEquals(loadedAll, ResourceLoader.isLoadedAll());
    }

    private static JsonObject only(JsonArray values, String key, Object expected, String... nested) {
        var found = StreamSupport.stream(values.spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .map(value -> nested.length == 0 ? value : value.getAsJsonObject(nested[0]))
                .filter(value -> value.get(key).getAsString().equals(expected.toString()))
                .toList();
        assertEquals(1, found.size(), key + "=" + expected);
        return found.get(0);
    }

    private static JsonObject read(Path path, String hash) throws Exception {
        byte[] data = Files.readAllBytes(path);
        assertEquals(hash, sha(data));
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

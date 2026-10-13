package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.excels.HomeWorldLevelData;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

class FurnitureResourceUpgradeTest {
    private final Gson gson = new Gson();

    JsonArray resources() throws Exception {
        return JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        System.getenv()
                                                .getOrDefault(
                                                        "LUNAGC_TEST_RESOURCES", "/resources"),
                                        "ExcelBinOutput/HomeworldLevelExcelConfigData.json")))
                .getAsJsonArray();
    }

    JsonArray fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/furniture-levels/7.1.json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonArray();
        }
    }

    @Test
    void runtimeRankTenProvidesFifteenSlots() throws Exception {
        assertEquals(
                15,
                gson.fromJson(resources().get(9), HomeWorldLevelData.class)
                        .getFurnitureMakeSlotCount());
    }

    @Test
    void runtimeRankTenKeepsItsExistingFourHourReduction() throws Exception {
        assertEquals(
                14400,
                gson.fromJson(resources().get(9), HomeWorldLevelData.class)
                        .getFurnitureMakeTimeReduction());
    }

    @Test
    void runtimeRankTenAdvertisesTimeReduction() throws Exception {
        var functions = resources().get(9).getAsJsonObject().getAsJsonArray("levelFuncs");
        boolean advertised = false;
        for (var function : functions)
            advertised |= function.getAsString().equals("UI_HOMEWORLD_LEVELFUNCTION_REDUCE_TIME");
        assertTrue(advertised);
    }

    @Test
    void earlierRanksMatchThePinned71Source() throws Exception {
        var actual = resources();
        var expected = fixture();
        assertEquals(10, actual.size());
        for (int i = 0; i < 9; i++)
            assertEquals(normalize(expected.get(i)), normalize(actual.get(i)), "rank " + (i + 1));
    }

    @Test
    void entireRankTenMatchesThePinned71Source() throws Exception {
        assertEquals(normalize(fixture().get(9)), normalize(resources().get(9)));
    }

    JsonObject normalize(JsonElement element) {
        var row = element.getAsJsonObject().deepCopy();
        if (row.has("HMEPEMLDHII")) row.add("HALLBIKHGGJ", row.remove("HMEPEMLDHII"));
        var legacyZero = row.remove("HKPNMEHOIGF");
        if (legacyZero != null) assertEquals(0, legacyZero.getAsInt());
        if (!row.has("exp")) row.addProperty("exp", 0);
        return row;
    }
}

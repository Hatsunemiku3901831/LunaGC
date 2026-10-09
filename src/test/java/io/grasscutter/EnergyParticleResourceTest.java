package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EnergyParticleResourceTest {
    @Test
    void supportedAdsorbatesStillChargeWhenTheOptionalPickupFlagIsOmitted() {
        var particle = JsonUtils.decode("{\"id\":2017,\"materialType\":\"MATERIAL_ADSORBATE\","
                + "\"itemUse\":[{\"useOp\":\"ITEM_USE_ADD_ALL_ENERGY\",\"useParam\":[\"2\"]}]}", ItemData.class);
        particle.onLoad();
        assertTrue(particle.isUseOnGain());
    }
    @Test
    void currentParticlesAreConsumedOnPickup() throws Exception {
        var rows = JsonUtils.loadToList(Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"), ItemData.class);
        for (int id = 2017; id <= 2024; id++) {
            int particleId = id;
            var particle = rows.stream().filter(row -> row.getId() == particleId).findFirst().orElseThrow();
            particle.onLoad();
            assertTrue(particle.isUseOnGain(), "particle " + id + " should charge energy on pickup");
        }
    }

    @Test
    void unimplementedAdsorbatesAndOrdinaryMaterialsAreNotConsumed() {
        var unsupported = JsonUtils.decode("{\"id\":2030,\"materialType\":\"MATERIAL_ADSORBATE\","
                + "\"itemUse\":[{\"useOp\":\"ITEM_USE_NONE\"}]}", ItemData.class);
        unsupported.onLoad();
        assertFalse(unsupported.isUseOnGain());
        var ordinary = JsonUtils.decode("{\"id\":9999,\"materialType\":\"MATERIAL_EXCHANGE\","
                + "\"itemUse\":[{\"useOp\":\"ITEM_USE_ADD_ALL_ENERGY\",\"useParam\":[\"1\"]}]}", ItemData.class);
        ordinary.onLoad();
        assertFalse(ordinary.isUseOnGain());
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.game.tower.TowerData;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class TowerBlessingPersistenceTest extends QuestTestFixture {
    @Entity("tower_blessing_saves")
    static class Save {
        @Id ObjectId id = new ObjectId();
        TowerData towerData = new TowerData();
    }

    @Test
    void actualMorphiaRoundTripPreservesChoicesAndAnUnselectedOffer() throws Exception {
        TowerBlessingStateTest.resources();
        var resources = new TowerBlessingStateTest();
        resources.setup();
        try {
            store.getMapper().map(Save.class);
            var save = new Save();
            var state = save.towerData.getBlessings();
            state.prepare(1053, 1, 123, 20000, resources.pool("2:100", "18:100", "19:100"));
            assertTrue(state.choose(1053, 1, 2));
            var offers = state.prepare(1053, 2, 123, 20000, resources.pool("1:100", "18:100", "19:100"));
            store.save(save);
            var loaded = store.find(Save.class).filter(Filters.eq("_id", save.id)).first();
            assertNotNull(loaded);
            assertEquals(List.of(2), loaded.towerData.getBlessings().active(1053, 2));
            assertEquals(offers, loaded.towerData.getBlessings().prepare(1053, 2, 123, 20000, null));
            assertTrue(loaded.towerData.getBlessings().choose(1053, 2, 1));
        } finally {
            resources.restore();
        }
    }
}

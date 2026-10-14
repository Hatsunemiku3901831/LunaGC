package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;

import emu.grasscutter.game.world.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class SceneBuildingMongoTest {
    @Test
    void acknowledgedPlacementsSurviveReloadAndOwnerScopedDeletion() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri, "The isolated Mongo runtime is required");
        String name = "lunagc_building_" + UUID.randomUUID().toString().replace("-", "");
        try (var fixture = new AbilityTestFixture();
                var client = MongoClients.create(uri)) {
            var datastore =
                    Morphia.createDatastore(
                            client,
                            name,
                            MapperOptions.builder().storeEmpties(true).ignoreFinals(true).build());
            try {
                datastore.getMapper().map(SceneBuilding.class, Position.class);
                var store = new SceneBuildingManager.MongoStore(datastore);
                var a =
                        new SceneBuilding(
                                10001,
                                3,
                                70300001,
                                new Position(1, 2, 3),
                                new Position(0, 45, 0),
                                true);
                var b =
                        new SceneBuilding(
                                10002, 3, 70300001, new Position(4, 5, 6), new Position(), true);
                var otherScene =
                        new SceneBuilding(10001, 4, 70300001, new Position(), new Position(), true);
                store.save(a);
                store.save(b);
                store.save(otherScene);
                var restored = store.load(10001, 3);
                assertEquals(1, restored.size());
                assertEquals(a.getId(), restored.get(0).getId());
                assertEquals(1, restored.get(0).getPosition().getX());
                assertEquals(45, restored.get(0).getRotation().getY());
                assertTrue(restored.get(0).isPersistent());
                store.delete(10002, a.getId());
                assertEquals(1, store.load(10001, 3).size());
                store.delete(10001, a.getId());
                assertTrue(store.load(10001, 3).isEmpty());
                assertEquals(1, store.load(10002, 3).size());
                assertEquals(1, store.load(10001, 4).size());
            } finally {
                datastore.getDatabase().drop();
            }
        }
    }
}

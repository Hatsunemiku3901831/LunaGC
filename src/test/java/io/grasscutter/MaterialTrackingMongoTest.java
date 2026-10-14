package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;

import emu.grasscutter.game.managers.mapmark.MapMark;
import emu.grasscutter.game.world.Position;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class MaterialTrackingMongoTest {
    @Entity(value = "marker_probe", useDiscriminator = false)
    public static class SavedMarker {
        @Id String id;
        MapMark mark;

        public SavedMarker() {}

        SavedMarker(MapMark mark) {
            this.id = "probe";
            this.mark = mark;
        }
    }

    @Test
    void durableEmbeddedMarkerRetainsItsOwnershipMetadataAfterColdDecode() throws Exception {
        var uri = System.getenv("LUNAGC_MONGO_URI");
        assertNotNull(uri, "Run with the isolated test MongoDB URI");
        try (var fixture = new AbilityTestFixture();
                var client = MongoClients.create(uri)) {
            var database = "lunagc_material_marks_" + UUID.randomUUID().toString().replace("-", "");
            try {
                var ds = Morphia.createDatastore(client, database);
                ds.getMapper().map(SavedMarker.class, MapMark.class, Position.class);
                var marker =
                        (MapMark)
                                MapMark.class
                                        .getMethod(
                                                "materialPoint",
                                                int.class,
                                                Position.class,
                                                int.class,
                                                String.class)
                                        .invoke(
                                                null,
                                                3,
                                                new Position(1, 2, 3),
                                                101232,
                                                "材料 101232");
                ds.save(new SavedMarker(marker));
                var loaded = ds.find(SavedMarker.class).first();
                assertNotNull(loaded);
                var decoded = loaded.mark;
                assertEquals(101232, MapMark.class.getMethod("getTrackingItemId").invoke(decoded));
                assertEquals(3, decoded.getSceneId());
                assertEquals(2, decoded.getPosition().getY());
                var personal =
                        new MapMark(
                                emu.grasscutter.net.proto.MapMarkPointOuterClass.MapMarkPoint
                                        .newBuilder()
                                        .setSceneId(3)
                                        .setName("Material personal")
                                        .build());
                assertEquals(0, MapMark.class.getMethod("getTrackingItemId").invoke(personal));
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }
}

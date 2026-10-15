package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Current and legacy Player documents must retain cooldowns across reconnects. */
class ItemUseCooldownPersistenceTest extends WidgetPersistenceTest {
    @Test
    void expiryGroupsSurviveRealPlayerSaveReadAndUpdates() throws Exception {
        var actual = actual();
        actual.rememberItemUseCooldown(99, 1_800_000_120_000L);
        actual.rememberItemUseCooldown(98, 1_800_000_300_000L);
        var restored = read();
        assertEquals(1_800_000_120_000L, restored.getItemUseCooldownEnd(99));
        assertEquals(1_800_000_300_000L, restored.getItemUseCooldownEnd(98));
        restored.rememberItemUseCooldown(99, 1_800_000_100_000L);
        assertEquals(1_800_000_120_000L, read().getItemUseCooldownEnd(99));
    }

    @Test
    void legacyPlayerWithoutCooldownFieldStartsReady() throws Exception {
        var actual = actual();
        actual.save();
        read();
        var collection = store.getDatabase().getCollection("players");
        collection.updateOne(
                new org.bson.Document("_id", 10002),
                new org.bson.Document("$unset", new org.bson.Document("itemUseCooldowns", "")));
        var restored = read();
        assertEquals(0, restored.getItemUseCooldownEnd(99));
        restored.rememberItemUseCooldown(99, 1_800_000_120_000L);
        assertEquals(1_800_000_120_000L, read().getItemUseCooldownEnd(99));
    }

    @Test
    void coldLoadedPlayerPublishesTheSavedExpiryWithoutWritingBack() throws Exception {
        var actual = actual();
        actual.rememberItemUseCooldown(99, 1_800_000_120_000L);
        actual.rememberItemUseCooldown(98, 1_800_000_300_000L);
        var restored = read();
        var collection = store.getDatabase().getCollection("players");
        var before = collection.find(new org.bson.Document("_id", 10002)).first();
        assertNotNull(before);
        var packet =
                new emu.grasscutter.server.packet.send.PacketItemCdGroupTimeNotify(
                        restored, 1_800_000_119_999L);
        assertEquals(
                java.util.Map.of(99, 1_800_000_120_000L, 98, 1_800_000_300_000L),
                ItemCooldownNotifyTest.nativeMap(packet));
        assertTrue(
                emu.grasscutter.database.DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        assertEquals(before, collection.find(new org.bson.Document("_id", 10002)).first());
        assertEquals(
                java.util.Map.of(98, 1_800_000_300_000L),
                ItemCooldownNotifyTest.nativeMap(
                        new emu.grasscutter.server.packet.send.PacketItemCdGroupTimeNotify(
                                restored, 1_800_000_120_000L)));
    }
}

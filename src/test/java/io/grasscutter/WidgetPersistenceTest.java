package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.net.proto.GetWidgetSlotRspOuterClass.GetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketGetWidgetSlotRsp;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Actual Player codecs and isolated Mongo, including legacy records with only widgetId. */
class WidgetPersistenceTest extends QuestTestFixture {
    private final List<Runnable> restore = new ArrayList<>();

    void session(Player actual) {
        var session =
                new emu.grasscutter.server.game.GameSession(player.server) {
                    @Override
                    public void send(emu.grasscutter.net.packet.BasePacket packet) {}
                };
        session.setPlayer(actual);
        actual.setSession(session);
    }

    @AfterEach
    void restoreWidgets() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
    }

    Player actual() throws Exception {
        for (int id : new int[] {220003, 220023}) {
            var data =
                    JsonUtils.decode(
                            "{id:"
                                    + id
                                    + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_WIDGET',stackLimit:1}",
                            ItemData.class);
            data.onLoad();
            var old = GameData.getItemDataMap().put(id, data);
            restore.add(
                    () -> {
                        if (old == null) GameData.getItemDataMap().remove(id);
                        else GameData.getItemDataMap().put(id, old);
                    });
        }
        store.getMapper().map(new Class<?>[] {Player.class, GameItem.class});
        var actual = new Player();
        actual.setUid(10002);
        session(actual);
        actual.setWorld(player.getWorld());
        actual.setScene(player.getScene());
        replaceField(Player.class, actual, "teamManager", new TeamManager(actual));
        actual.getTeamManager().setEntity(new EntityTeam(actual));
        for (int id : new int[] {220003, 220023}) {
            var item = new GameItem(id, 1);
            item.setOwner(actual);
            actual.getInventory().getItems().put(item.getGuid(), item);
            actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
            DatabaseHelper.saveGameSync(item);
        }
        return actual;
    }

    Player read() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var restored = store.find(Player.class).field("id").equal(10002).first();
        assertNotNull(restored);
        session(restored);
        restored.setWorld(player.getWorld());
        restored.setScene(player.getScene());
        replaceField(Player.class, restored, "teamManager", new TeamManager(restored));
        restored.getTeamManager().setEntity(new EntityTeam(restored));
        for (var item : DatabaseHelper.getInventoryItems(restored)) {
            item.setItemData(GameData.getItemDataMap().get(item.getItemId()));
            // Inventory.putItem assigns a fresh runtime GUID on real database restoration.
            item.setOwner(restored);
            restored.getInventory().getItems().put(item.getGuid(), item);
            restored.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        }
        return restored;
    }

    @Test
    void independentSlotsSurviveSaveReadAndDetach() throws Exception {
        var actual = actual();
        assertEquals(0, actual.getWidgets().setSlot(220003, 0, List.of(0)).retcode());
        assertEquals(0, actual.getWidgets().setSlot(220023, 0, List.of(1)).retcode());
        var restored = read();
        assertEquals(220003, restored.getWidgetId());
        assertEquals(220023, restored.getWidgetPetId());
        var slots =
                GetWidgetSlotRsp.parseFrom(new PacketGetWidgetSlotRsp(restored).getData())
                        .getSlotListList();
        assertEquals(2, slots.size());
        assertEquals(0, restored.getWidgets().setSlot(220023, 1, List.of(1)).retcode());
        var detached = read();
        assertEquals(220003, detached.getWidgetId());
        assertEquals(0, detached.getWidgetPetId());
    }

    @Test
    void legacyPetMovesToFollowerSlotAndPersistsWithoutSerializingManager() throws Exception {
        var actual = actual();
        actual.setWidgetId(220023);
        actual.save();
        var legacy = read();
        assertEquals(220023, legacy.getWidgetId());
        assertEquals(0, legacy.getWidgetPetId());
        legacy.getWidgets().prepareForScene();
        var migrated = read();
        assertEquals(0, migrated.getWidgetId());
        assertEquals(220023, migrated.getWidgetPetId());
        var saved =
                store.getDatabase()
                        .getCollection("players")
                        .find(new org.bson.Document("_id", 10002))
                        .first();
        assertNotNull(saved);
        assertFalse(saved.containsKey("widgets"));
    }
}

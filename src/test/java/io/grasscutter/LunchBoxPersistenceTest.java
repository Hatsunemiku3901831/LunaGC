package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

/** Player and item codecs must persist food choices, independently of runtime widget state. */
class LunchBoxPersistenceTest extends WidgetPersistenceTest {
    final List<Runnable> foodRestores = new ArrayList<>();

    @BeforeEach
    void foodData() throws Exception {
        LunchBoxConfigurationTest.resources();
        LunchBoxConfigurationTest.resources.forEach(
                (id, data) -> {
                    var old = GameData.getItemDataMap().put(id, data);
                    foodRestores.add(
                            () -> {
                                if (old == null) GameData.getItemDataMap().remove(id);
                                else GameData.getItemDataMap().put(id, old);
                            });
                });
    }

    @AfterEach
    void restoreFoodData() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(foodRestores);
        foodRestores.forEach(Runnable::run);
    }

    void food(Player actual) {
        for (var id : List.of(220008, 108033, 108005)) {
            var item = new GameItem(id, id == 220008 ? 1 : 3);
            item.setOwner(actual);
            actual.getInventory().getItems().put(item.getGuid(), item);
            actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
            store.save(item);
        }
    }

    @Test
    void choicesSurviveRealPlayerSaveAndReadAndPartialClear() throws Exception {
        var actual = actual();
        food(actual);
        assertEquals(0, actual.getWidgets().setLunchBox(Map.of(1, 108033, 2, 108005)));
        var restored = read();
        assertEquals(
                Map.of(1, 108033, 2, 108005),
                restored.getWidgets().lunchBox().getSlotMaterialMapMap());
        assertEquals(0, restored.getWidgets().setLunchBox(Map.of(1, 0)));
        assertEquals(Map.of(2, 108005), read().getWidgets().lunchBox().getSlotMaterialMapMap());
        var document =
                store.getDatabase()
                        .getCollection("players")
                        .find(new org.bson.Document("_id", 10002))
                        .first();
        assertNotNull(document);
        assertTrue(document.containsKey("lunchBoxFood"));
        assertFalse(document.containsKey("widgets"));
    }

    @Test
    void exhaustedFoodKeepsItsSavedSelectionForRestocking() throws Exception {
        var actual = actual();
        food(actual);
        assertEquals(0, actual.getWidgets().setLunchBox(Map.of(1, 108033)));
        var restored = read();
        var food = restored.getInventory().getItemById(108033);
        assertTrue(restored.getInventory().removeItem(food, food.getCount()));
        assertEquals(Map.of(1, 108033), read().getWidgets().lunchBox().getSlotMaterialMapMap());
    }
}

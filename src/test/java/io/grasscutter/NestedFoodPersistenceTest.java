package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.systems.InventorySystem;

import org.junit.jupiter.api.Test;

/**
 * The actual special recipe must finish its paid consumption and persist the real random outcome.
 */
class NestedFoodPersistenceTest extends FoodUsePaymentPersistenceTest {
    @Test
    void specialFoodConsumesItsRealItemAndPersistsAtMostOnePointOfHealing() throws Exception {
        var definition =
                emu.grasscutter.utils.JsonUtils.loadToList(
                                java.nio.file.Path.of(
                                        "resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                emu.grasscutter.data.excels.ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 108566)
                        .findFirst()
                        .orElseThrow();
        definition.onLoad();
        resource(GameData.getItemDataMap(), definition.getId(), definition);
        var item = new GameItem(definition, 1);
        item.setOwner(target);
        target.getInventory().getItems().put(item.getGuid(), item);
        target.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        store.save(item);
        assertSame(
                item,
                new InventorySystem(player.server)
                        .useItem(target, avatar.getGuid(), item.getGuid(), 1, 0, false));
        var restored = read();
        assertNull(restored.getPendingItemUse());
        assertEquals(0, restored.getInventory().getItemCountById(definition.getId()));
        assertEquals(1, restored.getInventory().getItemCountById(108033));
        double hp = savedAvatar().get("currentHp", Number.class).doubleValue();
        assertTrue(hp == 1000 || hp == 1001);
    }
}

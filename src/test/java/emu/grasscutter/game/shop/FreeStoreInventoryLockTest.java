package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FreeStoreInventoryLockTest {
    @TempDir Path directory;

    @Test
    void cosmeticUnlockDoesNotInvertTheInventoryAndPlayerOrder() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        int id = 990073;
        var item = new Gson().fromJson("{\"id\":" + id
                + ",\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_NAMECARD\","
                + "\"useOnGain\":true,\"itemUse\":[{\"useOp\":\"ITEM_USE_GAIN_NAME_CARD\",\"useParam\":[]}]}", ItemData.class);
        item.onLoad();
        var previous = GameData.getItemDataMap().put(id, item);
        try {
            var player = new FreeStoreTest.TestPlayer() {
                @Override public void addNameCard(int nameCardId) {
                    assertFalse(Thread.holdsLock(this), "Cosmetic delivery must not hold Player before Inventory");
                    super.addNameCard(nameCardId);
                }
            };
            var good = new ShopInfo();
            good.setGoodsId(990074);
            good.setGoodsItem(new ItemParamData(id, 1));
            good.setCostItemList(List.of());
            SpecialCosmeticShop.purchaseFree(player, good);
            SpecialCosmeticShop.purchaseFree(player, good);
            assertTrue(player.getNameCardList().contains(id));
            assertEquals(1, player.getGoodsLimit(good.getGoodsId()).getHasBought());
        } finally {
            if (previous == null) GameData.getItemDataMap().remove(id);
            else GameData.getItemDataMap().put(id, previous);
        }
    }

    @Test
    void cashProductDoesNotWaitForInventoryWhileHoldingThePlayerMonitor() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var previous = GameData.getItemDataMap().put(201, new Gson().fromJson(
                "{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        try {
            var player = new FreeStoreTest.TestPlayer() {
                @Override public Inventory getInventory() {
                    assertFalse(Thread.holdsLock(this),
                            "Food use and use-on-gain effects already acquire Inventory before Player");
                    return super.getInventory();
                }
            };
            var store = new FreeStore(new ShopCatalog(directory.resolve("shop.json"))) {
                @Override protected void persist(Player owner) {}
            };
            var field = FreeStore.class.getDeclaredField("products");
            field.setAccessible(true);
            field.set(store, List.of(new FreeStore.Product("primogems:1", "", "原石", "primogems",
                    60, 0, 0, 0, 0, 0, 0, "")));
            var receipt = "concurrent-cash-purchase-0001";
            store.purchase(player, "primogems:1", receipt);
            store.purchase(player, "primogems:1", receipt);
            assertEquals(60, player.granted.get(201));
            assertEquals(1, player.getFreeProductPurchases().get("primogems:1"));
        } finally {
            if (previous == null) GameData.getItemDataMap().remove(201);
            else GameData.getItemDataMap().put(201, previous);
        }
    }
}

package emu.grasscutter.game.combine;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;

import java.util.List;

final class StrongboxDelivery {
    private StrongboxDelivery() {}

    static void consume(Player player, StrongboxReceipt receipt, List<GameItem> ingredients) {
        var inventory = player.getInventory();
        for (int index = 0; index < ingredients.size(); index++) {
            var item = ingredients.get(index);
            var expected = receipt.getIngredients().get(index);
            int before = inventory.getItemCountById(item.getItemId());
            if (inventory.getItemByGuid(item.getGuid()) != item
                    || item.getOwnerId() != player.getUid()
                    || !item.isDestroyable()
                    || !expected.matches(item)
                    || !inventory.removeItem(item.getGuid())
                    || inventory.getItemByGuid(item.getGuid()) != null
                    || item.getCount() != 0
                    || before - inventory.getItemCountById(item.getItemId()) != 1)
                throw new IllegalStateException("Strongbox ingredient was not removed exactly");
            DatabaseHelper.saveGameSync(item);
            receipt.removed(index);
        }
        receipt.paid();
        DatabaseHelper.saveGameSync(player);
    }

    static void grant(Player player, StrongboxReceipt receipt, List<GameItem> outputs) {
        var inventory = player.getInventory();
        for (int index = 0; index < outputs.size(); index++) {
            var item = outputs.get(index);
            int before = inventory.getItemCountById(item.getItemId());
            if (!inventory.addItem(item)
                    || inventory.getItemByGuid(item.getGuid()) != item
                    || item.getOwnerId() != player.getUid()
                    || !receipt.getOutputs().get(index).matches(item)
                    || (long) inventory.getItemCountById(item.getItemId()) - before != 1)
                throw new IllegalStateException("Strongbox output was not fully stored");
            DatabaseHelper.saveGameSync(item);
            receipt.delivered(index, item);
        }
    }
}

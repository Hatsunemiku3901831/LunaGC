package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;

import java.util.List;

/** Acknowledged resin and EXP writes shared by the two original Aster recovery modes. */
public final class AsterRewardDelivery {
    private AsterRewardDelivery() {}

    public static void grant(Player player, int exp, int friendship, int currencyId) {
        for (int id : List.of(102, 105, currencyId)) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null
                    || item.getItemType() != emu.grasscutter.game.inventory.ItemType.ITEM_VIRTUAL)
                throw new IllegalStateException("Aster virtual reward resource missing: " + id);
        }
        DatabaseHelper.saveGameSync(player);
        var inventory = player.getInventory();
        synchronized (inventory) {
            if (!inventory.addItem(102, exp, ActionReason.TakeGeneralReward)
                    || !inventory.addItem(105, friendship, ActionReason.TakeGeneralReward))
                throw new IllegalStateException("Aster EXP reward was not granted");
            for (var avatar : player.getTeamManager().getActiveTeam())
                DatabaseHelper.saveGameSync(avatar.getAvatar());
            DatabaseHelper.saveGameSync(player);
        }
        // The activity receipt records 109/110; these virtual currencies are not bag stacks.
    }
}

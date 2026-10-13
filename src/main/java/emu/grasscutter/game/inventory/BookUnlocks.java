package emu.grasscutter.game.inventory;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.codex.CodexBookData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.ItemUseUnlockCodex;
import emu.grasscutter.game.props.ItemUseTarget;

/** Only a canonical automatic book grant may replace physical inventory delivery. */
public final class BookUnlocks {
    private BookUnlocks() {}

    public static CodexBookData definition(ItemData data) {
        if (data == null
                || !data.isUseOnGain()
                || data.getItemType() != ItemType.ITEM_MATERIAL
                || data.getUseTarget() != ItemUseTarget.ITEM_USE_TARGET_NONE) return null;
        var actions = data.getItemUseActions();
        if (actions == null
                || actions.size() != 1
                || !(actions.get(0) instanceof ItemUseUnlockCodex action)) return null;
        var book = GameData.getCodexBookDataMap().get(action.getI());
        return book != null
                        && book.getId() == action.getI()
                        && book.getMaterialId() == data.getId()
                        && GameData.getCodexBookDataItemIdMap().get(data.getId()) == book
                ? book
                : null;
    }

    public static boolean owned(Player player, int materialId) {
        return player != null
                && player.getCodex() != null
                && player.getCodex().getUnlockedBook().contains(materialId);
    }
}

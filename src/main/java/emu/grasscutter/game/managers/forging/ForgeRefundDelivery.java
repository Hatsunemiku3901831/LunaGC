package emu.grasscutter.game.managers.forging;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketResinChangeNotify;

import java.util.*;

/** Restore only recorded costs, including resin paid before a configuration switch. */
final class ForgeRefundDelivery {
    private ForgeRefundDelivery() {}

    static int validate(Player player, List<ItemParamData> costs) {
        if (costs == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        var materials = new ArrayList<ItemParamData>();
        var ids = new HashSet<Integer>();
        for (var cost : costs) {
            if (cost == null || cost.getId() <= 0 || cost.getCount() <= 0 || !ids.add(cost.getId()))
                return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            var data = GameData.getItemDataMap().get(cost.getId());
            if (data == null || data.getId() != cost.getId() || data.isUseOnGain())
                return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            if (cost.getId() == 202 || cost.getId() == 106) {
                if (data.getItemType() != ItemType.ITEM_VIRTUAL)
                    return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                int before =
                        cost.getId() == 202
                                ? player.getMora()
                                : player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
                int maximum =
                        cost.getId() == 202
                                ? Integer.MAX_VALUE
                                : player.getPropertyMax(PlayerProperty.PROP_PLAYER_RESIN);
                if (before < 0 || (long) before + cost.getCount() > maximum)
                    return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
            } else if (data.getItemType() == ItemType.ITEM_MATERIAL) materials.add(cost);
            else return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        }
        return materials.isEmpty() ? 0 : InvestigationRewardDelivery.validate(player, materials);
    }

    /** Caller owns inventory and queue monitors until the refund receipt is confirmed. */
    static List<ItemHint> grant(Player player, List<ItemParamData> costs) {
        if (validate(player, costs) != 0)
            throw new IllegalStateException("Forge refund preflight changed");
        var hints = new ArrayList<ItemHint>();
        var inventory = player.getInventory();
        for (var cost : costs) {
            int id = cost.getId(), amount = cost.getCount();
            if (id == 202) {
                int before = player.getMora();
                if (!player.setMora(Math.addExact(before, amount))
                        || (long) player.getMora() - before != amount)
                    throw new IllegalStateException("Forge mora refund was not applied");
                hints.add(ItemHint.newBuilder().setItemId(id).setCount(amount).build());
            } else if (id == 106) {
                int before = player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
                if (!player.setProperty(
                                PlayerProperty.PROP_PLAYER_RESIN, Math.addExact(before, amount))
                        || (long) player.getProperty(PlayerProperty.PROP_PLAYER_RESIN) - before
                                != amount)
                    throw new IllegalStateException("Forge resin refund was not applied");
                if (player.getProperty(PlayerProperty.PROP_PLAYER_RESIN)
                        >= emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.cap)
                    player.setNextResinRefresh(0);
                player.sendPacket(new PacketResinChangeNotify(player));
                hints.add(ItemHint.newBuilder().setItemId(id).setCount(amount).build());
            } else {
                int before = inventory.getItemCountById(id);
                var item = new GameItem(GameData.getItemDataMap().get(id), amount);
                if (!inventory.addItem(item)
                        || (long) inventory.getItemCountById(id) - before != amount)
                    throw new IllegalStateException("Forge material refund was not applied");
                var stored = inventory.getItemById(id);
                if (stored == null)
                    throw new IllegalStateException("Forge refund stack disappeared");
                DatabaseHelper.saveGameSync(stored);
                hints.add(
                        stored.toItemHintProto().toBuilder()
                                .setCount(amount)
                                .setIsNew(item.isNewItem())
                                .build());
            }
        }
        return hints;
    }
}

package emu.grasscutter.game.combine;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.CombineData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.InvestigationHandbook;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.shop.BeyondCurrency;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

public class CombineManger extends BaseGameSystem {
    private static final ThreadLocal<Set<Inventory>> combining =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Int2ObjectMap<List<Integer>> reliquaryDecomposeData =
            new Int2ObjectOpenHashMap<>();

    public CombineManger(GameServer server) {
        super(server);
    }

    public static void initialize() {
        // Read the data we need for strongbox.
        try {
            DataLoader.loadList("ReliquaryDecompose.json", ReliquaryDecomposeEntry.class)
                    .forEach(
                            entry -> {
                                reliquaryDecomposeData.put(entry.getConfigId(), entry.getItems());
                            });
            Grasscutter.getLogger()
                    .debug("Loaded {} reliquary decompose entries.", reliquaryDecomposeData.size());
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load reliquary decompose data.", ex);
        }
    }

    public boolean unlockCombineDiagram(Player player, int combineId) {
        if (!player.getUnlockedCombines().add(combineId)) {
            return false; // Already unlocked
        }
        // Tell the client that this diagram is now unlocked and add the unlocked item to the
        // player.
        player.sendPacket(new PacketCombineFormulaDataNotify(combineId));
        return true;
    }

    public CombineResult combineItem(Player player, int cid, int count) {
        if (player == null) return null;
        var inventory = player.getInventory();
        synchronized (inventory) {
            var active = combining.get();
            if (!active.add(inventory)) return fail(player, Retcode.RET_SVR_ERROR_VALUE);
            try {
                return combineLocked(player, inventory, cid, count);
            } finally {
                active.remove(inventory);
                if (active.isEmpty()) combining.remove();
            }
        }
    }

    private CombineResult combineLocked(Player player, Inventory inventory, int cid, int count) {
        if (player.getPendingCombine() != null) return fail(player, Retcode.RET_SVR_ERROR_VALUE);
        var data = GameData.getCombineDataMap().get(cid);
        if (data == null || data.getId() != cid || count <= 0)
            return fail(player, Retcode.RET_FAIL_VALUE);
        if (data.getPlayerLevel() > player.getLevel())
            return fail(player, Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE);
        if (!data.isDefaultShow() && !player.getUnlockedCombines().contains(cid))
            return fail(player, Retcode.RET_COMBINE_IS_LOCKED_VALUE);
        long quantity = (long) data.getResultItemCount() * count;
        var output = GameData.getItemDataMap().get(data.getResultItemId());
        if (output == null
                || output.getId() != data.getResultItemId()
                || output.isUseOnGain()
                || quantity <= 0
                || quantity > Integer.MAX_VALUE)
            return fail(player, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var costs = scaledCosts(data, count);
        if (costs == null) return fail(player, Retcode.RET_FAIL_VALUE);
        if (!emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage)
            costs = costs.stream().filter(cost -> cost.getId() != 106).toList();
        if (!canReceive(player, inventory, output, (int) quantity, costs))
            return fail(player, Retcode.RET_ITEM_EXCEED_LIMIT_VALUE);
        Map<Integer, Integer> before;
        try {
            before = CombineDelivery.paymentStock(player, costs);
        } catch (IllegalArgumentException invalid) {
            return fail(player, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        }
        if (costs.stream().anyMatch(cost -> before.get(cost.getId()) < cost.getCount()))
            return fail(player, Retcode.RET_ITEM_COMBINE_COUNT_NOT_ENOUGH_VALUE);
        var receipt = new CombineReceipt(cid, count, output.getId(), (int) quantity, costs, before);
        player.setPendingCombine(receipt);
        try {
            DatabaseHelper.saveGameSync(player);
            CombineDelivery.pay(player, receipt, costs);
            CombineDelivery.grant(player, output, (int) quantity, receipt);
            InvestigationHandbook.trigger(player, "TRIGGER_DO_COMBINE", cid, count);
            receipt.completed();
            DatabaseHelper.saveGameSync(player);
            player.setPendingCombine(null);
            DatabaseHelper.saveGameSync(player);
        } catch (RuntimeException failure) {
            player.setPendingCombine(receipt);
            // Retry only the unchanged receipt write; never retry an economic operation.
            try {
                DatabaseHelper.saveGameSync(player);
            } catch (RuntimeException saveFailure) {
                failure.addSuppressed(saveFailure);
            }
            Grasscutter.getLogger()
                    .error(
                            "Combine needs reconciliation: UID {}, recipe {}",
                            player.getUid(),
                            cid,
                            failure);
            return fail(player, Retcode.RET_SVR_ERROR_VALUE);
        }
        CombineResult result = new CombineResult();
        result.setMaterial(costs);
        result.setResult(List.of(new ItemParamData(output.getId(), (int) quantity)));
        // TODO lucky characters
        result.setExtra(List.of());
        result.setBack(List.of());

        return result;
    }

    private static CombineResult fail(Player player, int code) {
        player.sendPacket(new PacketCombineRsp(code));
        return null;
    }

    private static List<ItemParamData> scaledCosts(CombineData data, int count) {
        if (data.getMaterialItems() == null || data.getScoinCost() < 0) return null;
        var totals = new LinkedHashMap<Integer, Long>();
        var original = new ArrayList<>(data.getMaterialItems());
        if (data.getScoinCost() > 0) original.add(new ItemParamData(202, data.getScoinCost()));
        for (var item : original) {
            if (item == null || item.getId() < 0 || item.getCount() < 0) return null;
            if (item.getCount() == 0) continue;
            if (item.getId() == 0) return null;
            long amount = (long) item.getCount() * count;
            if (amount > Integer.MAX_VALUE) return null;
            long total = totals.merge(item.getId(), amount, Long::sum);
            if (total > Integer.MAX_VALUE) return null;
        }
        return totals.entrySet().stream()
                .map(entry -> new ItemParamData(entry.getKey(), entry.getValue().intValue()))
                .toList();
    }

    private static boolean canReceive(
            Player player,
            Inventory inventory,
            ItemData output,
            int quantity,
            List<ItemParamData> costs) {
        if (output.getItemType() == ItemType.ITEM_VIRTUAL) {
            var property = BeyondCurrency.propertyForItem(output.getId());
            int before =
                    property == null
                            ? switch (output.getId()) {
                                case 201 -> player.getPrimogems();
                                case 202 -> player.getMora();
                                case 203 -> player.getCrystals();
                                case 204 -> player.getHomeCoin();
                                case 106 -> player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
                                case 107 -> player.getProperty(
                                        PlayerProperty.PROP_PLAYER_LEGENDARY_KEY);
                                default -> -1;
                            }
                            : player.getProperty(property);
            long spent =
                    costs.stream()
                            .filter(item -> item.getId() == output.getId())
                            .mapToLong(ItemParamData::getCount)
                            .sum();
            if (before < 0) return false;
            long after = (long) before - spent + quantity;
            if (output.getId() == 106) {
                if (!emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage)
                    return false;
                return after <= player.getPropertyMax(PlayerProperty.PROP_PLAYER_RESIN);
            }
            if (output.getId() == 107)
                return after <= player.getPropertyMax(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY);
            return after >= 0 && after <= Integer.MAX_VALUE;
        }
        var tab = inventory.getInventoryTab(output.getItemType());
        if (tab == null) return false;
        if (output.getItemType() == ItemType.ITEM_WEAPON
                || output.getItemType() == ItemType.ITEM_RELIQUARY)
            return (long) tab.getSize() + quantity <= tab.getMaxCapacity();
        if (output.getItemType() == ItemType.ITEM_TPS_WEAPON)
            return quantity == 1
                    && tab.getItemById(output.getId()) == null
                    && tab.getSize() < tab.getMaxCapacity();
        if (output.getStackLimit() <= 0
                || Set.of(
                                MaterialType.MATERIAL_AVATAR,
                                MaterialType.MATERIAL_FLYCLOAK,
                                MaterialType.MATERIAL_COSTUME,
                                MaterialType.MATERIAL_NAMECARD,
                                MaterialType.MATERIAL_AVATAR_TRACE,
                                MaterialType.MATERIAL_WEAPON_SKIN)
                        .contains(output.getMaterialType())) return false;
        long remaining = tab.getItemCountById(output.getId());
        int freed = 0;
        for (var cost : costs) {
            var stack = tab.getItemById(cost.getId());
            if (stack != null && stack.getCount() == cost.getCount()) freed++;
            if (cost.getId() == output.getId()) remaining -= cost.getCount();
        }
        if (remaining < 0 || remaining + quantity > output.getStackLimit()) return false;
        return (long) tab.getSize() - freed + (remaining == 0 ? 1 : 0) <= tab.getMaxCapacity();
    }

    public synchronized void decomposeReliquaries(
            Player player, int configId, int count, List<Long> input) {
        // Check if the configId is legal.
        List<Integer> possibleDrops = reliquaryDecomposeData.get(configId);
        if (possibleDrops == null) {
            player.sendPacket(
                    new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
            return;
        }

        // Check if the number of input items matches the output count.
        if (input.size() != count * 3) {
            player.sendPacket(
                    new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
            return;
        }

        // Check if all the input reliquaries actually are in the player's inventory.
        for (long guid : input) {
            if (player.getInventory().getItemByGuid(guid) == null) {
                player.sendPacket(
                        new PacketReliquaryDecomposeRsp(
                                Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
                return;
            }
        }

        // Delete the input reliquaries.
        for (long guid : input) {
            player.getInventory().removeItem(guid);
        }

        // Generate outoput reliquaries.
        List<Long> resultItems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int itemId = Utils.drawRandomListElement(possibleDrops);
            GameItem newReliquary = new GameItem(itemId, 1);

            player.getInventory().addItem(newReliquary);
            resultItems.add(newReliquary.getGuid());
        }

        // Send packet.
        player.sendPacket(new PacketReliquaryDecomposeRsp(resultItems));
    }
}

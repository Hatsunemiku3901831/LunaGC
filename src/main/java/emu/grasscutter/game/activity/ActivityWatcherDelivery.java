package emu.grasscutter.game.activity;

import static emu.grasscutter.game.activity.ActivityWatcherReceipt.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.shop.SpecialCosmeticShop;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.bson.json.*;
import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class ActivityWatcherDelivery {
    static final class Rejected extends RuntimeException {
        final int code;

        Rejected(int code) {
            this.code = code;
        }
    }

    private static void reject(boolean condition, int code) {
        if (!condition) throw new Rejected(code);
    }

    static String signature(ItemData item) {
        String fields =
                item.getItemType()
                        + "|"
                        + item.getMaterialType()
                        + "|"
                        + item.getStackLimit()
                        + "|"
                        + item.isUseOnGain()
                        + "|"
                        + JsonUtils.encode(item.getItemUse());
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(fields.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean cosmetic(ItemData item) {
        return item.isUseOnGain()
                && SpecialCosmeticShop.valid(item)
                && item.getItemUseActions() != null
                && item.getItemUseActions().size() == 1;
    }

    static List<Line> prepare(PlayerActivityData data, List<ItemParamData> raw) {
        int invalid = Retcode.RET_SVR_ERROR_VALUE, full = Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        reject(raw != null && !raw.isEmpty(), invalid);
        var player = data.getPlayer();
        var inventory = player.getInventory();
        var counts = new LinkedHashMap<Integer, Long>();
        for (var row : raw) {
            reject(row != null && row.getId() > 0 && row.getCount() > 0, invalid);
            reject(
                    counts.merge(row.getId(), (long) row.getCount(), Long::sum)
                            <= Integer.MAX_VALUE,
                    full);
        }
        var slots = new HashMap<InventoryTab, Long>();
        for (var row : counts.entrySet()) {
            var item = GameData.getItemDataMap().get(row.getKey().intValue());
            reject(item != null && item.getId() == row.getKey(), invalid);
            long count = row.getValue();
            var type = item.getItemType();
            if (item.isUseOnGain()) {
                reject(cosmetic(item) && count == 1, invalid);
                continue;
            }
            reject(
                    SpecialCosmeticShop.kind(item.getId()).isEmpty()
                            && item.getMaterialType() != MaterialType.MATERIAL_AVATAR,
                    invalid);
            if (type == ItemType.ITEM_VIRTUAL) {
                boolean own =
                        Objects.equals(
                                ActivityManager.configurationSnapshot()
                                        .currencyActivities()
                                        .get(item.getId()),
                                data.getActivityId());
                reject(item.getId() == 201 || item.getId() == 202 || own, invalid);
                int before = balance(data, item.getId());
                reject(before >= 0 && (long) before + count <= Integer.MAX_VALUE, full);
                if (own) reject(ActivityCurrencies.canCredit(player, item.getId(), count), invalid);
                continue;
            }
            var tab = inventory.getInventoryTab(type);
            reject(tab != null, invalid);
            if (item.isEquip()) {
                slots.merge(tab, count, Long::sum);
                continue;
            }
            reject(
                    type == ItemType.ITEM_MATERIAL
                            || type == ItemType.ITEM_FURNITURE
                            || type == ItemType.ITEM_TPS_WEAPON,
                    invalid);
            var existing = tab.getItemById(item.getId());
            reject(existing == null || existing.getOwnerId() == player.getUid(), invalid);
            int before = existing == null ? 0 : existing.getCount();
            reject(
                    before >= 0
                            && item.getStackLimit() > 0
                            && before + count <= item.getStackLimit(),
                    full);
            if (type == ItemType.ITEM_TPS_WEAPON) reject(existing == null && count == 1, full);
            if (existing == null) slots.merge(tab, 1L, Long::sum);
        }
        for (var row : slots.entrySet())
            reject(
                    (long) row.getKey().getSize() + row.getValue() <= row.getKey().getMaxCapacity(),
                    full);
        var lines = new ArrayList<Line>();
        for (var row : counts.entrySet()) {
            var item = GameData.getItemDataMap().get(row.getKey().intValue());
            int count = row.getValue().intValue();
            int kind =
                    cosmetic(item)
                            ? COSMETIC
                            : item.getItemType() == ItemType.ITEM_VIRTUAL
                                    ? VIRTUAL
                                    : item.isEquip() ? EQUIPMENT : STACK;
            int instances = kind == EQUIPMENT ? count : 1;
            for (int i = 0; i < instances; i++) {
                var offered = new GameItem(item, kind == EQUIPMENT ? 1 : count);
                reject(offered.getCount() == (kind == EQUIPMENT ? 1 : count), full);
                String objectId = null;
                int before = 0;
                if (kind == STACK || kind == EQUIPMENT) {
                    var existing = kind == STACK ? inventory.getItemById(item.getId()) : null;
                    before = existing == null ? 0 : existing.getCount();
                    var id = existing == null ? new ObjectId() : existing.ensurePersistenceId();
                    offered.restorePersistenceId(id);
                    objectId = id.toHexString();
                } else if (kind == VIRTUAL) before = balance(data, item.getId());
                else before = SpecialCosmeticShop.owned(player, item.getId()) ? 1 : 0;
                int after = kind == COSMETIC ? 1 : Math.addExact(before, offered.getCount());
                String template =
                        DatabaseManager.getGameDatastore()
                                .getMapper()
                                .toDocument(offered)
                                .toJson(
                                        JsonWriterSettings.builder()
                                                .outputMode(JsonMode.EXTENDED)
                                                .build());
                String coin =
                        kind == VIRTUAL && ActivityCurrencies.contains(item.getId())
                                ? UUID.randomUUID().toString()
                                : null;
                lines.add(
                        new Line(
                                item.getId(),
                                offered.getCount(),
                                kind,
                                before,
                                after,
                                objectId,
                                template,
                                signature(item),
                                coin));
            }
        }
        return lines;
    }

    static int balance(PlayerActivityData data, int id) {
        if (id == 201) return data.getPlayer().getPrimogems();
        if (id == 202) return data.getPlayer().getMora();
        return data.getActivityCoins() == null ? 0 : data.getActivityCoins().getOrDefault(id, 0);
    }

    private static ItemData definition(Line line) {
        var item = GameData.getItemDataMap().get(line.getItemId());
        require(item != null && signature(item).equals(line.getDefinition()), "活动奖励资源已改变，保留原计划。");
        return item;
    }

    static void grant(PlayerActivityData data, Line line) {
        grant(data, line, () -> {});
    }

    static void grant(PlayerActivityData data, Line line, Runnable starting) {
        var player = data.getPlayer();
        var item = definition(line);
        var inventory = player.getInventory();
        if (line.getKind() == COSMETIC && SpecialCosmeticShop.owned(player, item.getId())) {
            DatabaseHelper.saveGameSync(player);
            return;
        }
        if (line.getKind() == VIRTUAL)
            require(balance(data, item.getId()) == line.getBefore(), "活动虚拟奖励发放前数量已改变。");
        if (line.getKind() == STACK) {
            var existing = inventory.getItemById(item.getId());
            require(
                    (existing == null ? 0 : existing.getCount()) == line.getBefore()
                            && (existing == null
                                    || existing.getObjectId() != null
                                            && existing.getObjectId()
                                                    .toHexString()
                                                    .equals(line.getObjectId())),
                    "活动奖励库存或持久 ID 已改变。");
        }
        if (line.getKind() == EQUIPMENT)
            require(live(player, line.getObjectId()) == null, "活动装备实例已经存在，需先核对。");
        var offered =
                GameItem.fromUnownedTemplate(
                        DatabaseManager.getGameDatastore(), Document.parse(line.getTemplate()));
        require(offered.getOwnerId() == 0, "活动奖励模板已经拥有玩家。");
        starting.run();
        require(inventory.addItem(offered, ActionReason.ActivityWatcher), "活动奖励未被接受。");
        if (line.getKind() == COSMETIC)
            require(SpecialCosmeticShop.owned(player, item.getId()), "活动外观未解锁。");
        else if (line.getKind() == VIRTUAL)
            require(balance(data, item.getId()) == line.getAfter(), "活动虚拟奖励数量不符。");
        else {
            var actual = live(player, line.getObjectId());
            require(
                    actual != null
                            && actual.getItemId() == line.getItemId()
                            && actual.getCount() == line.getAfter(),
                    "活动奖励未进入预留的库存实例。");
            DatabaseHelper.saveGameSync(actual);
        }
        // Includes global currencies, codex and obtained history changed by inventory admission.
        DatabaseHelper.saveGameSync(player);
    }

    static GameItem live(Player player, String objectId) {
        return player.getInventory().getItems().values().stream()
                .filter(
                        i ->
                                i.getObjectId() != null
                                        && i.getObjectId().toHexString().equals(objectId))
                .findFirst()
                .orElse(null);
    }
    /**
     * New-instance identity and idempotent cosmetics provide proof without guessing stack deltas.
     */
    static boolean reconcileStarted(PlayerActivityData data, Line line) {
        definition(line);
        var player = data.getPlayer();
        if (line.getCoinOperationId() != null) {
            var pending = data.getPendingCoinOperation();
            var last = data.getLastCoinOperation();
            var coin = pending != null ? pending : last;
            if (coin != null && coin.getOperationId().equals(line.getCoinOperationId())) {
                require(
                        coin.isCredit()
                                && coin.getAmounts()
                                        .equals(Map.of(line.getItemId(), line.getCount()))
                                && coin.getBefore().getOrDefault(line.getItemId(), 0)
                                        == line.getBefore()
                                && coin.getAfter().getOrDefault(line.getItemId(), 0)
                                        == line.getAfter(),
                        "关联的活动货币计划不符。");
                data.getCoinTransaction().resume(line.getCoinOperationId());
                DatabaseHelper.saveGameSync(player);
                return true;
            }
            // No matching currency intent ever applied; the same fixed ID remains safe to start.
            require(
                    pending == null && balance(data, line.getItemId()) == line.getBefore(),
                    "活动货币已有其他待核对计划。");
            grant(data, line);
            return true;
        }
        if (line.getKind() == COSMETIC) {
            grant(data, line);
            return true;
        }
        if ((line.getKind() == EQUIPMENT || line.getKind() == STACK && line.getBefore() == 0)
                && line.getObjectId() != null) {
            var stored =
                    DatabaseManager.getGameDatastore()
                            .find(GameItem.class)
                            .filter(
                                    dev.morphia.query.experimental.filters.Filters.eq(
                                            "id", new ObjectId(line.getObjectId())))
                            .first();
            var live = live(player, line.getObjectId());
            if (stored == null && live == null) return false;
            var proof = stored != null ? stored : live;
            require(
                    proof.getOwnerId() == player.getUid() && proof.getItemId() == line.getItemId(),
                    "预留奖励实例归属不符。");
            if (stored == null) DatabaseHelper.saveGameSync(live);
            DatabaseHelper.saveGameSync(player);
            return true;
        }
        return false;
    }
}

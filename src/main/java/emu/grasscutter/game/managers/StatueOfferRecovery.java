package emu.grasscutter.game.managers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.city.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.*;
import org.bson.json.*;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Explicit reconciliation against frozen economics and acknowledged documents, never a refund
 * guess.
 */
final class StatueOfferRecovery {
    private final Player player;
    private final Runnable save;
    private final Consumer<GameItem> saveItem;

    StatueOfferRecovery(Player player, Runnable save, Consumer<GameItem> saveItem) {
        this.player = player;
        this.save = save;
        this.saveItem = saveItem;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    private static PlayerProperty property(int id) {
        return switch (id) {
            case 102 -> PlayerProperty.PROP_PLAYER_EXP;
            case 201 -> PlayerProperty.PROP_PLAYER_HCOIN;
            case 202 -> PlayerProperty.PROP_PLAYER_SCOIN;
            default -> throw new IllegalStateException("不支持的供奉虚拟奖励。");
        };
    }

    private record Experience(int level, int exp) {}

    private static Experience experience(int level, int exp, int gain) {
        int after = Math.addExact(exp, gain);
        for (var data = GameData.getPlayerLevelDataMap().get(level);
                data != null && data.getExp() > 0 && after >= data.getExp();
                data = GameData.getPlayerLevelDataMap().get(level)) {
            after -= data.getExp();
            level++;
        }
        return new Experience(level, after);
    }

    /**
     * Current city rewards are stack materials and these three currencies; extensions keep legacy
     * protection.
     */
    static void freeze(
            Player player,
            StatueOfferReceipt receipt,
            GameItem source,
            int scene,
            int area,
            List<Integer> forces) {
        for (var row : receipt.getRewards()) {
            var data = GameData.getItemDataMap().get(row.getId());
            if (data == null
                    || row.getId() == receipt.getItemId()
                    || data.getItemType() != ItemType.ITEM_MATERIAL
                            && (data.getItemType() != ItemType.ITEM_VIRTUAL
                                    || !Set.of(102, 201, 202).contains(row.getId()))) return;
        }
        var frozen = new ArrayList<StatueOfferReceipt.Reward>();
        for (var row : receipt.getRewards()) {
            boolean virtual =
                    GameData.getItemDataMap().get(row.getId()).getItemType()
                            == ItemType.ITEM_VIRTUAL;
            if (virtual) {
                int before = player.getProperty(property(row.getId()));
                int level = row.getId() == 102 ? player.getLevel() : 0;
                var after =
                        row.getId() == 102
                                ? experience(level, before, row.getCount())
                                : new Experience(0, Math.addExact(before, row.getCount()));
                frozen.add(
                        new StatueOfferReceipt.Reward(
                                row.getId(),
                                row.getCount(),
                                before,
                                after.exp(),
                                level,
                                after.level(),
                                null,
                                true));
            } else {
                var item = player.getInventory().getItemById(row.getId());
                int before = item == null ? 0 : item.getCount();
                String id =
                        (item == null ? new ObjectId() : item.ensurePersistenceId()).toHexString();
                frozen.add(
                        new StatueOfferReceipt.Reward(
                                row.getId(),
                                row.getCount(),
                                before,
                                Math.addExact(before, row.getCount()),
                                0,
                                0,
                                id,
                                false));
            }
        }
        receipt.freeze(
                receipt.getConsumed() == 0 ? null : source.ensurePersistenceId().toHexString(),
                scene,
                area,
                forces,
                frozen);
    }

    static void grant(
            Player player, StatueOfferReceipt receipt, Runnable save, Consumer<GameItem> saveItem) {
        if (receipt.getVersion() != 1) {
            if (!receipt.getRewards().isEmpty())
                InvestigationRewardDelivery.grant(
                        player,
                        receipt.getRewards().stream()
                                .map(x -> new ItemParamData(x.getId(), x.getCount()))
                                .toList(),
                        ActionReason.CityLevelupReward);
            return;
        }
        for (var row : receipt.getRewards()) deliver(player, row, save, saveItem);
    }

    private static boolean matches(Player player, StatueOfferReceipt.Reward row, boolean after) {
        int expected = after ? row.getAfter() : row.getBefore();
        if (row.isVirtual())
            return player.getProperty(property(row.getId())) == expected
                    && (row.getId() != 102
                            || player.getLevel()
                                    == (after ? row.getLevelAfter() : row.getLevelBefore()));
        var item = player.getInventory().getItemById(row.getId());
        return item == null
                ? expected == 0
                : item.getCount() == expected
                        && item.getOwnerId() == player.getUid()
                        && item.getObjectId() != null
                        && item.getItemId() == row.getId()
                        && player.getInventory().getItemByGuid(item.getGuid()) == item
                        && item.getObjectId().toHexString().equals(row.getObjectId());
    }

    private static void deliver(
            Player player,
            StatueOfferReceipt.Reward row,
            Runnable save,
            Consumer<GameItem> saveItem) {
        require(matches(player, row, false), "发奖前的数量、等级或持久 ID 已改变。");
        if (row.getId() == 102)
            require(
                    experience(player.getLevel(), player.getExp(), row.getCount())
                            .equals(new Experience(row.getLevelAfter(), row.getAfter())),
                    "阅历等级资源已改变。");
        var data = GameData.getItemDataMap().get(row.getId());
        require(
                data != null
                        && data.getId() == row.getId()
                        && !data.isUseOnGain()
                        && data.getItemType()
                                == (row.isVirtual()
                                        ? ItemType.ITEM_VIRTUAL
                                        : ItemType.ITEM_MATERIAL),
                "供奉奖励资源已改变，保留记录。");
        var item = new GameItem(data, row.getCount());
        if (!row.isVirtual()) item.restorePersistenceId(new ObjectId(row.getObjectId()));
        require(player.getInventory().addItem(item, ActionReason.CityLevelupReward), "供奉奖励发放未确认。");
        require(matches(player, row, true), "供奉奖励与冻结快照不同，保留记录。");
        if (!row.isVirtual()) saveItem.accept(player.getInventory().getItemById(row.getId()));
        // Virtual rewards live in this document; material obtained-history is acknowledged here
        // too.
        save.run();
    }

    private void flush() {
        try {
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)), "数据库仍有待写入操作，请稍后重试。");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("供奉核对被中断。", error);
        }
    }

    private record Saved(CityInfoData city, Map<Integer, Integer> properties) {}

    private Saved saved(int cityId) {
        // Project embedded values only: decoding a second Player can invoke unrelated lifecycle
        // writes.
        var raw =
                DatabaseManager.getGameDatastore()
                        .getMapper()
                        .getCollection(Player.class)
                        .withDocumentClass(BsonDocument.class)
                        .find(new BsonDocument("_id", new BsonInt32(player.getUid())))
                        .projection(
                                new BsonDocument("cityInfoData", new BsonInt32(1))
                                        .append("properties", new BsonInt32(1)))
                        .first();
        require(raw != null, "玩家存档不存在。");
        var cities = raw.getDocument("cityInfoData", new BsonDocument());
        String key = Integer.toString(cityId);
        var city =
                cities.containsKey(key) && !cities.isNull(key)
                        ? JsonUtils.decode(
                                cities.getDocument(key)
                                        .toJson(
                                                JsonWriterSettings.builder()
                                                        .outputMode(JsonMode.RELAXED)
                                                        .build()),
                                CityInfoData.class)
                        : null;
        var properties = new HashMap<Integer, Integer>();
        raw.getDocument("properties", new BsonDocument())
                .forEach(
                        (id, value) ->
                                properties.put(Integer.parseInt(id), value.asNumber().intValue()));
        return new Saved(city, properties);
    }

    private CityInfoData live(int cityId) {
        return player.getCityInfoData() == null ? null : player.getCityInfoData().get(cityId);
    }

    /** Compare unique stacks and exact durable identities, including already deleted payments. */
    private int stack(int itemId, String objectId) {
        require(objectId != null && ObjectId.isValid(objectId), "记录缺少有效的物品持久 ID。");
        var live =
                player.getInventory().getItems().values().stream()
                        .filter(x -> x.getItemId() == itemId)
                        .toList();
        var stored = new ArrayList<GameItem>();
        try (var cursor =
                DatabaseManager.getGameDatastore()
                        .find(GameItem.class)
                        .field("ownerId")
                        .equal(player.getUid())
                        .field("itemId")
                        .equal(itemId)
                        .iterator()) {
            cursor.forEachRemaining(stored::add);
        }
        require(live.size() <= 1 && stored.size() == live.size(), "背包堆叠与存档存在状态不同，请重登后核对。");
        var reserved =
                DatabaseManager.getGameDatastore()
                        .find(GameItem.class)
                        .field("id")
                        .equal(new ObjectId(objectId))
                        .first();
        if (live.isEmpty()) {
            require(reserved == null, "预留持久 ID 已被其他物品占用。");
            return 0;
        }
        var a = live.get(0);
        var b = stored.get(0);
        require(
                a.getOwnerId() == player.getUid()
                        && a.getCount() > 0
                        && a.getCount() == b.getCount()
                        && a.getObjectId() != null
                        && a.getObjectId().equals(b.getObjectId())
                        && objectId.equals(a.getObjectId().toHexString())
                        && reserved != null,
                "物品归属、数量或持久 ID 不等于存档快照。");
        return a.getCount();
    }

    private int balance(Saved saved, PlayerProperty property) {
        require(
                saved.properties().containsKey(property.getId())
                        && player.getProperty(property) == saved.properties().get(property.getId()),
                "玩家属性与存档不一致，请重登后核对。");
        return player.getProperty(property);
    }

    List<String> inspect(int cityId) {
        synchronized (player.getInventory()) {
            flush();
            var live = live(cityId);
            var saved = saved(cityId);
            var receipt = live == null ? null : live.getPendingOffer();
            var result = new ArrayList<String>();
            result.add(
                    "uid="
                            + player.getUid()
                            + " city="
                            + cityId
                            + " liveLevel="
                            + (live == null ? 1 : live.getLevel())
                            + " storedLevel="
                            + (saved.city() == null ? 1 : saved.city().getLevel()));
            if (receipt == null) result.add("无待核对的供奉记录。");
            else {
                result.add(
                        "operation="
                                + receipt.getOperationId()
                                + " state="
                                + receipt.getState()
                                + " version="
                                + receipt.getVersion()
                                + " cost="
                                + receipt.getItemId()
                                + " before="
                                + receipt.getItemBefore()
                                + " consumed="
                                + receipt.getConsumed()
                                + " objectId="
                                + receipt.getCostObjectId());
                for (var row : receipt.getRewards())
                    result.add(
                            "reward="
                                    + row.getId()
                                    + " x"
                                    + row.getCount()
                                    + " before="
                                    + row.getBefore()
                                    + " after="
                                    + row.getAfter()
                                    + " objectId="
                                    + row.getObjectId());
                if (receipt.getVersion() != 1) result.add("旧记录缺少完整经济快照，保留人工核对。");
                else {
                    try {
                        check(cityId, receipt.getOperationId(), saved);
                        result.add("存档与冻结快照一致，可使用操作 ID 恢复；仅未扣费预留可取消。");
                    } catch (IllegalStateException mismatch) {
                        result.add("不能自动恢复：" + mismatch.getMessage());
                    }
                }
            }
            return List.copyOf(result);
        }
    }

    private record Checked(
            CityInfoData city,
            StatueOfferReceipt receipt,
            boolean paid,
            List<Boolean> delivered,
            boolean staminaApplied) {}

    private Checked check(int cityId, String operationId, Saved saved) {
        var city = live(cityId);
        var receipt = city == null ? null : city.getPendingOffer();
        require(
                receipt != null && Objects.equals(receipt.getOperationId(), operationId),
                "操作 ID 不匹配或记录已处理。");
        require(receipt.getVersion() == 1, "旧供奉记录缺少经济快照，需要人工核对。");
        require(
                saved.city() != null
                        && saved.city().getCityId() == cityId
                        && city.getCityId() == cityId
                        && city.getLevel() == saved.city().getLevel()
                        && city.getNumCrystal() == saved.city().getNumCrystal()
                        && JsonUtils.toJson(receipt)
                                .equals(JsonUtils.toJson(saved.city().getPendingOffer())),
                "供奉记录或进度与存档不一致，请重登后核对。");
        require(
                receipt.getState() >= 1
                        && receipt.getState() <= 3
                        && receipt.getConsumed() >= 0
                        && receipt.getConsumed() <= receipt.getItemBefore()
                        && receipt.getLevelBefore() > 0
                        && receipt.getLevelAfter() >= receipt.getLevelBefore()
                        && receipt.getCrystalsBefore() >= 0
                        && receipt.getCrystalsAfter() >= 0
                        && receipt.getStaminaBefore() >= 0
                        && receipt.getStaminaAfter() >= receipt.getStaminaBefore()
                        && receipt.getStaminaAfter() <= PlayerProperty.PROP_MAX_STAMINA.getMax()
                        && receipt.getRewards() != null
                        && receipt.getForces() != null
                        && receipt.getSceneId() > 0
                        && receipt.getAreaId() > 0,
                "供奉快照内容无效。");
        boolean applied = receipt.getState() == 3;
        require(
                city.getLevel() == (applied ? receipt.getLevelAfter() : receipt.getLevelBefore())
                        && city.getNumCrystal()
                                == (applied
                                        ? receipt.getCrystalsAfter()
                                        : receipt.getCrystalsBefore()),
                "城市进度不等于供奉前后快照。");
        int cost =
                receipt.getConsumed() == 0
                        ? receipt.getItemBefore()
                        : stack(receipt.getItemId(), receipt.getCostObjectId());
        boolean paid =
                receipt.getConsumed() == 0
                        || cost == receipt.getItemBefore() - receipt.getConsumed();
        require(paid || cost == receipt.getItemBefore(), "成本数量不等于支付前后快照。");
        require(receipt.getState() == 1 || paid, "已确认支付的成本与存档不符。");
        int stamina = balance(saved, PlayerProperty.PROP_MAX_STAMINA);
        require(
                stamina == receipt.getStaminaBefore() || stamina == receipt.getStaminaAfter(),
                "体力上限已改变，不能猜测补发。");
        require(!applied || stamina == receipt.getStaminaAfter(), "已结算记录的体力与存档不符。");
        var unique = new HashSet<Integer>();
        var ids = new HashSet<String>();
        if (receipt.getCostObjectId() != null) ids.add(receipt.getCostObjectId());
        var delivered = new ArrayList<Boolean>();
        for (var row : receipt.getRewards()) {
            require(
                    row != null
                            && row.getId() > 0
                            && row.getCount() > 0
                            && row.getBefore() >= 0
                            && row.getAfter() >= 0
                            && unique.add(row.getId())
                            && row.getId() != receipt.getItemId(),
                    "奖励快照内容无效或重复。");
            int count;
            if (row.isVirtual()) {
                count = balance(saved, property(row.getId()));
                if (row.getId() == 102) balance(saved, PlayerProperty.PROP_PLAYER_LEVEL);
                else
                    require(
                            (long) row.getBefore() + row.getCount() == row.getAfter(),
                            "虚拟奖励快照数量无效。");
            } else {
                require(
                        ids.add(row.getObjectId())
                                && (long) row.getBefore() + row.getCount() == row.getAfter(),
                        "材料奖励数量或持久 ID 重复。");
                count = stack(row.getId(), row.getObjectId());
            }
            boolean after = count == row.getAfter() && matches(player, row, true);
            boolean before = count == row.getBefore() && matches(player, row, false);
            require(after || before, "奖励 " + row.getId() + " 不等于发放前后快照。");
            require(!applied || after, "已结算记录的奖励与存档不符。");
            require(receipt.getState() > 1 || before, "尚未确认支付但奖励已经改变，需要人工核对。");
            delivered.add(after);
        }
        return new Checked(
                city, receipt, paid, List.copyOf(delivered), stamina == receipt.getStaminaAfter());
    }

    private void clear(CityInfoData city, StatueOfferReceipt receipt) {
        city.setPendingOffer(null);
        try {
            save.run();
        } catch (RuntimeException error) {
            city.setPendingOffer(receipt);
            throw error;
        }
    }

    StatueOfferReceipt recover(int cityId, String operationId, boolean resume) {
        synchronized (player.getInventory()) {
            flush();
            var checked = check(cityId, operationId, saved(cityId));
            var receipt = checked.receipt();
            var scene = player.getScene();
            require(
                    scene != null
                            && scene.getId() == receipt.getSceneId()
                            && scene.getWorld() == player.getWorld()
                            && scene.getWorld().getHost() == player,
                    "请在自己的世界、对应神像场景中恢复。");
            if (!resume) {
                require(
                        receipt.getState() == 1
                                && (receipt.getConsumed() == 0 || !checked.paid())
                                && checked.delivered().stream().noneMatch(Boolean::booleanValue)
                                && player.getProperty(PlayerProperty.PROP_MAX_STAMINA)
                                        == receipt.getStaminaBefore(),
                        "只能取消未扣费且未发奖的预留；已扣费记录请使用 resume。");
                clear(checked.city(), receipt);
            } else {
                var remaining = new ArrayList<ItemParamData>();
                for (int i = 0; i < receipt.getRewards().size(); i++)
                    if (!checked.delivered().get(i)) {
                        var row = receipt.getRewards().get(i);
                        remaining.add(new ItemParamData(row.getId(), row.getCount()));
                        var data = GameData.getItemDataMap().get(row.getId());
                        require(
                                data != null
                                        && data.getItemType()
                                                == (row.isVirtual()
                                                        ? ItemType.ITEM_VIRTUAL
                                                        : ItemType.ITEM_MATERIAL),
                                "奖励资源类型已改变。");
                        if (row.getId() == 102)
                            require(
                                    experience(
                                                    row.getLevelBefore(),
                                                    row.getBefore(),
                                                    row.getCount())
                                            .equals(
                                                    new Experience(
                                                            row.getLevelAfter(), row.getAfter())),
                                    "阅历等级资源已改变。");
                    }
                int freed =
                        !checked.paid() && receipt.getItemBefore() == receipt.getConsumed() ? 1 : 0;
                require(
                        remaining.isEmpty()
                                || InvestigationRewardDelivery.validate(player, remaining, freed)
                                        == 0,
                        "奖励资源、数量或背包容量不满足恢复条件。");
                if (!checked.paid()) {
                    var source = player.getInventory().getItemById(receipt.getItemId());
                    require(
                            source != null
                                    && source.getItemData() != null
                                    && source.getItemType() == ItemType.ITEM_MATERIAL
                                    && !source.getItemData().isUseOnGain()
                                    && source.getItemData().getId() == receipt.getItemId()
                                    && source.getItemId() == receipt.getItemId()
                                    && source.getCount() == receipt.getItemBefore()
                                    && source.getObjectId() != null
                                    && source.getObjectId()
                                            .toHexString()
                                            .equals(receipt.getCostObjectId())
                                    && player.getInventory().getItemByGuid(source.getGuid())
                                            == source,
                            "供奉材料资源不可用。");
                    int before = source.getCount();
                    require(
                            player.getInventory().removeItem(source, receipt.getConsumed())
                                    && before - source.getCount() == receipt.getConsumed(),
                            "供奉扣除未准确确认。");
                    saveItem.accept(source);
                }
                if (receipt.getState() < 2) {
                    receipt.paid();
                    save.run();
                }
                for (int i = 0; i < receipt.getRewards().size(); i++)
                    if (!checked.delivered().get(i))
                        deliver(player, receipt.getRewards().get(i), save, saveItem);
                if (receipt.getState() != 3) {
                    if (!checked.staminaApplied())
                        require(
                                player.setProperty(
                                                PlayerProperty.PROP_MAX_STAMINA,
                                                receipt.getStaminaAfter(),
                                                true)
                                        && player.getProperty(PlayerProperty.PROP_MAX_STAMINA)
                                                == receipt.getStaminaAfter(),
                                "体力更新未得到确认，保留记录。");
                    checked.city().setLevel(receipt.getLevelAfter());
                    checked.city().setNumCrystal(receipt.getCrystalsAfter());
                    receipt.applied();
                    save.run();
                }
                clear(checked.city(), receipt);
            }
            Grasscutter.getLogger()
                    .info(
                            "Statue offering recovery uid={} city={} operation={} action={}",
                            player.getUid(),
                            cityId,
                            operationId,
                            resume ? "resume" : "cancel");
            return receipt;
        }
    }
}

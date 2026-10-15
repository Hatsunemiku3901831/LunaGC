package emu.grasscutter.game.reliquary;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer.ArtifactTransmuterOptions;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ReliquaryOfferDataNotifyOuterClass.ReliquaryOfferDataNotify;
import emu.grasscutter.net.proto.ReliquaryOfferDefineInfoOuterClass.ReliquaryOfferDefineInfo;
import emu.grasscutter.net.proto.ReliquaryOfferDefineReqOuterClass.ReliquaryOfferDefineReq;
import emu.grasscutter.net.proto.ReliquaryOfferDefineRspOuterClass.ReliquaryOfferDefineRsp;
import emu.grasscutter.net.proto.ReliquaryOfferExtractReqOuterClass.ReliquaryOfferExtractReq;
import emu.grasscutter.net.proto.ReliquaryOfferExtractRspOuterClass.ReliquaryOfferExtractRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferDataNotify;

import java.util.*;
import java.util.function.*;

/** Validate a complete extraction before reserving, paying or granting anything. */
public final class ArtifactTransmuter {
    public static final int GADGET_ITEM_ID = 220109;
    private final Player player;
    private final LongSupplier clock;
    private final Runnable save;
    private final Consumer<GameItem> saveItem;

    public ArtifactTransmuter(Player player) {
        this(
                player,
                () -> System.currentTimeMillis() / 1000L,
                () -> DatabaseHelper.saveGameSync(player),
                DatabaseHelper::saveGameSync);
    }

    public ArtifactTransmuter(
            Player player, LongSupplier clock, Runnable save, Consumer<GameItem> saveItem) {
        this.player = Objects.requireNonNull(player);
        this.clock = Objects.requireNonNull(clock);
        this.save = Objects.requireNonNull(save);
        this.saveItem = Objects.requireNonNull(saveItem);
    }

    private static final class Rejected extends RuntimeException {
        final int code;

        Rejected(int code) {
            this.code = code;
        }
    }

    private void require(boolean condition, int retcode) {
        if (!condition) throw new Rejected(retcode);
    }

    private ArtifactTransmuterOptions options() {
        var game = Grasscutter.getConfig().server.game.gameOptions;
        return game == null || game.artifactTransmuter == null
                ? new ArtifactTransmuterOptions()
                : game.artifactTransmuter;
    }

    private GameItem owned(int id) {
        var inventory = player.getInventory();
        var item = inventory.getItemById(id);
        return item != null
                        && item.getCount() > 0
                        && item.getOwnerId() == player.getUid()
                        && item.getItemData() != null
                        && item.getItemData().getId() == id
                        && item.getItemId() == id
                        && inventory.getItemByGuid(item.getGuid()) == item
                ? item
                : null;
    }

    public boolean ownsGadget() {
        synchronized (player.getInventory()) {
            var gadget = owned(GADGET_ITEM_ID);
            return gadget != null
                    && gadget.getItemType() == ItemType.ITEM_MATERIAL
                    && gadget.getItemData().getMaterialType() == MaterialType.MATERIAL_WIDGET;
        }
    }

    private ArtifactTransmuterData ready(ArtifactTransmuterRules rules) {
        var config = options();
        long now = clock.getAsLong();
        require(
                config.scheduleId > 0
                        && config.cycleDurationSeconds > 0
                        && config.cycleDurationSeconds <= 366L * 86400
                        && now >= 0
                        && now <= 0xffff_ffffL - config.cycleDurationSeconds,
                Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var data = player.getArtifactTransmuterData();
        require(
                data.getProgressUnits() >= 0
                        && data.getProgressUnits() < rules.requiredUnits()
                        && data.getExtractedCount() >= 0
                        && data.getExtractedCount() <= rules.cycleLimit()
                        && data.getDefinedCounts().entrySet().stream()
                                .allMatch(
                                        x ->
                                                x.getKey() != null
                                                        && x.getKey() > 0
                                                        && x.getValue() != null
                                                        && x.getValue() >= 0
                                                        && x.getValue()
                                                                <= rules.setDefinitionLimit())
                        && data.getCycleEndTime() >= 0
                        && data.getCycleEndTime() <= 0xffff_ffffL,
                Retcode.RET_SVR_ERROR_VALUE);
        require(data.getPending() == null, Retcode.RET_SVR_ERROR_VALUE);
        if (data.getCycleEndTime() <= now || data.getScheduleId() != config.scheduleId) {
            data.setScheduleId(config.scheduleId);
            data.setCycleEndTime(now + config.cycleDurationSeconds);
            data.setExtractedCount(0);
            data.setDefinedCounts(Map.of());
            save.run();
        }
        return data;
    }

    private void notifyData(ArtifactTransmuterData data) {
        var counts =
                new TreeMap<>(data.getDefinedCounts())
                        .entrySet().stream()
                                .map(
                                        x ->
                                                ReliquaryOfferDefineInfo.newBuilder()
                                                        .setDefineId(x.getKey())
                                                        .setCount(x.getValue())
                                                        .build())
                                .toList();
        player.sendPacket(
                new PacketReliquaryOfferDataNotify(
                        ReliquaryOfferDataNotify.newBuilder()
                                .setScheduleId(data.getScheduleId())
                                .setCycleEndTime((int) data.getCycleEndTime())
                                .setProgress(
                                        (int)
                                                (data.getProgressUnits()
                                                        / ArtifactTransmuterRules.SCALE))
                                .setFractionalProgress(
                                        (data.getProgressUnits() % ArtifactTransmuterRules.SCALE)
                                                / (float) ArtifactTransmuterRules.SCALE)
                                .setExtractedCount(data.getExtractedCount())
                                .addAllDefineCountList(counts)
                                .build()));
    }

    /** The current widget resource opens RelicCraftPage from the bag and has no quick-use tag. */
    public int open() {
        synchronized (player.getInventory()) {
            try {
                require(ownsGadget(), Retcode.RET_ITEM_NOT_EXIST_VALUE);
                notifyData(ready(ArtifactTransmuterRules.current()));
                return 0;
            } catch (Rejected denied) {
                return denied.code;
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "Artifact transmuter open failed for UID {}",
                                player.getUid(),
                                failure);
                return Retcode.RET_SVR_ERROR_VALUE;
            }
        }
    }

    public void onLogin() {
        if (ownsGadget()) open();
    }

    public ReliquaryOfferDefineRsp define(ReliquaryOfferDefineReq request) {
        var response =
                ReliquaryOfferDefineRsp.newBuilder()
                        .setScheduleId(request.getScheduleId())
                        .setPurchaseId(request.getPurchaseId());
        synchronized (player.getInventory()) {
            try {
                require(ownsGadget(), Retcode.RET_ITEM_NOT_EXIST_VALUE);
                var rules = ArtifactTransmuterRules.current();
                var data = ready(rules);
                require(
                        request.getScheduleId() == data.getScheduleId(),
                        Retcode.RET_RELIQUARY_OFFER_PURCHASE_VERSION_IDX_ERROR_VALUE);
                var plan = ArtifactDefinition.plan(request, rules);
                require(
                        data.getDefinedCounts().getOrDefault(plan.setId(), 0)
                                < rules.setDefinitionLimit(),
                        Retcode.RET_RELIQUARY_PURCHASE_SUITE_TIMES_OVER_LIMIT_VALUE);
                var cost = owned(rules.elixirItemId());
                require(
                        cost != null
                                && cost.getItemType() == ItemType.ITEM_MATERIAL
                                && cost.getCount() >= plan.cost(),
                        Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE);
                var tab = player.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY);
                require(
                        tab != null && tab.getSize() < tab.getMaxCapacity(),
                        Retcode.RET_ITEM_EXCEED_LIMIT_VALUE);
                var receipt =
                        ArtifactTransmuterReceipt.definition(data, plan, Map.of(cost, plan.cost()));
                data.setPending(receipt);
                save.run();
                int before = cost.getCount();
                if (!player.getInventory().removeItem(cost, plan.cost())
                        || before - cost.getCount() != plan.cost())
                    throw new IllegalStateException("Artifact definition payment was not exact");
                saveItem.accept(cost);
                receipt.paid();
                save.run();
                var output = plan.item();
                if (!player.getInventory().addItem(output)
                        || player.getInventory().getItemByGuid(output.getGuid()) != output
                        || output.getOwnerId() != player.getUid()
                        || output.getCount() != 1)
                    throw new IllegalStateException("Defined artifact delivery was not confirmed");
                saveItem.accept(output);
                var next = new HashMap<>(data.getDefinedCounts());
                next.put(plan.setId(), receipt.getDefinedAfter());
                data.setDefinedCounts(next);
                receipt.applied();
                save.run();
                data.setPending(null);
                try {
                    save.run();
                } catch (RuntimeException uncertain) {
                    data.setPending(receipt);
                    throw uncertain;
                }
                notifyData(data);
            } catch (ArtifactDefinition.Invalid invalid) {
                response.setRetcode(invalid.code);
            } catch (Rejected denied) {
                response.setRetcode(denied.code);
            } catch (RuntimeException failure) {
                response.setRetcode(Retcode.RET_SVR_ERROR_VALUE);
                Grasscutter.getLogger()
                        .error(
                                "Artifact definition failed for UID {}; retained any unresolved"
                                    + " receipt",
                                player.getUid(),
                                failure);
            }
        }
        return response.build();
    }

    public ReliquaryOfferExtractRsp extract(ReliquaryOfferExtractReq req) {
        var response = ReliquaryOfferExtractRsp.newBuilder().setScheduleId(req.getScheduleId());
        synchronized (player.getInventory()) {
            try {
                require(ownsGadget(), Retcode.RET_ITEM_NOT_EXIST_VALUE);
                var rules = ArtifactTransmuterRules.current();
                var data = ready(rules);
                require(
                        req.getScheduleId() == data.getScheduleId(),
                        Retcode.RET_RELIQUARY_OFFER_PURCHASE_VERSION_IDX_ERROR_VALUE);
                require(
                        data.getExtractedCount() < rules.cycleLimit(),
                        Retcode.RET_RELIQUARY_OFFER_VERSION_TIMES_LIMIT_VALUE);
                require(
                        req.getReliquaryGuidListCount() <= rules.maxArtifactBatch()
                                && req.getItemListCount() <= rules.materialUnits().size()
                                && req.getReliquaryGuidListCount() + req.getItemListCount() > 0,
                        Retcode.RET_FAIL_VALUE);
                var costs = new LinkedHashMap<GameItem, Integer>();
                long gained = 0;
                var guids = new HashSet<Long>();
                for (long guid : req.getReliquaryGuidListList()) {
                    var item = player.getInventory().getItemByGuid(guid);
                    require(
                            guids.add(guid)
                                    && item != null
                                    && item.getOwnerId() == player.getUid()
                                    && item.getCount() == 1
                                    && item.getItemData() != null
                                    && item.getItemData().getId() == item.getItemId()
                                    && item.getItemType() == ItemType.ITEM_RELIQUARY
                                    && item.getItemData().getRankLevel() >= rules.minRank()
                                    && item.getLevel() >= rules.minLevel()
                                    && item.getLevel() <= rules.artifactUnits().size()
                                    && item.isDestroyable(),
                            Retcode.RET_FAIL_VALUE);
                    long value = rules.artifactUnits().get(item.getLevel() - 1);
                    require(value > 0, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                    gained = Math.addExact(gained, value);
                    costs.put(item, 1);
                }
                var ids = new HashSet<Integer>();
                for (var submitted : req.getItemListList()) {
                    long count = Integer.toUnsignedLong(submitted.getCount());
                    var rate = rules.materialUnits().get(submitted.getItemId());
                    require(
                            ids.add(submitted.getItemId())
                                    && rate != null
                                    && count > 0
                                    && count <= Integer.MAX_VALUE,
                            Retcode.RET_FAIL_VALUE);
                    var item = owned(submitted.getItemId());
                    require(
                            item != null
                                    && item.getItemType() == ItemType.ITEM_MATERIAL
                                    && item.getCount() >= count,
                            Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE);
                    gained = Math.addExact(gained, Math.multiplyExact(rate, count));
                    costs.put(item, (int) count);
                }
                require(gained > 0, Retcode.RET_FAIL_VALUE);
                long total = Math.addExact(data.getProgressUnits(), gained);
                require(
                        total
                                <= Math.multiplyExact(
                                        rules.requiredUnits(),
                                        rules.cycleLimit() - data.getExtractedCount()),
                        Retcode.RET_RELIQUARY_OFFER_PROGRESS_OVER_LIMIT_VALUE);
                int rewards = Math.toIntExact(total / rules.requiredUnits());
                var reward = owned(rules.elixirItemId());
                int rewardBefore = reward == null ? 0 : reward.getCount();
                if (rewards > 0) {
                    var definition = GameData.getItemDataMap().get(rules.elixirItemId());
                    var tab = player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL);
                    require(
                            player.getInventory().getItemById(rules.elixirItemId()) == null
                                    || reward != null,
                            Retcode.RET_SVR_ERROR_VALUE);
                    require(
                            definition != null
                                    && definition.getItemType() == ItemType.ITEM_MATERIAL
                                    && !definition.isUseOnGain(),
                            Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                    long freed =
                            costs.entrySet().stream()
                                    .filter(
                                            x ->
                                                    x.getKey().getItemType()
                                                                    == ItemType.ITEM_MATERIAL
                                                            && x.getKey().getCount()
                                                                    == x.getValue())
                                    .count();
                    require(
                            tab != null
                                    && tab.getSize() - freed + (reward == null ? 1 : 0)
                                            <= tab.getMaxCapacity()
                                    && (long) rewardBefore + rewards <= definition.getStackLimit(),
                            Retcode.RET_ITEM_EXCEED_LIMIT_VALUE);
                }
                var receipt =
                        new ArtifactTransmuterReceipt(
                                data,
                                total % rules.requiredUnits(),
                                data.getExtractedCount() + rewards,
                                rules.elixirItemId(),
                                rewardBefore,
                                rewards,
                                costs);
                data.setPending(receipt);
                save.run();
                for (var entry : costs.entrySet()) {
                    var item = entry.getKey();
                    int before = item.getCount();
                    if (!player.getInventory().removeItem(item, entry.getValue())
                            || before - item.getCount() != entry.getValue())
                        throw new IllegalStateException(
                                "Artifact extraction payment was not confirmed");
                    saveItem.accept(item);
                }
                receipt.paid();
                save.run();
                if (rewards > 0) {
                    if (!player.getInventory().addItem(rules.elixirItemId(), rewards))
                        throw new IllegalStateException("Artifact extraction reward was rejected");
                    reward = owned(rules.elixirItemId());
                    if (reward == null || reward.getCount() - rewardBefore != rewards)
                        throw new IllegalStateException("Artifact extraction reward was not exact");
                    saveItem.accept(reward);
                }
                data.setProgressUnits(receipt.getProgressAfter());
                data.setExtractedCount(receipt.getExtractedAfter());
                receipt.applied();
                save.run();
                data.setPending(null);
                try {
                    save.run();
                } catch (RuntimeException uncertain) {
                    data.setPending(receipt);
                    throw uncertain;
                }
                notifyData(data);
                response.addAllItemList(req.getItemListList())
                        .addAllReliquaryGuidList(req.getReliquaryGuidListList());
            } catch (Rejected denied) {
                response.setRetcode(denied.code);
            } catch (RuntimeException failure) {
                response.setRetcode(Retcode.RET_SVR_ERROR_VALUE);
                Grasscutter.getLogger()
                        .error(
                                "Artifact extraction failed for UID {}; retained any unresolved"
                                        + " receipt",
                                player.getUid(),
                                failure);
            }
        }
        return response.build();
    }
}

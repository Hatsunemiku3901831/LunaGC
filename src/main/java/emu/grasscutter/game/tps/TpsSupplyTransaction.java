package emu.grasscutter.game.tps;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerSaveStamp;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.*;
import org.bson.json.*;

import java.util.*;
import java.util.function.*;

/** A complete supply has an acknowledged intent and an atomic player-document result. */
public final class TpsSupplyTransaction {
    public record Amount(int pool, int amount) {}

    public record Stored(int uid, Map<Integer, Integer> ammunition, TpsSupplyReceipt receipt) {}

    private final Player player;
    private final Runnable save;
    private final Function<Boolean, Stored> read;
    private final LongSupplier clock;

    public TpsSupplyTransaction(Player player) {
        this(
                player,
                player::saveTpsAmmunition,
                current -> readStored(player, current),
                System::currentTimeMillis);
    }

    public TpsSupplyTransaction(Player player, Runnable save) {
        this(player, save, current -> readStored(player, current), System::currentTimeMillis);
    }

    public TpsSupplyTransaction(
            Player player, Runnable save, Supplier<Stored> read, LongSupplier clock) {
        this(player, save, current -> read.get(), clock);
        Objects.requireNonNull(read);
    }

    private TpsSupplyTransaction(
            Player player, Runnable save, Function<Boolean, Stored> read, LongSupplier clock) {
        this.player = Objects.requireNonNull(player);
        this.save = Objects.requireNonNull(save);
        this.read = Objects.requireNonNull(read);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean grant(EntityAvatar recipient, List<Amount> amounts) {
        synchronized (player.getInventory()) {
            if (player.getPendingTpsSupply() != null
                    || player.getTpsReportTransaction().isPending()
                    || !TpsWeaponSystem.isSupplyRecipient(recipient)
                    || recipient.getPlayer() != player
                    || amounts == null) return false;
            var before = new LinkedHashMap<>(player.getTpsAmmunition());
            if (before.entrySet().stream()
                    .anyMatch(e -> e.getKey() == null || e.getKey() <= 0 || e.getValue() == null))
                return false;
            var after = new LinkedHashMap<>(before);
            var effective = new LinkedHashMap<Integer, Integer>();
            var limits = new LinkedHashMap<Integer, Integer>();
            var gains = new ArrayList<TpsSupplyReceipt.Gain>();
            var counts = new HashMap<Integer, Integer>();
            for (var amount : amounts) {
                if (amount == null || amount.amount() < 0) return false;
                var data = GameData.getTpsAmmunitionDataMap().get(amount.pool());
                if (data == null) return false;
                int initial =
                        counts.computeIfAbsent(
                                amount.pool(), id -> TpsWeaponSystem.getReserve(player, id));
                int limit = TpsWeaponSystem.ammunitionLimit(player, data);
                int next = (int) Math.min((long) initial + amount.amount(), limit);
                if (next > initial) {
                    effective.putIfAbsent(amount.pool(), initial);
                    limits.putIfAbsent(amount.pool(), limit);
                    gains.add(new TpsSupplyReceipt.Gain(amount.pool(), next - initial));
                    counts.put(amount.pool(), next);
                    after.put(amount.pool(), next);
                }
            }
            if (gains.isEmpty()) return true;
            var receipt =
                    new TpsSupplyReceipt(
                            clock.getAsLong(),
                            recipient.getScene().getId(),
                            recipient.getAvatar().getAvatarId(),
                            before,
                            after,
                            effective,
                            limits,
                            gains);
            synchronized (player) { player.setPendingTpsSupply(receipt); }
            finish(receipt);
            var packet = new BasePacket(PacketOpcodes.TpsAmmunitionChangeNotify);
            var notification = TpsAmmunitionChangeNotify.newBuilder();
            for (var gain : receipt.getGains())
                notification.addAmmunitionList(
                        TpsAmmunitionChangeNotifyEntry.newBuilder()
                                .setAmmunitionConfigId(gain.getPool())
                                .setChangeCount(gain.getAmount()));
            packet.setData(notification);
            player.sendPacket(packet);
            return true;
        }
    }

    private void finish(TpsSupplyReceipt receipt) {
        // Publish each coherent phase under Player for save snapshots, but perform acknowledged
        // writes outside it. Inventory serializes the full supply with equipment and ammunition.
        save.run();
        if (receipt.getState() == 1) {
            synchronized (player) {
                player.getTpsAmmunition().clear();
                player.getTpsAmmunition().putAll(receipt.getAfter());
                receipt.applied();
            }
            save.run();
        }
        synchronized (player) { player.setPendingTpsSupply(null); }
        try {
            save.run();
        } catch (RuntimeException uncertain) {
            synchronized (player) { player.setPendingTpsSupply(receipt); }
            throw uncertain;
        }
    }

    public String pending() {
        synchronized (player.getInventory()) {
            var receipt = player.getPendingTpsSupply();
            if (receipt == null) return "没有待处理的 TPS 补给。";
            return "TPS 补给 "
                    + receipt.getOperationId()
                    + "，阶段 "
                    + receipt.getState()
                    + "，弹药池 "
                    + receipt.getAfter().keySet()
                    + "。请先 audit 核对。";
        }
    }

    private TpsSupplyReceipt validatePending(String id) {
        var receipt = player.getPendingTpsSupply();
        require(
                receipt != null && Objects.equals(id, receipt.getOperationId()),
                "TPS 补给操作 ID 不匹配。");
        receipt.validate();
        require(
                player.getTpsAmmunition()
                        .equals(receipt.getState() == 1 ? receipt.getBefore() : receipt.getAfter()),
                "当前弹药与冻结阶段不符，停止恢复。");
        return receipt;
    }

    private TpsSupplyReceipt checked(String id, boolean current) {
        var receipt = validatePending(id);
        var stored = read.apply(current);
        require(stored != null && stored.uid() == player.getUid(), "玩家存档不存在或归属不符。");
        var disk = stored.receipt();
        if (disk != null) {
            disk.validate();
            require(receipt.samePlan(disk), "存档中的 TPS 补给计划不同。");
            require(
                    stored.ammunition()
                            .equals(
                                    disk.getState() == 1
                                            ? receipt.getBefore()
                                            : receipt.getAfter()),
                    "存档弹药与记录阶段不符。");
            require(!(receipt.getState() == 1 && disk.getState() == 2), "当前补给阶段落后于存档，请重新登录后核对。");
        } else
            require(
                    stored.ammunition()
                            .equals(
                                    receipt.getState() == 1
                                            ? receipt.getBefore()
                                            : receipt.getAfter()),
                    "缺少记录且存档弹药与冻结阶段不同。");
        return receipt;
    }

    public String audit(String id) {
        synchronized (player.getInventory()) {
            var receipt = checked(id, false);
            return "TPS 补给核对通过："
                    + receipt.getOperationId()
                    + "，阶段 "
                    + receipt.getState()
                    + (receipt.getState() == 1 ? "；可 resume 或 cancel。" : "；可 resume 确认完整结果。");
        }
    }

    public String resume(String id) {
        synchronized (player.getInventory()) {
            validatePending(id);
            player.resolvePendingSave();
            var receipt = checked(id, true);
            finish(receipt);
            TpsWeaponSystem.sendSceneAmmunition(player);
            return "TPS 补给已完成：" + id + "。";
        }
    }

    public String cancel(String id) {
        synchronized (player.getInventory()) {
            validatePending(id);
            player.resolvePendingSave();
            var receipt = checked(id, true);
            require(receipt.getState() == 1, "弹药已进入完整结果阶段，不能取消，请 resume。");
            synchronized (player) { player.setPendingTpsSupply(null); }
            try {
                save.run();
            } catch (RuntimeException uncertain) {
                synchronized (player) { player.setPendingTpsSupply(receipt); }
                throw uncertain;
            }
            TpsWeaponSystem.sendSceneAmmunition(player);
            return "TPS 补给已取消：" + id + "。";
        }
    }

    private static Stored readStored(Player player, boolean current) {
        var filter =
                current
                        ? PlayerSaveStamp.condition(player.getUid(), player.getSaveStamp())
                        : new Document("_id", player.getUid());
        var raw =
                DatabaseManager.getGameDatastore()
                        .getMapper()
                        .getCollection(Player.class)
                        .withDocumentClass(BsonDocument.class)
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .find(filter)
                        .projection(
                                new BsonDocument("tpsAmmunition", new BsonInt32(1))
                                        .append("pendingTpsSupply", new BsonInt32(1)))
                        .first();
        require(raw != null, "玩家存档不存在或版本已变化，请重新核对。");
        var amounts = new LinkedHashMap<Integer, Integer>();
        if (raw.containsKey("tpsAmmunition"))
            for (var entry : raw.getDocument("tpsAmmunition").entrySet()) {
                var value = entry.getValue();
                require(value.isInt32(), "存档弹药不是有效整数。");
                amounts.put(Integer.parseInt(entry.getKey()), value.asInt32().getValue());
            }
        var receipt =
                raw.containsKey("pendingTpsSupply") && !raw.isNull("pendingTpsSupply")
                        ? JsonUtils.decode(
                                raw.getDocument("pendingTpsSupply")
                                        .toJson(
                                                JsonWriterSettings.builder()
                                                        .outputMode(JsonMode.RELAXED)
                                                        .build()),
                                TpsSupplyReceipt.class)
                        : null;
        return new Stored(raw.getInt32("_id").getValue(), amounts, receipt);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}

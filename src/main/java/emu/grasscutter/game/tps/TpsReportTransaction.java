package emu.grasscutter.game.tps;

import static emu.grasscutter.game.tps.TpsReportReceipt.require;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;

import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerSaveStamp;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.bson.*;
import org.bson.json.*;

import java.util.*;
import java.util.function.*;

/**
 * Cost/reload results use one acknowledged atomic write. Unconfirmed attempts belong to the active
 * Player; a cold read sees either the previous state or the complete committed result.
 */
public final class TpsReportTransaction {
    public record Stored(
            int uid,
            Map<Integer, Integer> ammunition,
            Map<Integer, Integer> magazines,
            TpsReportReceipt receipt) {}

    private record Attempt(TpsReportReceipt receipt, TpsReportReceipt previous) {}

    private final Player player;
    private final Runnable save;
    private final Function<Boolean, Stored> read;
    private final LongSupplier clock;
    private Attempt pending;

    public TpsReportTransaction(Player player) {
        this(
                player,
                player::saveTpsAmmunition,
                current -> readStored(player, current),
                System::currentTimeMillis);
    }

    public TpsReportTransaction(Player player, Runnable save) {
        this(player, save, current -> readStored(player, current), System::currentTimeMillis);
    }

    public TpsReportTransaction(
            Player player, Runnable save, Supplier<Stored> read, LongSupplier clock) {
        this(player, save, current -> read.get(), clock);
        Objects.requireNonNull(read);
    }

    private TpsReportTransaction(
            Player player, Runnable save, Function<Boolean, Stored> read, LongSupplier clock) {
        this.player = Objects.requireNonNull(player);
        this.save = Objects.requireNonNull(save);
        this.read = Objects.requireNonNull(read);
        this.clock = Objects.requireNonNull(clock);
    }

    boolean commit(
            EntityAvatar avatar,
            AbilityInvokeEntry invoke,
            Map<Integer, Integer> after,
            Map<Integer, Integer> magazinesAfter) {
        synchronized (player) {
            if (pending != null || player.getPendingTpsSupply() != null) return false;
            if (after.equals(player.getTpsAmmunition())
                    && magazinesAfter.equals(player.getTpsMagazineAmmunition())) return true;
            var receipt =
                    new TpsReportReceipt(
                            player, avatar, invoke, after, magazinesAfter, clock.getAsLong());
            pending = new Attempt(receipt, player.getLastTpsReport());
            install(receipt.getAfter(), receipt.getMagazinesAfter(), receipt);
            // On an ambiguous write, keep the exact candidate pinned until storage is checked.
            save.run();
            pending = null;
            return true;
        }
    }

    private void install(
            Map<Integer, Integer> ammunition,
            Map<Integer, Integer> magazines,
            TpsReportReceipt receipt) {
        player.getTpsAmmunition().clear();
        player.getTpsAmmunition().putAll(ammunition);
        player.getTpsMagazineAmmunition().clear();
        player.getTpsMagazineAmmunition().putAll(magazines);
        player.setLastTpsReport(receipt);
    }

    public boolean isPending() {
        synchronized (player) {
            return pending != null;
        }
    }

    public String pending() {
        synchronized (player) {
            return pending == null
                    ? "没有待核对的 TPS 射击／换弹。"
                    : "TPS 射击／换弹 " + pending.receipt.getOperationId() + " 的写入未确认，请先 audit。";
        }
    }

    private TpsReportReceipt validatePending(String id) {
        require(
                pending != null && Objects.equals(pending.receipt.getOperationId(), id),
                "TPS 上报操作 ID 不符或没有待处理记录。");
        var receipt = pending.receipt;
        require(player.getUid() == receipt.getUid(), "TPS 上报玩家身份已变化。");
        receipt.validate();
        require(
                player.getPendingTpsSupply() == null
                        && receipt.samePlan(player.getLastTpsReport())
                        && player.getTpsAmmunition().equals(receipt.getAfter())
                        && player.getTpsMagazineAmmunition().equals(receipt.getMagazinesAfter()),
                "当前 TPS 上报状态与冻结结果不同。");
        return receipt;
    }

    private boolean checked(String id, boolean current) {
        var receipt = validatePending(id);
        var stored = read.apply(current);
        require(stored != null && stored.uid() == player.getUid(), "TPS 上报存档归属不符。");
        if (stored.receipt() != null) stored.receipt().validate();
        if (stored.receipt() != null && stored.receipt().getOperationId().equals(id)) {
            require(
                    receipt.samePlan(stored.receipt())
                            && stored.ammunition().equals(receipt.getAfter())
                            && stored.magazines().equals(receipt.getMagazinesAfter()),
                    "存档中的 TPS 上报结果不完整或已改变。");
            return true;
        }
        require(
                Objects.equals(
                                receipt.getPreviousOperationId(),
                                stored.receipt() == null ? null : stored.receipt().getOperationId())
                        && (pending.previous == null
                                ? stored.receipt() == null
                                : pending.previous.samePlan(stored.receipt()))
                        && stored.ammunition().equals(receipt.getBefore())
                        && stored.magazines().equals(receipt.getMagazinesBefore()),
                "存档中的 TPS 上报已变化，不能覆盖。");
        return false;
    }

    public String audit(String id) {
        synchronized (player) {
            return checked(id, false)
                    ? "TPS 上报已完整写入；resume 可确认并同步当前数量。"
                    : "TPS 上报尚未写入；可 resume 原结果或 cancel。";
        }
    }

    public String resume(String id) {
        synchronized (player) {
            validatePending(id);
            player.resolvePendingSave();
            if (!checked(id, true)) save.run();
            pending = null;
            TpsWeaponSystem.sendRecoveredAmmunition(player);
            return "TPS 射击／换弹已确认：" + id + "。";
        }
    }

    public String cancel(String id) {
        synchronized (player) {
            validatePending(id);
            player.resolvePendingSave();
            require(!checked(id, true), "TPS 上报已完整写入，不能取消，请 resume。");
            var attempt = pending;
            var receipt = attempt.receipt;
            install(receipt.getBefore(), receipt.getMagazinesBefore(), attempt.previous);
            try {
                save.run();
            } catch (RuntimeException uncertain) {
                install(receipt.getAfter(), receipt.getMagazinesAfter(), receipt);
                throw uncertain;
            }
            pending = null;
            TpsWeaponSystem.sendRecoveredAmmunition(player);
            return "TPS 射击／换弹已取消：" + id + "。";
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
                                        .append("tpsMagazineAmmunition", new BsonInt32(1))
                                        .append("lastTpsReport", new BsonInt32(1)))
                        .first();
        require(raw != null, "玩家存档不存在或版本已变化，请重新核对。");
        var receipt =
                raw.containsKey("lastTpsReport") && !raw.isNull("lastTpsReport")
                        ? JsonUtils.decode(
                                raw.getDocument("lastTpsReport")
                                        .toJson(
                                                JsonWriterSettings.builder()
                                                        .outputMode(JsonMode.RELAXED)
                                                        .build()),
                                TpsReportReceipt.class)
                        : null;
        return new Stored(
                raw.getInt32("_id").getValue(),
                amounts(raw, "tpsAmmunition"),
                amounts(raw, "tpsMagazineAmmunition"),
                receipt);
    }

    private static Map<Integer, Integer> amounts(BsonDocument raw, String field) {
        var result = new LinkedHashMap<Integer, Integer>();
        if (raw.containsKey(field))
            for (var entry : raw.getDocument(field).entrySet()) {
                require(entry.getValue().isInt32(), "TPS 存档数量不是有效整数。");
                result.put(Integer.parseInt(entry.getKey()), entry.getValue().asInt32().getValue());
            }
        return result;
    }
}

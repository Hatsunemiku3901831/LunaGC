package emu.grasscutter.game.expedition;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto._AvatarExpeditionBasicInfoOuterClass._AvatarExpeditionBasicInfo;
import emu.grasscutter.net.proto._AvatarExpeditionRewardInfoOuterClass._AvatarExpeditionRewardInfo;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Completed trip selection and acknowledged delivery, including claim-all. */
public final class ExpeditionClaims {
    private ExpeditionClaims() {}

    public record Result(int retcode, List<_AvatarExpeditionRewardInfo> rewards) {}

    private record Trip(
            long guid,
            ExpeditionInfo info,
            List<ItemParamData> items,
            List<ItemParamData> extras) {}

    public static boolean finished(ExpeditionInfo info, int now) {
        return info != null
                && (info.getState() == 1 || info.getState() == 2)
                && info.getHourTime() > 0
                && info.getStartTime() > 0
                && info.getShortenRate() >= 0
                && info.getShortenRate() < 10000
                && (long) now - info.getStartTime()
                        >= ((long) info.getHourTime() * 3600 * (10000 - info.getShortenRate())
                                        + 9999)
                                / 10000;
    }

    /** Caller holds the inventory monitor while serializing the current queue. */
    public static boolean refresh(Player player, int now) {
        boolean changed = false;
        for (var entry : player.getExpeditionInfo().entrySet()) {
            var info = entry.getValue();
            changed |= ExpeditionBonuses.bind(player, entry.getKey(), info);
            if (info.getState() == 1 && finished(info, now)) {
                info.setState(2);
                changed = true;
            }
        }
        return changed;
    }

    public static Result take(
            Player player, ExpeditionSystem system, long guid, boolean all, int now) {
        if (player == null || system == null) return error(Retcode.RET_SVR_ERROR_VALUE);
        synchronized (player.getInventory()) {
            var trips = new ArrayList<Trip>();
            var entries =
                    player.getExpeditionInfo().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .toList();
            for (var entry : entries) {
                var info = entry.getValue();
                if (!all && entry.getKey() != guid) continue;
                ExpeditionBonuses.bind(player, entry.getKey(), info);
                if (!finished(info, now)) continue;
                if (info.getRewardState() != 0)
                    return error(
                            info.getRewardState() == 2
                                    ? Retcode.RET_REWARD_HAS_TAKEN_VALUE
                                    : Retcode.RET_SVR_ERROR_VALUE);
                var definitions = system.getResolvedRewardDataList(info.getExpId());
                if (definitions == null) return error(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                var matches =
                        definitions.stream()
                                .filter(Objects::nonNull)
                                .filter(d -> d.getHourTime() == info.getHourTime())
                                .toList();
                if (matches.size() != 1
                        || matches.get(0).getExpeditionRewardData() == null
                        || matches.get(0).getExpeditionRewardData().isEmpty())
                    return error(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                var items = new ArrayList<ItemParamData>();
                for (var row : matches.get(0).getExpeditionRewardData()) {
                    if (row == null
                            || row.getItemId() <= 0
                            || row.getMinCount() <= 0
                            || row.getMaxCount() < row.getMinCount())
                        return error(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                    int count =
                            (int)
                                    ThreadLocalRandom.current()
                                            .nextLong(
                                                    row.getMinCount(),
                                                    (long) row.getMaxCount() + 1);
                    items.add(new ItemParamData(row.getItemId(), count));
                }
                if (info.getExtraRate() < 0
                        || info.getExtraRate() > 10000
                        || info.getExtraProbability() < 0
                        || info.getExtraProbability() > 10000)
                    return error(Retcode.RET_NOT_FOUND_CONFIG_VALUE);
                var extras = new ArrayList<ItemParamData>();
                if (info.getExtraRate() > 0
                        && ThreadLocalRandom.current().nextInt(10000)
                                < info.getExtraProbability()) {
                    for (var item : items) {
                        int extra = (int) ((long) item.getCount() * info.getExtraRate() / 10000);
                        if (extra > 0) extras.add(new ItemParamData(item.getId(), extra));
                    }
                }
                trips.add(new Trip(entry.getKey(), info, items, extras));
            }
            if (trips.isEmpty()) return error(Retcode.RET_FAIL_VALUE);
            var items =
                    trips.stream()
                            .flatMap(
                                    trip ->
                                            java.util.stream.Stream.concat(
                                                    trip.items().stream(), trip.extras().stream()))
                            .toList();
            int validation = InvestigationRewardDelivery.validate(player, items);
            if (validation != 0) return error(validation);
            try {
                // Reservation is acknowledged before any inventory output can be sent or stored.
                trips.forEach(trip -> trip.info().setRewardState(1));
                DatabaseHelper.saveGameSync(player);
                InvestigationRewardDelivery.grant(player, items, ActionReason.ExpeditionReward);
                trips.forEach(trip -> trip.info().setRewardState(2));
                for (var trip : trips)
                    InvestigationHandbook.trigger(
                            player, "TRIGGER_AVATAR_EXPEDITION", trip.info().getExpId(), 1);
                DatabaseHelper.saveGameSync(player);
                var rewards = trips.stream().map(ExpeditionClaims::rewardInfo).toList();
                trips.forEach(trip -> player.getExpeditionInfo().remove(trip.guid(), trip.info()));
                DatabaseHelper.saveGameSync(player);
                return new Result(0, rewards);
            } catch (RuntimeException failure) {
                // A reservation or partial output may be durable. Never reset it or regrant
                // blindly.
                Grasscutter.getLogger()
                        .error(
                                "Expedition reward needs reconciliation: UID {}",
                                player.getUid(),
                                failure);
                return error(Retcode.RET_SVR_ERROR_VALUE);
            }
        }
    }

    private static _AvatarExpeditionRewardInfo rewardInfo(Trip trip) {
        var result =
                _AvatarExpeditionRewardInfo.newBuilder()
                        .setBasicInfo(
                                _AvatarExpeditionBasicInfo.newBuilder()
                                        .setAvatarGuid(trip.guid())
                                        .setExpId(trip.info().getExpId())
                                        .setHourTime(trip.info().getHourTime())
                                        .setShortenRatio(trip.info().toProto().getShortenRatio()));
        trip.items()
                .forEach(
                        item ->
                                result.addItemList(
                                        ItemParam.newBuilder()
                                                .setItemId(item.getId())
                                                .setCount(item.getCount())));
        trip.extras()
                .forEach(
                        item ->
                                result.addExtraItemList(
                                        ItemParam.newBuilder()
                                                .setItemId(item.getId())
                                                .setCount(item.getCount())));
        return result.build();
    }

    private static Result error(int code) {
        return new Result(code, List.of());
    }
}

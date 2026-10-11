package emu.grasscutter.game.activity.aster;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;
import java.util.function.*;

/**
 * Recovery drops are fixed, flat virtual rewards; activity energy stays in this replay's document.
 */
public final class AsterMidRewards {
    private AsterMidRewards() {}

    public record Reward(
            int resin, int monsterLevel, int adventureExp, int companionshipExp, int energy) {}

    public static Reward reward(int difficulty) {
        for (int id : List.of(102, 105, 109)) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null
                    || item.getItemType() != emu.grasscutter.game.inventory.ItemType.ITEM_VIRTUAL)
                return null;
        }
        var meta = GameData.getAsterMidDifficultyDataMap().get(difficulty);
        var drop = meta == null ? null : GameData.getDropTableDataMap().get(meta.getDropId());
        if (meta == null
                || meta.getResin() != 20
                || meta.getMonsterLevel() <= 0
                || drop == null
                || drop.getRandomType() != 1
                || drop.getNodeType() != 1
                || drop.isFallToGround()
                || drop.getDropVec() == null
                || drop.getDropVec().size() != 3) return null;
        var counts = new HashMap<Integer, Integer>();
        for (var item : drop.getDropVec()) {
            if (item == null
                    || !Set.of(102, 105, 109).contains(item.getId())
                    || item.getWeight() != 10000
                    || item.getCountRange() == null
                    || !item.getCountRange().matches("[1-9][0-9]*")) return null;
            int count;
            try {
                count = Integer.parseInt(item.getCountRange());
            } catch (NumberFormatException failure) {
                return null;
            }
            if (counts.putIfAbsent(item.getId(), count) != null) return null;
        }
        if (counts.size() != 3) return null;
        return new Reward(
                meta.getResin(),
                meta.getMonsterLevel(),
                counts.get(102),
                counts.get(105),
                counts.get(109));
    }

    public static int take(
            PlayerActivityData data,
            ActivityConfigItem config,
            int rank,
            long now,
            int campId,
            int difficulty,
            BooleanSupplier debit,
            Consumer<Reward> grant,
            Runnable save) {
        if (!AsterSchedule.phaseOpen(data, config, rank, now, 2))
            return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        var reward = reward(difficulty);
        if (reward == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        synchronized (data) {
            var progress = AsterSchedule.progress(data);
            if (!progress.midCampAvailable(campId)
                    || progress.midReward(campId) != null
                    || !Objects.equals(progress.midOffers().get(campId), difficulty))
                return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            if (progress.hasPendingReward())
                return Retcode.RET_SVR_ERROR_VALUE;
            if ((long) progress.credit() + reward.energy() > Integer.MAX_VALUE
                    || progress.midCollectCount() == Integer.MAX_VALUE)
                return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
            for (int id = 1200110; id <= 1200115; id++)
                if (data.getWatcherInfoMap().get(id) == null)
                    return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            String original = data.getDetail();
            progress.beginMidClaim(campId);
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException failure) {
                data.setDetailJson(original);
                throw failure;
            }
            String reservation = data.getDetail();
            try {
                if (!debit.getAsBoolean()) {
                    progress.cancelMidClaim();
                    data.setDetail(progress);
                    try {
                        save.run();
                    } catch (RuntimeException failure) {
                        data.setDetailJson(reservation);
                        throw failure;
                    }
                    return Retcode.RET_RESIN_NOT_ENOUGH_VALUE;
                }
                grant.accept(reward);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "Aster recovery claim needs reconciliation: uid {} schedule {} camp"
                                        + " {}",
                                data.getUid(),
                                data.getScheduleId(),
                                campId,
                                failure);
                throw failure;
            }
            var oldWatcherCounts = new HashMap<Integer, Integer>();
            progress.finishMidClaim(reward.energy());
            for (int id = 1200110; id <= 1200115; id++) {
                var watcher = data.getWatcherInfoMap().get(id);
                oldWatcherCounts.put(id, watcher.getCurProgress());
                watcher.setCurProgress(
                        Math.min(watcher.getTotalProgress(), progress.midCollectCount()));
            }
            data.setDetail(progress);
            try {
                save.run();
            } catch (RuntimeException failure) {
                oldWatcherCounts.forEach(
                        (id, count) ->
                                data.getWatcherInfoMap().get(id.intValue()).setCurProgress(count));
                data.setDetailJson(reservation);
                Grasscutter.getLogger()
                        .error(
                                "Aster recovery acknowledgement failed: uid {} schedule {} camp {}",
                                data.getUid(),
                                data.getScheduleId(),
                                campId,
                                failure);
                throw failure;
            }
            return 0;
        }
    }

    /**
     * The shared point belongs to the host; resin, currency, counts and receipts belong to the
     * claimant. Both reservations must be acknowledged before any reward write.
     */
    public static int takeShared(
            PlayerActivityData owner,
            PlayerActivityData claimant,
            ActivityConfigItem config,
            int rank,
            long now,
            int campId,
            String token,
            BooleanSupplier debit,
            Consumer<Reward> grant,
            Runnable saveOwner,
            Runnable saveClaimant) {
        if (owner == null
                || claimant == null
                || owner != claimant && owner.getUid() == claimant.getUid())
            return Retcode.RET_FORBIDDEN_VALUE;
        if (!AsterSchedule.phaseOpen(owner, config, rank, now, 2)
                || !AsterSchedule.phaseOpen(claimant, config, rank, now, 2))
            return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        // Use one lock order even when another world is processing an activity operation.
        var first = owner.getUid() <= claimant.getUid() ? owner : claimant;
        var second = first == owner ? claimant : owner;
        synchronized (first) {
            synchronized (second) {
                return takeSharedLocked(
                        owner, claimant, now, campId, token, debit, grant, saveOwner, saveClaimant);
            }
        }
    }

    private static int takeSharedLocked(
            PlayerActivityData owner,
            PlayerActivityData claimant,
            long now,
            int campId,
            String token,
            BooleanSupplier debit,
            Consumer<Reward> grant,
            Runnable saveOwner,
            Runnable saveClaimant) {
        boolean same = owner == claimant;
        var hostProgress = AsterSchedule.progress(owner);
        var progress = same ? hostProgress : AsterSchedule.progress(claimant);
        var offer = hostProgress.midReward(campId);
        if (offer == null
                || !Objects.equals(offer.token(), token)
                || !offer.canClaim(claimant.getUid(), now)
                || progress.hasMidClaim(token)) return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
        if (progress.hasPendingReward())
            return Retcode.RET_SVR_ERROR_VALUE;
        var reward = reward(offer.difficulty());
        if (reward == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        if ((long) progress.credit() + reward.energy() > Integer.MAX_VALUE
                || progress.midCollectCount() == Integer.MAX_VALUE)
            return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        for (int id = 1200110; id <= 1200115; id++)
            if (claimant.getWatcherInfoMap().get(id) == null)
                return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;

        String hostBefore = owner.getDetail(), before = claimant.getDetail();
        hostProgress.reserveMidReward(campId, claimant.getUid());
        progress.beginSharedMidClaim(campId, token);
        owner.setDetail(hostProgress);
        if (!same) claimant.setDetail(progress);
        String hostReservation = owner.getDetail(), reservation = claimant.getDetail();
        try {
            saveOwner.run();
        } catch (RuntimeException failure) {
            owner.setDetailJson(hostBefore);
            if (!same) claimant.setDetailJson(before);
            throw failure;
        }
        if (!same) {
            try {
                saveClaimant.run();
            } catch (RuntimeException failure) {
                // The host reservation is already durable. Do not reopen an uncertain guest write.
                claimant.setDetailJson(reservation);
                reconciliation(owner, claimant, campId, failure);
                throw failure;
            }
        }
        try {
            if (!debit.getAsBoolean()) {
                hostProgress.cancelMidReward(campId, claimant.getUid());
                progress.cancelMidClaim();
                owner.setDetail(hostProgress);
                if (!same) claimant.setDetail(progress);
                try {
                    if (!same) saveClaimant.run();
                    saveOwner.run();
                } catch (RuntimeException failure) {
                    owner.setDetailJson(hostReservation);
                    if (!same) claimant.setDetailJson(reservation);
                    throw failure;
                }
                return Retcode.RET_RESIN_NOT_ENOUGH_VALUE;
            }
            grant.accept(reward);
        } catch (RuntimeException failure) {
            reconciliation(owner, claimant, campId, failure);
            throw failure;
        }

        var oldWatcherCounts = new HashMap<Integer, Integer>();
        progress.finishSharedMidClaim(reward.energy());
        hostProgress.acknowledgeMidReward(campId, claimant.getUid(), now);
        for (int id = 1200110; id <= 1200115; id++) {
            var watcher = claimant.getWatcherInfoMap().get(id);
            oldWatcherCounts.put(id, watcher.getCurProgress());
            watcher.setCurProgress(
                    Math.min(watcher.getTotalProgress(), progress.midCollectCount()));
        }
        owner.setDetail(hostProgress);
        if (!same) claimant.setDetail(progress);
        try {
            if (!same) saveClaimant.run();
            else saveOwner.run();
        } catch (RuntimeException failure) {
            oldWatcherCounts.forEach(
                    (id, count) ->
                            claimant.getWatcherInfoMap().get(id.intValue()).setCurProgress(count));
            owner.setDetailJson(hostReservation);
            if (!same) claimant.setDetailJson(reservation);
            reconciliation(owner, claimant, campId, failure);
            throw failure;
        }
        if (!same) {
            try {
                saveOwner.run();
            } catch (RuntimeException failure) {
                // The personal receipt is confirmed. Keep it, and retain the shared reservation.
                owner.setDetailJson(hostReservation);
                reconciliation(owner, claimant, campId, failure);
                throw failure;
            }
        }
        return 0;
    }

    private static void reconciliation(
            PlayerActivityData owner,
            PlayerActivityData claimant,
            int campId,
            RuntimeException failure) {
        Grasscutter.getLogger()
                .error(
                        "Shared Aster recovery needs reconciliation: host {} uid {} schedule {}"
                            + " camp {}",
                        owner.getUid(),
                        claimant.getUid(),
                        owner.getScheduleId(),
                        campId,
                        failure);
    }

    /**
     * Resin is acknowledged before the EXP writes. A failed write leaves the activity claim
     * reserved.
     */
    public static void grant(Player player, Reward reward) {
        for (int id : List.of(102, 105, 109)) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null
                    || item.getItemType() != emu.grasscutter.game.inventory.ItemType.ITEM_VIRTUAL)
                throw new IllegalStateException("Aster virtual reward resource missing: " + id);
        }
        DatabaseHelper.saveGameSync(player);
        var inventory = player.getInventory();
        synchronized (inventory) {
            if (!inventory.addItem(102, reward.adventureExp(), ActionReason.TakeGeneralReward)
                    || !inventory.addItem(
                            105, reward.companionshipExp(), ActionReason.TakeGeneralReward))
                throw new IllegalStateException("Aster EXP reward was not granted");
            for (var avatar : player.getTeamManager().getActiveTeam())
                DatabaseHelper.saveGameSync(avatar.getAvatar());
            DatabaseHelper.saveGameSync(player);
        }
    }
}

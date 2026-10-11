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
                    || !Objects.equals(progress.midOffers().get(campId), difficulty))
                return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            if (progress.pendingMidClaim() != 0 || progress.pendingWatcherReward() != 0)
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

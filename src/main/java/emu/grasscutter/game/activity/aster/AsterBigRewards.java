package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;
import java.util.function.*;

/** Personal original world-level drops, reserved in both documents before resin or EXP changes. */
public final class AsterBigRewards {
    public static final int GROUP = 133001413, CONFIG = 413124, GADGET = 70330041, RESIN = 40;

    private AsterBigRewards() {}

    /** Ordinary fields keep archived activity documents compatible with the project's Gson 2.9. */
    public static final class Reward {
        private int worldLevel, dropId, adventureExp, companionshipExp, essence;

        Reward(int level, int drop, int exp, int friendship, int essence) {
            worldLevel = level;
            dropId = drop;
            adventureExp = exp;
            companionshipExp = friendship;
            this.essence = essence;
        }

        public int worldLevel() {
            return worldLevel;
        }

        public int dropId() {
            return dropId;
        }

        public int adventureExp() {
            return adventureExp;
        }

        public int companionshipExp() {
            return companionshipExp;
        }

        public int essence() {
            return essence;
        }

        boolean isValid() {
            return worldLevel >= 0
                    && dropId >= 210020000
                    && dropId <= 210020080
                    && adventureExp > 0
                    && companionshipExp > 0
                    && essence > 0;
        }

        boolean matches(Reward other) {
            return other != null
                    && worldLevel == other.worldLevel
                    && dropId == other.dropId
                    && adventureExp == other.adventureExp
                    && companionshipExp == other.companionshipExp
                    && essence == other.essence;
        }
    }

    public static Reward reward(int worldLevel) {
        if (worldLevel < 0) return null;
        for (int id : List.of(102, 105, 110)) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null
                    || item.getItemType() != emu.grasscutter.game.inventory.ItemType.ITEM_VIRTUAL)
                return null;
        }
        var entry = GameData.getMpPlayGroupDataMap().get(2);
        if (entry == null
                || entry.getRewardGroupId() != GROUP
                || entry.getRewardConfigId() != CONFIG
                || entry.getResinCost() != RESIN) return null;
        final int dropId;
        try {
            dropId = entry.rewardForWorldLevel(worldLevel).getDropId();
        } catch (IllegalStateException invalid) {
            return null;
        }
        var drop = GameData.getDropTableDataMap().get(dropId);
        if (drop == null
                || drop.getRandomType() != 1
                || drop.getNodeType() != 1
                || drop.isFallToGround()
                || drop.getDropVec() == null
                || drop.getDropVec().size() != 3
                || drop.getEverydayLimit() != 0
                || drop.getHistoryLimit() != 0
                || drop.getActivityLimit() != 0) return null;
        var amounts = new HashMap<Integer, Integer>();
        for (var row : drop.getDropVec()) {
            if (row == null
                    || !Set.of(102, 105, 110).contains(row.getId())
                    || row.getWeight() != 10000
                    || row.getCountRange() == null
                    || !row.getCountRange().matches("[1-9][0-9]*")) return null;
            try {
                if (amounts.putIfAbsent(row.getId(), Integer.parseInt(row.getCountRange())) != null)
                    return null;
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        if (amounts.size() != 3) return null;
        var result =
                new Reward(
                        worldLevel, dropId, amounts.get(102), amounts.get(105), amounts.get(110));
        return result.isValid() ? result : null;
    }

    public static int takeShared(
            PlayerActivityData owner,
            PlayerActivityData claimant,
            ActivityConfigItem config,
            int rank,
            long now,
            String token,
            BooleanSupplier debit,
            Consumer<Reward> grant,
            Runnable saveOwner,
            Runnable saveClaimant) {
        if (!AsterSchedule.phaseOpen(claimant, config, rank, now, 3)
                || owner == null
                || owner.getActivityId() != AsterSchedule.ACTIVITY_ID
                || owner.getScheduleId() != config.getScheduleId())
            return Retcode.RET_ACTIVITY_CLOSE_VALUE;
        if (owner.getUid() <= 0
                || claimant.getUid() <= 0
                || owner != claimant && owner.getUid() == claimant.getUid())
            return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
        var first = owner.getUid() <= claimant.getUid() ? owner : claimant;
        var second = first == owner ? claimant : owner;
        synchronized (first) {
            synchronized (second) {
                return takeLocked(owner, claimant, token, debit, grant, saveOwner, saveClaimant);
            }
        }
    }

    private static int takeLocked(
            PlayerActivityData owner,
            PlayerActivityData claimant,
            String token,
            BooleanSupplier debit,
            Consumer<Reward> grant,
            Runnable saveOwner,
            Runnable saveClaimant) {
        boolean same = owner == claimant;
        var host = AsterSchedule.progress(owner);
        var player = same ? host : AsterSchedule.progress(claimant);
        var point = host.bigReward();
        if (point == null
                || !Objects.equals(point.token(), token)
                || !point.canClaim(claimant.getUid())
                || player.hasBigClaim(token)) return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
        if (player.hasPendingReward()) return Retcode.RET_SVR_ERROR_VALUE;
        var reward = point.rewards().get(claimant.getUid());
        if (reward == null || !reward.matches(reward(reward.worldLevel())))
            return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        if ((long) player.bigCredit() + reward.essence() > Integer.MAX_VALUE
                || player.bigCollectCount() == Integer.MAX_VALUE)
            return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        var milestone = AsterMilestones.prepare(claimant, AsterMilestones.LARGE);
        if (milestone == null) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        String hostBefore = owner.getDetail(), before = claimant.getDetail();
        host.reserveBigReward(claimant.getUid());
        player.beginBigClaim(token);
        owner.setDetail(host);
        if (!same) claimant.setDetail(player);
        String hostReserved = owner.getDetail(), reserved = claimant.getDetail();
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
                claimant.setDetailJson(reserved);
                throw failure;
            }
        }
        if (!debit.getAsBoolean()) {
            host.cancelBigReward(claimant.getUid());
            player.cancelBigClaim();
            owner.setDetail(host);
            if (!same) claimant.setDetail(player);
            try {
                if (!same) saveClaimant.run();
                saveOwner.run();
            } catch (RuntimeException failure) {
                owner.setDetailJson(hostReserved);
                if (!same) claimant.setDetailJson(reserved);
                throw failure;
            }
            return Retcode.RET_RESIN_NOT_ENOUGH_VALUE;
        }
        grant.accept(reward);
        player.finishBigClaim(reward.essence());
        host.acknowledgeBigReward(claimant.getUid());
        milestone.apply(player.bigCollectCount());
        owner.setDetail(host);
        if (!same) claimant.setDetail(player);
        try {
            if (!same) saveClaimant.run();
            else saveOwner.run();
        } catch (RuntimeException failure) {
            milestone.rollback();
            owner.setDetailJson(hostReserved);
            if (!same) claimant.setDetailJson(reserved);
            throw failure;
        }
        if (!same) {
            try {
                saveOwner.run();
            } catch (RuntimeException failure) {
                owner.setDetailJson(hostReserved);
                throw failure;
            }
        }
        return 0;
    }
}

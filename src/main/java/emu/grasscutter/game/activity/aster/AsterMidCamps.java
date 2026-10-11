package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AsterMidCampInfoOuterClass.AsterMidCampInfo;
import emu.grasscutter.utils.JsonUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Group coordinates and seal/reward IDs extracted from the pinned activity scripts. */
public final class AsterMidCamps {
    private AsterMidCamps() {}

    public static final class Camp {
        private int campId, groupId, sealConfigId, sealGadgetId, rewardConfigId, emptySuite;
        private Position position, groupPosition;
        private Map<Integer, Position> battleGroups;

        public int campId() {
            return campId;
        }

        public int groupId() {
            return groupId;
        }

        public int sealConfigId() {
            return sealConfigId;
        }

        public int sealGadgetId() {
            return sealGadgetId;
        }

        public int emptySuite() {
            return emptySuite;
        }

        public int rewardConfigId() {
            return rewardConfigId;
        }

        public Position position() {
            return new Position(position);
        }

        public Position groupPosition() {
            return new Position(groupPosition);
        }

        public Map<Integer, Position> battleGroups() {
            return battleGroups.entrySet().stream()
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    Map.Entry::getKey, e -> new Position(e.getValue())));
        }

        public AsterMidCampInfo toProto() {
            return AsterMidCampInfo.newBuilder()
                    .setCampId(campId)
                    .setPos(groupPosition.toProto())
                    .build();
        }
    }

    private static final class Holder {
        static final Map<Integer, Camp> CAMPS = load();

        static Map<Integer, Camp> load() {
            try (var stream =
                    AsterMidCamps.class.getResourceAsStream(
                            "/historical-scripts/Activity/2001/mid-camps.json")) {
                if (stream == null) throw new IllegalStateException("Aster camp manifest missing");
                var rows =
                        JsonUtils.loadToList(
                                new InputStreamReader(stream, StandardCharsets.UTF_8), Camp.class);
                if (rows.size() != 60
                        || rows.stream()
                                .anyMatch(
                                        c ->
                                                c.campId <= 0
                                                        || c.groupId <= 0
                                                        || c.sealConfigId <= 0
                                                        || c.rewardConfigId <= 0
                                                        || c.position == null
                                                        || c.groupPosition == null
                                                        || c.battleGroups == null
                                                        || c.battleGroups.isEmpty()))
                    throw new IllegalStateException("Aster camp manifest incomplete");
                return rows.stream().collect(Collectors.toUnmodifiableMap(Camp::campId, c -> c));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    public static Map<Integer, Camp> camps() {
        return Holder.CAMPS;
    }

    public static Camp byGroup(int groupId) {
        return camps().values().stream().filter(c -> c.groupId == groupId).findFirst().orElse(null);
    }

    public static boolean owns(int groupId) {
        return camps().values().stream()
                .anyMatch(c -> c.groupId == groupId || c.battleGroups.containsKey(groupId));
    }

    public static boolean resourcesAvailable() {
        if (GameData.getAsterMidDataMap().size() != 15
                || GameData.getAsterMidGroupsDataMap().size() != 60
                || GameData.getAsterMidDifficultyDataMap().size() != 9) return false;
        for (int id : List.of(102, 105, 109)) {
            var item = GameData.getItemDataMap().get(id);
            if (item == null
                    || item.getItemType() != emu.grasscutter.game.inventory.ItemType.ITEM_VIRTUAL)
                return false;
        }
        for (int id : List.of(70950015, 70950019, 70950020, 70950021)) {
            var gadget = GameData.getGadgetDataMap().get(id);
            if (gadget == null
                    || gadget.getType()
                            != (id == 70950021
                                    ? emu.grasscutter.game.props.EntityType.GeneralRewardPoint
                                    : emu.grasscutter.game.props.EntityType.SealGadget))
                return false;
        }
        var ids = new HashSet<Integer>();
        for (var batch : GameData.getAsterMidDataMap().values()) {
            if (batch.getCampVec() == null
                    || batch.getCampVec().size() != 4
                    || new HashSet<>(batch.getCampVec()).size() != 4) return false;
            for (var id : batch.getCampVec()) {
                var camp = camps().get(id);
                var meta = GameData.getAsterMidGroupsDataMap().get(id.intValue());
                if (!ids.add(id)
                        || camp == null
                        || meta == null
                        || meta.getGroupId() != camp.groupId
                        || meta.getBattleGroupVec() == null
                        || !new HashSet<>(meta.getBattleGroupVec())
                                .equals(camp.battleGroups.keySet())) return false;
            }
        }
        if (ids.size() != 60) return false;
        for (int id = 1; id <= 9; id++) if (AsterMidRewards.reward(id) == null) return false;
        for (int id = 1200110; id <= 1200115; id++) {
            var watcher = GameData.getActivityWatcherDataMap().get(id);
            var reward =
                    watcher == null ? null : GameData.getRewardDataMap().get(watcher.getRewardID());
            if (watcher == null
                    || watcher.isDisuse()
                    || watcher.getProgress() != (id - 1200109) * 5
                    || watcher.getTriggerConfig() == null
                    || watcher.getTriggerConfig().getWatcherTriggerType()
                            != emu.grasscutter.game.props.WatcherTriggerType
                                    .TRIGGER_TAKE_GENERAL_REWARD
                    || !watcher.getTriggerConfig().getParamList().equals(List.of("70950021"))
                    || reward == null
                    || reward.getRewardItemList() == null
                    || reward.getRewardItemList().isEmpty()) return false;
        }
        return true;
    }
}

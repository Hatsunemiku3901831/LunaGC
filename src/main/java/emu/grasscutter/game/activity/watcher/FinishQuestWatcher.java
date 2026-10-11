package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.ActivityWatcher;
import emu.grasscutter.game.activity.ActivityWatcherType;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketActivityUpdateWatcherNotify;

import java.util.*;

/** A boolean conjunction of completed quests, refreshed from the player's current quest state. */
@ActivityWatcherType(WatcherTriggerType.TRIGGER_FINISH_QUEST_AND)
public class FinishQuestWatcher extends ActivityWatcher {
    @Override
    protected boolean isMeet(String... param) {
        if (param == null || param.length == 0 || param[0] == null) return false;
        var ids = questIds();
        if (ids.isEmpty()) return false;
        try {
            return ids.contains(Integer.parseInt(param[0].trim()));
        } catch (NumberFormatException malformed) {
            return false;
        }
    }

    private Set<Integer> questIds() {
        var meta = getActivityWatcherData();
        if (meta == null
                || meta.getId() != getWatcherId()
                || meta.isDisuse()
                || meta.getProgress() != 1
                || meta.getTriggerConfig() == null
                || meta.getTriggerConfig().getWatcherTriggerType()
                        != WatcherTriggerType.TRIGGER_FINISH_QUEST_AND
                || meta.getTriggerConfig().getParamList() == null) return Set.of();
        var ids = new LinkedHashSet<Integer>();
        for (String entry : meta.getTriggerConfig().getParamList()) {
            if (entry == null || entry.isBlank()) return Set.of();
            for (String value : entry.split(",", -1)) {
                try {
                    int id = Integer.parseInt(value.trim());
                    if (id <= 0) return Set.of();
                    ids.add(id);
                } catch (NumberFormatException malformed) {
                    return Set.of();
                }
            }
        }
        return ids;
    }

    @Override
    public boolean refreshProgress(PlayerActivityData data) {
        if (data == null) return false;
        synchronized (data) {
            var ids = questIds();
            var player = data.getPlayer();
            if (ids.isEmpty()
                    || player == null
                    || data.getUid() != player.getUid()
                    || data.getWatcherInfoMap() == null) return false;
            var info = data.getWatcherInfoMap().get(getWatcherId());
            if (info == null
                    || info.getWatcherId() != getWatcherId()
                    || info.getTotalProgress() != 1
                    || info.getCurProgress() != 0) return false;
            for (int id : ids) {
                var quest = player.getQuestManager().getQuestById(id);
                if (quest == null || quest.getState() != QuestState.QUEST_STATE_FINISHED)
                    return false;
            }
            data.getWatcherInfoMap()
                    .put(
                            getWatcherId(),
                            PlayerActivityData.WatcherInfo.of()
                                    .watcherId(getWatcherId())
                                    .totalProgress(1)
                                    .curProgress(1)
                                    .isTakenReward(info.isTakenReward())
                                    .build());
            return true;
        }
    }

    @Override
    public void trigger(PlayerActivityData data, String... param) {
        if (data == null || !isMeet(param)) return;
        synchronized (data) {
            var previous =
                    data.getWatcherInfoMap() == null
                            ? null
                            : data.getWatcherInfoMap().get(getWatcherId());
            if (!refreshProgress(data)) return;
            try {
                data.saveSync();
            } catch (RuntimeException | Error failure) {
                data.getWatcherInfoMap().put(getWatcherId(), previous);
                throw failure;
            }
            data.getPlayer()
                    .sendPacket(
                            new PacketActivityUpdateWatcherNotify(
                                    data.getActivityId(),
                                    data.getWatcherInfoMap().get(getWatcherId())));
        }
    }
}

package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE)
public class ExecRefreshGroupSuite extends QuestExecHandler {
    private record SuiteChange(int groupId, int suiteId) {}

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (quest == null
                || quest.getMainQuest() == null
                || params == null
                || params.length < 2
                || params[0] == null
                || params[1] == null) return false;
        final int sceneId;
        final List<SuiteChange> changes = new ArrayList<>();
        try {
            sceneId = Integer.parseInt(params[0].trim());
            if (sceneId <= 0) return false;
            for (var entry : params[1].split(";")) {
                var pair = entry.split(",", -1);
                if (pair.length != 2) return false;
                int groupId = Integer.parseInt(pair[0].trim());
                int suiteId = Integer.parseInt(pair[1].trim());
                if (groupId <= 0 || suiteId < 0) return false;
                changes.add(new SuiteChange(groupId, suiteId));
            }
        } catch (NumberFormatException error) {
            return false;
        }
        if (changes.isEmpty()) return false;
        var owner = quest.getOwner();
        var world = owner == null ? null : owner.getWorld();
        if (world == null) return false;
        var scene = world.getSceneById(sceneId);
        if (scene == null) return false;
        var scripts = scene.getScriptManager();
        if (scripts.isDestroyed()) return false;
        if (!scripts.isInit()) {
            // Initialization may complete between these two status reads.
            if (scripts.isInitAttempted() && !scripts.isInit()) return false;
            final QuestState state;
            final QuestData definition;
            final long revision;
            synchronized (quest) {
                state = quest.getState();
                definition = quest.getQuestData();
                revision = quest.getExecutionRevision();
            }
            scripts.whenInitialized(
                    ready -> {
                        if (!ready) return;
                        scene.getScheduler()
                                .scheduleDelayedTask(
                                        () -> {
                                            synchronized (quest) {
                                                if (quest.getOwner() == owner
                                                        && owner.getWorld() == world
                                                        && world.getScenes().get(sceneId) == scene
                                                        && owner.getQuestManager()
                                                                        .getQuestById(
                                                                                quest
                                                                                        .getSubQuestId())
                                                                == quest
                                                        && quest.getState() == state
                                                        && quest.getQuestData() == definition
                                                        && quest.getExecutionRevision() == revision
                                                        && scripts.isInit()
                                                        && !scripts.isDestroyed()) {
                                                    applyChanges(quest, scene, changes);
                                                }
                                            }
                                        },
                                        1);
                    });
            return true;
        }
        return applyChanges(quest, scene, changes);
    }

    private static boolean applyChanges(GameQuest quest, Scene scene, List<SuiteChange> changes) {
        boolean result = true;
        for (var change : changes) {
            try {
                if (!refresh(quest, scene, change)) result = false;
            } catch (RuntimeException error) {
                Grasscutter.getLogger()
                        .warn(
                                "Quest {} could not refresh group {} suite {}",
                                quest.getSubQuestId(),
                                change.groupId(),
                                change.suiteId(),
                                error);
                result = false;
            }
        }
        return result;
    }

    private static boolean hasQuestOverride(World world, Player owner, int sceneId, int groupId) {
        var players = new HashSet<>(world.getPlayers());
        players.add(owner);
        return players.stream()
                .filter(player -> player != null && player.getWorld() == world)
                .anyMatch(
                        player ->
                                player.getQuestManager().getSceneGroupSuite(sceneId).stream()
                                        .anyMatch(
                                                saved ->
                                                        saved.getGroup() == groupId
                                                                && saved.getSuite() > 0));
    }

    private static boolean refresh(GameQuest quest, Scene scene, SuiteChange change) {
        synchronized (scene) {
            var scripts = scene.getScriptManager();
            if (scripts.isDestroyed() || quest.getOwner().getWorld() != scene.getWorld())
                return false;
            var group = scripts.getGroupById(change.groupId());
            if (group == null) return false;
            boolean previous = group.dontUnload;
            // finishParent may already exclude this chapter from the active-override query.
            boolean ownOverride =
                    quest.getMainQuest().getQuestGroupSuites().stream()
                            .anyMatch(
                                    saved ->
                                            saved != null
                                                    && saved.getScene() == scene.getId()
                                                    && saved.getGroup() == group.id
                                                    && saved.getSuite() > 0);
            boolean independent =
                    previous
                            && (group.dynamic_load
                                    || !ownOverride
                                            && !hasQuestOverride(
                                                    scene.getWorld(),
                                                    quest.getOwner(),
                                                    scene.getId(),
                                                    group.id));
            group.dontUnload = true; // Cover the actual refresh, including off-grid groups.
            boolean committed = false;
            try {
                if (!scripts.refreshGroupSuite(group.id, change.suiteId(), quest)) return false;
                boolean keep =
                        independent
                                || change.suiteId() > 0
                                || hasQuestOverride(
                                        scene.getWorld(),
                                        quest.getOwner(),
                                        scene.getId(),
                                        group.id);
                group.dontUnload = keep;
                committed = true;
                return true;
            } finally {
                if (!committed) group.dontUnload = previous;
            }
        }
    }
}

package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;

import java.util.function.BooleanSupplier;

@QuestValueExec(QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA)
public class ExecNotifyGroupLua extends QuestExecHandler {

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (quest == null
                || quest.getMainQuest() == null
                || params == null
                || params.length < 2
                || params[0] == null
                || params[1] == null) return false;
        final int sceneId, groupId;
        try {
            sceneId = Integer.parseInt(params[0].trim());
            groupId = Integer.parseInt(params[1].trim());
        } catch (NumberFormatException error) {
            return false;
        }
        if (sceneId <= 0 || groupId <= 0) return false;
        var owner = quest.getOwner();
        var world = owner == null ? null : owner.getWorld();
        if (world == null) return false;
        var scene = world.getSceneById(sceneId);
        if (scene == null) return false;
        var scripts = scene.getScriptManager();
        if (scripts.isDestroyed()
                || !scripts.isInit() && scripts.isInitAttempted() && !scripts.isInit())
            return false;
        final QuestState state;
        final QuestData definition;
        final long revision;
        synchronized (quest) {
            state = quest.getState();
            definition = quest.getQuestData();
            revision = quest.getExecutionRevision();
        }
        if (state == null || definition == null) return false;
        BooleanSupplier current =
                () ->
                        quest.getOwner() == owner
                                && owner.getWorld() == world
                                && world.getScenes().get(sceneId) == scene
                                && scripts.isInit()
                                && !scripts.isDestroyed()
                                && owner.getQuestManager().getQuestById(quest.getSubQuestId())
                                        == quest
                                && quest.getState() == state
                                && quest.getQuestData() == definition
                                && quest.getExecutionRevision() == revision;
        BooleanSupplier present =
                () ->
                        current.getAsBoolean()
                                && owner.getScene() == scene
                                && scene.getPlayers().stream().anyMatch(member -> member == owner);
        Runnable dispatch =
                () -> {
                    synchronized (scene) {
                        if (!present.getAsBoolean() || !scene.isFinishedLoading()) return;
                        var group = scripts.getGroupById(groupId);
                        if (group == null || !scripts.allowsGroupCall(group)) {
                            Grasscutter.getLogger()
                                    .warn(
                                            "Quest {} could not notify group {} in scene {}",
                                            quest.getSubQuestId(),
                                            groupId,
                                            sceneId);
                            return;
                        }
                        int eventType =
                                state == QuestState.QUEST_STATE_FINISHED
                                        ? EventType.EVENT_QUEST_FINISH
                                        : EventType.EVENT_QUEST_START;
                        scripts.callEvent(
                                new ScriptArgs(
                                                groupId,
                                                eventType,
                                                quest.getSubQuestId(),
                                                state == QuestState.QUEST_STATE_FINISHED ? 1 : 0)
                                        .setEventSource(quest.getSubQuestId()),
                                () ->
                                        present.getAsBoolean()
                                                && scripts.getLoadedGroupById(groupId) == group,
                                true);
                    }
                };
        scripts.whenInitialized(
                ready -> {
                    if (!ready || !current.getAsBoolean()) return;
                    scene.runWhenPlayerEnters(
                            owner,
                            () ->
                                    scene.runWhenFinished(
                                            () ->
                                                    scene.getScheduler()
                                                            .scheduleDelayedTask(dispatch, 1)));
                });
        return true;
    }
}

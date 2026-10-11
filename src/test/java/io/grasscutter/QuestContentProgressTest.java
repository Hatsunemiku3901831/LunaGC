package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Original objectives through real content handlers, with isolated quest state and Mongo. */
class QuestContentProgressTest extends QuestTestFixture {
    @Test
    void unrelatedInteractionCannotClearTheFirstOriginalObjective() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710099);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void threeOriginalInteractionsAccumulateAndCompleteTheRealQuest() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertArrayEquals(new int[] {1, 1, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710033);
        assertArrayEquals(new int[] {1, 1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void originalSevenInteractionGoalCannotFinishAfterOneInteraction() throws Exception {
        var quest = active(1110302);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710064);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
    }

    @Test
    void originalRepeatedGoalKeepsIndependentCountersAndRequiresAllThree() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 2}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 3}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void partialInteractionProgressIsPersistedBeforeCompletion() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertArrayEquals(new int[] {1, 0, 0}, stored(quest).getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, stored(quest).getState());
    }

    @Test
    void originalTempleQuestStillUsesItsCompletedDungeonHistory() throws Exception {
        var quest = active(30901);
        player.getPlayerProgress().markDungeonAsComplete(1001);
        drain();
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        player.getPlayerProgress().markDungeonAsComplete(1);
        drain();
        assertArrayEquals(new int[] {1, 1, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        player.getPlayerProgress().markDungeonAsComplete(1003);
        drain();
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void originalSevenInteractionGoalFinishesExactlyOnTheSeventhEvent() throws Exception {
        var quest = active(1110302);
        for (int n = 1; n <= 7; n++) {
            event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710064);
            assertArrayEquals(new int[] {n}, quest.getFinishProgressList());
            assertEquals(
                    n < 7 ? QuestState.QUEST_STATE_UNFINISHED : QuestState.QUEST_STATE_FINISHED,
                    quest.getState());
        }
        int packets = player.packets.size();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710064);
        assertArrayEquals(new int[] {7}, quest.getFinishProgressList());
        assertEquals(packets, player.packets.size());
    }

    @Test
    void aSatisfiedSingleGoalDoesNotAccumulateOrNotifyAgain() throws Exception {
        var quest = active(1100509);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(1, notifications().size());
        player.packets.clear();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710099);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void notificationContainsTheEntireNewProgressSnapshot() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        player.packets.clear();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        var updates = notifications();
        assertEquals(1, updates.size());
        assertEquals(quest.getSubQuestId(), updates.get(0).getQuestId());
        assertEquals(List.of(1, 2), updates.get(0).getFinishProgressListList());
        assertEquals(List.of(), updates.get(0).getFailProgressListList());
    }

    @Test
    void coldMongoQuestCanResumeItsIndependentCounters() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        var main =
                store.find(GameMainQuest.class)
                        .filter(
                                Filters.eq("ownerUid", player.getUid()),
                                Filters.eq("parentQuestId", quest.getMainQuestId()))
                        .first();
        assertNotNull(main);
        main.setOwner(player);
        for (var child : main.getChildQuests().values()) {
            child.setMainQuest(main);
            child.setConfig(GameData.getQuestDataMap().get(child.getSubQuestId()));
        }
        player.getQuestManager().getMainQuests().put(main.getParentQuestId(), main);
        quest = main.getChildQuestById(1909002);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertArrayEquals(new int[] {1, 2}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void liveQuestVariableMustBecomeFalseAgainBeforeTheOtherGoalFinishes() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_QUEST_VAR_EQUAL',param:[0,1]},"
                                + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]}]",
                        "[]",
                        "LOGIC_AND");
        quest.getMainQuest().setQuestVar(0, 1);
        drain();
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());
        quest.getMainQuest().setQuestVar(0, 0);
        drain();
        assertArrayEquals(new int[] {0, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        quest.getMainQuest().setQuestVar(0, 1);
        drain();
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void failureGoalsHaveTheirOwnCountsAndPersistTheFailedState() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710099]}]",
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]},"
                            + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710032],count:2}]",
                        "LOGIC_AND");
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertArrayEquals(new int[] {1, 1}, quest.getFailProgressList());
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {1, 1}, stored(quest).getFailProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FAILED, stored(quest).getState());
        assertArrayEquals(new int[] {1, 2}, stored(quest).getFailProgressList());
    }

    @Test
    void luaProgressIsASnapshotAndRepeatedNotificationsDoNotAddCounts() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],"
                                + "param_str:'counted_lua',count:3}]",
                        "[]",
                        "LOGIC_AND");
        player.getPlayerProgress().addToCurrentProgress("counted_lua", 2);
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "counted_lua", 0);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "counted_lua", 0);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        player.getPlayerProgress().addToCurrentProgress("counted_lua", 1);
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "counted_lua", 0);
        assertArrayEquals(new int[] {3}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void execProgressResetMustUpdateTheActiveCounter() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_ADD_QUEST_PROGRESS',param:[110051],count:3}]",
                        "[]",
                        "LOGIC_AND");
        player.getPlayerProgress().addToCurrentProgress("110051", 2);
        event(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 110051);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        player.getPlayerProgress().resetCurrentProgress("110051");
        event(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 110051);
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        player.getPlayerProgress().addToCurrentProgress("110051", 3);
        event(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 110051);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void adventureRankUsesCurrentLevelRatherThanNumberOfLevelEvents() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_PLAYER_LEVEL_UP',param:[0,0],count:25}]",
                        "[]",
                        "LOGIC_AND");
        event(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, 20);
        assertArrayEquals(new int[] {20}, quest.getFinishProgressList());
        for (int n = 0; n < 5; n++) event(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, 20);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {20}, quest.getFinishProgressList());
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 25);
        event(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, 25);
        assertArrayEquals(new int[] {25}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void itemQuantityUsesInventoryAndCanDecreaseBeforeTheOtherGoalFinishes() throws Exception {
        var counts = new HashMap<Integer, Integer>();
        replaceField(
                Player.class,
                player,
                "inventory",
                new Inventory(player) {
                    @Override
                    public int getItemCountById(int id) {
                        return counts.getOrDefault(id, 0);
                    }
                });
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_OBTAIN_ITEM',param:[100001],count:3},"
                                + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]}]",
                        "[]",
                        "LOGIC_AND");
        counts.put(100001, 3);
        event(QuestContent.QUEST_CONTENT_OBTAIN_ITEM, 100001);
        assertArrayEquals(new int[] {3, 0}, quest.getFinishProgressList());
        counts.put(100001, 1);
        event(QuestContent.QUEST_CONTENT_OBTAIN_ITEM, 100001);
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        counts.put(100001, 4);
        event(QuestContent.QUEST_CONTENT_OBTAIN_ITEM, 100001);
        assertArrayEquals(new int[] {3, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void orCombinationMustWaitUntilOneCounterReachesItsTarget() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031],count:2},"
                            + "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710032],count:3}]",
                        "[]",
                        "LOGIC_OR");
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710032);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertArrayEquals(new int[] {2, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void oldTalkHistoryCannotCountAnUnrelatedNewTalkTowardARepeatedGoal() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1100599],count:2}]",
                        "[]",
                        "LOGIC_AND");
        GameData.getQuestTalkMap().put(1100599, 11005);
        quest.getMainQuest()
                .getTalks()
                .put(1100599, gson.fromJson("{id:1100599}", MainQuestData.TalkData.class));
        event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 1100599);
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 1100500);
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 1100599);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void alreadyActiveQuestDoesNotRequireAnAcceptConditionForCompletion() throws Exception {
        var quest = active(1100509);
        replaceField(QuestData.class, quest.getQuestData(), "acceptCond", null);
        for (int id : new int[] {70710031, 70710032, 70710033})
            event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, id);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void unmatchedUnsupportedContentCannotEraseOrInventProgress() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_ACTIVITY_TRIGGER_UPDATE',param:[1],count:3}]",
                        "[]",
                        "LOGIC_AND");
        quest.setFinishProgress(0, 2);
        event(QuestContent.QUEST_CONTENT_ACTIVITY_TRIGGER_UPDATE, 1);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void unknownCounterIsZeroAndCannotCompleteAnUnmatchedLuaGoal() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],"
                                + "param_str:'missing_count',count:3}]",
                        "[]",
                        "LOGIC_AND");
        event(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "other_count", 0);
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void completedGroupGoalSurvivesUnloadingBeforeTheNextGroupIsCleared() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_CLEAR_GROUP_MONSTER',param:[133003001]},"
                                + "{type:'QUEST_CONTENT_CLEAR_GROUP_MONSTER',param:[133003002]}]",
                        "[]",
                        "LOGIC_AND");
        var field = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var groups =
                (Map<Integer, SceneGroupInstance>) field.get(player.getScene().getScriptManager());
        var first = SceneGroup.of(133003001);
        first.monsters = Map.of();
        groups.put(first.id, new SceneGroupInstance(first, player));
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, first.id);
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());
        groups.remove(first.id);
        var second = SceneGroup.of(133003002);
        second.monsters = Map.of();
        groups.put(second.id, new SceneGroupInstance(second, player));
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, second.id);
        assertArrayEquals(new int[] {1, 1}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }
}

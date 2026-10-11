package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.*;

/** Real old-schema Mongo documents and current resource objectives through the load path. */
class QuestResourceRebindTest extends QuestTestFixture {
    private static final String FIRST = "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710327,0]}";
    private static final String SECOND =
            "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710356,0],count:3}";

    private QuestData definition(GameQuest quest, String finish, String fail) {
        var json = gson.toJsonTree(quest.getQuestData()).getAsJsonObject();
        json.add("finishCond", gson.fromJson(finish, com.google.gson.JsonElement.class));
        json.add("failCond", gson.fromJson(fail, com.google.gson.JsonElement.class));
        var meta = gson.fromJson(json, QuestData.class);
        meta.onLoad();
        GameData.getBeginCondQuestMap().clear();
        return meta;
    }

    private GameQuest reload(GameQuest quest) throws Exception {
        replaceField(ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        return player.getQuestManager().getQuestById(quest.getSubQuestId());
    }

    private void oldSchema(GameQuest quest, List<Integer> finish, List<Integer> fail) {
        String base = "childQuests." + quest.getSubQuestId() + ".";
        var updates = new ArrayList<org.bson.conversions.Bson>();
        for (String name :
                List.of(
                        "finishConditionKeys",
                        "failConditionKeys",
                        "retiredFinishProgress",
                        "retiredFailProgress",
                        "legacyFinishProgressList",
                        "legacyFailProgressList")) updates.add(Updates.unset(base + name));
        updates.add(
                finish == null
                        ? Updates.unset(base + "finishProgressList")
                        : Updates.set(base + "finishProgressList", finish));
        updates.add(
                fail == null
                        ? Updates.unset(base + "failProgressList")
                        : Updates.set(base + "failProgressList", fail));
        assertEquals(
                1,
                store.getDatabase()
                        .getCollection("quests")
                        .updateOne(
                                Filters.and(
                                        Filters.eq("ownerUid", player.getUid()),
                                        Filters.eq("parentQuestId", quest.getMainQuestId())),
                                Updates.combine(updates))
                        .getMatchedCount());
    }

    private Document raw(GameQuest quest) {
        var main =
                store.getDatabase()
                        .getCollection("quests")
                        .find(
                                Filters.and(
                                        Filters.eq("ownerUid", player.getUid()),
                                        Filters.eq("parentQuestId", quest.getMainQuestId())))
                        .first();
        assertNotNull(main);
        return main.get("childQuests", Document.class)
                .get("" + quest.getSubQuestId(), Document.class);
    }

    @Test
    void missingLegacyArraysAreInitializedAndTheRealQuestCanStillFinish() throws Exception {
        var quest = active(1100509);
        oldSchema(quest, null, null);
        quest = reload(quest);
        assertArrayEquals(new int[] {0, 0, 0}, quest.getFinishProgressList());
        assertArrayEquals(
                new int[quest.getQuestData().getFailCond().size()], quest.getFailProgressList());
        assertEquals(List.of(0, 0, 0), quest.toProto().getFinishProgressListList());
        for (int id : new int[] {70710031, 70710032, 70710033})
            event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, id);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void shorterUnknownLegacyArrayIsRetainedWithoutAssigningItToNewGoals() throws Exception {
        var quest = active(1100509);
        oldSchema(quest, List.of(1), List.of());
        quest = reload(quest);
        assertArrayEquals(new int[] {0, 0, 0}, quest.getFinishProgressList());
        assertEquals(List.of(1), raw(quest).getList("legacyFinishProgressList", Integer.class));
        assertEquals(List.of(0, 0, 0), stored(quest).toProto().getFinishProgressListList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void longerUnknownLegacyArrayCannotFinishAReplacedDefinition() throws Exception {
        var quest = active(1100509);
        oldSchema(quest, List.of(1, 1, 1, 1), List.of(1, 1));
        quest = reload(quest);
        assertArrayEquals(new int[] {0, 0, 0}, quest.getFinishProgressList());
        assertArrayEquals(
                new int[quest.getQuestData().getFailCond().size()], quest.getFailProgressList());
        assertEquals(
                List.of(1, 1, 1, 1), raw(quest).getList("legacyFinishProgressList", Integer.class));
        assertEquals(List.of(1, 1), raw(quest).getList("legacyFailProgressList", Integer.class));
    }

    @Test
    void compatibleLegacyShapeKeepsExistingCountsAndReceivesIdentityMetadata() throws Exception {
        var quest = active(1909002);
        oldSchema(quest, List.of(1, 2), List.of());
        quest = reload(quest);
        assertArrayEquals(new int[] {1, 2}, quest.getFinishProgressList());
        var keys = raw(quest).getList("finishConditionKeys", String.class);
        assertNotNull(keys);
        assertEquals(2, keys.size());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void unchangedDefinitionKeepsArrayReferencesAndRecordedProgress() throws Exception {
        var quest = active(1909002);
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        var finish = quest.getFinishProgressList();
        var fail = quest.getFailProgressList();
        quest.setConfig(quest.getQuestData());
        assertSame(finish, quest.getFinishProgressList());
        assertSame(fail, quest.getFailProgressList());
        assertArrayEquals(new int[] {1, 2}, finish);
    }

    @Test
    void reorderedKnownObjectivesKeepTheirOwnCounts() throws Exception {
        var quest = active(1909002);
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        quest.setConfig(definition(quest, "[" + SECOND + "," + FIRST + "]", "[]"));
        assertArrayEquals(new int[] {2, 1}, quest.getFinishProgressList());
        assertEquals(List.of(2, 1), quest.toProto().getFinishProgressListList());
    }

    @Test
    void addingAGoalPreservesIdentifiedGoalsAndStartsTheNewOneAtZero() throws Exception {
        var quest = active(1909002);
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        quest.setConfig(
                definition(
                        quest,
                        "["
                                + FIRST
                                + ","
                                + SECOND
                                + ",{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710357,0]}]",
                        "[]"));
        assertArrayEquals(new int[] {1, 2, 0}, quest.getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710357);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void replacingOneSameSizedGoalCannotTransferItsCountToAnUnrelatedTarget() throws Exception {
        var quest = active(1909002);
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        quest.setConfig(
                definition(
                        quest,
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710358,0]},"
                                + SECOND
                                + "]",
                        "[]"));
        assertArrayEquals(new int[] {0, 2}, quest.getFinishProgressList());
    }

    @Test
    void coldMongoLoadUsesPersistedIdentitiesToDetectAResourceReorder() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710327);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        var meta = definition(quest, "[" + SECOND + "," + FIRST + "]", "[]");
        GameData.getQuestDataMap().put(meta.getId(), meta);
        quest = reload(quest);
        assertArrayEquals(new int[] {2, 1}, quest.getFinishProgressList());
        assertArrayEquals(new int[] {2, 1}, stored(quest).getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
    }

    @Test
    void loadedMainQuestReconnectsThePlayersQuestManager() throws Exception {
        var quest = reload(active(1100509));
        assertSame(player, quest.getOwner());
        assertSame(player.getQuestManager(), quest.getMainQuest().getQuestManager());
    }

    @Test
    void failedAndFinishedStatesAndTimestampsSurviveArrayRepair() throws Exception {
        var quest = active(1100509);
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        quest.setAcceptTime(111);
        quest.setStartTime(222);
        quest.setFinishTime(333);
        quest.save();
        drain();
        oldSchema(quest, List.of(1), List.of(1));
        quest = reload(quest);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(111, quest.getAcceptTime());
        assertEquals(222, quest.getStartTime());
        assertEquals(333, quest.getFinishTime());
        assertArrayEquals(new int[] {0, 0, 0}, quest.getFinishProgressList());
        assertEquals(333, stored(quest).getFinishTime());
    }

    @Test
    void unrelatedQuestDefinitionCannotReplaceThePlayersMetadata() throws Exception {
        var quest = active(1909002);
        var before = quest.getQuestData();
        quest.setConfig(GameData.getQuestDataMap().get(1100509));
        quest.setConfig(null);
        assertSame(before, quest.getQuestData());
    }

    @Test
    void definitionFromAnotherParentIsRejectedEvenIfTheSubQuestIdMatches() throws Exception {
        var quest = active(1909002);
        var before = quest.getQuestData();
        var json = gson.toJsonTree(before).getAsJsonObject();
        json.addProperty("mainId", 11005);
        quest.setConfig(gson.fromJson(json, QuestData.class));
        assertSame(before, quest.getQuestData());
    }

    @Test
    void removedKnownGoalCanRecoverAfterAColdLoadWhenItsDefinitionReturns() throws Exception {
        var quest = active(1909002);
        var original = quest.getQuestData();
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        var reduced = definition(quest, "[" + FIRST + "]", "[]");
        quest.setConfig(reduced);
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        GameData.getQuestDataMap().put(reduced.getId(), reduced);
        quest.save();
        drain();
        quest = reload(quest);
        quest.setConfig(original);
        assertArrayEquals(new int[] {1, 2}, quest.getFinishProgressList());
    }

    @Test
    void explicitQuestResetClearsRetiredProgressBeforeAResourceReturns() throws Exception {
        var quest = active(1909002);
        var original = quest.getQuestData();
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        quest.setConfig(definition(quest, "[" + FIRST + "]", "[]"));
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        assertTrue(quest.clearProgress(false));
        drain();
        quest.setConfig(original);
        assertArrayEquals(new int[] {0, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNSTARTED, quest.getState());
    }

    @Test
    void goalsWithSameEventButDifferentCountsRemainDistinctWhenReordered() throws Exception {
        var quest =
                controlled(
                        "["
                                + FIRST
                                + ",{type:'QUEST_CONTENT_INTERACT_GADGET',"
                                + "param:[70710327,0],count:3}]",
                        "[]",
                        "LOGIC_AND");
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        quest.setConfig(
                definition(
                        quest,
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',"
                                + "param:[70710327,0],count:3},"
                                + FIRST
                                + "]",
                        "[]"));
        assertArrayEquals(new int[] {2, 1}, quest.getFinishProgressList());
    }

    @Test
    void temporarilyMissingChildDefinitionKeepsTheWholeSavedChapterForRetry() throws Exception {
        var quest = active(1100509);
        var original = quest.getQuestData();
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        GameData.getQuestDataMap().remove(quest.getSubQuestId());
        reload(quest);
        assertNull(player.getQuestManager().getMainQuestById(quest.getMainQuestId()));
        assertEquals(List.of(1, 0, 0), raw(quest).getList("finishProgressList", Integer.class));
        GameData.getQuestDataMap().put(original.getId(), original);
        quest = reload(quest);
        assertNotNull(quest);
        assertArrayEquals(new int[] {1, 0, 0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void inconsistentParentDefinitionCannotPublishAPartiallyBoundChapter() throws Exception {
        var quest = active(1909002);
        var original = quest.getQuestData();
        var json = gson.toJsonTree(original).getAsJsonObject();
        json.addProperty("mainId", 11005);
        GameData.getQuestDataMap().put(original.getId(), gson.fromJson(json, QuestData.class));
        reload(quest);
        assertNull(player.getQuestManager().getMainQuestById(quest.getMainQuestId()));
        assertNotNull(raw(quest));
        GameData.getQuestDataMap().put(original.getId(), original);
        assertNotNull(reload(quest));
    }

    @Test
    void temporarilyMissingChapterDefinitionKeepsTheSavedChapterForRetry() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        var original = GameData.getMainQuestDataMap().remove(quest.getMainQuestId());
        reload(quest);
        assertNull(player.getQuestManager().getMainQuestById(quest.getMainQuestId()));
        assertEquals(List.of(0, 1), raw(quest).getList("finishProgressList", Integer.class));
        GameData.getMainQuestDataMap().put(original.getId(), original);
        quest = reload(quest);
        assertNotNull(quest);
        assertArrayEquals(new int[] {0, 1}, quest.getFinishProgressList());
    }

    @Test
    void deferredSavedChapterCannotBeCreatedAgainWithFreshProgress() throws Exception {
        var quest = active(1909002);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710356);
        player.packets.clear();
        int subId = quest.getSubQuestId();
        int sibling =
                quest.getMainQuest().getChildQuests().keySet().stream()
                        .filter(id -> id != subId)
                        .findFirst()
                        .orElseThrow();
        var removed = GameData.getQuestDataMap().remove(sibling);
        reload(quest);
        var manager = player.getQuestManager();
        assertNull(manager.addMainQuest(quest.getQuestData()));
        assertNull(manager.addQuest(quest.getQuestData()));
        drain();
        assertEquals(List.of(0, 1), raw(quest).getList("finishProgressList", Integer.class));
        assertEquals(1, store.getDatabase().getCollection("quests").countDocuments());
        assertTrue(player.packets.isEmpty());
        GameData.getQuestDataMap().put(sibling, removed);
        quest = reload(quest);
        assertNotNull(quest);
        assertArrayEquals(new int[] {0, 1}, quest.getFinishProgressList());
    }

    @Test
    void failureObjectivesAreReboundSeparatelyFromCompletionObjectives() throws Exception {
        String a = "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031],count:2}";
        String b = "{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710032],count:3}";
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710099]}]",
                        "[" + a + "," + b + "]",
                        "LOGIC_AND");
        quest.setFailProgress(0, 1);
        quest.setFailProgress(1, 2);
        quest.setConfig(
                definition(
                        quest,
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710099]}]",
                        "[" + b + "," + a + "]"));
        assertArrayEquals(new int[] {2, 1}, quest.getFailProgressList());
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
    }

    @Test
    void conditionIdentityIncludesAllNumericParameters() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_QUEST_VAR_EQUAL',param:[0,1]}]", "[]", "LOGIC_AND");
        quest.setFinishProgress(0, 1);
        quest.setConfig(
                definition(quest, "[{type:'QUEST_CONTENT_QUEST_VAR_EQUAL',param:[0,2]}]", "[]"));
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
    }

    @Test
    void conditionIdentityKeepsLiteralStringsAndSurvivesBsonRoundTrip() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],"
                                + "param_str:'chapter.alpha|2.$节点',count:3}]",
                        "[]",
                        "LOGIC_AND");
        var original = quest.getQuestData();
        quest.setFinishProgress(0, 2);
        var changed =
                definition(
                        quest,
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],"
                                + "param_str:'chapter.beta|2.$节点',count:3}]",
                        "[]");
        quest.setConfig(changed);
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        GameData.getQuestDataMap().put(changed.getId(), changed);
        quest.save();
        drain();
        quest = reload(quest);
        quest.setConfig(original);
        assertArrayEquals(new int[] {2}, quest.getFinishProgressList());
    }

    @Test
    void implicitOneTimeCountAndExplicitOneHaveTheSameMeaning() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031]}]",
                        "[]",
                        "LOGIC_AND");
        quest.setFinishProgress(0, 1);
        var before = quest.getFinishProgressList();
        quest.setConfig(
                definition(
                        quest,
                        "[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031],count:1}]",
                        "[]"));
        assertSame(before, quest.getFinishProgressList());
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
    }

    @Test
    void repeatedLoadKeepsRetiredProgressWithoutGrowingItsHistory() throws Exception {
        var quest = active(1909002);
        quest.setFinishProgress(0, 1);
        quest.setFinishProgress(1, 2);
        var reduced = definition(quest, "[" + FIRST + "]", "[]");
        quest.setConfig(reduced);
        GameData.getQuestDataMap().put(reduced.getId(), reduced);
        quest.save();
        drain();
        var before = raw(quest).getList("retiredFinishProgress", Document.class);
        assertNotNull(before);
        assertEquals(1, before.size());
        for (int n = 0; n < 3; n++) quest = reload(quest);
        assertEquals(before, raw(quest).getList("retiredFinishProgress", Document.class));
    }

    @Test
    void finishedOrFailedQuestsAreNotRestartedByRebinding() throws Exception {
        var quest = active(1909002);
        quest.setState(QuestState.QUEST_STATE_FAILED);
        quest.setFinishTime(12345);
        quest.save();
        drain();
        oldSchema(quest, List.of(1), List.of(1));
        quest = reload(quest);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
        assertEquals(12345, quest.getFinishTime());
        assertTrue(player.packets.isEmpty());
    }
}

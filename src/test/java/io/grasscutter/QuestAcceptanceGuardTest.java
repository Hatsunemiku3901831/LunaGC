package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.model.Filters;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.enums.*;
import java.nio.file.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** Missing or inconsistent quest definitions must not abort login or replace saved progress. */
class QuestAcceptanceGuardTest extends QuestTestFixture {
    private QuestData definition(int parent, int child) {
        var data =
                gson.fromJson(
                        "{mainId:"
                                + parent
                                + ",subId:"
                                + child
                                + ",acceptCond:[],finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:["
                                + child
                                + "]}]}",
                        QuestData.class);
        data.onLoad();
        GameData.getQuestDataMap().put(child, data);
        return data;
    }

    private void chapter(int parent, int... children) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("id", parent);
        var rows = new com.google.gson.JsonArray();
        for (int child : children) {
            var row = new com.google.gson.JsonObject();
            row.addProperty("subId", child);
            rows.add(row);
        }
        json.add("subQuests", rows);
        var data = gson.fromJson(json, MainQuestData.class);
        data.onLoad();
        GameData.getMainQuestDataMap().put(parent, data);
    }

    private Document saved(int parent) {
        return store
                .getDatabase()
                .getCollection("quests")
                .find(
                        Filters.and(
                                Filters.eq("ownerUid", player.getUid()), Filters.eq("parentQuestId", parent)))
                .first();
    }

    private void reload() throws Exception {
        replaceField(ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        player.packets.clear();
    }

    @Test
    void actualCompletedChaptersCanPassLoginDespiteExcelOnlyChildren() throws Exception {
        var missing =
                Map.of(
                        2202205, 22022, 30318, 303, 42710, 427, 42711, 427, 48006, 480, 7113908, 71139, 7165415,
                        71654, 7201303, 72013);
        var parents = new HashSet<>(missing.values());
        for (var data :
                QuestFixtureResources.quests(
                        Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json"), parents)) {
            data.onLoad();
            GameData.getQuestDataMap().put(data.getId(), data);
        }
        for (int parent : parents) {
            var data =
                    gson.fromJson(
                            Files.readString(Path.of("resources/BinOutput/Quest", parent + ".json")),
                            MainQuestData.class);
            data.onLoad();
            GameData.getMainQuestDataMap().put(parent, data);
            var main = new GameMainQuest(player, parent);
            replaceField(GameMainQuest.class, main, "isFinished", true);
            replaceField(
                    GameMainQuest.class, main, "state", ParentQuestState.PARENT_QUEST_STATE_FINISHED);
            for (var child : main.getChildQuests().values()) {
                child.setState(QuestState.QUEST_STATE_FINISHED);
                child.setFinishTime(12345);
            }
            DatabaseHelper.saveGameSync(main);
        }
        reload();
        var before = new HashMap<Integer, Document>();
        for (int parent : parents) before.put(parent, saved(parent));
        for (var entry : missing.entrySet()) {
            var data = GameData.getQuestDataMap().get(entry.getKey().intValue());
            assertNotNull(data);
            assertFalse(
                    Arrays.stream(
                                    GameData.getMainQuestDataMap().get(entry.getValue().intValue()).getSubQuests())
                            .anyMatch(child -> child.getSubId() == data.getId()));
            assertNull(player.getQuestManager().getQuestById(data.getId()));
            for (var condition : data.getAcceptCond()) {
                assertTrue(
                        player
                                .server
                                .getQuestSystem()
                                .triggerCondition(
                                        player, data, condition, condition.getParamStr(), condition.getParam()));
            }
        }

        assertDoesNotThrow(() -> player.getQuestManager().onLogin());
        drain();
        for (int parent : parents) assertEquals(before.get(parent), saved(parent));
        for (int child : missing.keySet()) assertNull(player.getQuestManager().getQuestById(child));
    }

    @Test
    void unavailableParentDoesNotCreateAChapterOrSendNotifications() {
        var data = definition(99001, 9900101);
        assertNull(player.getQuestManager().addQuest(data));
        assertNull(player.getQuestManager().addMainQuest(data));
        assertTrue(player.getQuestManager().getMainQuests().isEmpty());
        assertTrue(player.packets.isEmpty());
        assertEquals(0, store.getDatabase().getCollection("quests").countDocuments());
    }

    @Test
    void excelOnlyChildDoesNotCreateAnIncompleteChapter() {
        definition(99001, 9900101);
        var missing = definition(99001, 9900102);
        chapter(99001, 9900101);
        assertNull(player.getQuestManager().addQuest(missing));
        assertNull(player.getQuestManager().addMainQuest(missing));
        assertTrue(player.getQuestManager().getMainQuests().isEmpty());
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void missingSavedChildKeepsExistingProgressAndCanBeRetried() throws Exception {
        definition(99001, 9900101);
        var data = definition(99001, 9900102);
        chapter(99001, 9900101, 9900102);
        var main = new GameMainQuest(player, 99001);
        main.getChildQuestById(9900101).setState(QuestState.QUEST_STATE_FINISHED);
        main.getChildQuestById(9900101).setFinishTime(12345);
        var missing = main.getChildQuests().remove(9900102);
        DatabaseHelper.saveGameSync(main);
        reload();
        var before = saved(99001);
        assertNull(player.getQuestManager().addQuest(data));
        drain();
        assertEquals(before, saved(99001));
        assertTrue(player.packets.isEmpty());
        var loaded = player.getQuestManager().getMainQuestById(99001);
        assertEquals(12345, loaded.getChildQuestById(9900101).getFinishTime());
        missing.setMainQuest(loaded);
        loaded.getChildQuests().put(9900102, missing);
        assertSame(missing, player.getQuestManager().addQuest(data));
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, missing.getState());
    }

    @Test
    void completedParentDoesNotAutomaticallyStartAnUnstartedChild() throws Exception {
        var data = definition(99001, 9900101);
        chapter(99001, 9900101);
        var main = new GameMainQuest(player, 99001);
        replaceField(GameMainQuest.class, main, "isFinished", true);
        replaceField(GameMainQuest.class, main, "state", ParentQuestState.PARENT_QUEST_STATE_FINISHED);
        player.getQuestManager().getMainQuests().put(99001, main);
        player.getQuestManager().enableQuests();
        assertEquals(QuestState.QUEST_STATE_UNSTARTED, main.getChildQuestById(9900101).getState());
        assertTrue(player.packets.isEmpty());
        // Explicit GM starts retain their existing behavior for a real child.
        assertSame(main.getChildQuestById(9900101), player.getQuestManager().addQuest(data));
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, main.getChildQuestById(9900101).getState());
    }

    @Test
    void aValidChapterStillAcceptsItsQuestAutomatically() {
        definition(99001, 9900101);
        chapter(99001, 9900101);
        player.getQuestManager().enableQuests();
        assertEquals(
                QuestState.QUEST_STATE_UNFINISHED,
                player.getQuestManager().getQuestById(9900101).getState());
    }
}

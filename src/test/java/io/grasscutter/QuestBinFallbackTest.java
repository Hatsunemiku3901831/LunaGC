package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

/** Recover native objectives without inventing missing server acceptance rules. */
class QuestBinFallbackTest extends QuestTestFixture {
    private static Set<Integer> excelIds;

    @BeforeAll
    static void readActualQuestTable() throws Exception {
        excelIds = new HashSet<>();
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/QuestExcelConfigData.json")))
                        .getAsJsonArray();
        for (var row : rows) excelIds.add(row.getAsJsonObject().get("subId").getAsInt());
    }

    private JsonObject nativeChapter(int id) throws Exception {
        return JsonParser.parseString(
                        Files.readString(Path.of("resources/BinOutput/Quest", id + ".json")))
                .getAsJsonObject();
    }

    private MainQuestData load(JsonObject json) {
        var main = gson.fromJson(json, MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(main.getId(), main);
        return main;
    }

    private MainQuestData load(String json) {
        return load(JsonParser.parseString(json).getAsJsonObject());
    }

    private JsonObject child(JsonObject chapter, int id) {
        for (var row : chapter.getAsJsonArray("subQuests"))
            if (row.getAsJsonObject().get("subId").getAsInt() == id) return row.getAsJsonObject();
        throw new AssertionError("Missing original child " + id);
    }

    private boolean automaticallyRegistered(int id) {
        return GameData.getBeginCondQuestMap().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(row -> row.getId() == id);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                7665106, 7665209, 7665005, 7667311, 7667510, 7667110, 7668107, 7713706, 7767103,
                7719508, 701715
            })
    void actualMissingGateQuestRecoversItsNativeDefinition(int id) throws Exception {
        assertFalse(
                excelIds.contains(id), "This regression must exercise a real missing Excel row");
        var chapter = nativeChapter(id / 100);
        var original = child(chapter, id);
        var main = load(chapter);
        var restored = GameData.getQuestDataMap().get(id);
        assertNotNull(restored, "The native chapter already contains the missing objectives");
        assertEquals(id / 100, restored.getMainId());
        assertEquals(original.get("order").getAsInt(), restored.getOrder());
        assertEquals(original.get("descTextMapHash").getAsLong(), restored.getDescTextMapHash());
        assertEquals(
                gson.fromJson(original, QuestData.class).getFinishCond(), restored.getFinishCond());
        assertEquals(
                gson.fromJson(original, QuestData.class).getFinishExec(), restored.getFinishExec());
        assertTrue(Arrays.stream(main.getSubQuests()).anyMatch(row -> row.getSubId() == id));
        assertFalse(
                automaticallyRegistered(id),
                "Missing acceptance rules must not become unconditional");
        assertNotNull(restored.getAcceptCond());
        assertNotNull(restored.getFailCond());
        assertNotNull(restored.getBeginExec());
    }

    @Test
    void primaryExcelDefinitionWinsOverConflictingBinContent() throws Exception {
        var original = GameData.getQuestDataMap().get(1100509);
        var originalConditions = List.copyOf(original.getFinishCond());
        var chapter = nativeChapter(11005);
        child(chapter, 1100509)
                .add(
                        "finishCond",
                        JsonParser.parseString(
                                "[{type:'QUEST_CONTENT_PLAYER_LEVEL_UP',param:[60]}]"));
        load(chapter);
        assertSame(original, GameData.getQuestDataMap().get(1100509));
        assertEquals(originalConditions, original.getFinishCond());
    }

    @Test
    void repeatedChapterLoadKeepsDefinitionAndAcceptanceCacheUnique() {
        var json =
                "{id:99001,subQuests:[{subId:9900101,mainId:99001,order:1,acceptCond:[],finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[9900101]}]}]}";
        load(json);
        var original = GameData.getQuestDataMap().get(9900101);
        assertNotNull(original);
        load(json);
        assertSame(original, GameData.getQuestDataMap().get(9900101));
        assertEquals(
                1,
                GameData.getBeginCondQuestMap().values().stream()
                        .flatMap(Collection::stream)
                        .filter(row -> row.getId() == 9900101)
                        .count());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{subId:9900101,mainId:99002,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}]}",
                "{subId:9900101,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}]}",
                "{subId:9900101,mainId:99001}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'FUTURE_UNKNOWN_CONTENT',param:[1]}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]},"
                    + " {type:'FUTURE_UNKNOWN_CONTENT',param:[2]}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}],finishExec:[{type:'FUTURE_UNKNOWN_EXEC',param:['1']}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}],acceptCond:[{type:'FUTURE_UNKNOWN_COND',param:[2]}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[]}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1],count:-1}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]},"
                    + " {param:[2]}]}",
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',_type:'QUEST_CONTENT_LUA_NOTIFY',param:[1]}]}"
            })
    void incompleteOrUnknownNativeDefinitionStaysUnavailable(String child) {
        load("{id:99001,subQuests:[" + child + "]}");
        assertFalse(GameData.getQuestDataMap().containsKey(9900101));
        assertFalse(automaticallyRegistered(9900101));
    }

    @Test
    void duplicateNativeChildIdsDoNotChooseAnArbitraryDefinition() {
        var child =
                "{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}]}";
        load("{id:99001,subQuests:[" + child + "," + child + "]}");
        assertFalse(GameData.getQuestDataMap().containsKey(9900101));
    }

    @Test
    void recoveredActiveObjectiveCanProgressAndPersistBeforeCompletion() throws Exception {
        load(
                "{id:99001,subQuests:[{subId:9900101,mainId:99001,order:1,finishCond:[{type:'QUEST_CONTENT_INTERACT_GADGET',param:[70710031],count:2}]}]}");
        var quest = active(9900101);
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        assertArrayEquals(new int[] {1}, stored(quest).getFinishProgressList());
        event(QuestContent.QUEST_CONTENT_INTERACT_GADGET, 70710031);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
        assertFalse(automaticallyRegistered(9900101));
    }

    @Test
    void actualRecoveredChapterKeepsUnfinishedSaveOnColdReload() throws Exception {
        load(nativeChapter(77671));
        assertNotNull(GameData.getQuestDataMap().get(7767103));
        var quest = active(7767103);
        quest.setFinishProgress(0, 0);
        quest.save();
        drain();
        var before = stored(quest);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, before.getState());
        replaceField(emu.grasscutter.data.ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        var loaded = player.getQuestManager().getQuestById(7767103);
        assertNotNull(loaded);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, loaded.getState());
        assertArrayEquals(new int[] {0}, loaded.getFinishProgressList());
        assertFalse(automaticallyRegistered(7767103));
        assertEquals(
                1,
                store.find(emu.grasscutter.game.quest.GameMainQuest.class)
                        .filter(
                                dev.morphia.query.experimental.filters.Filters.eq(
                                        "parentQuestId", 77671))
                        .count());
    }

    @Test
    void knownParameterlessExecutionIsPreserved() {
        load(
                "{id:99001,subQuests:[{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}],finishExec:[{type:'QUEST_EXEC_NONE'}]}]}");
        var row = GameData.getQuestDataMap().get(9900101);
        assertNotNull(row);
        assertEquals(QuestExec.QUEST_EXEC_NONE, row.getFinishExec().get(0).getType());
    }

    @Test
    void conflictingNamedStringParametersStayUnavailable() {
        load(
                "{id:99001,subQuests:[{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0],_param_str:'original',param_str:'different'}]}]}");
        assertFalse(GameData.getQuestDataMap().containsKey(9900101));
        assertFalse(automaticallyRegistered(9900101));
    }

    @Test
    void genericAcceptanceEventsDoNotStartMissingAcceptanceRules() throws Exception {
        load(
                "{id:99001,subQuests:[{subId:9900101,mainId:99001,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[1]}]}]}");
        assertNotNull(GameData.getQuestDataMap().get(9900101));
        player.getQuestManager().queueEvent(QuestCond.QUEST_COND_NONE, 0);
        player.getQuestManager().queueEvent(QuestCond.QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER, 60);
        drain();
        assertNull(player.getQuestManager().getQuestById(9900101));
    }

    @Test
    void chapterWithUnsupportedSiblingKeepsItsSaveUnavailableForRetry() throws Exception {
        load(nativeChapter(7017));
        var quest = active(701715);
        assertNull(GameData.getQuestDataMap().get(701702));
        // An older server could have saved a sibling whose execution is unsupported here.
        var oldDefinition =
                gson.fromJson(
                        "{subId:701702,mainId:7017,finishCond:[{type:'QUEST_CONTENT_COMPLETE_TALK',param:[701702]}]}",
                        QuestData.class);
        oldDefinition.onLoad();
        var sibling = new emu.grasscutter.game.quest.GameQuest(quest.getMainQuest(), oldDefinition);
        sibling.setState(QuestState.QUEST_STATE_UNFINISHED);
        quest.getMainQuest().getChildQuests().put(701702, sibling);
        quest.getMainQuest().save();
        drain();
        replaceField(emu.grasscutter.data.ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        assertNull(player.getQuestManager().getQuestById(701715));
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, stored(quest).getState());
    }

    @Test
    void originalRecoveredTalkObjectiveCompletesAndUnlocksItsNativeFeature() throws Exception {
        var previous = new ArrayList<>(GameData.getOpenStateList());
        try {
            GameData.getOpenStateList().clear();
            var rows =
                    JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    "resources/ExcelBinOutput/OpenStateConfigData.json")))
                            .getAsJsonArray();
            for (var row : rows)
                if (row.getAsJsonObject().get("id").getAsInt() == 7016)
                    gson.fromJson(row, OpenStateData.class).onLoad();
            load(nativeChapter(77671));
            var quest = active(7767103);
            player.getProgressManager().tryUnlockOpenStates(false);
            assertEquals(0, player.getProgressManager().getOpenState(7016));
            quest.getMainQuest()
                    .getTalks()
                    .put(7767102, new MainQuestData.TalkData(7767102, "TALK_HERO_MAIN"));
            event(QuestContent.QUEST_CONTENT_COMPLETE_TALK, 7767102);
            assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
            assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
            assertEquals(1, player.getProgressManager().getOpenState(7016));
        } finally {
            drain();
            GameData.getOpenStateList().clear();
            GameData.getOpenStateList().addAll(previous);
        }
    }
}

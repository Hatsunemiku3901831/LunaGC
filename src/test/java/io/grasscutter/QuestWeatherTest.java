package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.world.WeatherData;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.quest.exec.ExecSetWeatherGadget;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SceneAreaWeatherNotifyOuterClass.SceneAreaWeatherNotify;
import emu.grasscutter.scripts.ScriptLib;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

/** Native 35901 weather actions, original notifications and cold quest state. */
class QuestWeatherTest extends QuestTestFixture {
    private Map<Integer, WeatherData> oldWeather;

    @BeforeEach
    void weatherResources() throws Exception {
        oldWeather = new HashMap<>(GameData.getWeatherDataMap());
        GameData.getWeatherDataMap().clear();
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/WeatherExcelConfigData.json")),
                        WeatherData[].class);
        for (var row : rows) GameData.getWeatherDataMap().put(row.getId(), row);
        var quests =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class);
        for (var row : quests)
            if (row.getMainId() == 359) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/359.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(359, main);
        GameData.getBeginCondQuestMap().clear();
        player.setSceneId(3);
        player.getScene().getPlayers().add(player);
        player.packets.clear();
    }

    @AfterEach
    void restoreWeatherResources() throws Exception {
        if (oldWeather == null) return;
        drain();
        GameData.getWeatherDataMap().clear();
        GameData.getWeatherDataMap().putAll(oldWeather);
    }

    private List<SceneAreaWeatherNotify> weatherPackets() throws Exception {
        var result = new ArrayList<SceneAreaWeatherNotify>();
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneAreaWeatherNotify)
                result.add(SceneAreaWeatherNotify.parseFrom(packet.getData()));
        return result;
    }

    private void action(GameQuest quest, int area, int enabled) throws Exception {
        var row =
                gson.fromJson(
                        "{type:'QUEST_EXEC_SET_WEATHER_GADGET',param:['"
                                + area
                                + "','"
                                + enabled
                                + "']}",
                        QuestData.QuestExecParam.class);
        player.getServer().getQuestSystem().triggerExec(quest, row, row.getParam());
        drain();
    }

    private void beginNativeWeather(GameQuest quest) throws Exception {
        for (var row : quest.getQuestData().getBeginExec())
            player.getServer().getQuestSystem().triggerExec(quest, row, row.getParam());
        drain();
    }

    private boolean restore() throws Exception {
        return (boolean)
                QuestManager.class
                        .getMethod("restoreActiveQuestWeather", int.class)
                        .invoke(player.getQuestManager(), player.getScene().getId());
    }

    private void move(int id) {
        player.setScene(
                new Scene(
                        player.getWorld(),
                        gson.fromJson("{id:" + id + ",type:'SCENE_WORLD'}", SceneData.class)));
        player.setSceneId(id);
        player.getScene().getPlayers().add(player);
    }

    @Test
    void original35901BeginActivatesItsWeatherGadget() throws Exception {
        var quest = active(35901);
        beginNativeWeather(quest);
        assertEquals(3, player.getWeatherId());
        assertEquals(70020003, weatherPackets().get(0).getWeatherGadgetId());
        assertEquals(3, weatherPackets().get(0).getWeatherAreaId());
        assertEquals(
                GameData.getWeatherDataMap().get(3).getDefaultClimate().getValue(),
                weatherPackets().get(0).getClimateType());
    }

    @Test
    void nativeFinishClearsWeatherWithoutResettingAnUnrelatedArea() throws Exception {
        var quest = active(35901);
        player.setWeather(3);
        quest.finish();
        drain();
        assertEquals(0, player.getWeatherId());
        assertEquals(0, weatherPackets().get(weatherPackets().size() - 1).getWeatherGadgetId());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
    }

    @Test
    void weatherActivationIsIdempotent() throws Exception {
        var quest = active(35901);
        beginNativeWeather(quest);
        player.packets.clear();
        beginNativeWeather(quest);
        assertEquals(3, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void foreignSceneCannotActivateTheStormEvenWithStaleSavedSceneId() throws Exception {
        var quest = active(35901);
        move(4);
        player.setSceneId(3);
        beginNativeWeather(quest);
        assertEquals(0, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void finishingAfterLeavingTheSceneClearsStoredQuestWeather() throws Exception {
        var quest = active(35901);
        player.setWeather(3);
        move(4);
        quest.finish();
        drain();
        assertEquals(0, player.getWeatherId());
    }

    @Test
    void disablingAnUnrelatedAreaKeepsTheCurrentOverride() throws Exception {
        var quest = active(35901);
        player.setWeather(1);
        player.packets.clear();
        action(quest, 3, 0);
        assertEquals(1, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void packetDoesNotPublishForeignSceneWeather() throws Exception {
        move(4);
        player.setSceneId(3);
        player.setWeather(3);
        var packet = weatherPackets().get(0);
        assertEquals(0, packet.getWeatherAreaId());
        assertEquals(0, packet.getWeatherGadgetId());
        assertEquals(ClimateType.CLIMATE_SUNNY.getValue(), packet.getClimateType());
        assertEquals(
                3, player.getWeatherId(), "Suppressing a packet does not erase a held override");
    }

    @Test
    void packetKeepsAnExplicitClimateWithoutAnArea() throws Exception {
        player.setWeather(0, ClimateType.CLIMATE_RAIN);
        assertEquals(ClimateType.CLIMATE_RAIN.getValue(), weatherPackets().get(0).getClimateType());
        assertEquals(0, weatherPackets().get(0).getWeatherGadgetId());
    }

    @Test
    void luaUsesActivationFlagsAndCanDeactivateTheArea() throws Exception {
        var library = new ScriptLib();
        library.setSceneScriptManager(player.getScene().getScriptManager());
        assertEquals(0, library.SetWeatherAreaState(3, 1));
        assertEquals(70020003, weatherPackets().get(0).getWeatherGadgetId());
        assertEquals(0, library.SetWeatherAreaState(3, 0));
        assertEquals(0, player.getWeatherId());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 4})
    void unsupportedLuaFlagsDoNotChangeWeather(int value) throws Exception {
        var library = new ScriptLib();
        library.setSceneScriptManager(player.getScene().getScriptManager());
        assertEquals(1, library.SetWeatherAreaState(3, value));
        assertEquals(0, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void missingLuaContextReturnsFailureInsteadOfThrowing() {
        assertEquals(1, new ScriptLib().SetWeatherAreaState(3, 1));
    }

    @Test
    void activeOriginalQuestWeatherReturnsAfterColdReload() throws Exception {
        var quest = active(35901);
        quest.save();
        drain();
        replaceField(ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        assertEquals(
                QuestState.QUEST_STATE_UNFINISHED,
                player.getQuestManager().getQuestById(35901).getState());
        assertTrue(restore());
        assertEquals(3, player.getWeatherId());
        assertEquals(1, weatherPackets().size());
        assertFalse(restore());
        assertEquals(1, weatherPackets().size());
    }

    @Test
    void existingManualOverrideIsNotReplacedByQuestRestoration() throws Exception {
        active(35901);
        player.setWeather(1);
        player.packets.clear();
        assertFalse(restore());
        assertEquals(1, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void finishedQuestDoesNotRestoreWeather() throws Exception {
        var quest = active(35901);
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        assertFalse(restore());
        assertEquals(0, player.getWeatherId());
    }

    @Test
    void unrelatedSceneDoesNotRestoreActiveQuestWeather() throws Exception {
        active(35901);
        move(4);
        assertFalse(restore());
        assertEquals(0, player.getWeatherId());
    }

    @Test
    void originalAndriusLuaAreaWorksWithoutAWeatherGadget() throws Exception {
        var library = new ScriptLib();
        library.setSceneScriptManager(player.getScene().getScriptManager());
        assertEquals(0, GameData.getWeatherDataMap().get(4).getGadgetID());
        assertEquals(0, library.SetWeatherAreaState(4, 1));
        assertEquals(4, player.getWeatherId());
        assertEquals(
                ClimateType.CLIMATE_CLOUDY.getValue(), weatherPackets().get(0).getClimateType());
        assertEquals(0, weatherPackets().get(0).getWeatherGadgetId());
        assertEquals(0, library.SetWeatherAreaState(4, 0));
        assertEquals(0, player.getWeatherId());
        assertEquals(
                ClimateType.CLIMATE_SUNNY.getValue(), weatherPackets().get(1).getClimateType());
    }

    @Test
    void gadgetActionDoesNotActivateAnAreaWithoutAGadget() throws Exception {
        var quest = active(35901);
        assertFalse(new ExecSetWeatherGadget().execute(quest, null, "4", "1"));
        assertEquals(0, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void sameSceneOverrideCanBeClearedAfterItsResourceRowDisappears() throws Exception {
        var quest = active(35901);
        player.setWeather(3);
        GameData.getWeatherDataMap().remove(3);
        player.packets.clear();
        assertTrue(new ExecSetWeatherGadget().execute(quest, null, "3", "0"));
        assertEquals(0, player.getWeatherId());
        assertEquals(0, weatherPackets().get(0).getWeatherAreaId());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "['3']",
                "['bad','1']",
                "['3','bad']",
                "['0','1']",
                "['3','2']",
                "['999999','1']",
                "[null,'1']"
            })
    void invalidQuestActionsDoNotChangeWeather(String json) throws Exception {
        var quest = active(35901);
        var args = gson.fromJson(json, String[].class);
        assertFalse(new ExecSetWeatherGadget().execute(quest, null, args));
        assertEquals(0, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    private void weatherActions(int questId, String begin, String finish) throws Exception {
        var json = gson.toJsonTree(GameData.getQuestDataMap().get(questId)).getAsJsonObject();
        json.add("beginExec", JsonParser.parseString(begin));
        json.add("finishExec", JsonParser.parseString(finish));
        var meta = gson.fromJson(json, QuestData.class);
        meta.onLoad();
        GameData.getQuestDataMap().put(questId, meta);
        var main = player.getQuestManager().getMainQuests().get(meta.getMainId());
        var quest = main == null ? active(questId) : main.getChildQuestById(questId);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        // A chapter can already hold this child from its original resource definition.
        quest.setConfig(meta);
        quest.save();
        drain();
    }

    private static String weatherAction(int area, int flag) {
        return "[{type:'QUEST_EXEC_SET_WEATHER_GADGET',param:['" + area + "','" + flag + "']}]";
    }

    @Test
    void oneShotActivationWithoutMatchingFinishIsNotReplayed() throws Exception {
        weatherActions(35901, weatherAction(3, 1), weatherAction(1, 0));
        assertFalse(restore());
        assertEquals(0, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void competingActiveQuestAreasDoNotInventAWinningWeather() throws Exception {
        weatherActions(35901, weatherAction(3, 1), weatherAction(3, 0));
        weatherActions(35902, weatherAction(1, 1), weatherAction(1, 0));
        assertEquals(2, player.getQuestManager().getMainQuests().get(359).getActiveQuests().size());
        assertFalse(restore());
        assertEquals(0, player.getWeatherId());
        assertTrue(weatherPackets().isEmpty());
    }

    @Test
    void duplicateActiveQuestAreasRestoreOnlyOnce() throws Exception {
        weatherActions(35901, weatherAction(3, 1), weatherAction(3, 0));
        weatherActions(35902, weatherAction(3, 1), weatherAction(3, 0));
        assertEquals(2, player.getQuestManager().getMainQuests().get(359).getActiveQuests().size());
        assertTrue(restore());
        assertEquals(3, player.getWeatherId());
        assertEquals(1, weatherPackets().size());
    }

    @Test
    void incompleteOrMalformedNativeWeatherActionsAreNotRestored() throws Exception {
        weatherActions(
                35901,
                "[null,{type:'QUEST_EXEC_SET_WEATHER_GADGET',param:['3']},{type:'QUEST_EXEC_SET_WEATHER_GADGET',param:['bad','1']}]",
                weatherAction(3, 0));
        assertFalse(restore());
        assertEquals(0, player.getWeatherId());
    }

    @Test
    void weatherActionWithoutAGadgetIsNotRestored() throws Exception {
        weatherActions(35901, weatherAction(4, 1), weatherAction(4, 0));
        assertFalse(restore());
        assertEquals(0, player.getWeatherId());
    }
}

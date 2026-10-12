package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerPropChangeNotifyOuterClass.PlayerPropChangeNotify;
import emu.grasscutter.net.proto.PlayerPropNotifyOuterClass.PlayerPropNotify;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;

/** Original 36005 completion plus the real Player property validation and notification path. */
class QuestWeatherLockTest extends QuestTestFixture {
    private static final PlayerProperty LOCK = PlayerProperty.PROP_IS_WEATHER_LOCKED;

    @BeforeEach
    void nativeQuest() throws Exception {
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class)) {
            if (row.getMainId() == 360) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/360.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(360, main);
        GameData.getBeginCondQuestMap().clear();
    }

    private QuestExecHandler handler() throws Exception {
        var member = QuestSystem.class.getDeclaredField("execHandlers");
        member.setAccessible(true);
        var map = (Int2ObjectMap<?>) member.get(player.server.getQuestSystem());
        var handler =
                (QuestExecHandler) map.get(QuestExec.QUEST_EXEC_SET_IS_WEATHER_LOCKED.getValue());
        assertNotNull(handler, "Weather lock exec type 9 has no registered handler");
        return handler;
    }

    private QuestData.QuestExecParam command(int value) {
        return gson.fromJson(
                "{type:'QUEST_EXEC_SET_IS_WEATHER_LOCKED',param:['" + value + "']}",
                QuestData.QuestExecParam.class);
    }

    private void notifyValue(int value, int delta) throws Exception {
        var values =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerPropNotify)
                        .toList();
        var changes =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerPropChangeNotify)
                        .toList();
        assertEquals(1, values.size());
        assertEquals(1, changes.size());
        var valuePacket = PlayerPropNotify.parseFrom(values.get(0).getData());
        assertEquals(1, valuePacket.getPropMapCount());
        assertEquals(value, valuePacket.getPropMapOrThrow(10007).getIval());
        assertEquals(value, valuePacket.getPropMapOrThrow(10007).getVal());
        var change = PlayerPropChangeNotify.parseFrom(changes.get(0).getData());
        assertEquals(10007, change.getPropType());
        assertEquals(delta, change.getPropDelta());
    }

    @Test
    void actualQuestSystemRegistersWeatherLockExecNine() throws Exception {
        assertEquals(
                QuestExec.QUEST_EXEC_SET_IS_WEATHER_LOCKED,
                handler().getClass().getAnnotation(QuestValueExec.class).value());
    }

    @Test
    void original36005FinishExecUnlocksWeatherAndSendsNativePropertyPackets() throws Exception {
        var quest = active(36005);
        var nativeAction =
                quest.getQuestData().getFinishExec().stream()
                        .filter(e -> e.getType() == QuestExec.QUEST_EXEC_SET_IS_WEATHER_LOCKED)
                        .findFirst()
                        .orElseThrow();
        assertArrayEquals(new String[] {"0", "0"}, nativeAction.getParam());
        player.getProperties().put(LOCK.getId(), 1);
        quest.finish();
        drain();
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(0, player.getProperty(LOCK));
        notifyValue(0, -1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void questDispatchUsesRealPropertySetterAndKeepsWeatherAreaAndGameTime(int value)
            throws Exception {
        var quest = active(1100509);
        player.getProperties().put(LOCK.getId(), 1 - value);
        int weather = player.getWeatherId();
        var climate = player.getClimate();
        int timeLocked = player.getProperty(PlayerProperty.PROP_IS_GAME_TIME_LOCKED);
        var action = command(value);
        handler();
        player.server.getQuestSystem().triggerExec(quest, action, action.getParam());
        drain();
        assertEquals(value, player.getProperty(LOCK));
        assertEquals(weather, player.getWeatherId());
        assertEquals(climate, player.getClimate());
        assertEquals(timeLocked, player.getProperty(PlayerProperty.PROP_IS_GAME_TIME_LOCKED));
        assertEquals(2, player.packets.size());
        notifyValue(value, value == 1 ? 1 : -1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "bad", "-1", "2", "2147483648", "1.5", "true", "false"})
    void malformedValuesDoNotChangeStateOrSendNotifications(String value) throws Exception {
        var quest = active(1100509);
        player.getProperties().put(LOCK.getId(), 1);
        assertFalse(handler().execute(quest, null, value));
        assertEquals(1, player.getProperty(LOCK));
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void missingQuestOrParametersReturnFailure() throws Exception {
        var quest = active(1100509);
        var handler = handler();
        assertFalse(handler.execute(null, null, "1"));
        assertFalse(handler.execute(quest, null, (String[]) null));
        assertFalse(handler.execute(quest, null));
        assertFalse(handler.execute(quest, null, (String) null));
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void repeatedAssignmentsUseTheRealZeroDeltaNotification() throws Exception {
        var quest = active(1100509);
        var handler = handler();
        assertTrue(handler.execute(quest, null, " 1 "));
        assertEquals(1, player.getProperty(LOCK));
        player.packets.clear();
        assertTrue(handler.execute(quest, null, "1"));
        notifyValue(1, 0);
    }

    @Test
    void actionUpdatesOnlyItsQuestOwner() throws Exception {
        var quest = active(1100509);
        var guest = new TestPlayer();
        guest.setUid(10002);
        guest.getProperties().put(LOCK.getId(), 0);
        assertTrue(handler().execute(quest, null, "1"));
        assertEquals(1, player.getProperty(LOCK));
        assertEquals(0, guest.getProperty(LOCK));
        assertTrue(guest.packets.isEmpty());
    }
}

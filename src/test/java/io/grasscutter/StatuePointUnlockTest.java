package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.exec.ExecUnlockPoint;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.server.packet.recv.HandlerUnlockTransPointReq;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

/** Unlock rewards must follow current point/gadget data, independently of caller hints. */
class StatuePointUnlockTest extends QuestTestFixture {
    final List<String> rewards = new ArrayList<>();
    Map<Integer, ScenePointEntry> pointsBefore;
    Map<Integer, GadgetData> gadgetsBefore;
    JsonObject points;

    @BeforeAll
    static void lua() throws Exception {
        if (emu.grasscutter.scripts.ScriptLoader.getEngine() == null)
            emu.grasscutter.scripts.ScriptLoader.init();
    }

    @BeforeEach
    void points() throws Exception {
        pointsBefore = new HashMap<>(GameData.getScenePointEntryMap());
        gadgetsBefore = new HashMap<>(GameData.getGadgetDataMap());
        points =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Scene/Point/scene3_point.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("points");
        for (int id : new int[] {7, 31, 1515, 1516, 1517}) putPoint(id, false);
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json")),
                        GadgetData[].class)) {
            GameData.getGadgetDataMap().put(row.getId(), row);
        }
        replaceField(
                Player.class,
                player,
                "inventory",
                new Inventory(player) {
                    @Override
                    public boolean addItem(int item, int count, ActionReason reason) {
                        rewards.add(item + ":" + count);
                        return true;
                    }
                });
        player.getSession().setPlayer(player);
        GameData.getBeginCondQuestMap().clear();
        player.packets.clear();
    }

    void putPoint(int id, boolean omitSpring) {
        var json = points.getAsJsonObject(Integer.toString(id)).deepCopy();
        if (omitSpring) {
            json.remove("maxSpringVolume");
            json.remove("PHBHHNDGFCF");
        }
        var data = gson.fromJson(json, PointData.class);
        data.setId(id);
        GameData.getScenePointEntryMap().put((3 << 16) + id, new ScenePointEntry(3, data));
    }

    @AfterEach
    void restorePoints() throws Exception {
        drain();
        GameData.getScenePointEntryMap().clear();
        GameData.getScenePointEntryMap().putAll(pointsBefore);
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetDataMap().putAll(gadgetsBefore);
    }

    void request(int point) throws Exception {
        new HandlerUnlockTransPointReq()
                .handle(
                        player.getSession(),
                        new byte[0],
                        UnlockTransPointReq.newBuilder()
                                .setSceneId(3)
                                .setPointId(point)
                                .build()
                                .toByteArray());
        drain();
    }

    void expectExp(int exp) {
        assertEquals(List.of("201:5", "102:" + exp), rewards);
    }

    GameQuest quest(int mainId) {
        return new GameQuest() {
            @Override
            public Player getOwner() {
                return player;
            }

            @Override
            public int getMainQuestId() {
                return mainId;
            }
        };
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 1515, 1516, 1517})
    void clientStatueDiscoveryReceivesStatueReward(int point) throws Exception {
        request(point);
        expectExp(50);
        assertTrue(player.getUnlockedScenePoints(3).contains(point));
        request(point);
        expectExp(50);
    }

    @Test
    void clientWaypointKeepsItsNormalReward() throws Exception {
        request(31);
        expectExp(10);
    }

    @Test
    void unrelatedQuestStillRecognizesTheActualStatue() throws Exception {
        assertTrue(new ExecUnlockPoint().execute(quest(999), null, "3", "7"));
        drain();
        expectExp(50);
    }

    @Test
    void statueQuestCannotPromoteANormalWaypointReward() throws Exception {
        assertTrue(new ExecUnlockPoint().execute(quest(303), null, "3", "31"));
        drain();
        expectExp(10);
    }

    @Test
    void legacyFalseHintCannotDemoteAStatue() throws Exception {
        assertTrue(player.getProgressManager().unlockTransPoint(3, 7, false));
        drain();
        expectExp(50);
    }

    @Test
    void legacyTrueHintCannotPromoteAWaypoint() throws Exception {
        assertTrue(player.getProgressManager().unlockTransPoint(3, 31, true));
        drain();
        expectExp(10);
    }

    @Test
    void gadgetCategoryIdentifiesAStatueWhenSpringFieldIsMissing() throws Exception {
        putPoint(1515, true);
        request(1515);
        expectExp(50);
    }

    @Test
    void forceLockStillRejectsClientUnlockAndDoesNotAward() throws Exception {
        assertTrue(player.getProgressManager().lockTransPoint(3, 1515));
        request(1515);
        assertTrue(player.isScenePointForceLocked(3, 1515));
        assertFalse(player.getUnlockedScenePoints(3).contains(1515));
        assertTrue(rewards.isEmpty());
    }

    @Test
    void questLockReleaseDoesNotRepeatStatueReward() throws Exception {
        request(7);
        assertTrue(player.getProgressManager().lockTransPoint(3, 7));
        assertTrue(new ExecUnlockPoint().execute(quest(999), null, "3", "7"));
        drain();
        expectExp(50);
        assertFalse(player.isScenePointForceLocked(3, 7));
    }
}

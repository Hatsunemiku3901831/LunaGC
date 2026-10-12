package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.battlepass.*;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.enums.DungeonType;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.game.GameServer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real settlement notification and battle-pass dispatch with isolated quest state and Mongo. */
class InvestigationDungeonProgressTest extends QuestTestFixture {
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private Map<Integer, BattlePassMissionData> oldMissions;
    private Scene scene;
    private DungeonManager manager;

    @BeforeEach
    void prepareHandbook() throws Exception {
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        oldMissions = new HashMap<>(GameData.getBattlePassMissionDataMap());
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getBattlePassMissionDataMap().clear();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        for (int gate : List.of(1100, 1101, 1102)) player.getOpenStates().put(gate, 1);
        replaceField(Player.class, player, "inventory", new Inventory(player));
        var pass =
                new BattlePassManager(player) {
                    @Override
                    public boolean refreshMissions() {
                        return false;
                    }

                    @Override
                    public void save() {}
                };
        replaceField(Player.class, player, "battlePassManager", pass);
        chapter(10001);
        target(60001, 10001, "TRIGGER_FINISH_DUNGEON", "4300", 5);
        for (var entry : Map.of(900001, "", 900002, "4300").entrySet()) {
            var data =
                    gson.fromJson(
                            "{id:"
                                    + entry.getKey()
                                    + ",progress:5,refreshType:'BATTLE_PASS_MISSION_REFRESH_CYCLE',triggerConfig:{triggerType:'TRIGGER_FINISH_DUNGEON',paramList:['"
                                    + entry.getValue()
                                    + "']}}",
                            BattlePassMissionData.class);
            data.onLoad();
            GameData.getBattlePassMissionDataMap().put(data.getId(), data);
        }
        replaceField(
                GameServer.class,
                player.server,
                "battlePassSystem",
                new BattlePassSystem(player.server));
        scene = player.getScene();
        scene.getPlayers().add(player);
        replaceField(
                Scene.class,
                scene,
                "scriptManager",
                new SceneScriptManager(scene, false) {
                    @Override
                    public Future<?> callEvent(ScriptArgs args) {
                        return CompletableFuture.completedFuture(null);
                    }
                });
        dungeon(4300, DungeonType.DUNGEON_PLOT);
    }

    @AfterEach
    void restoreHandbook() {
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        GameData.getBattlePassMissionDataMap().clear();
        GameData.getBattlePassMissionDataMap().putAll(oldMissions);
    }

    private void chapter(int id) {
        GameData.getInvestigationDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE'}",
                                InvestigationData.class));
    }

    private void target(int id, int parent, String trigger, String ids, int total) {
        GameData.getInvestigationTargetDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",investigationId:"
                                        + parent
                                        + ",progress:"
                                        + total
                                        + ",triggerConfig:{triggerType:'"
                                        + trigger
                                        + "',paramList:['"
                                        + ids
                                        + "','','']}}",
                                InvestigationTargetData.class));
    }

    private void dungeon(int id, DungeonType type) {
        manager =
                new DungeonManager(
                        scene,
                        gson.fromJson(
                                "{id:" + id + ",sceneId:3,type:'" + type.name() + "'}",
                                DungeonData.class));
    }

    private int progress(int id) {
        return player.getPlayerProgress().getInvestigation().get(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUNGEON_PLOT", "DUNGEON_WEEKLY_FIGHT"})
    void qualifyingDungeonPassesItsIdToTheHandbookAndBattlePass(String type) throws Exception {
        dungeon(4300, DungeonType.valueOf(type));
        manager.notifyEndDungeon(true);
        assertEquals(1, progress(60001));
        assertEquals(1, player.getBattlePassManager().loadMissionById(900001).getProgress());
        assertEquals(1, player.getBattlePassManager().loadMissionById(900002).getProgress());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerInvestigationTargetNotify)
                        .findFirst()
                        .orElseThrow();
        var update = PlayerInvestigationTargetNotify.parseFrom(packet.getData());
        assertEquals(60001, update.getInvestigationTargetList(0).getQuestId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUNGEON_DAILY_FIGHT", "DUNGEON_ACTIVITY"})
    void handbookCountsSuccessfulDungeonsThatDoNotQualifyForBattlePass(String type) {
        dungeon(4300, DungeonType.valueOf(type));
        manager.notifyEndDungeon(true);
        assertEquals(1, progress(60001));
        assertEquals(0, player.getBattlePassManager().loadMissionById(900001).getProgress());
    }

    @ParameterizedTest
    @CsvSource({"60034,4300", "60040,5000", "60052,4200", "60067,2021", "60078,2022"})
    void actualResourceDungeonListsAreSatisfiedThroughTheSettlementEntry(
            int targetId, int dungeonId) throws Exception {
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of(
                                        "resources/ExcelBinOutput/InvestigationTargetConfigData.json")),
                        InvestigationTargetData[].class);
        var target =
                Arrays.stream(rows)
                        .filter(row -> row.getId() == targetId)
                        .findFirst()
                        .orElseThrow();
        chapter(target.getInvestigationId());
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().put(targetId, target);
        var dungeons =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/DungeonExcelConfigData.json")),
                        DungeonData[].class);
        var data =
                Arrays.stream(dungeons)
                        .filter(row -> row.getId() == dungeonId)
                        .findFirst()
                        .orElseThrow();
        dungeon(dungeonId, data.getType());
        manager.notifyEndDungeon(true);
        assertEquals(1, progress(targetId));
    }

    @Test
    void anotherDungeonDoesNotSatisfyAFilteredTarget() {
        dungeon(4301, DungeonType.DUNGEON_PLOT);
        manager.notifyEndDungeon(true);
        assertEquals(0, progress(60001));
        assertEquals(0, player.getBattlePassManager().loadMissionById(900002).getProgress());
    }

    @Test
    void aMalformedDungeonListDoesNotBecomeAWildcard() {
        target(60001, 10001, "TRIGGER_FINISH_DUNGEON", "4300;bad", 5);
        manager.notifyEndDungeon(true);
        assertEquals(0, progress(60001));
    }

    @Test
    void unfilteredHandbookTargetAdvancesOnceThroughTheBattlePassBridge() {
        target(60001, 10001, "TRIGGER_FINISH_DUNGEON", "", 5);
        manager.notifyEndDungeon(true);
        assertEquals(1, progress(60001));
    }

    @Test
    void closedHandbookDoesNotBlockOrdinaryDungeonCompletionOrBattlePass() {
        player.getOpenStates().put(1101, 0);
        manager.notifyEndDungeon(true);
        assertEquals(0, progress(60001));
        assertTrue(player.getPlayerProgress().getCompletedDungeons().contains(4300));
        assertEquals(1, player.getBattlePassManager().loadMissionById(900001).getProgress());
    }

    @Test
    void failedSettlementDoesNotAdvanceHandbookOrBattlePass() {
        manager.notifyEndDungeon(false);
        assertEquals(0, progress(60001));
        assertFalse(player.getPlayerProgress().getCompletedDungeons().contains(4300));
        assertEquals(0, player.getBattlePassManager().loadMissionById(900001).getProgress());
    }

    @Test
    void completingADungeonDoesNotPretendItsFirstRewardWasClaimed() {
        target(62004, 10001, "TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD", "4300", 1);
        manager.notifyEndDungeon(true);
        InvestigationHandbook.refreshAndNotify(player);
        assertEquals(0, progress(62004));
        assertFalse(player.getPlayerProgress().getInvestigation().hasFirstDungeonReward(4300));
    }

    @Test
    void separateCompletedRunsStopAtTheResourceTotal() {
        target(60001, 10001, "TRIGGER_FINISH_DUNGEON", "4300", 2);
        for (int i = 0; i < 4; i++) {
            dungeon(4300, DungeonType.DUNGEON_PLOT);
            manager.notifyEndDungeon(true);
        }
        assertEquals(2, progress(60001));
        assertEquals(4, player.getBattlePassManager().loadMissionById(900001).getProgress());
    }

    @Test
    void aPlayerOutsideTheSettledSceneDoesNotGainProgress() {
        scene.getPlayers().remove(player);
        manager.notifyEndDungeon(true);
        assertEquals(0, progress(60001));
        assertEquals(0, player.getBattlePassManager().loadMissionById(900001).getProgress());
    }
}

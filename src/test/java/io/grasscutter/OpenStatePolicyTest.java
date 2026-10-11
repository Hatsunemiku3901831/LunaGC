package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.data.excels.OpenStateData.OpenStateCondType;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.PlayerProgressManager;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.OpenStateUpdateNotifyOuterClass.OpenStateUpdateNotify;
import emu.grasscutter.server.packet.send.PacketOpenStateUpdateNotify;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

/** Current resource rows through the server condition checker and native login snapshot. */
class OpenStatePolicyTest extends QuestTestFixture {
    private List<OpenStateData> previousStates;
    private Map<Integer, OpenStateData> previousMap;
    private OpenStateData[] resources;

    @BeforeEach
    void setupOpenStates() throws Exception {
        previousStates = new ArrayList<>(GameData.getOpenStateList());
        previousMap = new HashMap<>(GameData.getOpenStateDataMap());
        GameData.getOpenStateList().clear();
        GameData.getOpenStateDataMap().clear();
        resources =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/OpenStateConfigData.json")),
                        OpenStateData[].class);
        replaceField(Configuration.GAME_OPTIONS.resinOptions, "resinUsage", true);
        replaceField(Configuration.GAME_OPTIONS, "unlockAllOpenStatesOnLogin", false);
    }

    private void replaceField(Object owner, String name, Object value) throws Exception {
        super.replaceField(owner.getClass(), owner, name, value);
    }

    private OpenStateData install(OpenStateData state) {
        state.onLoad();
        GameData.getOpenStateDataMap().put(state.getId(), state);
        return state;
    }

    private OpenStateData original(int id) {
        return install(
                Arrays.stream(resources)
                        .filter(state -> state.getId() == id)
                        .findFirst()
                        .orElseThrow());
    }

    private OpenStateData controlled(String json) {
        return install(gson.fromJson(json, OpenStateData.class));
    }

    private Map<Integer, Integer> snapshot() throws Exception {
        return OpenStateUpdateNotify.parseFrom(new PacketOpenStateUpdateNotify(player).getData())
                .getOpenStateMapMap();
    }

    private void refresh() throws Exception {
        player.getProgressManager().tryUnlockOpenStates(false);
        drain();
    }

    private Map<Integer, Integer> loginSnapshot() throws Exception {
        player.packets.clear();
        player.getProgressManager().onPlayerLogin();
        drain();
        var snapshots =
                player.packets.stream()
                        .filter(packet -> packet.getOpcode() == PacketOpcodes.OpenStateUpdateNotify)
                        .toList();
        assertEquals(1, snapshots.size());
        return OpenStateUpdateNotify.parseFrom(snapshots.get(0).getData()).getOpenStateMapMap();
    }

    private void loadFamily(int id) throws Exception {
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class);
        for (var row : rows)
            if (row.getMainId() == id) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest", id + ".json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(id, main);
        GameData.getBeginCondQuestMap().clear();
    }

    @Test
    void nativeSevenOneFieldRetainsItsRealQuestPrerequisite() {
        var state = original(7022);
        assertEquals(1, state.getCond().size());
        assertEquals(OpenStateCondType.OPEN_STATE_COND_QUEST, state.getCond().get(0).getCondType());
        assertEquals(701715, state.getCond().get(0).getParam());
    }

    @Test
    void nativeSevenOneGateCannotOpenBeforeItsQuestIsFinished() throws Exception {
        original(7022);
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(7022));
        assertNotEquals(1, snapshot().get(7022));
    }

    @Test
    void emptyNonDefaultConditionsDoNotAutomaticallyOpenAFeature() throws Exception {
        controlled("{id:990001,cond:[{},{}]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990001));
    }

    @Test
    void unsupportedOfferingRequirementsRemainClosed() throws Exception {
        var condition =
                Arrays.stream(resources)
                        .filter(row -> row.getCond() != null)
                        .flatMap(row -> row.getCond().stream())
                        .filter(
                                cond ->
                                        cond.getCondType()
                                                == OpenStateCondType.OPEN_STATE_OFFERING_LEVEL)
                        .findFirst()
                        .orElseThrow();
        var state = controlled("{id:990004,cond:[" + gson.toJson(condition) + "]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(state.getId()));
        assertNotEquals(1, snapshot().get(state.getId()));
    }

    @Test
    void unknownRequirementCannotBeDiscardedBesideASatisfiedLevelGate() throws Exception {
        controlled(
                "{id:990002,cond:[{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:1},"
                        + "{condType:'FUTURE_REQUIRED_GATE',param:0}]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990002));
    }

    @Test
    void genuineDefaultsAreVisibleWhenResourcesArriveAfterTheManagerWasCreated() throws Exception {
        original(10);
        assertEquals(1, snapshot().get(10));
    }

    @Test
    void storedClosedStateOverridesTheResourceDefaultInTheSnapshot() throws Exception {
        original(10);
        player.getOpenStates().put(10, 0);
        assertEquals(0, snapshot().get(10));
    }

    @Test
    void resinConfigurationStillControlsItsNativeSnapshotValue() throws Exception {
        original(45);
        player.getOpenStates().put(45, 1);
        replaceField(Configuration.GAME_OPTIONS.resinOptions, "resinUsage", false);
        assertEquals(0, snapshot().get(45));
    }

    @Test
    void blacklistAndIgnoredStatesAreNotAutomaticallyOpened() throws Exception {
        controlled("{id:48,defaultState:true,cond:[]}");
        controlled("{id:1404,defaultState:true,cond:[]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(48));
        assertEquals(0, player.getProgressManager().getOpenState(1404));
    }

    @Test
    void clientOwnedDefaultsStillRequireTheirClientRequest() throws Exception {
        controlled("{id:990003,defaultState:true,allowClientOpen:true,cond:[]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990003));
        assertFalse(snapshot().containsKey(990003));
    }

    @Test
    void loginCanSendItsSnapshotWhenStatueResourcesAreUnavailable() throws Exception {
        original(10);
        assertDoesNotThrow(() -> player.getProgressManager().onPlayerLogin());
        drain();
    }

    @Test
    void bulkUnlockIsDisabledByDefaultAndLocalQuestingRemainsEnabled() {
        var options = new ConfigContainer.GameOptions();
        assertFalse(options.unlockAllOpenStatesOnLogin);
        assertTrue(options.questing.enabled);
    }

    @Test
    void legacyConfigDoesNotImplicitlyEnableBulkUnlock() {
        var config =
                gson.fromJson(
                        "{version:13,server:{game:{gameOptions:{questing:{enabled:false}}}}}",
                        ConfigContainer.class);
        assertFalse(config.server.game.gameOptions.unlockAllOpenStatesOnLogin);
        assertFalse(config.server.game.gameOptions.questing.enabled);
    }

    @Test
    void explicitBulkOptionSurvivesConfigurationSerialization() {
        var config =
                gson.fromJson(
                        "{server:{game:{gameOptions:{unlockAllOpenStatesOnLogin:true}}}}",
                        ConfigContainer.class);
        assertTrue(config.server.game.gameOptions.unlockAllOpenStatesOnLogin);
        assertTrue(
                gson.fromJson(gson.toJson(config), ConfigContainer.class)
                        .server
                        .game
                        .gameOptions
                        .unlockAllOpenStatesOnLogin);
        assertEquals(14, new ConfigContainer().version);
    }

    @Test
    void normalLoginPreservesUnmetLevelAndRegionalQuestGates() throws Exception {
        controlled("{id:990010,cond:[{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:60}]}");
        original(6701);
        var map = loginSnapshot();
        assertEquals(0, player.getProgressManager().getOpenState(990010));
        assertEquals(0, player.getProgressManager().getOpenState(6701));
        assertNotEquals(Integer.valueOf(1), map.get(990010));
        assertNotEquals(Integer.valueOf(1), map.get(6701));
    }

    @Test
    void optionalBulkUnlockIsAppliedBeforeTheNativeLoginSnapshot() throws Exception {
        controlled("{id:990011,cond:[{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:60}]}");
        controlled("{id:990012,allowClientOpen:true,cond:[]}");
        controlled("{id:48,defaultState:true,cond:[]}");
        controlled("{id:1404,defaultState:true,cond:[]}");
        replaceField(Configuration.GAME_OPTIONS, "unlockAllOpenStatesOnLogin", true);
        var map = loginSnapshot();
        assertEquals(1, map.get(990011));
        assertEquals(1, map.get(990012));
        assertEquals(0, player.getProgressManager().getOpenState(48));
        assertEquals(0, player.getProgressManager().getOpenState(1404));
        assertTrue(
                player.packets.stream()
                        .noneMatch(
                                packet ->
                                        packet.getOpcode() == PacketOpcodes.OpenStateChangeNotify));
    }

    @Test
    void disablingQuestingKeepsStarterNavigationWithoutBulkUnlockingFeatures() throws Exception {
        original(47);
        original(6701);
        controlled("{id:990013,cond:[{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:60}]}");
        replaceField(Configuration.GAME_OPTIONS.questing, "enabled", false);
        var map = loginSnapshot();
        assertEquals(1, map.get(47));
        assertTrue(player.getUnlockedScenePoints(3).contains(7));
        assertTrue(player.getUnlockedSceneAreas(3).contains(1));
        assertEquals(0, player.getProgressManager().getOpenState(6701));
        assertEquals(0, player.getProgressManager().getOpenState(990013));
    }

    @Test
    void turningBulkUnlockOffDoesNotRevokeAnExistingPlayerState() throws Exception {
        controlled("{id:990014,cond:[{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:60}]}");
        replaceField(Configuration.GAME_OPTIONS, "unlockAllOpenStatesOnLogin", true);
        assertEquals(1, loginSnapshot().get(990014));
        replaceField(Configuration.GAME_OPTIONS, "unlockAllOpenStatesOnLogin", false);
        assertEquals(1, loginSnapshot().get(990014));
        assertEquals(1, player.getProgressManager().getOpenState(990014));
    }

    @Test
    void legacyLevelFieldStillOpensOnlyAfterItsThresholdIsReached() throws Exception {
        controlled("{id:990015,cond:[{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:30}]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990015));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 30);
        refresh();
        assertEquals(1, player.getProgressManager().getOpenState(990015));
    }

    @Test
    void completedQuestCanSatisfyANativeFieldAlongsideALevelRequirement() throws Exception {
        var quest = active(1100509);
        controlled(
                "{id:990016,cond:[{cond_type:'OPEN_STATE_COND_QUEST',param:1100509},"
                        + "{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:30}]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990016));
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990016));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 30);
        refresh();
        assertEquals(1, player.getProgressManager().getOpenState(990016));
        assertEquals(1, snapshot().get(990016));
    }

    @Test
    void parentQuestGateRequiresTheCompletedChapterState() throws Exception {
        var quest = active(1100509);
        controlled("{id:990017,cond:[{condType:'OPEN_STATE_COND_PARENT_QUEST',param:11005}]}");
        refresh();
        assertEquals(0, player.getProgressManager().getOpenState(990017));
        replaceField(
                GameMainQuest.class,
                quest.getMainQuest(),
                "state",
                ParentQuestState.PARENT_QUEST_STATE_FINISHED);
        refresh();
        assertEquals(1, player.getProgressManager().getOpenState(990017));
    }

    @Test
    void replacingAResourceRemovesItsFormerDefaultAndCompatibilityViewEntry() throws Exception {
        original(10);
        assertTrue(PlayerProgressManager.DEFAULT_OPEN_STATES.contains(10));
        controlled("{id:10,cond:[{condType:'OPEN_STATE_COND_QUEST',param:1100509}]}");
        assertFalse(PlayerProgressManager.DEFAULT_OPEN_STATES.contains(10));
        assertFalse(snapshot().containsKey(10));
        assertEquals(
                1, GameData.getOpenStateList().stream().filter(row -> row.getId() == 10).count());
    }

    @Test
    void paimonFallbackRemainsAvailableWithClientOwnedResources() throws Exception {
        original(1);
        assertEquals(1, snapshot().get(1));
        assertTrue(PlayerProgressManager.DEFAULT_OPEN_STATES.contains(1));
    }

    @Test
    void alreadyStoredStateIsNotDowngradedByAnUnsupportedRequirement() throws Exception {
        controlled(
                "{id:990018,cond:[{condType:'OPEN_STATE_CITY_REPUTATION_LEVEL',param:3,param2:4}]}");
        player.getOpenStates().put(990018, 1);
        refresh();
        assertEquals(1, player.getProgressManager().getOpenState(990018));
        assertEquals(1, snapshot().get(990018));
    }

    @Test
    void clientRequestCannotOpenAnUnknownOrUnsupportedRequirement() throws Exception {
        controlled(
                "{id:990019,allowClientOpen:true,cond:[{condType:'FUTURE_REQUIRED_GATE',param:0}]}");
        controlled(
                "{id:990020,allowClientOpen:true,cond:[{condType:'OPEN_STATE_CITY_REPUTATION_LEVEL',param:3,param2:4}]}");
        for (int id : new int[] {990019, 990020}) {
            player.packets.clear();
            player.getProgressManager().setOpenStateFromClient(id, 1);
            drain();
            assertEquals(0, player.getProgressManager().getOpenState(id));
            assertEquals(PacketOpcodes.SetOpenStateRsp, player.packets.get(0).getOpcode());
            assertTrue(
                    player.packets.stream()
                            .noneMatch(
                                    packet ->
                                            packet.getOpcode()
                                                    == PacketOpcodes.OpenStateChangeNotify));
        }
    }

    @Test
    void clientRequestHonorsLevelAndOwnershipAndPublishesAChangeOnce() throws Exception {
        controlled(
                "{id:990021,allowClientOpen:true,cond:[{cond_type:'OPEN_STATE_COND_PLAYER_LEVEL',param:30}]}");
        controlled("{id:990022,cond:[]}");
        for (int id : new int[] {990021, 990022, 990023}) {
            player.getProgressManager().setOpenStateFromClient(id, 1);
            drain();
            assertEquals(0, player.getProgressManager().getOpenState(id));
        }
        player.packets.clear();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 30);
        player.getProgressManager().setOpenStateFromClient(990021, 1);
        drain();
        assertEquals(1, player.getProgressManager().getOpenState(990021));
        assertEquals(
                1,
                player.packets.stream()
                        .filter(packet -> packet.getOpcode() == PacketOpcodes.OpenStateChangeNotify)
                        .count());
        player.packets.clear();
        player.getProgressManager().setOpenStateFromClient(990021, 1);
        drain();
        assertTrue(
                player.packets.stream()
                        .noneMatch(
                                packet ->
                                        packet.getOpcode() == PacketOpcodes.OpenStateChangeNotify));
    }

    @Test
    void nativeAndLegacyConditionsSurviveJsonRoundTrip() {
        var state =
                controlled(
                        "{id:990024,cond:[{cond_type:'OPEN_STATE_COND_QUEST',param:1100509},"
                                + "{condType:'OPEN_STATE_COND_PLAYER_LEVEL',param:30}]}");
        var copy = gson.fromJson(gson.toJson(state), OpenStateData.class);
        assertEquals(OpenStateCondType.OPEN_STATE_COND_QUEST, copy.getCond().get(0).getCondType());
        assertEquals(
                OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL,
                copy.getCond().get(1).getCondType());
        assertEquals(1100509, copy.getCond().get(0).getParam());
    }

    @Test
    void aDeferredSavedStatueChapterDoesNotBreakLoginOrCreateDuplicateProgress() throws Exception {
        loadFamily(303);
        var quest = active(30302);
        GameData.getQuestDataMap().remove(30302);
        replaceField(ResourceLoader.class, null, "loadedAll", true);
        player.getQuestManager().getMainQuests().clear();
        player.getQuestManager().loadFromDatabase();
        drain();
        assertNull(player.getQuestManager().getMainQuestById(303));
        original(10);
        assertEquals(1, loginSnapshot().get(10));
        assertEquals(1, store.getDatabase().getCollection("quests").countDocuments());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, stored(quest).getState());
    }

    @AfterEach
    void restoreOpenStates() throws Exception {
        drain();
        GameData.getOpenStateList().clear();
        GameData.getOpenStateList().addAll(previousStates);
        GameData.getOpenStateDataMap().clear();
        GameData.getOpenStateDataMap().putAll(previousMap);
    }
}

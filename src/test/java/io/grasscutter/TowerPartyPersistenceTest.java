package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.tower.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TowerEnterLevelRspOuterClass.TowerEnterLevelRsp;
import emu.grasscutter.server.game.GameServer;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Real player save/cold loads; selected parties restore without activating them. */
class TowerPartyPersistenceTest extends QuestTestFixture {
    private static final long FIRST = 4294967301L, SECOND = 8589934597L;
    private Map<Integer, TowerFloorData> oldFloors;
    private Map<Integer, TowerLevelData> oldLevels;

    @Entity(value = "players", useDiscriminator = false)
    static class SavedTowerPlayer extends Player {
        @Transient final TeamManager team = new TeamManager(this);
        @Transient GameServer server;
        @Transient final List<BasePacket> packets = new ArrayList<>();

        public SavedTowerPlayer() {
            super();
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    static class OwnedAvatar extends Avatar {
        final int id;
        final long guid;
        final ObjectId oid = new ObjectId();

        OwnedAvatar(int id, long guid) {
            this.id = id;
            this.guid = guid;
        }

        @Override
        public int getAvatarId() {
            return id;
        }

        @Override
        public long getGuid() {
            return guid;
        }

        @Override
        public ObjectId getObjectId() {
            return oid;
        }

        @Override
        public AvatarData getAvatarData() {
            return new AvatarData();
        }

        @Override
        public void recalcStats(boolean notify) {}

        @Override
        public void save() {}
    }

    @BeforeEach
    void resources() {
        oldFloors = new HashMap<>(GameData.getTowerFloorDataMap());
        oldLevels = new HashMap<>(GameData.getTowerLevelDataMap());
        GameData.getTowerFloorDataMap()
                .put(
                        6001,
                        gson.fromJson(
                                "{floorId:6001,floorIndex:1,levelGroupId:6101,teamNum:1}",
                                TowerFloorData.class));
        GameData.getTowerFloorDataMap()
                .put(
                        6002,
                        gson.fromJson(
                                "{floorId:6002,floorIndex:5,levelGroupId:6201,teamNum:2}",
                                TowerFloorData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6101,
                        gson.fromJson(
                                "{levelId:6101,levelIndex:1,levelGroupId:6101}",
                                TowerLevelData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6201,
                        gson.fromJson(
                                "{levelId:6201,levelIndex:1,levelGroupId:6201}",
                                TowerLevelData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6202,
                        gson.fromJson(
                                "{levelId:6202,levelIndex:2,levelGroupId:6201}",
                                TowerLevelData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6203,
                        gson.fromJson(
                                "{levelId:6203,levelIndex:3,levelGroupId:6201}",
                                TowerLevelData.class));
        store.getMapper().map(SavedTowerPlayer.class);
    }

    @AfterEach
    void restoreResources() throws Exception {
        drain();
        GameData.getTowerFloorDataMap().clear();
        GameData.getTowerFloorDataMap().putAll(oldFloors);
        GameData.getTowerLevelDataMap().clear();
        GameData.getTowerLevelDataMap().putAll(oldLevels);
    }

    private void owned(Player model) {
        assertTrue(model.getAvatars().addAvatar(new OwnedAvatar(10000021, FIRST)));
        assertTrue(model.getAvatars().addAvatar(new OwnedAvatar(10000022, SECOND)));
    }

    private SavedTowerPlayer model() {
        var model = new SavedTowerPlayer();
        model.setUid(20115);
        model.server = player.server;
        model.setSession(player.getSession());
        model.setSceneId(3);
        owned(model);
        return model;
    }

    private SavedTowerPlayer cold() throws Exception {
        drain();
        var restored = store.find(SavedTowerPlayer.class).filter(Filters.eq("id", 20115)).first();
        assertNotNull(restored);
        restored.server = player.server;
        restored.setSession(player.getSession());
        owned(restored);
        return restored;
    }

    private Document row() throws Exception {
        drain();
        var row =
                store.getDatabase()
                        .getCollection("players")
                        .find(new Document("_id", 20115))
                        .first();
        assertNotNull(row);
        return row;
    }

    private boolean restore(Player model) throws Exception {
        return (boolean)
                TowerManager.class
                        .getMethod("restoreTemporaryTeams")
                        .invoke(model.getTowerManager());
    }

    private List<List<Long>> selected(Player model) throws Exception {
        return (List<List<Long>>)
                TowerManager.class.getMethod("getAbyssTeamGuids").invoke(model.getTowerManager());
    }

    private List<List<Integer>> live(Player model) throws Exception {
        var field = TeamManager.class.getDeclaredField("temporaryTeam");
        field.setAccessible(true);
        var slots = (List<TeamInfo>) field.get(model.getTeamManager());
        return slots == null
                ? List.of()
                : slots.stream().map(team -> List.copyOf(team.getAvatars())).toList();
    }

    private int activeIndex(Player model) throws Exception {
        var field = TeamManager.class.getDeclaredField("useTemporarilyTeamIndex");
        field.setAccessible(true);
        return field.getInt(model.getTeamManager());
    }

    private void seed(Player model, List<List<Long>> guids, int level) throws Exception {
        model.getTowerManager().tryTeamSelect(6002, List.of(List.of(FIRST), List.of(SECOND)));
        model.save();
        drain();
        var fields =
                new Document("towerData.abyssTeamGuids", guids)
                        .append("towerData.currentLevel", level);
        store.getDatabase()
                .getCollection("players")
                .updateOne(new Document("_id", 20115), new Document("$set", fields));
    }

    @Test
    void successfulSelectionSavesTheTwoFullWidthGuidParties() throws Exception {
        var model = model();
        assertTrue(
                model.getTowerManager()
                        .tryTeamSelect(6002, List.of(List.of(FIRST), List.of(SECOND))));
        assertEquals(
                List.of(List.of(FIRST), List.of(SECOND)),
                row().get("towerData", Document.class).get("abyssTeamGuids"));
    }

    @Test
    void coldLoadRestoresPartiesAndTheCurrentChamberWithoutActivatingThem() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(SECOND)), 1);
        var restored = cold();
        assertTrue(live(restored).isEmpty());
        assertTrue(restore(restored));
        assertEquals(List.of(List.of(10000021), List.of(10000022)), live(restored));
        assertEquals(6202, restored.getTowerManager().getCurrentLevelId());
        assertEquals(-1, activeIndex(restored));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrNullOlderGuidRecordsCanBeMigratedFromValidLiveParties(boolean explicitNull)
            throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(SECOND)), 0);
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20115),
                        new Document(
                                explicitNull ? "$set" : "$unset",
                                new Document(
                                        "towerData.abyssTeamGuids", explicitNull ? null : "")));
        var restored = cold();
        restored.getTeamManager().trySetupTemporaryTeam(List.of(List.of(FIRST), List.of(SECOND)));
        assertTrue(restore(restored));
        assertEquals(
                List.of(List.of(FIRST), List.of(SECOND)),
                row().get("towerData", Document.class).get("abyssTeamGuids"));
    }

    @Test
    void olderRecordWithoutLivePartiesDoesNotInventASquad() throws Exception {
        var model = model();
        seed(model, List.of(), 0);
        var restored = cold();
        assertFalse(restore(restored));
        assertTrue(live(restored).isEmpty());
    }

    @Test
    void invalidStoredGuidDoesNotFallBackToAnUnrelatedLiveParty() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(9999999999L)), 0);
        var restored = cold();
        restored.getTeamManager().trySetupTemporaryTeam(List.of(List.of(SECOND), List.of(FIRST)));
        assertFalse(restore(restored));
        assertEquals(List.of(List.of(10000022), List.of(10000021)), live(restored));
    }

    @Test
    void repeatedStoredAvatarCannotBeRestoredIntoBothHalves() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(FIRST)), 0);
        var restored = cold();
        assertFalse(restore(restored));
        assertTrue(live(restored).isEmpty());
    }

    @Test
    void savedGuidsCannotBeMutatedThroughThePublicSnapshot() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(SECOND)), 0);
        var restored = cold();
        var snapshot = selected(restored);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(0).clear());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertTrue(restore(restored));
        assertEquals(List.of(List.of(FIRST), List.of(SECOND)), selected(restored));
    }

    @Test
    void changingTheRequestListsAfterSelectionCannotChangeTheSavedParties() throws Exception {
        var model = model();
        var request = new ArrayList<List<Long>>();
        request.add(new ArrayList<>(List.of(FIRST)));
        request.add(new ArrayList<>(List.of(SECOND)));
        assertTrue(model.getTowerManager().tryTeamSelect(6002, request));
        request.get(0).clear();
        request.clear();
        assertEquals(
                List.of(List.of(FIRST), List.of(SECOND)),
                row().get("towerData", Document.class).get("abyssTeamGuids"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3, 99})
    void impossibleSavedChamberIndicesDoNotRestore(int level) throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(SECOND)), level);
        var restored = cold();
        assertFalse(restore(restored));
        assertTrue(live(restored).isEmpty());
    }

    @Test
    void changedFloorTeamCountRejectsTheSavedSelection() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(SECOND)), 0);
        GameData.getTowerFloorDataMap()
                .put(
                        6002,
                        gson.fromJson(
                                "{floorId:6002,levelGroupId:6201,teamNum:1}",
                                TowerFloorData.class));
        assertFalse(restore(cold()));
    }

    @Test
    void singleTeamFloorsAreSavedAndRestoredAsOneTeam() throws Exception {
        var model = model();
        assertTrue(model.getTowerManager().tryTeamSelect(6001, List.of(List.of(SECOND, FIRST))));
        var restored = cold();
        assertTrue(restore(restored));
        assertEquals(List.of(List.of(10000022, 10000021)), live(restored));
        assertEquals(6101, restored.getTowerManager().getCurrentLevelId());
    }

    @Test
    void invalidReselectionCannotReplaceThePersistedParties() throws Exception {
        var model = model();
        assertTrue(
                model.getTowerManager()
                        .tryTeamSelect(6002, List.of(List.of(FIRST), List.of(SECOND))));
        assertFalse(
                model.getTowerManager()
                        .tryTeamSelect(6002, List.of(List.of(FIRST), List.of(9999999999L))));
        assertEquals(
                List.of(List.of(FIRST), List.of(SECOND)),
                row().get("towerData", Document.class).get("abyssTeamGuids"));
    }

    @Test
    void entryUsesRestoreValidationAndReturnsTheNativeErrorFieldFourteen() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(9999999999L)), 0);
        var restored = cold();
        player.packets.clear();
        restored.getTowerManager().enterLevel(123);
        assertEquals(1, player.packets.size());
        var packet = player.packets.get(0);
        assertEquals(PacketOpcodes.TowerEnterLevelRsp, packet.getOpcode());
        var response = TowerEnterLevelRsp.parseFrom(packet.getData());
        assertEquals(-1, response.getRetcode());
        assertEquals(0, response.getTowerBuffIdListCount());
        var input = com.google.protobuf.CodedInputStream.newInstance(packet.getData());
        boolean found = false;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (tag == 112) {
                assertEquals(-1, input.readInt32());
                found = true;
            } else input.skipField(tag);
        }
        assertTrue(found);
        assertEquals(-1, activeIndex(restored));
        assertTrue(live(restored).isEmpty());
    }

    @Test
    void mirrorRejectsBadSavedPartiesBeforeSendingOrActivatingAnything() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(9999999999L)), 0);
        var restored = cold();
        restored.getTowerManager().mirrorTeamSetUp(1);
        assertTrue(restored.packets.isEmpty());
        assertEquals(-1, activeIndex(restored));
        assertTrue(live(restored).isEmpty());
    }

    @Test
    void mirrorCannotActivateAnIndexOutsideTheRestoredPartyList() throws Exception {
        var model = model();
        seed(model, List.of(List.of(FIRST), List.of(SECOND)), 0);
        var restored = cold();
        restored.getTowerManager().mirrorTeamSetUp(2);
        assertTrue(restored.packets.isEmpty());
        assertEquals(-1, activeIndex(restored));
        assertEquals(List.of(List.of(10000021), List.of(10000022)), live(restored));
    }
}

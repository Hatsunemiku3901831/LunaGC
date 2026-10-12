package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Uses the real Avatar.setOwner and Player GUID allocator across actual player cold loads. */
class TowerPartyStableIdentityTest extends QuestTestFixture {
    private Map<Integer, TowerFloorData> oldFloors;
    private Map<Integer, TowerLevelData> oldLevels;
    private static final int FIRST = 10000021, SECOND = 10000022;

    @Entity(value = "players", useDiscriminator = false)
    static class SavedPlayer extends Player {
        @Transient final TeamManager team = new TeamManager(this);
        @Transient GameServer server;

        public SavedPlayer() {
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
        public void sendPacket(BasePacket packet) {}
    }

    static class OwnedAvatar extends Avatar {
        final int id;
        final ObjectId oid = new ObjectId();

        OwnedAvatar(int id) {
            this.id = id;
        }

        @Override
        public int getAvatarId() {
            return id;
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
                        6002,
                        gson.fromJson(
                                "{floorId:6002,floorIndex:5,levelGroupId:6201,teamNum:2}",
                                TowerFloorData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6201,
                        gson.fromJson(
                                "{levelId:6201,levelIndex:1,levelGroupId:6201}",
                                TowerLevelData.class));
        store.getMapper().map(SavedPlayer.class);
    }

    @AfterEach
    void restoreResources() throws Exception {
        drain();
        GameData.getTowerFloorDataMap().clear();
        GameData.getTowerFloorDataMap().putAll(oldFloors);
        GameData.getTowerLevelDataMap().clear();
        GameData.getTowerLevelDataMap().putAll(oldLevels);
    }

    private SavedPlayer model() {
        var model = new SavedPlayer();
        model.setUid(20117);
        model.server = player.server;
        model.setSession(player.getSession());
        return model;
    }

    private void own(Player model, int... ids) {
        for (int id : ids) assertTrue(model.getAvatars().addAvatar(new OwnedAvatar(id)));
    }

    private List<List<Long>> guids(Player model) {
        return List.of(
                List.of(model.getAvatars().getAvatarById(FIRST).getGuid()),
                List.of(model.getAvatars().getAvatarById(SECOND).getGuid()));
    }

    private SavedPlayer cold(int... loadOrder) throws Exception {
        drain();
        var model = store.find(SavedPlayer.class).filter(Filters.eq("id", 20117)).first();
        assertNotNull(model);
        model.server = player.server;
        model.setSession(player.getSession());
        own(model, loadOrder);
        return model;
    }

    private Document tower() throws Exception {
        drain();
        return store.getDatabase()
                .getCollection("players")
                .find(new Document("_id", 20117))
                .first()
                .get("towerData", Document.class);
    }

    private List<List<Integer>> live(Player model) throws Exception {
        var field = TeamManager.class.getDeclaredField("temporaryTeam");
        field.setAccessible(true);
        var teams = (List<TeamInfo>) field.get(model.getTeamManager());
        return teams == null
                ? List.of()
                : teams.stream().map(team -> List.copyOf(team.getAvatars())).toList();
    }

    @Test
    void selectedPartiesPersistStableAvatarIdsWithTheirRuntimeGuids() throws Exception {
        var model = model();
        own(model, FIRST, SECOND);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, guids(model)));
        assertEquals(List.of(List.of(FIRST), List.of(SECOND)), tower().get("abyssAvatarIds"));
    }

    @Test
    void differentGuidAllocationOrderCannotSwapTheTwoHalves() throws Exception {
        var model = model();
        own(model, FIRST, SECOND);
        var original = guids(model);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, original));
        var restored = cold(SECOND, FIRST);
        assertEquals(original.get(0).get(0), restored.getAvatars().getAvatarById(SECOND).getGuid());
        assertTrue(restored.getTowerManager().restoreTemporaryTeams());
        assertEquals(List.of(List.of(FIRST), List.of(SECOND)), live(restored));
        assertEquals(guids(restored), restored.getTowerManager().getAbyssTeamGuids());
    }

    @Test
    void oldGuidsThatNoLongerExistCanBeReboundByStableIds() throws Exception {
        var model = model();
        model.getNextGameGuid();
        model.getNextGameGuid();
        own(model, FIRST, SECOND);
        var original = guids(model);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, original));
        var restored = cold(SECOND, FIRST);
        assertNull(restored.getAvatars().getAvatarByGuid(original.get(0).get(0)));
        assertTrue(restored.getTowerManager().restoreTemporaryTeams());
        assertEquals(List.of(List.of(FIRST), List.of(SECOND)), live(restored));
        assertEquals(guids(restored), tower().get("abyssTeamGuids"));
    }

    @Test
    void aMissingOwnedAvatarDoesNotReuseItsRecycledGuid() throws Exception {
        var model = model();
        own(model, FIRST, SECOND);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, guids(model)));
        var restored = cold(SECOND, 10000023);
        assertFalse(restored.getTowerManager().restoreTemporaryTeams());
        assertTrue(live(restored).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyGuidOnlyRecordsCannotBeTrustedAfterColdLoad(boolean explicitNull) throws Exception {
        var model = model();
        own(model, FIRST, SECOND);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, guids(model)));
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20117),
                        new Document(
                                explicitNull ? "$set" : "$unset",
                                new Document(
                                        "towerData.abyssAvatarIds", explicitNull ? null : "")));
        var restored = cold(SECOND, FIRST);
        assertFalse(restored.getTowerManager().restoreTemporaryTeams());
        assertTrue(live(restored).isEmpty());
    }

    @Test
    void legacyGuidOnlyRecordCanBeMigratedWhenTheLivePartyStillMatches() throws Exception {
        var model = model();
        own(model, FIRST, SECOND);
        var selected = guids(model);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, selected));
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20117),
                        new Document("$unset", new Document("towerData.abyssAvatarIds", "")));
        var restored = cold(FIRST, SECOND);
        assertTrue(restored.getTeamManager().trySetupTemporaryTeam(selected));
        assertTrue(restored.getTowerManager().restoreTemporaryTeams());
        assertEquals(List.of(List.of(FIRST), List.of(SECOND)), tower().get("abyssAvatarIds"));
    }

    @Test
    void corruptedStableIdsCannotBeHiddenByValidGuidRows() throws Exception {
        var model = model();
        own(model, FIRST, SECOND);
        assertTrue(model.getTowerManager().tryTeamSelect(6002, guids(model)));
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20117),
                        new Document(
                                "$set",
                                new Document(
                                        "towerData.abyssAvatarIds",
                                        List.of(List.of(FIRST), List.of(FIRST)))));
        assertFalse(cold(FIRST, SECOND).getTowerManager().restoreTemporaryTeams());
    }
}

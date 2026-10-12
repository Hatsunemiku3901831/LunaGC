package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tower.TowerManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Checkpoint save and restore through real player data; avatar resource data is controlled. */
class TowerChamberCheckpointTest extends QuestTestFixture {
    private Map<Integer, TowerFloorData> oldFloors;
    private Map<Integer, TowerLevelData> oldLevels;
    private static final int FIRST = 10000021, SECOND = 10000022;

    @Entity(value = "players", useDiscriminator = false)
    static class SavedPlayer extends Player {
        @Transient final TeamManager team = new TeamManager(this);
        @Transient GameServer server;
        @Transient final List<BasePacket> packets = new ArrayList<>();

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
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    static class OwnedAvatar extends Avatar {
        final int id;
        final ObjectId oid = new ObjectId();
        final AvatarSkillDepotData depot;

        OwnedAvatar(int id, AvatarSkillDepotData depot) {
            this.id = id;
            this.depot = depot;
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
        public AvatarSkillDepotData getSkillDepot() {
            return depot;
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
        for (int i = 0; i < 3; i++)
            GameData.getTowerLevelDataMap()
                    .put(
                            6201 + i,
                            gson.fromJson(
                                    "{levelId:"
                                            + (6201 + i)
                                            + ",levelIndex:"
                                            + (i + 1)
                                            + ",levelGroupId:6201,dungeonId:9001}",
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

    private OwnedAvatar own(Player model, int id, boolean special) {
        var depot =
                gson.fromJson(
                        special
                                ? "{id:901,energySkillData:{id:902,costElemType:'Grass',costElemVal:0,specialEnergyMin:50},elementType:'Grass'}"
                                : "{id:801,energySkillData:{id:802,costElemType:'Grass',costElemVal:60},elementType:'Grass'}",
                        AvatarSkillDepotData.class);
        var avatar = new OwnedAvatar(id, depot);
        assertTrue(model.getAvatars().addAvatar(avatar));
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, id == FIRST ? 37 : 82);
        avatar.setCurrentHp(id == FIRST ? 37 : 82);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, id == FIRST ? 11 : 5);
        avatar.setFightProperty(
                special
                        ? FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY
                        : FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY,
                special ? 80 : 60);
        avatar.setFightProperty(
                special
                        ? FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY
                        : FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY,
                id == FIRST ? 13 : 29);
        return avatar;
    }

    private SavedPlayer model() {
        var model = new SavedPlayer();
        model.setUid(20118);
        model.server = player.server;
        model.setSession(player.getSession());
        own(model, FIRST, false);
        own(model, SECOND, true);
        var first = model.getAvatars().getAvatarById(FIRST);
        var second = model.getAvatars().getAvatarById(SECOND);
        assertTrue(
                model.getTowerManager()
                        .tryTeamSelect(
                                6002,
                                List.of(List.of(first.getGuid()), List.of(second.getGuid()))));
        return model;
    }

    private SavedPlayer cold() throws Exception {
        drain();
        var model = store.find(SavedPlayer.class).filter(Filters.eq("id", 20118)).first();
        assertNotNull(model);
        model.server = player.server;
        model.setSession(player.getSession());
        own(model, SECOND, true);
        own(model, FIRST, false);
        assertTrue(model.getTowerManager().restoreTemporaryTeams());
        return model;
    }

    private boolean capture(Player model) throws Exception {
        return (boolean)
                TowerManager.class.getMethod("captureChamberState").invoke(model.getTowerManager());
    }

    private boolean restore(Player model) throws Exception {
        return (boolean)
                TowerManager.class.getMethod("restoreChamberState").invoke(model.getTowerManager());
    }

    private void spent(Player model) {
        for (var avatar : model.getAvatars()) {
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
            avatar.setCurrentHp(0);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 48);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, 0);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 0);
        }
    }

    private void original(Player model) {
        var first = model.getAvatars().getAvatarById(FIRST);
        var second = model.getAvatars().getAvatarById(SECOND);
        assertEquals(37, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(37, first.getCurrentHp());
        assertEquals(82, second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(82, second.getCurrentHp());
        assertEquals(11, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(5, second.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertEquals(13, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY));
        assertEquals(29, second.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @Test
    void checkpointRestoresBothPartiesWithoutFillingTheirBars() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        assertTrue(restore(model));
        original(model);
    }

    @Test
    void checkpointSurvivesMongoAndGuidReassignment() throws Exception {
        var model = model();
        assertTrue(capture(model));
        var loaded = cold();
        spent(loaded);
        assertTrue(restore(loaded));
        original(loaded);
    }

    @Test
    void repeatCaptureCannotReplaceTheStartWithSpentCombatState() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        assertTrue(capture(model));
        assertTrue(restore(model));
        original(model);
    }

    @Test
    void newSelectionCannotUseAPreviousRunsCheckpoint() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        assertTrue(
                model.getTowerManager()
                        .tryTeamSelect(
                                6002,
                                List.of(
                                        List.of(model.getAvatars().getAvatarById(SECOND).getGuid()),
                                        List.of(
                                                model.getAvatars()
                                                        .getAvatarById(FIRST)
                                                        .getGuid()))));
        assertFalse(restore(model));
        assertEquals(0, model.getAvatars().getAvatarById(FIRST).getCurrentHp());
    }

    @Test
    void changedChamberCannotRestoreThePreviousChambersState() throws Exception {
        var model = model();
        assertTrue(capture(model));
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20118),
                        new Document("$set", new Document("towerData.currentLevel", 1)));
        var loaded = cold();
        spent(loaded);
        assertFalse(restore(loaded));
    }

    @Test
    void missingOwnedCharacterRejectsTheWholeRestore() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        model.getAvatars().getAvatars().remove(SECOND);
        assertFalse(restore(model));
        assertEquals(0, model.getAvatars().getAvatarById(FIRST).getCurrentHp());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, -1})
    void invalidStartHpDoesNotCreateACheckpoint(float invalid) throws Exception {
        var model = model();
        model.getAvatars()
                .getAvatarById(FIRST)
                .setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, invalid);
        assertFalse(capture(model));
        assertFalse(restore(model));
    }

    @Test
    void zeroEnergyIsAValidCheckpointValue() throws Exception {
        var model = model();
        model.getAvatars()
                .getAvatarById(FIRST)
                .setFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, 0);
        assertTrue(capture(model));
        model.getAvatars()
                .getAvatarById(FIRST)
                .setFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, 55);
        assertTrue(restore(model));
        assertEquals(
                0,
                model.getAvatars()
                        .getAvatarById(FIRST)
                        .getFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY));
    }

    @Test
    void restoredValuesAreBoundedByTheCurrentMaxima() throws Exception {
        var model = model();
        assertTrue(capture(model));
        var first = model.getAvatars().getAvatarById(FIRST);
        first.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 20);
        first.setFightProperty(FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY, 8);
        assertTrue(restore(model));
        assertEquals(20, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(8, first.getFightProperty(FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY));
    }

    @Test
    void challengeStartCapturesOnceWithoutReplacingSpentState() throws Exception {
        var model = model();
        model.setScene(player.getScene());
        model.getTowerManager().onBegin();
        spent(model);
        model.getTowerManager().onBegin();
        assertTrue(restore(model));
        original(model);
    }

    @Test
    void restoredEnergyUpdatesTheBackingValueUsedByLaterEntities() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        assertTrue(restore(model));
        var field = Avatar.class.getDeclaredField("currentEnergy");
        field.setAccessible(true);
        assertEquals(13, field.getFloat(model.getAvatars().getAvatarById(FIRST)));
        assertEquals(29, field.getFloat(model.getAvatars().getAvatarById(SECOND)));
    }

    @Test
    void activeEntityIsRevivedAndCanProcessASecondDeath() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        var entity = new EntityAvatar(player.getScene(), model.getAvatars().getAvatarById(FIRST));
        model.getTeamManager().getActiveTeam().add(entity);
        assertFalse(entity.isAlive());
        assertTrue(entity.tryBeginDeath());
        assertTrue(restore(model));
        assertTrue(entity.isAlive());
        assertEquals(11, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        assertTrue(entity.tryBeginDeath());
    }

    @Test
    void changedDepotRejectsTheWholeRestoreBeforeMutatingTheFirstAvatar() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        replaceField(
                AvatarSkillDepotData.class,
                model.getAvatars().getAvatarById(SECOND).getSkillDepot(),
                "id",
                902);
        assertFalse(restore(model));
        assertEquals(0, model.getAvatars().getAvatarById(FIRST).getCurrentHp());
    }

    @Test
    void invalidSecondMaximumRejectsTheWholeRestoreBeforeMutatingTheFirstAvatar() throws Exception {
        var model = model();
        assertTrue(capture(model));
        spent(model);
        model.getAvatars()
                .getAvatarById(SECOND)
                .setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, Float.NaN);
        assertFalse(restore(model));
        assertEquals(0, model.getAvatars().getAvatarById(FIRST).getCurrentHp());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldMissingOrNullCheckpointsCannotInventAStartState(boolean explicitNull) throws Exception {
        var model = model();
        model.save();
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", 20118),
                        new Document(
                                explicitNull ? "$set" : "$unset",
                                new Document("towerData.chamberState", explicitNull ? null : "")));
        assertFalse(restore(cold()));
    }
}

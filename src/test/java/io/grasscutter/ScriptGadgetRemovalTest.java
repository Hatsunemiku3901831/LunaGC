package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.nio.file.*;
import java.util.*;

/** Original Stormterror cleanup Lua, real entity removal, suite creation and cold Mongo records. */
class ScriptGadgetRemovalTest extends QuestTestFixture {
    private static final int[][] SEALS = {
        {133007004, 7}, {133007076, 300}, {133007078, 304}, {133007079, 306}, {133007001, 1}
    };
    private SceneScriptManager scripts;
    private ScriptLib library;
    private SceneGroup origin;
    private Map<Integer, GadgetData> previousGadgets;

    @BeforeAll
    static void initializeLua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void groups() throws Exception {
        store.getMapper().map(SceneGroupInstance.class);
        scripts = player.getScene().getScriptManager();
        library = ScriptLoader.getScriptLib();
        replaceField(GameServer.class, player.server, "dropSystem", new DropSystem(player.server));
        previousGadgets = new HashMap<>(GameData.getGadgetDataMap());
        for (var data :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json")),
                        GadgetData[].class)) {
            GameData.getGadgetDataMap().put(data.getId(), data);
        }
        origin = SceneGroup.of(133007183);
        scripts.loadGroupFromScript(origin);
        assertNotNull(origin.getScript());
        for (var seal : SEALS) {
            var group = SceneGroup.of(seal[0]);
            scripts.registerLocalGroups(List.of(group));
            scripts.loadGroupFromScript(group);
            assertNotNull(group.gadgets.get(seal[1]));
        }
        library.setSceneScriptManager(scripts);
        library.setCurrentGroup(origin);
        drain();
    }

    @AfterEach
    void closeGroups() throws Exception {
        library.removeSceneScriptManager();
        library.removeCurrentGroup();
        library.removeCurrentEntity();
        library.removeCurrentCallParams();
        scripts.onDestroy();
        drain();
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetDataMap().putAll(previousGadgets);
    }

    private SceneGroupInstance instance(int group) {
        return scripts.getGroupInstanceById(group);
    }

    private SceneSuite sealSuite(SceneGroupInstance instance, int config) {
        var suite = new SceneSuite();
        suite.sceneGadgets = List.of(instance.getLuaGroup().gadgets.get(config));
        return suite;
    }

    private EntityGadget spawn(int group, int config) {
        var instance = instance(group);
        var created = scripts.getGadgetsInGroupSuite(instance, sealSuite(instance, config));
        assertEquals(1, created.size());
        var entity = created.get(0);
        player.getScene().getEntities().put(entity.getId(), entity);
        return entity;
    }

    private int kill(String table) throws Exception {
        var bindings = ScriptLoader.getEngine().createBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        return ((Number)
                        ScriptLoader.getEngine()
                                .eval(
                                        "return ScriptLib.KillEntityByConfigId(ScriptLib,"
                                                + table
                                                + ")",
                                        bindings))
                .intValue();
    }

    private void originalCleanup() {
        var event = new ScriptArgs(origin.id, EventType.EVENT_QUEST_FINISH, 39403, 1);
        var context = library.createEventContext(event, origin);
        var args = CoerceJavaToLua.coerce(event);
        assertTrue(
                ((LuaValue) origin.getBindings().get("condition_EVENT_QUEST_FINISH_185"))
                        .call(context, args)
                        .toboolean());
        assertEquals(
                0,
                ((LuaValue) origin.getBindings().get("action_EVENT_QUEST_FINISH_185"))
                        .call(context, args)
                        .toint());
    }

    private SceneGroupInstance cold(int group) throws Exception {
        drain();
        var loaded = DatabaseHelper.loadGroupInstance(group, player);
        assertNotNull(loaded);
        loaded.setLuaGroup(instance(group).getLuaGroup());
        return loaded;
    }

    @Test
    void originalQuestLuaRemovesAllFiveSealsFromTheirOwnGroups() {
        var live = new ArrayList<EntityGadget>();
        for (var seal : SEALS) live.add(spawn(seal[0], seal[1]));
        originalCleanup();
        for (var entity : live) {
            assertNull(
                    player.getScene().getEntityById(entity.getId()),
                    "seal still present in group " + entity.getGroupId());
            assertTrue(
                    instance(entity.getGroupId()).getDeadEntities().contains(entity.getConfigId()));
        }
    }

    @Test
    void originalQuestLuaPersistsUnspawnedSealsAndColdSuiteDoesNotReviveThem() throws Exception {
        assertTrue(player.getScene().getEntities().isEmpty());
        originalCleanup();
        for (var seal : SEALS) {
            var restored = cold(seal[0]);
            assertTrue(
                    scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, seal[1]))
                            .isEmpty(),
                    "cold suite revived seal in group " + seal[0]);
            var raw =
                    store.getDatabase()
                            .getCollection("group_instances")
                            .find(
                                    new Document("ownerUid", player.getUid())
                                            .append("groupId", seal[0]))
                            .first();
            assertNotNull(raw);
            assertEquals(List.of(seal[1]), raw.getList("scriptDestroyedGadgets", Integer.class));
        }
    }

    @Test
    void explicitDestinationWorksWithoutAnOriginContext() throws Exception {
        var target = spawn(SEALS[0][0], SEALS[0][1]);
        library.removeCurrentGroup();
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        assertNull(player.getScene().getEntityById(target.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{config_id=7}", "{group_id=0,config_id=7}"})
    void omittedOrZeroDestinationRetainsCurrentGroupSemantics(String request) throws Exception {
        library.setCurrentGroup(instance(133007004).getLuaGroup());
        var target = spawn(133007004, 7);
        assertEquals(0, kill(request));
        assertNull(player.getScene().getEntityById(target.getId()));
    }

    @Test
    void explicitDestinationNeverKillsTheSameConfigInTheCallingGroup() throws Exception {
        var target = spawn(133007004, 7);
        var other = new EntityGadget(player.getScene(), target.getGadgetId(), target.getPosition());
        other.setGroupId(origin.id);
        other.setConfigId(7);
        player.getScene().getEntities().put(other.getId(), other);
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        assertNull(player.getScene().getEntityById(target.getId()));
        assertSame(other, player.getScene().getEntityById(other.getId()));
    }

    @Test
    void repeatedRemovalIsIdempotentAndScopedToTheGroup() throws Exception {
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        var restored = cold(133007004);
        assertTrue(scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, 7)).isEmpty());
        assertEquals(
                1,
                scripts.getGadgetsInGroupSuite(
                                instance(133007001), sealSuite(instance(133007001), 1))
                        .size());
    }

    @Test
    void offGridDestinationUsesTheRealGroupLoadPathAndKeepsItsColdRemoval() throws Exception {
        var group = instance(133007004).getLuaGroup();
        group.block_id = 3007;
        scripts.getLoadedGroupSetPerBlock().put(group.block_id, new HashSet<>());
        scripts.unregisterGroup(group);
        assertNull(scripts.getGroupInstanceById(group.id));
        assertNull(player.getScene().getEntityByConfigId(7, group.id));
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        assertSame(group, scripts.getLoadedGroupById(group.id));
        assertTrue(player.getScene().getLoadedGroups().contains(group));
        assertNull(player.getScene().getEntityByConfigId(7, group.id));
        var restored = cold(group.id);
        assertTrue(scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, 7)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{config_id=0}",
                "{config_id=-1}",
                "{config_id=7.5}",
                "{config_id='7'}",
                "{config_id=true}",
                "{config_id=2147483648}",
                "{group_id=-1,config_id=7}",
                "{group_id='133007004',config_id=7}",
                "{group_id=133007004.5,config_id=7}",
                "{group_id=2147483648,config_id=7}"
            })
    void malformedIdsDoNotTouchEntitiesOrPersistence(String request) throws Exception {
        library.setCurrentGroup(instance(133007004).getLuaGroup());
        var target = spawn(133007004, 7);
        assertEquals(1, kill(request));
        assertSame(target, player.getScene().getEntityById(target.getId()));
        assertFalse(cold(133007004).getDeadEntities().contains(7));
    }

    @Test
    void absentOrDestroyedSceneContextIsRejected() throws Exception {
        library.removeSceneScriptManager();
        assertEquals(1, kill("{group_id=133007004,config_id=7}"));
        library.setSceneScriptManager(scripts);
        scripts.onDestroy();
        assertEquals(1, kill("{group_id=133007004,config_id=7}"));
    }

    @Test
    void missingDestinationDoesNotFallBackToTheCallingGroup() throws Exception {
        library.setCurrentGroup(instance(133007004).getLuaGroup());
        var target = spawn(133007004, 7);
        assertEquals(1, kill("{group_id=999,config_id=7}"));
        assertSame(target, player.getScene().getEntityById(target.getId()));
    }

    @Test
    void ordinaryGadgetDeathKeepsItsExistingRespawnPolicy() throws Exception {
        var group = instance(133007004);
        var gadget = group.getLuaGroup().gadgets.get(7);
        assertFalse(gadget.isOneoff);
        player.getScene().killEntity(spawn(133007004, 7));
        group.save();
        var restored = cold(133007004);
        assertTrue(restored.getDeadEntities().contains(7));
        assertEquals(1, scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, 7)).size());
        gadget.isOneoff = true;
        gadget.persistent = true;
        assertTrue(scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, 7)).isEmpty());
    }

    @Test
    void explicitActivityRoundResetClearsScriptRemovalAlongsideOtherRoundState() throws Exception {
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        var restored = cold(133007004);
        assertTrue(scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, 7)).isEmpty());
        restored.reset();
        restored.save();
        drain();
        var reset = DatabaseHelper.loadGroupInstance(133007004, player);
        reset.setLuaGroup(restored.getLuaGroup());
        assertEquals(1, scripts.getGadgetsInGroupSuite(reset, sealSuite(reset, 7)).size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAndExplicitNullLegacyMarkerFieldsCanBeReadAndWritten(boolean explicitNull)
            throws Exception {
        var update = explicitNull ? "$set" : "$unset";
        var collection = store.getDatabase().getCollection("group_instances");
        assertEquals(
                1,
                collection
                        .updateOne(
                                new Document("ownerUid", player.getUid())
                                        .append("groupId", 133007004),
                                new Document(
                                        update,
                                        new Document(
                                                "scriptDestroyedGadgets",
                                                explicitNull ? null : "")))
                        .getMatchedCount());
        var restored = cold(133007004);
        scripts.getCachedGroupInstances().put(133007004, restored);
        scripts.loadGroupFromScript(restored.getLuaGroup());
        assertEquals(1, scripts.getGadgetsInGroupSuite(restored, sealSuite(restored, 7)).size());
        assertEquals(0, kill("{group_id=133007004,config_id=7}"));
        var reloaded = cold(133007004);
        assertTrue(scripts.getGadgetsInGroupSuite(reloaded, sealSuite(reloaded, 7)).isEmpty());
    }
}

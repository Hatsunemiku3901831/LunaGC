package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.quest.content.ContentClearGroupMonster;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

/** Original group objectives through real events, scene entities and isolated Mongo. */
class QuestGroupClearTest extends QuestTestFixture {
    private static List<JsonObject> originalRows;

    @BeforeAll
    static void readActualObjectives() throws Exception {
        originalRows = new ArrayList<>();
        var all =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/QuestExcelConfigData.json")))
                        .getAsJsonArray();
        for (var entry : all) {
            var row = entry.getAsJsonObject();
            if (row.get("mainId").getAsInt() == 397 || row.get("mainId").getAsInt() == 388)
                originalRows.add(row);
        }
    }

    private void family(int parentId) throws Exception {
        for (var row : originalRows)
            if (row.get("mainId").getAsInt() == parentId) {
                var data = gson.fromJson(row, QuestData.class);
                data.onLoad();
                GameData.getQuestDataMap().put(data.getId(), data);
            }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest", parentId + ".json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(parentId, main);
        GameData.getBeginCondQuestMap().clear();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, SceneGroupInstance> groups() throws Exception {
        var field = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances");
        field.setAccessible(true);
        return (Map<Integer, SceneGroupInstance>) field.get(player.getScene().getScriptManager());
    }

    private SceneGroup group(int id, int... configs) throws Exception {
        var result = SceneGroup.of(id);
        result.monsters = new HashMap<>();
        for (int config : configs) {
            var row = new SceneMonster();
            row.config_id = config;
            row.monster_id = 21010101;
            row.group = result;
            result.monsters.put(config, row);
        }
        groups().put(id, new SceneGroupInstance(result, player));
        return result;
    }

    private EntityMonster monster(SceneGroup group, int config, int entityId) {
        var data =
                gson.fromJson(
                        "{id:21010101,type:'MONSTER_ORDINARY',equips:[],affix:[],hpDrops:[]}",
                        MonsterData.class);
        var entity =
                new EntityMonster(player.getScene(), data, new Position(), new Position(), 1) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public void recalcStats() {}
                };
        entity.setId(entityId);
        entity.setGroupId(group.id);
        entity.setConfigId(config);
        entity.setMetaMonster(group.monsters.get(config));
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        player.getScene().addEntity(entity);
        return entity;
    }

    private void dieWhileMapped(EntityMonster entity) {
        assertTrue(
                player.getScene().getPlayers().isEmpty(),
                "Death fixture must not run unrelated player rewards");
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        entity.onDeath(0);
        assertTrue(entity.isDead());
        assertSame(entity, player.getScene().getEntityById(entity.getId()));
    }

    private boolean cleared(int id) {
        return player.getScene().getScriptManager().isClearedGroupMonsters(id);
    }

    @ParameterizedTest
    @ValueSource(ints = {39703, 38802})
    void otherGroupDeathCannotCompleteAnOriginalObjective(int id) throws Exception {
        family(id / 100);
        var quest = active(id);
        int target = quest.getQuestData().getFinishCond().get(0).getParam()[0];
        group(target);
        event(
                QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER,
                target == 133002233 ? 133004015 : 133002233);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, stored(quest).getState());
        assertTrue(notifications().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {39703, 38802})
    void matchingOriginalEmptyGroupCanCompleteAndPersist(int id) throws Exception {
        family(id / 100);
        var quest = active(id);
        int target = quest.getQuestData().getFinishCond().get(0).getParam()[0];
        group(target);
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, target);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertArrayEquals(new int[] {1}, stored(quest).getFinishProgressList());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
        assertEquals(1, notifications().get(0).getFinishProgressList(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {39703, 38802})
    void originalObjectiveCompletesAfterLastMonsterDeathBeforeRemoval(int id) throws Exception {
        family(id / 100);
        var quest = active(id);
        int target = quest.getQuestData().getFinishCond().get(0).getParam()[0];
        var group = group(target, 1);
        var entity = monster(group, 1, 100);
        assertFalse(cleared(target));
        dieWhileMapped(entity);
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, target);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
        assertSame(entity, player.getScene().getEntityById(entity.getId()));
    }

    @Test
    void livingDeclaredMonsterStillBlocksMatchingEvent() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_CLEAR_GROUP_MONSTER',param:[133002233]}]",
                        "[]",
                        "LOGIC_AND");
        var group = group(133002233, 1);
        monster(group, 1, 100);
        event(QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER, group.id);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
    }

    @Test
    void zeroHpBeforeDeathProcessingIsStillNotASettledClear() throws Exception {
        var group = group(133002233, 1);
        var entity = monster(group, 1, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        assertFalse(entity.isDead());
        assertFalse(cleared(group.id));
    }

    @Test
    void missingGroupDoesNotCountAsCleared() {
        assertFalse(cleared(133002233));
    }

    @Test
    void emptyLoadedGroupIsCleared() throws Exception {
        var group = group(133002233);
        assertTrue(cleared(group.id));
    }

    @Test
    void livingMonsterInAnotherGroupDoesNotBlockTheTarget() throws Exception {
        var target = group(133002233, 1);
        var other = group(133004015, 1);
        monster(other, 1, 100);
        assertTrue(cleared(target.id));
        assertFalse(cleared(other.id));
    }

    @Test
    void undeclaredConfigKeepsExistingMembershipRules() throws Exception {
        var group = group(133002233, 1);
        monster(group, 99, 100);
        assertTrue(cleared(group.id));
    }

    @Test
    void livingReplacementSharingACorpseConfigStillBlocks() throws Exception {
        var group = group(133002233, 1);
        var dead = monster(group, 1, 100);
        dieWhileMapped(dead);
        var replacement = monster(group, 1, 101);
        assertSame(dead, player.getScene().getEntityByConfigId(1, group.id));
        assertFalse(replacement.isDead());
        assertFalse(cleared(group.id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{param:null}", "{param:[]}", "{param:[0]}", "{param:[-1]}"})
    void malformedGroupObjectiveCannotAccessASceneOrComplete(String json) {
        var condition = gson.fromJson(json, QuestData.QuestContentCondition.class);
        assertFalse(new ContentClearGroupMonster().execute(null, condition, "", 133002233));
    }

    @Test
    void emptyEventDoesNotReadTheScene() {
        var condition = gson.fromJson("{param:[133002233]}", QuestData.QuestContentCondition.class);
        assertFalse(new ContentClearGroupMonster().execute(null, condition, ""));
    }

    @Test
    void nullEventDoesNotReadTheScene() {
        var condition = gson.fromJson("{param:[133002233]}", QuestData.QuestContentCondition.class);
        assertFalse(new ContentClearGroupMonster().execute(null, condition, "", (int[]) null));
    }

    @Test
    void unrelatedEventDoesNotReadTheScene() {
        var condition = gson.fromJson("{param:[133002233]}", QuestData.QuestContentCondition.class);
        assertFalse(new ContentClearGroupMonster().execute(null, condition, "", 133004015));
    }
}

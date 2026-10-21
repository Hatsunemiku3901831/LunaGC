package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tower.TowerBattleProfile;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;

import java.util.*;

class TowerSpawnIntegrationTest {
    private AbilityTestFixture env;
    private final Gson gson = new Gson();
    private final Map<Integer, MonsterData> originals = new HashMap<>();
    private DungeonManager manager;

    @BeforeEach
    void setup() throws Exception {
        env = new AbilityTestFixture();
        GameData.setConfigGlobalCombat(
                gson.fromJson(
                        "{defaultAbilities:{defaultMPLevelAbilities:[],levelElementAbilities:[],nonHumanoidMoveAbilities:[]}}",
                        emu.grasscutter.data.binout.config.ConfigGlobalCombat.class));
        for (int id : List.of(99100001, 99100002)) {
            originals.put(id, GameData.getMonsterDataMap().get(id));
            GameData.getMonsterDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",hpBase:1000,attackBase:100,defenseBase:50,affix:[1026],propGrowCurves:[],equips:[]}",
                                    MonsterData.class));
        }
        manager =
                new DungeonManager(
                        env.scene,
                        gson.fromJson(
                                "{id:3106,sceneId:3,showLevel:1,passCond:0}", DungeonData.class));
        manager.setTowerDungeon(true);
        manager.configureTower(TowerBattleProfile.DEFAULT, 12, 95);
        env.scene.setDungeonManager(manager);
    }

    @AfterEach
    void cleanup() {
        originals.forEach(
                (id, data) -> {
                    if (data == null) GameData.getMonsterDataMap().remove((int) id);
                    else GameData.getMonsterDataMap().put((int) id, data);
                });
        if (env != null) {
            env.scene.getScriptManager().onDestroy();
            env.close();
        }
    }

    private SceneMonster definition() {
        var m = new SceneMonster();
        m.config_id = 4001;
        m.monster_id = 99100001;
        m.pos = new Position(1, 0, 2);
        m.rot = new Position();
        m.affix = List.of(6105, 1026);
        m.ai_config_id = 77;
        return m;
    }

    @Test
    void scriptedSpawnCarriesMetadataAndFloorStatsBeforeAnyPlayerArrives() {
        assertTrue(env.scene.getPlayers().isEmpty());
        var monster = env.scene.getScriptManager().createMonster(233746004, 33746, definition());
        assertNotNull(monster);
        assertEquals(95, monster.getLevel());
        assertEquals(2500, monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(List.of(6105, 1026), monster.getEffectiveAffixes());
        assertEquals(77, monster.toProto().getMonster().getAiConfigId());
        assertEquals(List.of(6105, 1026), monster.toProto().getMonster().getAffixListList());
    }

    @Test
    void customSpawnUsesIdentityOverrideAndRecalculationPreservesHealthRatio() {
        manager.configureTower(
                new TowerBattleProfile(
                        62,
                        2,
                        3,
                        4,
                        5,
                        List.of(
                                new TowerBattleProfile.MonsterOverride(
                                        233746004, 4001, 99100002, 88, 1.5, 2, 0.5))),
                12,
                95);
        var metadata = definition();
        var monster = env.scene.getScriptManager().createMonster(233746004, 33746, metadata);
        assertEquals(99100002, monster.getMonsterData().getId());
        assertEquals(88, monster.getLevel());
        assertEquals(7500, monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(600, monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        assertEquals(100, monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE));
        monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 3750);
        monster.recalcStats();
        monster.recalcStats();
        assertEquals(7500, monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(3750, monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(99100001, metadata.monster_id);
        assertEquals(List.of(6105, 1026), metadata.affix);
    }

    @Test
    void positionSpawnAlsoUsesOverridesAndUnrelatedGroupsAreUnchanged() {
        manager.configureTower(
                new TowerBattleProfile(
                        62,
                        1,
                        1,
                        1,
                        5,
                        List.of(
                                new TowerBattleProfile.MonsterOverride(
                                        233746004, 4001, 99100002, 88, 1, 1, 1))),
                8,
                95);
        var group = SceneGroup.of(233746004);
        group.monsters = Map.of(4001, definition());
        var monster =
                env.scene
                        .getScriptManager()
                        .createMonsterByConfigIdByPos(group, 4001, new Position(9, 0, 8), null);
        assertEquals(88, monster.getLevel());
        assertEquals(99100002, monster.getMonsterData().getId());
        assertEquals(9, monster.getPosition().getX());
        var other = env.scene.getScriptManager().createMonster(233746005, 33746, definition());
        assertEquals(100, other.getLevel());
        assertEquals(99100001, other.getMonsterData().getId());
        assertEquals(2000, other.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
    }

    @Test
    void ordinaryMonstersDoNotReceiveTowerMultipliers() {
        manager.setTowerDungeon(false);
        var monster =
                new EntityMonster(
                        env.scene,
                        GameData.getMonsterDataMap().get(99100001),
                        new Position(),
                        new Position(),
                        1);
        assertEquals(1000, monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
    }
}

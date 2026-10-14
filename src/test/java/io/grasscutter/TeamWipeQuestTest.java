package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;

class TeamWipeQuestTest extends QuestTestFixture {
    AvatarSkillDepotData depot;

    @BeforeEach
    void party() throws Exception {
        replaceField(
                emu.grasscutter.Grasscutter.class,
                null,
                "pluginManager",
                new emu.grasscutter.plugin.PluginManager());
        player.getTeamManager().setEntity(new EntityTeam(player));
        player.getTeamManager().getActiveTeam().clear();
        player.getScene().getPlayers().add(player);
        depot =
                gson.fromJson(
                        "{id:7301,skills:[],energySkillData:{id:7308,costElemType:'Grass',costElemVal:50},elementType:'Grass'}",
                        AvatarSkillDepotData.class);
    }

    Member member() {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000073;
                    }

                    @Override
                    public AvatarSkillDepotData getSkillDepot() {
                        return depot;
                    }

                    @Override
                    public void save() {}
                };
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100f);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), 100f);
        var entity = new Member(avatar);
        player.getTeamManager().getActiveTeam().add(entity);
        player.getScene().getEntities().put(entity.getId(), entity);
        return entity;
    }

    class Member extends EntityAvatar {
        Runnable afterDeath;

        Member(Avatar avatar) {
            super(player.getScene(), avatar);
        }

        @Override
        public void initAbilities() {}

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }

        @Override
        public void onDeath(int killer) {
            super.onDeath(killer);
            if (afterDeath != null) afterDeath.run();
        }
    }

    GameQuest watching() throws Exception {
        return controlled(
                "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0,0],param_str:'notDone'}]",
                "[{type:'QUEST_CONTENT_TEAM_DEAD',param:[0,0]}]",
                "LOGIC_AND");
    }

    void kill(Member avatar) throws Exception {
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        avatar.checkIfDead();
        player.getScene().killEntity(avatar);
        drain();
    }

    @Test
    void lethalDamageOfFinalPartyMemberFailsQuestAndPersists() throws Exception {
        var quest = watching();
        var avatar = member();
        avatar.damage(100);
        drain();
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
        assertArrayEquals(new int[] {1}, quest.getFailProgressList());
        assertEquals(QuestState.QUEST_STATE_FAILED, stored(quest).getState());
    }

    @Test
    void onlyLastDeathFailsTheQuest() throws Exception {
        var quest = watching();
        var first = member();
        var last = member();
        kill(first);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        kill(last);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
    }

    @Test
    void concurrentFinalDeathsCountAsOneWipe() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0,0],param_str:'notDone'}]",
                        "[{type:'QUEST_CONTENT_TEAM_DEAD',param:[0,0],count:2}]",
                        "LOGIC_AND");
        var first = member();
        var second = member();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        Runnable rendezvous =
                () ->
                        assertDoesNotThrow(
                                () -> barrier.await(5, java.util.concurrent.TimeUnit.SECONDS));
        first.afterDeath = rendezvous;
        second.afterDeath = rendezvous;
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> player.getScene().killEntity(first));
            var b = workers.submit(() -> player.getScene().killEntity(second));
            a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            b.get(10, java.util.concurrent.TimeUnit.SECONDS);
            drain();
            assertArrayEquals(new int[] {1}, quest.getFailProgressList());
            assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void twoSeparateLivesCountAsTwoWipes() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_LUA_NOTIFY',param:[0,0],param_str:'notDone'}]",
                        "[{type:'QUEST_CONTENT_TEAM_DEAD',param:[0,0],count:2}]",
                        "LOGIC_AND");
        var avatar = member();
        kill(avatar);
        assertArrayEquals(new int[] {1}, quest.getFailProgressList());
        assertTrue(avatar.revive(50));
        player.getScene().getEntities().put(avatar.getId(), avatar);
        kill(avatar);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
    }

    @Test
    void offstageMemberStillPreventsTeamWipe() throws Exception {
        var quest = watching();
        var fallen = member();
        var offstage = member();
        player.getScene().getEntities().remove(offstage.getId());
        kill(fallen);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void avatarOutsideActivePartyCannotFailQuest() throws Exception {
        var quest = watching();
        var deadParty = member();
        deadParty.onDeath(0);
        var detached = member();
        player.getTeamManager().getActiveTeam().remove(detached);
        kill(detached);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void staleSceneAvatarCannotFailCurrentPlayerQuest() throws Exception {
        var quest = watching();
        var avatar = member();
        var scene = player.getScene();
        player.setScene(null);
        try {
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
            scene.killEntity(avatar);
            drain();
            assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        } finally {
            player.setScene(scene);
        }
    }

    @Test
    void successfulDeathCallbackRevivalDoesNotFailQuest() throws Exception {
        var quest = watching();
        var avatar = member();
        avatar.afterDeath = () -> avatar.revive(50);
        kill(avatar);
        assertTrue(avatar.isAlive());
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void repeatedDeathCannotFailQuestAcceptedAfterTheWipe() throws Exception {
        var avatar = member();
        kill(avatar);
        var quest = watching();
        player.getScene().killEntity(avatar);
        drain();
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void revivalAllowsAFutureWipeToFailANewQuest() throws Exception {
        var avatar = member();
        kill(avatar);
        assertTrue(avatar.revive(50));
        player.getScene().getEntities().put(avatar.getId(), avatar);
        var quest = watching();
        kill(avatar);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    void unrelatedEventFlagsDoNotFail(int flag) throws Exception {
        var quest = watching();
        event(QuestContent.QUEST_CONTENT_TEAM_DEAD, flag);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void missingFlagDoesNotFail() throws Exception {
        var quest = watching();
        event(QuestContent.QUEST_CONTENT_TEAM_DEAD, "", new int[0]);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
    }

    @Test
    void explicitWipeEventFailsWithoutInventingConditionParams() throws Exception {
        var quest = watching();
        event(QuestContent.QUEST_CONTENT_TEAM_DEAD, 1);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
    }

    @Test
    void drowningFinalMemberAlsoFailsQuest() throws Exception {
        var quest = watching();
        var avatar = member();
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        avatar.onDeath(PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN, 0);
        player.getScene().killEntity(avatar);
        drain();
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
    }

    @Test
    void actualResourceQuestFailsThroughOriginalCondition() throws Exception {
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/4005.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(4005, main);
        var rows =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class);
        for (var row : rows)
            if (row.getMainId() == 4005) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        GameData.getBeginCondQuestMap().clear();
        var quest = active(400517);
        var avatar = member();
        kill(avatar);
        assertEquals(QuestState.QUEST_STATE_FAILED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FAILED, stored(quest).getState());
    }
}

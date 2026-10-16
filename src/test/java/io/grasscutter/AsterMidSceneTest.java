package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.GuardedPlayerTestStore;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ActivityUpdateWatcherNotifyOuterClass.ActivityUpdateWatcherNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoNotifyOuterClass.GadgetGeneralRewardInfoNotify;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass.GadgetInteractRsp;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SealBattleBeginNotifyOuterClass.SealBattleBeginNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * Real original Lua, seal engine, reward entity and inventory, with isolated persistence/EXP sinks.
 */
class AsterMidSceneTest {
    private AbilityTestFixture environment;
    private AsterMidTestFixture resources;
    private RecoveryPlayer player;
    private AbilityTestFixture.TestScene scene;
    private TrackedScripts scripts;
    private EntityAvatar avatar;
    private AsterMidCamps.Camp camp;
    private SceneGroup core, battle;
    private EntityGadget seal;
    private Object originalDatastore, originalConfiguration;
    private Field datastoreField, configurationField;
    private final Map<Integer, MonsterData> originalMonsters = new HashMap<>();
    private final List<String> persistedDetails = new CopyOnWriteArrayList<>();
    private boolean resinUsage;
    private GuardedPlayerTestStore playerStore;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        resources = new AsterMidTestFixture();
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastoreField.setAccessible(true);
        originalDatastore = datastoreField.get(null);
        datastoreField.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    && args[0] instanceof PlayerActivityData data)
                                persistedDetails.add(data.getDetail());
                            try {
                                return method.invoke(playerStore.datastore(), args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        }));
        configurationField = ActivityManager.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        originalConfiguration = configurationField.get(null);
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(2001, resources.config),
                        Map.of(resources.config.getScheduleId(), resources.config)));
        resinUsage = emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        GameData.setConfigGlobalCombat(
                JsonUtils.decode(
                        "{\"defaultAbilities\":{\"defaultMPLevelAbilities\":[],\"levelElementAbilities\":[],\"nonHumanoidMoveAbilities\":[]}}",
                        ConfigGlobalCombat.class));
        player = new RecoveryPlayer();
        player.setUid(10001);
        playerStore = new GuardedPlayerTestStore(player, () -> {});
        var server = environment.scene.getWorld().getServer();
        player.server = server;
        player.inventorySystem =
                new InventorySystem(server) {
                    @Override
                    public void upgradeAvatarFetterLevel(Player p, Avatar a, int amount) {
                        ((RecoveryPlayer) p).companionshipExp += amount;
                    }
                };
        field(GameServer.class, "inventorySystem").set(server, player.inventorySystem);
        field(GameServer.class, "dropSystem")
                .set(server, new emu.grasscutter.game.drop.DropSystem(server));
        scene =
                new AbilityTestFixture.TestScene(
                        new AbilityTestFixture.TestWorld(server, player),
                        JsonUtils.decode(
                                "{\"id\":3,\"type\":\"SCENE_WORLD\"}",
                                emu.grasscutter.data.excels.scene.SceneData.class));
        scripts = new TrackedScripts(scene);
        field(Scene.class, "scriptManager").set(scene, scripts);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.setSceneLoadState(Player.SceneLoadState.LOADED);
        player.getActivityManager().getPlayerActivityDataMap().put(2001, resources.data);
        resources.data.setPlayer(player);
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
        scene.getWorld().getPlayers().add(player);
        var depot =
                JsonUtils.decode(
                        "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        avatar =
                new EntityAvatar(
                        scene, new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot)) {
                    @Override
                    public void initAbilities() {}
                };
        player.getTeamManager().getActiveTeam().add(avatar);
        scene.getEntities().put(avatar.getId(), avatar);
        camp = AsterMidCamps.camps().get(GameData.getAsterMidDataMap().get(1).getCampVec().get(0));
        var meta = SceneMeta.of(3);
        core =
                AsterMidSceneController.definition(
                                camp.groupId(), camp.groupPosition(), meta.blocks.values())
                        .load(3);
        var entry = camp.battleGroups().entrySet().iterator().next();
        battle =
                AsterMidSceneController.definition(
                                entry.getKey(), entry.getValue(), meta.blocks.values())
                        .load(3);
        for (var monster : battle.monsters.values()) {
            if (!originalMonsters.containsKey(monster.monster_id))
                originalMonsters.put(
                        monster.monster_id, GameData.getMonsterDataMap().get(monster.monster_id));
            GameData.getMonsterDataMap()
                    .put(
                            monster.monster_id,
                            JsonUtils.decode(
                                    "{\"id\":"
                                            + monster.monster_id
                                            + ",\"hpBase\":100,\"attackBase\":10,\"defenseBase\":5,\"propGrowCurves\":[],\"affix\":[]}",
                                    MonsterData.class));
        }
        var controller = scene.getAsterMidSceneController();
        @SuppressWarnings("unchecked")
        var mounted =
                (Map<Integer, SceneGroup>)
                        field(AsterMidSceneController.class, "mounted").get(controller);
        mounted.put(core.id, core);
        mounted.put(battle.id, battle);
        field(AsterMidSceneController.class, "scheduleId")
                .set(controller, resources.config.getScheduleId());
        scripts.registerLocalGroups(List.of(core, battle));
        for (var group : List.of(battle, core)) {
            scripts.getCachedGroupInstances().put(group.id, new SceneGroupInstance(group, player));
            scripts.loadGroupFromScript(group);
            scene.getLoadedGroups().add(group);
            scripts.refreshGroup(
                    scripts.getGroupInstanceById(group.id),
                    group == battle ? camp.emptySuite() : 0,
                    false);
        }
        scripts.awaitAll();
        seal = (EntityGadget) scene.getEntityByConfigId(camp.sealConfigId(), core.id);
        assertNotNull(seal);
        player.getPosition().set(seal.getPosition());
        avatar.getPosition().set(seal.getPosition());
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        assertSame(player, scene.getHost());
        assertSame(scene, player.getScene());
        assertTrue(scene.getPlayers().contains(player));
        assertEquals(Player.SceneLoadState.LOADED, player.getSceneLoadState());
        scene.packets.clear();
        player.packets.clear();
    }

    @AfterEach
    void close() throws Exception {
        if (scene != null) scene.getSealBattleManager().close();
        if (scripts != null) {
            scripts.awaitAll();
            scripts.onDestroy();
        }
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (playerStore != null) playerStore.close();
        if (configurationField != null) configurationField.set(null, originalConfiguration);
        if (datastoreField != null) datastoreField.set(null, originalDatastore);
        originalMonsters.forEach(
                (id, data) -> {
                    if (data == null) GameData.getMonsterDataMap().remove(id.intValue());
                    else GameData.getMonsterDataMap().put(id.intValue(), data);
                });
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = resinUsage;
        if (resources != null) resources.close();
        if (environment != null) environment.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void originalLuaSelectionAndWinProduceOneAcknowledgedResinReward(int difficulty)
            throws Exception {
        player.worldLevel = difficulty == 1 ? 1 : 3;
        assertEquals(0, select(difficulty));
        scripts.awaitAll();
        var begin =
                SealBattleBeginNotify.parseFrom(
                        scene.packets.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.SealBattleBeginNotify)
                                .findFirst()
                                .orElseThrow()
                                .getData());
        assertEquals(seal.getId(), begin.getSealEntityId());
        assertEquals(2, begin.getBattleType());
        var monsters =
                scene.getEntities().values().stream()
                        .filter(e -> e instanceof EntityMonster)
                        .map(e -> (EntityMonster) e)
                        .toList();
        assertFalse(monsters.isEmpty());
        assertTrue(
                monsters.stream()
                        .allMatch(
                                m ->
                                        m.getLevel()
                                                == AsterMidRewards.reward(difficulty)
                                                        .monsterLevel()));
        assertEquals(Retcode.RET_FREQUENT_VALUE, select(difficulty));
        assertNull(scene.getEntityByConfigId(camp.rewardConfigId(), core.id));
        win();
        var reward = reward();
        assertNotNull(reward);
        assertNotNull(reward.getContent());
        assertEquals(
                difficulty, AsterSchedule.progress(resources.data).midOffers().get(camp.campId()));
        assertEquals(20, reward.toProto().getGadget().getGeneralReward().getResin());
        player.getPosition().set(reward.getPosition());
        assertFalse(interact(reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertFalse(interact(reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertTrue(interact(reward, InterOpType.InterOpType_INTER_OP_FINISH));
        scripts.awaitAll();
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(100, player.adventureExp);
        assertEquals(
                AsterMidRewards.reward(difficulty).companionshipExp(), player.companionshipExp);
        assertEquals(
                AsterMidRewards.reward(difficulty).energy(),
                AsterSchedule.progress(resources.data).credit());
        assertEquals(1, resources.data.getWatcherInfoMap().get(1200110).getCurProgress());
        assertFalse(interact(reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertTrue(
                persistedDetails.stream()
                        .anyMatch(
                                s ->
                                        JsonUtils.decode(s, AsterProgress.class).pendingMidClaim()
                                                == camp.campId()));
        assertTrue(
                AsterSchedule.progress(resources.data).midFinishedCamps().contains(camp.campId()));
    }

    @Test
    void invalidWorldLevelAndDistanceNeverStartTheBattle() throws Exception {
        assertNotEquals(0, select(5));
        assertNotEquals(0, select(8));
        player.getPosition().setX(player.getPosition().getX() + 6);
        assertNotEquals(0, select(1));
        scripts.awaitAll();
        assertTrue(
                scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.SealBattleBeginNotify));
        assertTrue(AsterSchedule.progress(resources.data).midOffers().isEmpty());
    }

    @Test
    void expiredBattleAndUnloadedGroupCannotCreateRewards() throws Exception {
        assertEquals(0, select(1));
        scripts.awaitAll();
        player.timeMs = 601_000;
        scene.getSealBattleManager().tick();
        scripts.awaitAll();
        assertNull(reward());
        assertTrue(AsterSchedule.progress(resources.data).midOffers().isEmpty());
        assertFalse(
                scene.getAsterMidSceneController().createReward(core, camp.rewardConfigId(), 1));
        scripts.unregisterGroup(core);
        assertNotEquals(0, select(1));
        assertEquals(100, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void realSharedRewardRequiresEachPlayersPreviewAndGrantsIndependentProgress() throws Exception {
        var guest = addGuest(10002);
        guest.getPosition().setX(player.getPosition().getX() + 10_000);
        assertEquals(0, select(1));
        scripts.awaitAll();
        win();
        var reward = reward();
        var initial = reward.toProto().getGadget().getGeneralReward();
        assertEquals(List.of(10001, 10002), initial.getQualifyUidListList());
        assertEquals(initial.getQualifyUidListList(), initial.getRemainUidListList());
        assertEquals(0, initial.getDeadTime());
        player.getPosition().set(reward.getPosition());
        guest.getPosition().set(reward.getPosition());
        assertEquals(0, interact(player, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(
                Retcode.RET_FORBIDDEN_VALUE,
                interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(100, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertSame(reward, reward());
        assertNotNull(scene.getEntityByConfigId(camp.sealConfigId(), core.id));
        assertEquals(80, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        var updates = new ArrayList<ActivityUpdateWatcherNotify>();
        for (var packet : guest.packets)
            if (packet.getOpcode() == PacketOpcodes.ActivityUpdateWatcherNotify)
                updates.add(ActivityUpdateWatcherNotify.parseFrom(packet.getData()));
        var milestone =
                updates.stream()
                        .filter(p -> p.getWatcherInfo().getWatcherId() == 1200131)
                        .findFirst()
                        .orElseThrow();
        assertEquals(2001, milestone.getActivityId());
        assertEquals(1, milestone.getWatcherInfo().getCurProgress());
        assertEquals(14, milestone.getWatcherInfo().getTotalProgress());
        assertFalse(resources.data.getWatcherInfoMap().containsKey(1200131));
        assertEquals(100, guest.adventureExp);
        assertEquals(
                20, guest.companionshipExp, "Inventory applies the existing MP EXP multiplier");
        assertEquals(0, player.adventureExp);
        var guestData = guest.getActivityManager().getPlayerActivityDataMap().get(2001);
        assertEquals(1, AsterSchedule.progress(guestData).midCollectCount());
        assertEquals(2, AsterSchedule.progress(guestData).midRefreshId());
        assertEquals(0, AsterSchedule.progress(resources.data).midCollectCount());
        var update =
                GadgetGeneralRewardInfoNotify.parseFrom(
                        scene.packets.stream()
                                .filter(
                                        p ->
                                                p.getOpcode()
                                                        == PacketOpcodes
                                                                .GadgetGeneralRewardInfoNotify)
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getData());
        assertEquals(reward.getId(), update.getEntityId());
        assertEquals(List.of(10001), update.getGeneralRewardInfo().getRemainUidListList());
        assertEquals(List.of(10001, 10002), update.getGeneralRewardInfo().getQualifyUidListList());
        assertTrue(update.getGeneralRewardInfo().getDeadTime() > System.currentTimeMillis() / 1000);
        var guestInfo =
                AsterMidSceneController.infoForWorld(
                        guestData, resources.config, 20, System.currentTimeMillis());
        assertEquals(1, guestInfo.getCollectCount());
        assertEquals(
                AsterMidSceneController.info(
                                resources.data, resources.config, 20, System.currentTimeMillis())
                        .getCampListList(),
                guestInfo.getCampListList());
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(0, interact(player, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        scripts.awaitAll();
        assertNull(reward());
        assertNull(scene.getEntityByConfigId(camp.sealConfigId(), core.id));
        assertEquals(1, AsterSchedule.progress(resources.data).midCollectCount());
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void rewardReloadKeepsClaimedPlayersAndOriginalDeadlineThenExpiresTheCamp() throws Exception {
        var guest = addGuest(10002);
        assertEquals(0, select(1));
        scripts.awaitAll();
        win();
        var reward = reward();
        player.getPosition().set(reward.getPosition());
        assertEquals(0, interact(player, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(0, interact(player, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        var state = AsterSchedule.progress(resources.data).midReward(camp.campId());
        long deadline = state.deadTime() * 1000;
        scene.removeEntity(reward);
        var load =
                AsterMidSceneController.class.getDeclaredMethod("load", AsterMidCamps.Camp.class);
        load.setAccessible(true);
        load.invoke(scene.getAsterMidSceneController(), camp);
        scripts.awaitAll();
        var restored = reward();
        assertNotNull(restored);
        assertNotSame(reward, restored);
        var info = restored.toProto().getGadget().getGeneralReward();
        assertEquals(List.of(10002), info.getRemainUidListList());
        assertEquals(state.deadTime(), Integer.toUnsignedLong(info.getDeadTime()));
        assertNotEquals(0, interact(player, restored, InterOpType.InterOpType_INTER_OP_START));
        scene.getAsterMidSceneController().expireRewards(deadline - 1);
        assertSame(restored, reward());
        scene.getAsterMidSceneController().expireRewards(deadline);
        scripts.awaitAll();
        assertNull(reward());
        assertNull(scene.getEntityByConfigId(camp.sealConfigId(), core.id));
        assertTrue(
                AsterSchedule.progress(resources.data).midFinishedCamps().contains(camp.campId()));
        assertEquals(100, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, guest.adventureExp);
        assertEquals(
                0,
                AsterSchedule.progress(
                                guest.getActivityManager().getPlayerActivityDataMap().get(2001))
                        .midCollectCount());
    }

    @Test
    void lateJoinersCannotClaimAndDisconnectDoesNotExpandOrForgetQualification() throws Exception {
        var guest = addGuest(10002);
        assertEquals(0, select(1));
        scripts.awaitAll();
        win();
        var reward = reward();
        var late = addGuest(10003);
        late.getPosition().set(reward.getPosition());
        assertNotEquals(0, interact(late, reward, InterOpType.InterOpType_INTER_OP_START));
        assertNotEquals(0, interact(late, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        scene.getPlayers().remove(guest);
        scene.getWorld().getPlayers().remove(guest);
        guest.getPosition().set(reward.getPosition());
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(
                List.of(10001, 10002),
                reward.toProto().getGadget().getGeneralReward().getQualifyUidListList());
        scene.getPlayers().add(guest);
        scene.getWorld().getPlayers().add(guest);
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(80, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(100, late.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    private RecoveryPlayer addGuest(int uid) throws Exception {
        var guest = new RecoveryPlayer();
        guest.setUid(uid);
        guest.server = player.server;
        guest.inventorySystem = player.inventorySystem;
        guest.setScene(scene);
        guest.setWorld(scene.getWorld());
        guest.setSceneLoadState(Player.SceneLoadState.LOADED);
        var watchers = new HashMap<Integer, PlayerActivityData.WatcherInfo>();
        resources
                .data
                .getWatcherInfoMap()
                .forEach(
                        (id, w) ->
                                watchers.put(
                                        id,
                                        PlayerActivityData.WatcherInfo.of()
                                                .watcherId(id)
                                                .totalProgress(w.getTotalProgress())
                                                .build()));
        var data =
                PlayerActivityData.of()
                        .uid(uid)
                        .activityId(2001)
                        .scheduleId(resources.config.getScheduleId())
                        .detail("{}")
                        .watcherInfoMap(watchers)
                        .build();
        data.setPlayer(guest);
        var progress = AsterSchedule.progress(data);
        progress.startMidBatch(2);
        data.setDetail(progress);
        guest.getActivityManager().getPlayerActivityDataMap().put(2001, data);
        guest.getTeamManager().setEntity(new EntityTeam(guest));
        var depot =
                JsonUtils.decode(
                        "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        var avatar =
                new EntityAvatar(
                        scene, new SkillTalentLevelTest.RecordingAvatar(guest, 10000073, depot)) {
                    @Override
                    public void initAbilities() {}
                };
        guest.getTeamManager().getActiveTeam().add(avatar);
        scene.getEntities().put(avatar.getId(), avatar);
        guest.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        scene.getPlayers().add(guest);
        scene.getWorld().getPlayers().add(guest);
        field(World.class, "isMultiplayer").set(scene.getWorld(), true);
        return guest;
    }

    private int interact(RecoveryPlayer claimant, EntityGadget reward, InterOpType op)
            throws Exception {
        reward.onInteract(
                claimant,
                GadgetInteractReq.newBuilder()
                        .setGadgetEntityId(reward.getId())
                        .setOpType(op)
                        .build());
        return GadgetInteractRsp.parseFrom(
                        claimant.packets.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.GadgetInteractRsp)
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getData())
                .getRetcode();
    }

    private int select(int difficulty) {
        return scene.getAsterMidSceneController()
                .select(player, resources.config.getScheduleId(), seal.getId(), difficulty);
    }

    private EntityGadget reward() {
        return (EntityGadget) scene.getEntityByConfigId(camp.rewardConfigId(), core.id);
    }

    private boolean interact(EntityGadget reward, InterOpType op) {
        boolean present = scene.getEntities().get(reward.getId()) == reward;
        reward.onInteract(
                player,
                GadgetInteractReq.newBuilder()
                        .setGadgetEntityId(reward.getId())
                        .setOpType(op)
                        .build());
        return present && scene.getEntities().get(reward.getId()) != reward;
    }

    private void win() throws Exception {
        var monster =
                scene.getEntities().values().stream()
                        .filter(e -> e instanceof EntityMonster)
                        .map(e -> (EntityMonster) e)
                        .findFirst()
                        .orElseThrow();
        for (int i = 0; i < 20 && reward() == null; i++) {
            scene.getSealBattleManager().onMonsterKilled(monster, player);
            scripts.awaitAll();
            if (reward() != null) return;
            scene.removeEntity(monster);
            monster = scripts.createMonster(battle.id, battle.block_id, monster.getMetaMonster());
            assertNotNull(monster);
            scene.addEntity(monster);
        }
        assertNotNull(reward(), "Original seal end callback must create the reward entity");
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static final class TrackedScripts extends SceneScriptManager {
        final List<Future<?>> futures = new CopyOnWriteArrayList<>();

        TrackedScripts(Scene scene) {
            super(scene);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier guard) {
            var future = super.callEvent(args, guard);
            futures.add(future);
            return future;
        }

        void awaitAll() throws Exception {
            for (int i = 0; i < futures.size(); i++) futures.get(i).get(10, TimeUnit.SECONDS);
        }
    }

    static final class RecoveryPlayer extends AbilityTestFixture.TestPlayer {
        transient int worldLevel = 1, adventureExp, companionshipExp;
        transient long timeMs;
        transient GameServer server;
        transient InventorySystem inventorySystem;
        private final transient ActivityManager activity =
                new ActivityManager(this) {
                    @Override
                    public synchronized void refreshActivities() {}

                    @Override
                    public void triggerWatcher(WatcherTriggerType type, String... params) {}

                    @Override
                    public void triggerActivityConditions() {}
                };
        private final transient BattlePassManager battlePass =
                new BattlePassManager(this) {
                    @Override
                    public void triggerMission(WatcherTriggerType type) {}

                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int progress) {}

                    @Override
                    public void triggerMission(String name, int param, int progress) {}
                };

        @Override
        public int getLevel() {
            return 20;
        }

        @Override
        public int getWorldLevel() {
            return worldLevel;
        }

        @Override
        public long getPlayerTimeMs() {
            return timeMs;
        }

        @Override
        public boolean setProperty(PlayerProperty p, int v) {
            getProperties().put(p.getId(), v);
            return true;
        }

        @Override
        public void addExpDirectly(int gain) {
            adventureExp += gain;
        }

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return battlePass;
        }

        @Override
        public GameServer getServer() {
            return server;
        }
    }
}

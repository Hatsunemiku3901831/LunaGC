package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.Datastore;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.activity.MpPlayMatchData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.MpPlayGuestReplyInviteReqOuterClass.MpPlayGuestReplyInviteReq;
import emu.grasscutter.net.proto.MpPlayGuestReplyInviteRspOuterClass.MpPlayGuestReplyInviteRsp;
import emu.grasscutter.net.proto.MpPlayInviteResultNotifyOuterClass.MpPlayInviteResultNotify;
import emu.grasscutter.net.proto.MpPlayOwnerCheckReqOuterClass.MpPlayOwnerCheckReq;
import emu.grasscutter.net.proto.MpPlayOwnerCheckRspOuterClass.MpPlayOwnerCheckRsp;
import emu.grasscutter.net.proto.MpPlayOwnerStartInviteReqOuterClass.MpPlayOwnerStartInviteReq;
import emu.grasscutter.net.proto.MpPlayOwnerStartInviteRspOuterClass.MpPlayOwnerStartInviteRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Original scene groups and Lua event dispatch, without a running game or public database. */
class AsterBigEntranceTest {
    private static final int MAIN = 133001413;
    private static final List<Integer> GROUPS = List.of(MAIN, 133001426, 133001427, 133001498);
    private AbilityTestFixture environment;
    AsterBigTestFixture resources;
    AsterMidSceneTest.RecoveryPlayer host, guest;
    AbilityTestFixture.TestScene scene;
    TrackedScripts scripts;
    private Object oldConfiguration, oldDatastore;
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        resources = new AsterBigTestFixture();
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        oldDatastore = field(DatabaseManager.class, "gameDatastore").get(null);
        field(DatabaseManager.class, "gameDatastore")
                .set(
                        null,
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    assertEquals("save", method.getName());
                                    return args[0];
                                }));
        oldConfiguration = field(ActivityManager.class, "configuration").get(null);
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(
                        Map.of(2001, resources.config),
                        Map.of(resources.config.getScheduleId(), resources.config)));
        resources.original.load(
                "MpPlayMatchExcelConfigData.json",
                MpPlayMatchData.class,
                GameData.getMpPlayMatchDataMap(),
                id -> id == 2);
        resources.original.load(
                "GadgetExcelConfigData.json",
                GadgetData.class,
                GameData.getGadgetDataMap(),
                id -> id == 70900361);
        GameData.setConfigGlobalCombat(
                JsonUtils.decode(
                        "{\"defaultAbilities\":{\"defaultMPLevelAbilities\":[],\"levelElementAbilities\":[],\"nonHumanoidMoveAbilities\":[]}}",
                        ConfigGlobalCombat.class));
        installPlayTeamAbilities();
        field(GameServer.class, "players")
                .set(
                        environment.scene.getWorld().getServer(),
                        new ConcurrentHashMap<Integer, Player>());
        host = new AsterMidSceneTest.RecoveryPlayer();
        guest = new AsterMidSceneTest.RecoveryPlayer();
        scene =
                new AbilityTestFixture.TestScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), host),
                        JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        scripts = new TrackedScripts(scene);
        field(Scene.class, "scriptManager").set(scene, scripts);
        field(SceneScriptManager.class, "meta").set(scripts, SceneMeta.of(3));
        field(SceneScriptManager.class, "isInit").set(scripts, true);
        field(SceneScriptManager.class, "initAttempted").set(scripts, true);
        ready(host, resources.host, 1);
    }

    void ready(AsterMidSceneTest.RecoveryPlayer player, PlayerActivityData data, int peer) {
        ready(player, data, peer, scene);
    }

    void ready(
            AsterMidSceneTest.RecoveryPlayer player,
            PlayerActivityData data,
            int peer,
            Scene scene) {
        player.server = scene.getWorld().getServer();
        player.setUid(data.getUid());
        player.setPeerId(peer);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.setSceneLoadState(Player.SceneLoadState.LOADED);
        player.getPosition().set(GameData.getMpPlayGroupDataMap().get(2).centerPosition());
        player.getActivityManager().getPlayerActivityDataMap().put(2001, data);
        data.setPlayer(player);
        for (var mission : GameData.getAsterMissionDataMap().values()) {
            var meta = GameData.getActivityWatcherDataMap().get(mission.getWatcherId());
            data.getWatcherInfoMap()
                    .putIfAbsent(
                            mission.getWatcherId(),
                            PlayerActivityData.WatcherInfo.of()
                                    .watcherId(mission.getWatcherId())
                                    .totalProgress(meta == null ? 1 : meta.getProgress())
                                    .build());
        }
        player.getTeamManager().setEntity(new EntityTeam(player));
        var depot =
                JsonUtils.decode(
                        "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        var avatar =
                new EntityAvatar(
                        scene, new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot)) {
                    @Override
                    public void initAbilities() {}
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        player.getTeamManager().getActiveTeam().add(avatar);
        scene.getEntities().put(avatar.getId(), avatar);
        scene.getPlayers().add(player);
        scene.getWorld().getPlayers().add(player);
        player.server.getPlayers().put(player.getUid(), player);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (scene != null) scene.getScenePlayBattleManager().close();
        if (scripts != null) {
            scripts.awaitAll();
            scripts.onDestroy();
            scripts.awaitAll();
        }
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        field(DatabaseManager.class, "gameDatastore").set(null, oldDatastore);
        if (oldConfiguration != null)
            field(ActivityManager.class, "configuration").set(null, oldConfiguration);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (resources != null) resources.close();
        if (environment != null) environment.close();
    }

    // Reflection keeps the initial regression runnable before the missing controller exists.
    void update(long now) throws Exception {
        var controller = Scene.class.getMethod("getAsterBigSceneController").invoke(scene);
        controller.getClass().getMethod("update", long.class).invoke(controller, now);
        scripts.awaitAll();
    }

    @Test
    void originalOpeningMountsAllFourGroupsAndEnablesTheActualEntrance() throws Exception {
        update(resources.now);
        for (int id : GROUPS) {
            assertNotNull(scripts.getLoadedGroupById(id), "original group " + id);
            assertTrue(scene.getLoadedGroups().stream().anyMatch(g -> g.id == id));
        }
        var entrance = (EntityGadget) scene.getEntityByConfigId(413001, MAIN);
        assertNotNull(entrance);
        assertEquals(70900361, entrance.getGadgetId());
        assertTrue(entrance.toProto().getGadget().getIsEnableInteract());
        assertEquals(
                1, scripts.getGroupInstanceById(MAIN).getCachedVariables().get("interact_flag"));
        update(resources.now);
        assertSame(entrance, scene.getEntityByConfigId(413001, MAIN));
    }

    @Test
    void nativeOwnerAndStartRequestsReachPlayTwoAndPrepareSoloDirectBattle() throws Exception {
        update(resources.now);
        var session = session(host);
        new HandlerMpPlayOwnerCheckReq()
                .handle(
                        session,
                        new byte[0],
                        MpPlayOwnerCheckReq.newBuilder()
                                .setMpPlayId(2)
                                .setIsSkipMatch(true)
                                .build()
                                .toByteArray());
        assertEquals(0, ownerCheck(host).getRetcode());
        new HandlerMpPlayOwnerStartInviteReq()
                .handle(
                        session,
                        new byte[0],
                        MpPlayOwnerStartInviteReq.newBuilder()
                                .setMpPlayId(2)
                                .setIsSkipMatch(true)
                                .build()
                                .toByteArray());
        assertEquals(
                0,
                MpPlayOwnerStartInviteRsp.parseFrom(
                                last(host, PacketOpcodes.MpPlayOwnerStartInviteRsp))
                        .getRetcode());
        scripts.awaitAll();
        var info = scene.getScenePlayBattleManager().getInfo(MAIN);
        assertNotNull(info);
        assertEquals(2, info.getPlayId());
        assertEquals(1, info.getType());
        assertEquals(2, info.getState());
        assertEquals(20, info.getPrepareEndTime() - System.currentTimeMillis() / 1000, 1);
    }

    @Test
    void allSameWorldGuestsMustConsentBeforeTheOriginalReadyLuaRuns() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        var battles = scene.getScenePlayBattleManager();
        scene.getAsterBigSceneController().startInvitation(host, 2, true);
        assertEquals(0, battles.getInfo(MAIN).getState());
        new HandlerMpPlayGuestReplyInviteReq()
                .handle(
                        session(guest),
                        new byte[0],
                        MpPlayGuestReplyInviteReq.newBuilder()
                                .setMpPlayId(2)
                                .setIsAgree(true)
                                .build()
                                .toByteArray());
        assertEquals(
                0,
                MpPlayGuestReplyInviteRsp.parseFrom(
                                last(guest, PacketOpcodes.MpPlayGuestReplyInviteRsp))
                        .getRetcode());
        scripts.awaitAll();
        assertEquals(2, battles.getInfo(MAIN).getState());
        var clock =
                new java.util.concurrent.atomic.AtomicLong(
                        battles.getInfo(MAIN).getPrepareEndTime());
        field(emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager.class, "clock")
                .set(battles, (java.util.function.LongSupplier) clock::get);
        battles.tick();
        scripts.awaitAll();
        assertEquals(4, battles.getInfo(MAIN).getState());
        assertEquals(204, battles.getInfo(MAIN).getMode());
        assertEquals(2, battles.getPlayTeamEntities().size());
        assertTrue(
                battles.getPlayTeamEntities().stream()
                        .allMatch(e -> e.getGadgetConfigId() == 70370004));
    }

    @Test
    void refusingGuestCancelsForEveryoneWithoutStartingABattle() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        controller.startInvitation(host, 2, true);
        controller.replyInvitation(guest, 2, false);
        assertEquals(0, scene.getScenePlayBattleManager().getInfo(MAIN).getState());
        assertFalse(
                MpPlayInviteResultNotify.parseFrom(
                                last(host, PacketOpcodes.MpPlayInviteResultNotify))
                        .getAllAgree());
        controller.checkOwnerRequest(host, 2, true);
        assertEquals(0, ownerCheck(host).getRetcode());
    }

    @Test
    void invitationTimeoutReleasesTheEntrance() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        controller.startInvitation(host, 2, true);
        update(System.currentTimeMillis() + 31000);
        assertFalse(
                MpPlayInviteResultNotify.parseFrom(
                                last(host, PacketOpcodes.MpPlayInviteResultNotify))
                        .getAllAgree());
        controller.checkOwnerRequest(host, 2, true);
        assertEquals(0, ownerCheck(host).getRetcode());
    }

    @Test
    void lateWorldMemberCannotReplaceAnAlreadyPreparingBattle() throws Exception {
        update(resources.now);
        scene.getAsterBigSceneController().startInvitation(host, 2, true);
        // A direct battle already preparing cannot be replaced by a new member's invite.
        ready(guest, resources.guest, 2);
        scene.getAsterBigSceneController().checkOwnerRequest(host, 2, true);
        assertEquals(Retcode.RET_MP_IN_MP_PLAY_BATTLE_VALUE, ownerCheck(host).getRetcode());
    }

    @Test
    void lateWorldMemberCancelsConsentWithoutStartingABattle() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        controller.startInvitation(host, 2, true);
        ready(new AsterMidSceneTest.RecoveryPlayer(), resources.data(10003), 3);
        controller.replyInvitation(guest, 2, true);
        assertEquals(
                Retcode.RET_MP_REPLY_TIMEOUT_VALUE,
                MpPlayGuestReplyInviteRsp.parseFrom(
                                last(guest, PacketOpcodes.MpPlayGuestReplyInviteRsp))
                        .getRetcode());
        update(resources.now);
        assertEquals(0, scene.getScenePlayBattleManager().getInfo(MAIN).getState());
        assertFalse(
                MpPlayInviteResultNotify.parseFrom(
                                last(host, PacketOpcodes.MpPlayInviteResultNotify))
                        .getAllAgree());
    }

    @Test
    void queuedOldOpenEventCannotEnableAReplacementDefinition() throws Exception {
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        synchronized (scene) {
            scripts.callEvent(
                    new ScriptArgs(
                            MAIN,
                            emu.grasscutter.scripts.constants.EventType.EVENT_SCENE_MP_PLAY_OPEN,
                            2));
            controller.close();
            controller.update(resources.now);
            controller.close();
        }
        scripts.awaitAll();
        assertNull(scripts.getLoadedGroupById(MAIN));
        assertNull(scene.getEntityByConfigId(413001, MAIN));
    }

    @Test
    void guestIdentityReplacementCannotAcceptAnOldInvitation() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        scene.getAsterBigSceneController().startInvitation(host, 2, true);
        scene.getWorld()
                .getServer()
                .getPlayers()
                .put(guest.getUid(), new AsterMidSceneTest.RecoveryPlayer());
        scene.getAsterBigSceneController().replyInvitation(guest, 2, true);
        assertEquals(
                Retcode.RET_MP_REPLY_TIMEOUT_VALUE,
                MpPlayGuestReplyInviteRsp.parseFrom(
                                last(guest, PacketOpcodes.MpPlayGuestReplyInviteRsp))
                        .getRetcode());
        update(resources.now);
        assertEquals(0, scene.getScenePlayBattleManager().getInfo(MAIN).getState());
        assertFalse(
                MpPlayInviteResultNotify.parseFrom(
                                last(host, PacketOpcodes.MpPlayInviteResultNotify))
                        .getAllAgree());
    }

    @Test
    void departureCancelsTheCurrentInvitation() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        scene.getAsterBigSceneController().startInvitation(host, 2, true);
        scene.getAsterBigSceneController().onPlayerLeaving(guest);
        assertFalse(
                MpPlayInviteResultNotify.parseFrom(
                                last(host, PacketOpcodes.MpPlayInviteResultNotify))
                        .getAllAgree());
        assertEquals(0, scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    @Test
    void matchingOwnerCheckAcceptsEligiblePlayTwoWithoutStartingABattle() throws Exception {
        update(resources.now);
        scene.getAsterBigSceneController().checkOwnerRequest(host, 2, false);
        assertEquals(0, ownerCheck(host).getRetcode());
        assertEquals(0, scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    @Test
    void guestCannotInitiateAndUnloadedPlayersCannotJoin() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        controller.checkOwnerRequest(guest, 2, true);
        assertEquals(Retcode.RET_MP_NOT_IN_MY_WORLD_VALUE, ownerCheck(guest).getRetcode());
        guest.setSceneLoadState(Player.SceneLoadState.LOADING);
        controller.checkOwnerRequest(host, 2, true);
        assertEquals(Retcode.RET_MP_GUEST_LOADING_FIRST_ENTER_VALUE, ownerCheck(host).getRetcode());
        assertEquals(guest.getUid(), ownerCheck(host).getWrongUid());
    }

    @Test
    void outOfRegionAndDeadGuestsAreRejectedBeforeInvitation() throws Exception {
        ready(guest, resources.guest, 2);
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        guest.getPosition().setX(host.getPosition().getX() + 251);
        controller.checkOwnerRequest(host, 2, true);
        assertEquals(Retcode.RET_MP_OWNER_NOT_ENTER_VALUE, ownerCheck(host).getRetcode());
        guest.getPosition().set(host.getPosition());
        guest.getTeamManager()
                .getActiveTeam()
                .forEach(a -> a.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0));
        controller.checkOwnerRequest(host, 2, true);
        assertEquals(Retcode.RET_MP_REPLY_NO_VALID_AVATAR_VALUE, ownerCheck(host).getRetcode());
    }

    @Test
    void phaseThreeDateGatePreventsStaticOrDynamicGroupRevival() throws Exception {
        resources.config.setBeginTime(new Date(resources.now - 6 * 86400000L));
        resources.config.onLoad();
        update(resources.now);
        assertNull(scene.getEntityByConfigId(413001, MAIN));
        assertNull(scripts.getGroupById(MAIN));
        var handler = new AsterActivityHandler();
        handler.setActivityConfigItem(resources.config);
        var info = ActivityInfo.newBuilder();
        handler.onProtoBuild(resources.host, info);
        assertFalse(info.getAsterInfo().getAsterLarge().getIsOpen());
    }

    @Test
    void chapterThreeIsAdvertisedOnlyForEligiblePlayersAndTheirHost() throws Exception {
        update(resources.now);
        var handler = new AsterActivityHandler();
        handler.setActivityConfigItem(resources.config);
        var info = ActivityInfo.newBuilder();
        handler.onProtoBuild(resources.host, info);
        assertTrue(info.getAsterInfo().getAsterLarge().getIsOpen());
        ready(guest, resources.guest, 2);
        resources.host.setScheduleId(resources.config.getScheduleId() + 1);
        info = ActivityInfo.newBuilder();
        handler.onProtoBuild(resources.guest, info);
        assertFalse(info.getAsterInfo().getAsterLarge().getIsOpen());
    }

    @Test
    void expiryUnloadsAllGroupsAndInvalidatesOriginalDefinitions() throws Exception {
        update(resources.now);
        var old = scripts.getLoadedGroupById(MAIN);
        update(AsterSchedule.contentCloseTime(resources.config));
        for (int id : GROUPS) assertNull(scripts.getLoadedGroupById(id));
        assertFalse(scripts.allowsGroupCall(old));
        assertNull(scripts.createGadget(MAIN, old.block_id, old.gadgets.get(413001)));
    }

    @Test
    void idleDistanceUnloadAndReloadUsesNewDefinitionsAndResetsRetainedVariables()
            throws Exception {
        update(resources.now);
        var old = scripts.getLoadedGroupById(MAIN);
        scripts.getGroupInstanceById(MAIN).getCachedVariables().put("GM_Mode", 1);
        host.getPosition().setX(host.getPosition().getX() + 251);
        update(resources.now);
        assertNull(scripts.getLoadedGroupById(MAIN));
        host.getPosition().set(GameData.getMpPlayGroupDataMap().get(2).centerPosition());
        update(resources.now);
        assertNotSame(old, scripts.getLoadedGroupById(MAIN));
        assertEquals(0, scripts.getGroupInstanceById(MAIN).getCachedVariables().get("GM_Mode"));
        assertFalse(scripts.allowsGroupCall(old));
        assertTrue(((EntityGadget) scene.getEntityByConfigId(413001, MAIN)).isInteractEnabled());
    }

    @Test
    void activePreparationKeepsMountedGroupsBeyondTheIdleStreamingRadius() throws Exception {
        update(resources.now);
        scene.getAsterBigSceneController().startInvitation(host, 2, true);
        scripts.awaitAll();
        host.getPosition().setX(host.getPosition().getX() + 251);
        update(resources.now);
        for (int id : GROUPS) assertNotNull(scripts.getLoadedGroupById(id));
    }

    @Test
    void reloadedGroupsAfterAnInterruptedRoundAcceptTheNewOpenEvent() throws Exception {
        update(resources.now);
        var controller = scene.getAsterBigSceneController();
        controller.startInvitation(host, 2, true);
        scripts.awaitAll();
        controller.close();
        scripts.awaitAll();
        update(resources.now);
        assertTrue(((EntityGadget) scene.getEntityByConfigId(413001, MAIN)).isInteractEnabled());
        controller.startInvitation(host, 2, true);
        scripts.awaitAll();
        assertEquals(
                0,
                MpPlayOwnerStartInviteRsp.parseFrom(
                                last(host, PacketOpcodes.MpPlayOwnerStartInviteRsp))
                        .getRetcode());
        assertEquals(2, scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    @Test
    void ownerReceivesTheOriginalRemainingRewardWarningBeforeReplacingAnOffer() throws Exception {
        update(resources.now);
        resources.offer();
        scene.getAsterBigSceneController().checkOwnerRequest(host, 2, true);
        assertEquals(Retcode.RET_MP_PLAY_REMAIN_REWARDS_VALUE, ownerCheck(host).getRetcode());
        assertEquals(host.getUid(), ownerCheck(host).getWrongUid());
    }

    private GameSession session(Player player) {
        return new GameSession(player.getServer()) {
            @Override
            public Player getPlayer() {
                return player;
            }
        };
    }

    private byte[] last(AbilityTestFixture.TestPlayer player, int opcode) {
        return player.packets.stream()
                .filter(p -> p.getOpcode() == opcode)
                .reduce((a, b) -> b)
                .orElseThrow()
                .getData();
    }

    private MpPlayOwnerCheckRsp ownerCheck(AbilityTestFixture.TestPlayer player) throws Exception {
        return MpPlayOwnerCheckRsp.parseFrom(last(player, PacketOpcodes.MpPlayOwnerCheckRsp));
    }

    private <K, V> void replace(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private void installPlayTeamAbilities() throws Exception {
        resources.original.load(
                "GadgetExcelConfigData.json",
                GadgetData.class,
                GameData.getGadgetDataMap(),
                id -> id >= 70370001 && id <= 70370004);
        var configs =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Gadget/ConfigGadget_Scene_Meteorite.json")))
                        .getAsJsonObject();
        var abilities =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Meteorite.json")))
                        .getAsJsonArray();
        for (int id = 70370001; id <= 70370004; id++) {
            String name = GameData.getGadgetDataMap().get(id).getJsonName();
            replace(
                    GameData.getGadgetConfigData(),
                    name,
                    JsonUtils.decode(configs.get(name).toString(), ConfigEntityGadget.class));
            for (var row : abilities) {
                var raw = row.getAsJsonObject().getAsJsonObject("Default");
                if (raw != null
                        && raw.has("abilityName")
                        && name.equals(raw.get("abilityName").getAsString())) {
                    var ability = JsonUtils.decode(raw.toString(), AbilityData.class);
                    ability.initialize();
                    replace(GameData.getAbilityDataMap(), name, ability);
                }
            }
        }
    }

    static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static final class TrackedScripts extends SceneScriptManager {
        final List<Future<?>> futures = new CopyOnWriteArrayList<>();

        TrackedScripts(Scene scene) {
            super(scene);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier guard, boolean lockScene) {
            var result = super.callEvent(args, guard, lockScene);
            futures.add(result);
            return result;
        }

        @Override
        public SceneGroupInstance getCachedGroupInstanceById(int id) {
            var cached = getCachedGroupInstances().get(id);
            if (cached == null) {
                var group = findGroupById(id);
                if (group == null) return null;
                cached = new SceneGroupInstance(group, getScene().getHost());
                getCachedGroupInstances().put(id, cached);
            }
            return cached;
        }

        void awaitAll() throws Exception {
            for (int i = 0; i < futures.size(); i++) futures.get(i).get(10, TimeUnit.SECONDS);
        }
    }
}

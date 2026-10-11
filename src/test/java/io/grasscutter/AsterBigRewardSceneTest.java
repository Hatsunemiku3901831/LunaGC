package io.grasscutter;

import static emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.Datastore;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.activity.MpPlayMatchData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ActivityUpdateWatcherNotifyOuterClass.ActivityUpdateWatcherNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoNotifyOuterClass.GadgetGeneralRewardInfoNotify;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass.GadgetInteractRsp;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Actual original win Lua, real reward entity, inventory and resin; isolated acknowledged storage.
 */
class AsterBigRewardSceneTest {
    private AbilityTestFixture environment;
    private AsterBigTestFixture resources;
    private AsterMidSceneTest.RecoveryPlayer host, guest;
    private AsterBigScriptsTest.BigScene scene;
    private ScenePlayBattleManager battles;
    private SceneGroup main;
    private MpPlayMatchData match;
    private ScriptLib library;
    private Field datastoreField, configurationField;
    private Object oldDatastore, oldConfig;
    private boolean resinUsage;
    private int activityWrites, failActivityWrite;
    private final List<String> saved = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        resources = new AsterBigTestFixture();
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        datastoreField = field(DatabaseManager.class, "gameDatastore");
        oldDatastore = datastoreField.get(null);
        datastoreField.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> {
                            assertEquals("save", method.getName());
                            if (args[0] instanceof PlayerActivityData data) {
                                saved.add(data.getDetail());
                                if (++activityWrites == failActivityWrite)
                                    throw new IllegalStateException("write acknowledgement lost");
                            }
                            return args[0];
                        }));
        configurationField = field(ActivityManager.class, "configuration");
        oldConfig = configurationField.get(null);
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
        host = new AsterMidSceneTest.RecoveryPlayer();
        guest = new AsterMidSceneTest.RecoveryPlayer();
        var server = environment.scene.getWorld().getServer();
        var inventory =
                new InventorySystem(server) {
                    @Override
                    public void upgradeAvatarFetterLevel(
                            emu.grasscutter.game.player.Player player, Avatar avatar, int amount) {
                        ((AsterMidSceneTest.RecoveryPlayer) player).companionshipExp += amount;
                    }
                };
        field(GameServer.class, "inventorySystem").set(server, inventory);
        scene = new AsterBigScriptsTest.BigScene(new AbilityTestFixture.TestWorld(server, host));
        ready(host, resources.host, server, inventory, 1);
        ready(guest, resources.guest, server, inventory, 8);
        field(World.class, "isMultiplayer").set(scene.getWorld(), true);
        battles = scene.getScenePlayBattleManager();
        for (int id : List.of(AsterBigRewards.GROUP, 133001426, 133001427, 133001498)) {
            var group = SceneGroup.of(id).load(3);
            scene.scripts.loaded.put(id, group);
            scene.scripts.instances.put(id, new SceneGroupInstance(group, host));
        }
        main = scene.scripts.loaded.get(AsterBigRewards.GROUP);
        var controller = scene.getAsterBigSceneController();
        @SuppressWarnings("unchecked")
        var mounted =
                (Map<Integer, SceneGroup>)
                        field(AsterBigSceneController.class, "mounted").get(controller);
        mounted.putAll(scene.scripts.loaded);
        field(AsterBigSceneController.class, "scheduleId")
                .set(controller, resources.config.getScheduleId());
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scene.scripts);
        library.setCurrentGroup(main);
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/MpPlayMatchExcelConfigData.json")))
                        .getAsJsonArray())
            if (row.getAsJsonObject().get("id").getAsInt() == 2)
                match = JsonUtils.decode(row.toString(), MpPlayMatchData.class);
        assertNotNull(match);
        prepareAndStart();
    }

    private void ready(
            AsterMidSceneTest.RecoveryPlayer player,
            PlayerActivityData data,
            GameServer server,
            InventorySystem inventory,
            int level)
            throws Exception {
        player.setUid(data.getUid());
        player.setPeerId(data.getUid() - 10000);
        player.worldLevel = level;
        player.server = server;
        player.inventorySystem = inventory;
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.setSceneLoadState(emu.grasscutter.game.player.Player.SceneLoadState.LOADED);
        data.setPlayer(player);
        player.getActivityManager().getPlayerActivityDataMap().put(2001, data);
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
        player.getTeamManager().getActiveTeam().add(avatar);
        scene.getEntities().put(avatar.getId(), avatar);
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        scene.getPlayers().add(player);
        scene.getWorld().getPlayers().add(player);
    }

    private void prepareAndStart() {
        assertTrue(
                battles.prepare(
                        GameData.getMpPlayGroupDataMap().get(2),
                        match,
                        2,
                        MATCH,
                        List.of(host, guest)));
        scene.now.addAndGet(20);
        battles.tick();
        assertEquals(READY, battles.getInfo(main.id).getState());
        assertTrue(
                battles.prestart(
                        main.id,
                        new ScenePlayBattleParameters(600, 0, 1, List.of(0L, 500L, 2000L))));
        assertEquals(START, battles.getInfo(main.id).getState());
        assertTrue(battles.setUidValue(main.id, host.getUid(), "circle_ptr", 1));
        assertTrue(battles.setUidValue(main.id, host.getUid(), "tide_ptr", 1));
    }

    private EntityGadget win() {
        assertTrue(battles.addProgress(main.id, 2000));
        assertEquals(STOP, battles.getInfo(main.id).getState());
        synchronized (scene) {
            var event = new ScriptArgs(main.id, 50, 2, 2).setParam3(WIN).setUid(host.getUid());
            library.setCurrentCallParams(event);
            try {
                var context = library.createEventContext(event, main);
                assertEquals(
                        0,
                        ((LuaFunction) main.getBindings().get("LF_Battle_Win"))
                                .call(context, org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(event))
                                .toint());
            } finally {
                library.removeCurrentCallParams();
            }
        }
        var reward = (EntityGadget) scene.getEntityByConfigId(AsterBigRewards.CONFIG, main.id);
        assertNotNull(
                reward, "Original LF_Battle_Win must produce the configured real reward point");
        host.getPosition().set(reward.getPosition());
        guest.getPosition().set(reward.getPosition());
        return reward;
    }

    @AfterEach
    void cleanup() throws Exception {
        if (scene != null) {
            scene.scripts.onDestroy();
            scene.scripts.drain();
        }
        if (library != null) {
            library.removeCurrentGroup();
            library.removeCurrentCallParams();
            library.removeSceneScriptManager();
        }
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastoreField != null) datastoreField.set(null, oldDatastore);
        if (configurationField != null) configurationField.set(null, oldConfig);
        emu.grasscutter.config.Configuration.GAME_OPTIONS.resinOptions.resinUsage = resinUsage;
        if (resources != null) resources.close();
        if (environment != null) environment.close();
    }

    @Test
    void actualOriginalWinAndTwoPlayersClaimFrozenDropsOnceWithFortyResin() throws Exception {
        var reward = win();
        var info = reward.toProto().getGadget().getGeneralReward();
        assertEquals(40, info.getResin());
        assertEquals(0, info.getDeadTime());
        assertEquals(List.of(10001, 10002), info.getQualifyUidListList());
        assertEquals(List.of(10001, 10002), info.getRemainUidListList());
        assertNotNull(reward.getContent());
        assertEquals(1, saved.size());
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(100, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        guest.worldLevel = 0;
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(200, guest.adventureExp);
        assertEquals(80, guest.companionshipExp);
        assertEquals(50, AsterSchedule.progress(resources.guest).bigCredit());
        var milestone =
                ActivityUpdateWatcherNotify.parseFrom(
                        guest.packets.stream()
                                .filter(
                                        p ->
                                                p.getOpcode()
                                                        == PacketOpcodes
                                                                .ActivityUpdateWatcherNotify)
                                .findFirst()
                                .orElseThrow()
                                .getData());
        assertEquals(2001, milestone.getActivityId());
        assertEquals(1200132, milestone.getWatcherInfo().getWatcherId());
        assertEquals(1, milestone.getWatcherInfo().getCurProgress());
        assertEquals(7, milestone.getWatcherInfo().getTotalProgress());
        assertFalse(resources.host.getWatcherInfoMap().containsKey(1200132));
        assertEquals(0, AsterSchedule.progress(resources.guest).credit());
        assertSame(reward, scene.getEntityById(reward.getId()));
        var notify =
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
        assertEquals(reward.getId(), notify.getEntityId());
        assertEquals(List.of(10001), notify.getGeneralRewardInfo().getRemainUidListList());
        assertEquals(0, notify.getGeneralRewardInfo().getDeadTime());
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, interact(host, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(0, interact(host, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, host.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(200, host.adventureExp);
        assertEquals(60, host.companionshipExp);
        assertEquals(20, AsterSchedule.progress(resources.host).bigCredit());
        assertEquals(0, AsterSchedule.progress(resources.host).midCollectCount());
        assertNull(scene.getEntityById(reward.getId()));
    }

    @Test
    void rewardCannotBeCreatedBeforeWinAndRepeatedWinCallDoesNotReplaceTheGeneration() {
        assertEquals(
                -1, library.CreateScenePlayGeneralRewardGadget(main.id, AsterBigRewards.CONFIG));
        var reward = win();
        String token = AsterSchedule.progress(resources.host).bigReward().token();
        assertEquals(
                0, library.CreateScenePlayGeneralRewardGadget(main.id, AsterBigRewards.CONFIG));
        assertSame(reward, scene.getEntityById(reward.getId()));
        assertEquals(token, AsterSchedule.progress(resources.host).bigReward().token());
        assertEquals(1, saved.size());
        assertEquals(
                -1,
                library.CreateScenePlayGeneralRewardGadget(main.id, AsterBigRewards.CONFIG + 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void staleForeignDistantUnloadedAndCondensedRequestsCannotDebit(int invalid) throws Exception {
        var reward = win();
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        var req =
                GadgetInteractReq.newBuilder()
                        .setGadgetEntityId(reward.getId())
                        .setOpType(InterOpType.InterOpType_INTER_OP_FINISH);
        if (invalid == 0) req.setGadgetEntityId(reward.getId() + 1);
        if (invalid == 1) req.setGadgetId(AsterBigRewards.GADGET + 1);
        if (invalid == 2) req.setResinCostType(ResinCostType.ResinCostType_CONDENSE);
        if (invalid == 3) guest.getPosition().addX(6);
        if (invalid == 4)
            guest.setSceneLoadState(emu.grasscutter.game.player.Player.SceneLoadState.NONE);
        if (invalid == 5) scene.getWorld().getPlayers().remove(guest);
        if (invalid == 6) scene.scripts.loaded.put(main.id, SceneGroup.of(main.id).load(3));
        reward.onInteract(guest, req.build());
        assertNotEquals(0, lastCode(guest));
        assertEquals(100, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, guest.adventureExp);
    }

    @Test
    void newRoundRemovesOldPointAndRejectsItsPreview() throws Exception {
        var reward = win();
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        prepareAndStart();
        assertNull(scene.getEntityById(reward.getId()));
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(100, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        var fresh = win();
        assertNotSame(fresh, reward);
        assertNotEquals(0, interact(guest, fresh, InterOpType.InterOpType_INTER_OP_FINISH));
    }

    @Test
    void failedCompletionBlocksNewRoundAfterThePointIsRemoved() throws Exception {
        var reward = win();
        assertEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_START));
        failActivityWrite =
                4; // Creation, shared reservation, personal reservation, personal confirmation.
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(200, guest.adventureExp);
        assertTrue(AsterSchedule.progress(resources.guest).hasPendingReward());
        scene.removeEntity(reward);
        battles.tick();
        assertFalse(
                battles.prepare(
                        GameData.getMpPlayGroupDataMap().get(2),
                        match,
                        2,
                        MATCH,
                        List.of(host, guest)));
        assertNotEquals(0, interact(guest, reward, InterOpType.InterOpType_INTER_OP_FINISH));
        assertEquals(60, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(200, guest.adventureExp);
    }

    @Test
    void closingScheduleRemovesRewardWithoutChangingDurableQualification() {
        var reward = win();
        ActivityManager.installConfiguration(new ActivityManager.Configuration(Map.of(), Map.of()));
        battles.tick();
        assertNull(scene.getEntityById(reward.getId()));
        assertEquals(
                Set.of(10001, 10002),
                AsterSchedule.progress(resources.host).bigReward().remaining());
        assertEquals(100, host.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void worldQualificationIncludesPlayersInAnotherSceneAndRejectsLateJoiners() throws Exception {
        var away = new AsterMidSceneTest.RecoveryPlayer();
        ready(away, resources.data(10003), host.server, host.inventorySystem, 1);
        scene.getPlayers().remove(away); // World member, not one of the two battle participants.
        var reward = win();
        away.getPosition().set(reward.getPosition());
        assertEquals(
                List.of(10001, 10002, 10003),
                reward.toProto().getGadget().getGeneralReward().getQualifyUidListList());
        assertNotEquals(0, interact(away, reward, InterOpType.InterOpType_INTER_OP_START));
        scene.getPlayers().add(away);
        assertEquals(0, interact(away, reward, InterOpType.InterOpType_INTER_OP_START));
        var late = new AsterMidSceneTest.RecoveryPlayer();
        ready(late, resources.data(10004), host.server, host.inventorySystem, 8);
        late.getPosition().set(reward.getPosition());
        assertNotEquals(0, interact(late, reward, InterOpType.InterOpType_INTER_OP_START));
        assertEquals(100, late.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(
                List.of(10001, 10002, 10003),
                reward.toProto().getGadget().getGeneralReward().getQualifyUidListList());
    }

    @Test
    void luaRewardAdapterRejectsCoercionAndMissingIdentifiersBeforeTheDuplicateGuard() {
        var reward = win();
        var context =
                library.createEventContext(
                        new ScriptArgs(main.id, 50, 2, 2).setParam3(WIN).setUid(host.getUid()),
                        main);
        var adapter = ScriptLoader.getScriptLibLua().get("CreateScenePlayGeneralRewardGadget");
        for (var bad :
                List.of(
                        LuaValue.NIL,
                        LuaValue.TRUE,
                        LuaValue.valueOf("133001413"),
                        LuaValue.valueOf(0),
                        LuaValue.valueOf(-1),
                        LuaValue.valueOf(133001413.5),
                        LuaValue.valueOf(4294967296d))) {
            var args = new LuaTable();
            args.set("group_id", bad);
            args.set("config_id", AsterBigRewards.CONFIG);
            assertEquals(-1, adapter.call(context, args).toint());
            args.set("group_id", main.id);
            args.set("config_id", bad);
            assertEquals(-1, adapter.call(context, args).toint());
        }
        assertEquals(-1, adapter.call(context, LuaValue.NIL).toint());
        assertSame(reward, scene.getEntityById(reward.getId()));
        assertEquals(1, saved.size());
    }

    private int interact(
            AsterMidSceneTest.RecoveryPlayer player, EntityGadget reward, InterOpType op)
            throws Exception {
        reward.onInteract(
                player,
                GadgetInteractReq.newBuilder()
                        .setGadgetEntityId(reward.getId())
                        .setOpType(op)
                        .build());
        return lastCode(player);
    }

    private int lastCode(AsterMidSceneTest.RecoveryPlayer player) throws Exception {
        return GadgetInteractRsp.parseFrom(
                        player.packets.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.GadgetInteractRsp)
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getData())
                .getRetcode();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}

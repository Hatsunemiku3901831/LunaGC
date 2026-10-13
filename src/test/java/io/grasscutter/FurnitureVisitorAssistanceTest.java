package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.Datastore;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.HomeWorldLevelData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeBeHelpedNotifyOuterClass.FurnitureMakeBeHelpedNotify;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

class FurnitureVisitorAssistanceTest {
    static class Visitor extends FurnitureTakeFlowTest.CraftPlayer {
        final int uid;
        boolean online = true, failPacket;
        String name;
        final List<String> messages = new ArrayList<>();
        final emu.grasscutter.game.Account account = new emu.grasscutter.game.Account();

        Visitor(int uid) throws ReflectiveOperationException {
            this.uid = uid;
            name = "visitor-" + uid;
            var id = Player.class.getDeclaredField("id");
            id.setAccessible(true);
            id.setInt(this, uid);
        }

        @Override
        public int getUid() {
            return uid;
        }

        @Override
        public boolean isOnline() {
            return online;
        }

        @Override
        public String getNickname() {
            return name;
        }

        @Override
        public int getHeadImage() {
            return 10000014;
        }

        @Override
        public emu.grasscutter.game.Account getAccount() {
            return account;
        }

        @Override
        public void dropMessage(Object message) {
            messages.add(String.valueOf(message));
        }

        @Override
        public ProfilePicture getProfilePicture() {
            return ProfilePicture.newBuilder()
                    .setAvatarId(10000014)
                    .setCostumeId(201401)
                    .setProfilePictureId(210002)
                    .setProfileFrameId(10001)
                    .build();
        }

        @Override
        public void sendPacket(emu.grasscutter.net.packet.BasePacket packet) {
            if (failPacket) throw new IllegalStateException("Network delivery fault");
            super.sendPacket(packet);
        }
    }

    static class VisitWorld extends HomeWorld {
        VisitWorld(GameServer server, Player owner) {
            super(server, owner);
        }

        @Override
        public void refreshModuleManager() {}
    }

    final Gson gson =
            new GsonBuilder()
                    .setExclusionStrategies(
                            new ExclusionStrategy() {
                                public boolean shouldSkipField(FieldAttributes field) {
                                    return field.getAnnotation(
                                                    dev.morphia.annotations.Transient.class)
                                            != null;
                                }

                                public boolean shouldSkipClass(Class<?> type) {
                                    return false;
                                }
                            })
                    .create();
    AbilityTestFixture environment;
    Visitor owner, guest;
    VisitWorld world;
    HomeScene scene;
    Field datastore;
    Object previous;
    Map<Integer, HomeWorldLevelData> levels;
    int now, saves;
    boolean failSave;
    String durable;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        owner = new Visitor(10001);
        guest = new Visitor(10002);
        var server = environment.scene.getWorld().getServer();
        owner.setSession(new InvestigationRewardTest.ClaimSession(server, owner));
        guest.setSession(new InvestigationRewardTest.ClaimSession(server, guest));
        owner.home =
                GameHome.of()
                        .ownerUid(owner.getUid())
                        .player(owner)
                        .level(1)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        guest.home = GameHome.of().ownerUid(guest.getUid()).level(1).build();
        world = new VisitWorld(server, owner);
        var data = gson.fromJson("{id:2001,sceneType:'SCENE_HOME_WORLD'}", SceneData.class);
        scene = new HomeScene(world, data);
        world.getScenes().put(scene.getId(), scene);
        world.getPlayers().addAll(List.of(owner, guest));
        scene.getPlayers().addAll(List.of(owner, guest));
        owner.setWorld(world);
        guest.setWorld(world);
        owner.setScene(scene);
        guest.setScene(scene);
        levels = new HashMap<>(GameData.getHomeWorldLevelDataMap());
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        now = Utils.getCurrentSeconds();
        job(0);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previous = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) {
                                saves++;
                                if (failSave) throw new IllegalStateException("Write fault");
                                assertSame(owner.home, args[0]);
                                durable = gson.toJson(args[0]);
                                return args[0];
                            }
                            throw new UnsupportedOperationException(method.getName());
                        }));
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, previous);
        GameData.getHomeWorldLevelDataMap().clear();
        GameData.getHomeWorldLevelDataMap().putAll(levels);
        environment.close();
    }

    void job(int index) {
        owner.home
                .getFurnitureMakeSlotItemList()
                .add(
                        FurnitureMakeSlotItem.of()
                                .index(index)
                                .makeId(38)
                                .avatarId(0)
                                .beginTime(now)
                                .durTime(86400)
                                .build());
    }

    int assist(Player visitor) throws Exception {
        Method operation = null;
        try {
            operation = FurnitureAssistance.class.getMethod("assist", Player.class, int.class);
        } catch (NoSuchMethodException missing) {
        }
        assertNotNull(operation, "Missing authenticated home visitor assistance operation");
        try {
            return (int) operation.invoke(null, visitor, now);
        } catch (InvocationTargetException error) {
            throw new AssertionError("Visitor assistance leaked an exception", error.getCause());
        }
    }

    List<emu.grasscutter.net.packet.BasePacket> notifications() {
        return owner.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeBeHelpedNotify)
                .toList();
    }

    FurnitureMakeRsp query(GameHome home) throws Exception {
        return FurnitureMakeRsp.parseFrom(new PacketFurnitureMakeRsp(home).getData());
    }

    @Test
    void authenticVisitorPersistsProfileAndNotifiesOnlyOwnerAfterOneWrite() throws Exception {
        assertTrue(guest.isOnline(), "visitor online");
        assertSame(world, guest.getWorld(), "visitor world");
        assertSame(owner.home, world.getHome(), "world home");
        assertEquals(owner.home.getOwnerUid(), world.getOwnerUid(), "owner UID");
        assertTrue(world.isInHome(guest), "world membership");
        assertSame(scene, guest.getScene(), "visitor scene");
        assertSame(world, scene.getWorld(), "scene world");
        assertSame(scene, world.getScenes().get(scene.getId()), "registered scene");
        assertTrue(scene.getPlayers().contains(guest), "scene membership");
        assertEquals(0, assist(guest));
        assertEquals(1, saves);
        assertNotNull(durable);
        assertEquals(1, notifications().size());
        assertTrue(guest.packets.isEmpty());
        var message = FurnitureMakeBeHelpedNotify.parseFrom(notifications().get(0).getData());
        var profile = message.getHelpedData();
        assertEquals(guest.getUid(), profile.getUid());
        assertEquals(guest.getNickname(), profile.getPlayerName());
        assertEquals(now, profile.getTime());
        assertEquals(guest.getHeadImage(), profile.getIcon());
        assertEquals(guest.getProfilePicture(), profile.getProfilePicture());
        assertEquals(
                14400,
                message.getFurnitureMakeSlot().getFurnitureMakeDataList(0).getAccelerateTime());
        assertEquals(profile, query(owner.home).getHelpedDataList(0));
    }

    @Test
    void historySurvivesReloadAndRestoresInCurrentStatusPacket() throws Exception {
        assertEquals(0, assist(guest));
        var loaded = gson.fromJson(durable, GameHome.class);
        assertEquals(1, query(loaded).getHelpedDataListCount());
        assertEquals(guest.getUid(), query(loaded).getHelpedDataList(0).getUid());
        assertEquals(
                guest.getProfilePicture(), query(loaded).getHelpedDataList(0).getProfilePicture());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "normal_world",
                "missing_scene",
                "wrong_scene",
                "world_member",
                "scene_member",
                "wrong_owner",
                "stale_home"
            })
    void invalidVisitCannotChangeAnotherHomeOrSendNotifications(String reason) throws Exception {
        switch (reason) {
            case "normal_world" -> guest.setWorld(environment.scene.getWorld());
            case "missing_scene" -> guest.setScene(null);
            case "wrong_scene" -> guest.setScene(environment.scene);
            case "world_member" -> world.getPlayers().remove(guest);
            case "scene_member" -> scene.getPlayers().remove(guest);
            case "wrong_owner" -> owner.home.setOwnerUid(10003);
            case "stale_home" -> owner.home = GameHome.of().ownerUid(10001).build();
            default -> fail(reason);
        }
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, assist(guest));
        assertEquals(0, saves);
        assertTrue(notifications().isEmpty());
        assertEquals(0, world.getHome().getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
    }

    @Test
    void ownerAndMissingActorCannotAssistSelf() throws Exception {
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, assist(owner));
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, assist(null));
        assertEquals(0, saves);
    }

    @Test
    void offlineOwnerStillReceivesDurableAssistanceWithoutNotification() throws Exception {
        owner.online = false;
        assertEquals(0, assist(guest));
        assertEquals(1, saves);
        assertTrue(notifications().isEmpty());
        assertEquals(1, query(owner.home).getHelpedDataListCount());
    }

    @Test
    void duplicateAndCappedAssistanceDoesNotAppendProfilesOrNotify() throws Exception {
        assertEquals(0, assist(guest));
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(guest));
        assertEquals(1, notifications().size());
        assertEquals(1, query(owner.home).getHelpedDataListCount());
        assertEquals(1, saves);
    }

    @Test
    void failedWriteDoesNotNotifyAndRetryUsesFrozenIdentity() throws Exception {
        failSave = true;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(guest));
        assertTrue(notifications().isEmpty());
        String captured = guest.name;
        guest.name = "changed-after-attempt";
        failSave = false;
        assertEquals(0, assist(guest));
        assertEquals(2, saves);
        assertEquals(captured, query(owner.home).getHelpedDataList(0).getPlayerName());
        assertEquals(1, notifications().size());
        assertEquals(14400, owner.home.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
    }

    @Test
    void notificationFailureDoesNotRollBackCommittedAssistance() throws Exception {
        owner.failPacket = true;
        assertEquals(0, assist(guest));
        assertNotNull(durable);
        assertEquals(1, query(owner.home).getHelpedDataListCount());
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(guest));
        assertEquals(1, saves);
    }

    @Test
    void anotherDayReplacesOnlyThisGuestsLastProfile() throws Exception {
        assertEquals(0, assist(guest));
        now += 86400;
        job(1);
        guest.name = "updated-profile";
        assertEquals(0, assist(guest));
        assertEquals(1, query(owner.home).getHelpedDataListCount());
        assertEquals("updated-profile", query(owner.home).getHelpedDataList(0).getPlayerName());
        assertEquals(now, query(owner.home).getHelpedDataList(0).getTime());
    }

    @Test
    void managerProvidesCurrentVisitorOperation() throws Exception {
        Method action = null;
        try {
            action = guest.getFurnitureManager().getClass().getMethod("helpMake");
        } catch (NoSuchMethodException missing) {
        }
        assertNotNull(action, "Missing furniture manager gameplay entry");
        assertEquals(0, action.invoke(guest.getFurnitureManager()));
        assertEquals(1, notifications().size());
    }

    Object command() throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("emu.grasscutter.command.commands.FurnitureCommand");
        } catch (ClassNotFoundException missing) {
        }
        assertNotNull(type, "Missing usable furniture assistance command");
        return type.getConstructor().newInstance();
    }

    void runCommand(Player sender, Player target, List<String> arguments) throws Exception {
        var action = command();
        try {
            action.getClass()
                    .getMethod("execute", Player.class, Player.class, List.class)
                    .invoke(action, sender, target, arguments);
        } catch (InvocationTargetException error) {
            throw new AssertionError("Furniture command failed", error.getCause());
        }
    }

    @Test
    void commandUsesSenderVisitAndIgnoresUnrelatedTarget() throws Exception {
        runCommand(guest, owner, List.of("help"));
        assertEquals(1, saves);
        assertEquals(1, notifications().size());
        assertEquals(guest.getUid(), query(owner.home).getHelpedDataList(0).getUid());
        assertEquals(1, guest.messages.size());
    }

    @Test
    void commandDoesNotGiveConsoleAnotherPlayersIdentity() throws Exception {
        runCommand(null, guest, List.of("help"));
        assertEquals(0, saves);
        assertTrue(notifications().isEmpty());
    }

    @Test
    void commandUsageDoesNotApplyAssistance() throws Exception {
        runCommand(guest, null, List.of("help", "10003"));
        assertEquals(0, saves);
        assertTrue(notifications().isEmpty());
        assertEquals(1, guest.messages.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void realMongoReloadRetainsCapturedIdentityWithAndWithoutAcknowledgement(int loseAck)
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_assist_profile_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            try {
                var store = dev.morphia.Morphia.createDatastore(client, database);
                store.getMapper().map(GameHome.class);
                store.save(owner.home);
                datastore.set(
                        null,
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    Object result;
                                    try {
                                        result = method.invoke(store, args);
                                    } catch (InvocationTargetException error) {
                                        throw error.getCause();
                                    }
                                    if (loseAck == 1 && method.getName().equals("save"))
                                        throw new IllegalStateException(
                                                "Committed profile; acknowledgement lost");
                                    return result;
                                }));
                assertEquals(loseAck == 0 ? 0 : Retcode.RET_SVR_ERROR_VALUE, assist(guest));
                assertEquals(loseAck == 0 ? 1 : 0, notifications().size());
                var loaded = store.find(GameHome.class).first();
                assertNotNull(loaded);
                var data = query(loaded).getHelpedDataList(0);
                assertEquals(guest.getUid(), data.getUid());
                assertEquals(guest.getNickname(), data.getPlayerName());
                assertEquals(now, data.getTime());
                assertEquals(guest.getProfilePicture(), data.getProfilePicture());
                assertEquals(
                        14400, loaded.getFurnitureMakeSlotItemList().get(0).getAccelerateTime());
                assertEquals(
                        Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE,
                        FurnitureAssistance.assist(loaded, guest.getUid(), now));
                var raw = store.getDatabase().getCollection("homes").find().first();
                assertNotNull(raw);
                assertTrue(raw.containsKey("furnitureAssistanceProfiles"));
                assertFalse(raw.containsKey("pendingFurnitureAssistanceWrite"));
            } finally {
                datastore.set(null, previous);
                assertTrue(database.startsWith("lunagc_assist_profile_"));
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void legacyUidOnlyPendingAttemptCanBeConfirmedWithoutInventingAProfile() throws Exception {
        failSave = true;
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                FurnitureAssistance.assist(owner.home, guest.getUid(), now));
        failSave = false;
        assertEquals(0, assist(guest));
        assertEquals(2, saves);
        assertTrue(notifications().isEmpty());
        assertEquals(0, query(owner.home).getHelpedDataListCount());
    }

    @Test
    void sameUidDoesNotReplaceRegisteredVisitorIdentity() throws Exception {
        var stale = new Visitor(guest.getUid());
        stale.setSession(new InvestigationRewardTest.ClaimSession(world.getServer(), stale));
        stale.setWorld(world);
        stale.setScene(scene);
        assertTrue(world.getPlayers().contains(stale), "UID equality alone accepts this instance");
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, assist(stale));
        assertEquals(0, saves);
        assertTrue(notifications().isEmpty());
    }

    @Test
    void unconfirmedAssistanceIsNotPublishedByStatusQuery() throws Exception {
        failSave = true;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(guest));
        var response = query(owner.home);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, response.getRetcode());
        assertEquals(0, response.getHelpedDataListCount());
        assertFalse(response.hasFurnitureMakeSlot());
        failSave = false;
        assertEquals(0, assist(guest));
        assertEquals(0, query(owner.home).getRetcode());
        assertEquals(1, query(owner.home).getHelpedDataListCount());
    }

    @Test
    void ownerStatusRequestCanConfirmPendingWriteWithoutWaitingForGuestRetry() throws Exception {
        failSave = true;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, assist(guest));
        failSave = false;
        owner.getFurnitureManager().queryStatus();
        assertEquals(2, saves);
        assertNotNull(durable);
        var packet =
                owner.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeRsp)
                        .findFirst()
                        .orElseThrow();
        var response = FurnitureMakeRsp.parseFrom(packet.getData());
        assertEquals(0, response.getRetcode());
        assertEquals(1, response.getHelpedDataListCount());
        assertEquals(Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE, assist(guest));
    }
}

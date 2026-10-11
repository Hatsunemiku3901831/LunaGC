package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import dev.morphia.Datastore;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.MpPlayGroupData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.CoopRequest;
import emu.grasscutter.game.activity.ActivityConfigItem;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.activity.crucible.*;
import emu.grasscutter.game.activity.crucible.CrucibleMatchSystem;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.systems.MultiplayerSystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BackMyWorldRspOuterClass.BackMyWorldRsp;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.HomeKickPlayerReqOuterClass.HomeKickPlayerReq;
import emu.grasscutter.net.proto.HomeKickPlayerRspOuterClass.HomeKickPlayerRsp;
import emu.grasscutter.net.proto.PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultNotifyOuterClass.PlayerApplyEnterMpResultNotify;
import emu.grasscutter.net.proto.PlayerEnterSceneNotifyOuterClass.PlayerEnterSceneNotify;
import emu.grasscutter.net.proto.TryEnterHomeRspOuterClass.TryEnterHomeRsp;
import emu.grasscutter.server.event.player.PlayerEnterHomeEvent;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.server.packet.send.*;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.util.*;

class WorldEntryCallerTest {
    private AbilityTestFixture fixture;
    private GameServer server;
    private MultiplayerSystem multiplayer;
    private HomeWorldMPSystem homes;
    private final List<Runnable> restore = new ArrayList<>();
    private int uid;

    class Member extends AbilityTestFixture.TestPlayer {
        final GameHome home;
        private final GameSession session =
                new GameSession(null) {
                    @Override
                    public Player getPlayer() {
                        return Member.this;
                    }

                    @Override
                    public GameServer getServer() {
                        return server;
                    }

                    @Override
                    public void send(BasePacket packet) {
                        packets.add(packet);
                    }
                };

        Member() {
            setUid(++uid);
            setNickname("caller-" + getUid());
            setRealmList(new HashSet<>(Set.of(1)));
            setCurrentRealmId(1);
            home = GameHome.create(getUid());
            home.setPlayer(this);
            home.getSceneMap()
                    .put(
                            2001,
                            HomeSceneItem.of()
                                    .sceneId(2001)
                                    .blockItems(new HashMap<>())
                                    .bornPos(new Position(100, 200, 300))
                                    .bornRot(new Position(0, 90, 0))
                                    .build());
            home.getMainHouseMap()
                    .put(
                            2001,
                            HomeSceneItem.of()
                                    .sceneId(2201)
                                    .blockItems(new HashMap<>())
                                    .bornPos(new Position())
                                    .bornRot(new Position())
                                    .build());
            server.registerPlayer(this);
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public GameSession getSession() {
            return session;
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public GameHome getHome() {
            return home;
        }

        @Override
        public void save() {}
    }

    private void field(String name, Object value) throws Exception {
        var field = GameServer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(server, value);
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        server = fixture.scene.getWorld().getServer();
        uid = 18000;
        field("players", new HashMap<Integer, Player>());
        field("homeWorlds", new Int2ObjectOpenHashMap<HomeWorld>());
        field("crucibleMatchSystem", new CrucibleMatchSystem(server));
        multiplayer = new MultiplayerSystem(server);
        homes = new HomeWorldMPSystem(server);
        field("homeWorldMPSystem", homes);
        field("multiplayerSystem", multiplayer);
        assertTrue(DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        var storeField = DatabaseManager.class.getDeclaredField("gameDatastore");
        storeField.setAccessible(true);
        var previousStore = storeField.get(null);
        storeField.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        restore.add(
                () -> {
                    try {
                        assertTrue(DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
                        storeField.set(null, previousStore);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        var previous = GameData.getSceneDataMap().remove(3);
        restore.add(
                () -> {
                    if (previous != null) GameData.getSceneDataMap().put(3, previous);
                    else GameData.getSceneDataMap().remove(3);
                });
        var previousHome = GameData.getSceneDataMap().remove(2001);
        restore.add(
                () -> {
                    if (previousHome != null) GameData.getSceneDataMap().put(2001, previousHome);
                    else GameData.getSceneDataMap().remove(2001);
                });
    }

    @AfterEach
    void cleanup() {
        Collections.reverse(restore);
        try {
            for (var action : restore) action.run();
        } finally {
            fixture.close();
        }
    }

    private SceneData data(int id) {
        return new Gson().fromJson("{\"id\":" + id + ",\"type\":\"SCENE_WORLD\"}", SceneData.class);
    }

    private AbilityTestFixture.TestWorld world(Member owner, boolean mp) {
        var world =
                new AbilityTestFixture.TestWorld(server, owner) {
                    @Override
                    public boolean isMultiplayer() {
                        return mp;
                    }
                };
        var scene =
                new AbilityTestFixture.TestScene(world, data(3)) {
                    @Override
                    public void saveGroups() {}
                };
        scene.setDontDestroyWhenEmpty(true);
        world.registerScene(scene);
        return world;
    }

    private HomeWorld home(Member owner, boolean sceneAvailable) {
        var world =
                new HomeWorld(server, owner) {
                    private HomeModuleManager module;

                    @Override
                    public void refreshModuleManager() {}

                    @Override
                    public int getActiveIndoorSceneId() {
                        return -1;
                    }

                    @Override
                    public HomeModuleManager getModuleManager() {
                        if (module == null) module = new HomeModuleManager(this);
                        return module;
                    }

                    @Override
                    public void broadcastPacket(BasePacket packet) {
                        owner.packets.add(packet);
                    }
                };
        if (sceneAvailable) {
            var scene =
                    new HomeScene(world, data(2001)) {
                        @Override
                        public void saveGroups() {}

                        @Override
                        public void broadcastPacket(BasePacket packet) {
                            owner.packets.add(packet);
                        }
                    };
            world.registerScene(scene);
        }
        server.getHomeWorlds().put(owner.getUid(), world);
        return world;
    }

    private void loaded(World world) {
        world.getPlayers().forEach(p -> p.setSceneLoadState(SceneLoadState.LOADED));
    }

    private void noEntry(Member player) {
        assertTrue(
                player.packets.stream()
                        .noneMatch(
                                p ->
                                        p instanceof PacketPlayerEnterSceneNotify
                                                || p instanceof PacketEnterScenePeerNotify));
    }

    private void samePosition(Position expected, Position actual) {
        assertEquals(expected.toProto(), actual.toProto());
    }

    @Test
    void ordinaryCoopLeaveFailsWithoutAnAvailablePersonalScene() {
        var owner = new Member();
        var player = new Member();
        var source = world(owner, true);
        source.addPlayer(owner);
        source.addPlayer(player);
        loaded(source);
        player.setCurHomeWorld(home(player, false));
        player.packets.clear();
        var position = player.getPosition().clone();
        assertFalse(multiplayer.leaveCoop(player));
        assertSame(source, player.getWorld());
        samePosition(position, player.getPosition());
        noEntry(player);
    }

    @Test
    void ordinaryKickFailsWithoutAnAvailablePersonalScene() {
        var owner = new Member();
        var player = new Member();
        var source = world(owner, true);
        source.addPlayer(owner);
        source.addPlayer(player);
        loaded(source);
        player.packets.clear();
        assertFalse(multiplayer.kickPlayer(owner, player.getUid()));
        assertSame(source, player.getWorld());
        noEntry(player);
    }

    @Test
    void ordinaryHostCannotKickAPlayerInAnotherWorld() {
        var owner = new Member();
        var player = new Member();
        var source = world(owner, true);
        var other = world(player, false);
        source.addPlayer(owner);
        other.addPlayer(player);
        loaded(other);
        player.packets.clear();
        assertFalse(multiplayer.kickPlayer(owner, player.getUid()));
        assertSame(other, player.getWorld());
        noEntry(player);
    }

    @Test
    void acceptingARequestIntoAClosedWorldDoesNotMoveOrReportSuccess() throws Exception {
        var owner = new Member();
        var player = new Member();
        var target = world(owner, true);
        var source = world(player, false);
        target.addPlayer(owner);
        target.removePlayer(owner);
        source.addPlayer(player);
        // The host can be leaving concurrently after accepting; keep that stale reference as a real
        // caller sees it.
        owner.setWorld(target);
        owner.setScene(target.getSceneById(3));
        owner.getPosition().set(new Position(10, 20, 30));
        owner.getCoopRequests().put(player.getUid(), new CoopRequest(player));
        player.packets.clear();
        var position = player.getPosition().clone();
        var rotation = player.getRotation().clone();
        multiplayer.applyEnterMpReply(owner, player.getUid(), true);
        assertSame(source, player.getWorld());
        samePosition(position, player.getPosition());
        samePosition(rotation, player.getRotation());
        noEntry(player);
        var results =
                player.packets.stream()
                        .filter(p -> p instanceof PacketPlayerApplyEnterMpResultNotify)
                        .toList();
        assertFalse(results.isEmpty());
        for (var packet : results)
            assertFalse(PlayerApplyEnterMpResultNotify.parseFrom(packet.getData()).getIsAgreed());
    }

    @Test
    void failedHostPromotionDoesNotAcceptOrMoveTheRequester() throws Exception {
        var owner = new Member();
        var player = new Member();
        var original = world(owner, false);
        var source = world(player, false);
        original.addPlayer(owner);
        source.addPlayer(player);
        owner.getCoopRequests().put(player.getUid(), new CoopRequest(player));
        owner.packets.clear();
        player.packets.clear();
        assertDoesNotThrow(() -> multiplayer.applyEnterMpReply(owner, player.getUid(), true));
        assertSame(original, owner.getWorld());
        assertSame(source, player.getWorld());
        noEntry(owner);
        noEntry(player);
        for (var packet : player.packets)
            if (packet instanceof PacketPlayerApplyEnterMpResultNotify)
                assertFalse(
                        PlayerApplyEnterMpResultNotify.parseFrom(packet.getData()).getIsAgreed());
    }

    @Test
    void failedHomeEntryPreservesReturnCoordinatesAndHomeReferences() throws Exception {
        var owner = new Member();
        var player = new Member();
        var source = world(player, false);
        source.addPlayer(player);
        var previousHome = home(player, false);
        home(owner, false);
        player.setCurHomeWorld(previousHome);
        player.setPrevScene(42);
        player.getPrevPosForHome().set(new Position(7, 8, 9));
        var position = player.getPosition().clone();
        player.packets.clear();
        assertDoesNotThrow(() -> homes.enterHome(player, owner, 0, false));
        assertSame(source, player.getWorld());
        assertSame(previousHome, player.getCurHomeWorld());
        assertEquals(42, player.getPrevScene());
        samePosition(new Position(7, 8, 9), player.getPrevPosForHome());
        samePosition(position, player.getPosition());
        noEntry(player);
        var result =
                player.packets.stream()
                        .filter(p -> p instanceof PacketTryEnterHomeRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertNotEquals(0, TryEnterHomeRsp.parseFrom(result.getData()).getRetcode());
    }

    @Test
    void failedHomeLeavePreservesCurrentAndReturnPositions() {
        var owner = new Member();
        var player = new Member();
        var source = home(owner, true);
        home(player, false);
        source.addPlayer(owner, 2001);
        source.addPlayer(player, 2001);
        loaded(source);
        player.setCurHomeWorld(source);
        player.getPosition().set(new Position(100, 200, 300));
        player.getPrevPosForHome().set(new Position(7, 8, 9));
        player.packets.clear();
        assertFalse(homes.leaveCoop(player, 987654));
        assertSame(source, player.getWorld());
        assertSame(source, player.getCurHomeWorld());
        samePosition(new Position(100, 200, 300), player.getPosition());
        samePosition(new Position(7, 8, 9), player.getPrevPosForHome());
        noEntry(player);
    }

    @Test
    void failedHomeKickDoesNotReportDepartureOrChangeThePosition() {
        var owner = new Member();
        var player = new Member();
        var source = home(owner, true);
        home(player, false);
        source.addPlayer(owner, 2001);
        source.addPlayer(player, 2001);
        loaded(source);
        owner.setCurHomeWorld(source);
        player.setCurHomeWorld(source);
        player.getPosition().set(new Position(100, 200, 300));
        player.getPrevPosForHome().set(new Position(7, 8, 9));
        player.packets.clear();
        owner.packets.clear();
        assertFalse(homes.kickPlayerFromHome(owner, player.getUid()));
        assertSame(source, player.getWorld());
        samePosition(new Position(100, 200, 300), player.getPosition());
        noEntry(player);
        assertTrue(
                owner.packets.stream()
                        .noneMatch(p -> p instanceof PacketOtherPlayerEnterOrLeaveHomeNotify));
    }

    @Test
    void homeOwnerCannotKickAPlayerWhoIsInAnotherWorld() {
        var owner = new Member();
        var player = new Member();
        var home = home(owner, true);
        var other = world(player, false);
        home(player, false);
        home.addPlayer(owner, 2001);
        owner.setCurHomeWorld(home);
        other.addPlayer(player);
        player.setCurHomeWorld(home);
        loaded(other);
        player.packets.clear();
        assertFalse(homes.kickPlayerFromHome(owner, player.getUid()));
        assertSame(other, player.getWorld());
        noEntry(player);
    }

    private void personalSceneAvailable() {
        GameData.getSceneDataMap().put(3, data(3));
    }

    private long count(Member member, Class<?> type) {
        return member.packets.stream().filter(type::isInstance).count();
    }

    private PlayerEnterSceneNotify entryPacket(Member member) throws Exception {
        return PlayerEnterSceneNotify.parseFrom(
                member.packets.stream()
                        .filter(p -> p instanceof PacketPlayerEnterSceneNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    @Test
    void ordinaryPromotionAndJoinSendSuccessOnlyAfterBothPlayersEnter() throws Exception {
        personalSceneAvailable();
        var owner = new Member();
        var guest = new Member();
        var original = world(owner, false);
        var source = world(guest, false);
        original.addPlayer(owner);
        source.addPlayer(guest);
        owner.getPosition().set(new Position(10, 20, 30));
        owner.getRotation().set(new Position(0, 80, 0));
        owner.getCoopRequests().put(guest.getUid(), new CoopRequest(guest));
        owner.packets.clear();
        guest.packets.clear();
        multiplayer.applyEnterMpReply(owner, guest.getUid(), true);
        assertNotSame(original, owner.getWorld());
        assertTrue(owner.getWorld().isMultiplayer());
        assertSame(owner.getWorld(), guest.getWorld());
        assertSame(owner.getScene(), guest.getScene());
        samePosition(owner.getPosition(), guest.getPosition());
        samePosition(owner.getRotation(), guest.getRotation());
        assertEquals(1, count(owner, PacketPlayerEnterSceneNotify.class));
        assertEquals(1, count(guest, PacketPlayerEnterSceneNotify.class));
        var results =
                guest.packets.stream()
                        .filter(p -> p instanceof PacketPlayerApplyEnterMpResultNotify)
                        .toList();
        assertEquals(1, results.size());
        assertTrue(
                PlayerApplyEnterMpResultNotify.parseFrom(results.get(0).getData()).getIsAgreed());
        assertEquals(owner.getUid(), (entryPacket(guest).getTargetUid() ^ 4145) + 30259);
    }

    @Test
    void ordinaryLeaveReturnsToAnOwnedWorldAndSendsOneEntry() {
        personalSceneAvailable();
        var owner = new Member();
        var guest = new Member();
        var source = world(owner, true);
        source.addPlayer(owner);
        source.addPlayer(guest);
        loaded(source);
        guest.packets.clear();
        assertTrue(multiplayer.leaveCoop(guest));
        assertSame(guest, guest.getWorld().getHost());
        assertNotSame(source, guest.getWorld());
        assertEquals(List.of(owner), source.getPlayers());
        assertEquals(1, count(guest, PacketPlayerEnterSceneNotify.class));
        assertEquals(1, count(guest, PacketEnterScenePeerNotify.class));
    }

    @Test
    void ordinaryKickReturnsOnlyTheRequestedGuestToAnOwnedWorld() {
        personalSceneAvailable();
        var owner = new Member();
        var guest = new Member();
        var another = new Member();
        var source = world(owner, true);
        source.addPlayer(owner);
        source.addPlayer(guest);
        source.addPlayer(another);
        loaded(source);
        guest.packets.clear();
        assertFalse(multiplayer.kickPlayer(owner, owner.getUid()));
        assertTrue(multiplayer.kickPlayer(owner, guest.getUid()));
        assertSame(guest, guest.getWorld().getHost());
        assertSame(source, another.getWorld());
        assertEquals(1, count(guest, PacketPlayerEnterSceneNotify.class));
    }

    @Test
    void enteringAHomeSavesTheActualReturnPositionAndShowsItsEntry() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = world(guest, false);
        source.addPlayer(guest);
        var target = home(owner, true);
        target.addPlayer(owner, 2001);
        guest.getPosition().set(new Position(11, 22, 33));
        guest.setSceneId(42); // The live source scene is authoritative over stale saved metadata.
        guest.setHasSentInitPacketInHome(true);
        guest.packets.clear();
        homes.enterHome(guest, owner, 0, false);
        assertSame(target, guest.getWorld());
        assertSame(target, guest.getCurHomeWorld());
        assertEquals(2001, guest.getSceneId());
        samePosition(new Position(11, 22, 33), guest.getPrevPosForHome());
        assertEquals(3, guest.getPrevScene());
        samePosition(new Position(100, 200, 300), guest.getPosition());
        assertFalse(guest.isHasSentInitPacketInHome());
        assertEquals(EnterType.EnterType_ENTER_OTHER_HOME, entryPacket(guest).getType());
        var response =
                guest.packets.stream()
                        .filter(p -> p instanceof PacketTryEnterHomeRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(0, TryEnterHomeRsp.parseFrom(response.getData()).getRetcode());
    }

    @Test
    void successfulHomeLeaveRestoresSavedCoordinatesAndReinitializesOwnedHome() throws Exception {
        personalSceneAvailable();
        var owner = new Member();
        var guest = new Member();
        var source = home(owner, true);
        var ownHome = home(guest, false);
        source.addPlayer(owner, 2001);
        source.addPlayer(guest, 2001);
        loaded(source);
        guest.setCurHomeWorld(source);
        guest.getPrevPosForHome().set(new Position(7, 8, 9));
        guest.packets.clear();
        owner.packets.clear();
        assertTrue(homes.leaveCoop(guest, 3));
        assertSame(guest, guest.getWorld().getHost());
        assertSame(ownHome, guest.getCurHomeWorld());
        samePosition(new Position(7, 8, 9), guest.getPosition());
        assertEquals(EnterType.EnterType_ENTER_BACK, entryPacket(guest).getType());
        assertEquals(1, count(guest, PacketPlayerQuitFromHomeNotify.class));
        assertEquals(1, count(owner, PacketOtherPlayerEnterOrLeaveHomeNotify.class));
    }

    @Test
    void safeHomeEntryUsesSceneSpawnAndFallsBackToSavedSpawnWhenMissing() throws Exception {
        var owner = new Member();
        var first = new Member();
        var second = new Member();
        var firstSource = world(first, false);
        var secondSource = world(second, false);
        firstSource.addPlayer(first);
        secondSource.addPlayer(second);
        var target = home(owner, true);
        var manager = target.getSceneById(2001).getScriptManager();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (!manager.isInitAttempted() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(manager.isInitAttempted());
        var meta = new emu.grasscutter.scripts.data.SceneMeta();
        meta.config = new emu.grasscutter.scripts.data.SceneConfig();
        meta.config.born_pos = new Position(400, 500, 600);
        meta.blocks = Map.of();
        var field = emu.grasscutter.scripts.SceneScriptManager.class.getDeclaredField("meta");
        field.setAccessible(true);
        field.set(manager, meta);
        var initialized =
                emu.grasscutter.scripts.SceneScriptManager.class.getDeclaredField("isInit");
        initialized.setAccessible(true);
        initialized.set(manager, true);
        homes.enterHome(first, owner, 0, true);
        assertSame(target, first.getWorld());
        samePosition(new Position(400, 500, 600), first.getPosition());
        meta.config = null;
        homes.enterHome(second, owner, 0, true);
        assertSame(target, second.getWorld());
        samePosition(new Position(100, 200, 300), second.getPosition());
        samePosition(new Position(400, 500, 600), first.getPosition());
    }

    @Test
    void successfulHomeKickReturnsTheGuestAndReportsDeparture() {
        personalSceneAvailable();
        var owner = new Member();
        var guest = new Member();
        var source = home(owner, true);
        var ownHome = home(guest, false);
        source.addPlayer(owner, 2001);
        source.addPlayer(guest, 2001);
        loaded(source);
        owner.setCurHomeWorld(source);
        guest.setCurHomeWorld(source);
        guest.getPrevPosForHome().set(new Position(7, 8, 9));
        guest.packets.clear();
        owner.packets.clear();
        assertTrue(homes.kickPlayerFromHome(owner, guest.getUid()));
        assertSame(guest, guest.getWorld().getHost());
        assertSame(ownHome, guest.getCurHomeWorld());
        samePosition(new Position(7, 8, 9), guest.getPosition());
        assertEquals(1, count(owner, PacketOtherPlayerEnterOrLeaveHomeNotify.class));
        assertEquals(1, count(guest, PacketPlayerEnterSceneNotify.class));
    }

    @Test
    void rejectedHomeInvitationNeverSendsAnAgreedResult() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = world(guest, false);
        source.addPlayer(guest);
        home(owner, false);
        owner.getEnterHomeRequests().put(guest.getUid(), new EnterHomeRequest(guest));
        guest.packets.clear();
        homes.acceptEnterHomeRequest(owner, guest.getUid(), true);
        assertSame(source, guest.getWorld());
        noEntry(guest);
        var results =
                guest.packets.stream()
                        .filter(p -> p instanceof PacketPlayerApplyEnterHomeResultNotify)
                        .toList();
        assertEquals(1, results.size());
        assertFalse(
                PlayerApplyEnterHomeResultNotify.parseFrom(results.get(0).getData()).getIsAgreed());
    }

    @Test
    void acceptedHomeInvitationReportsSuccessAndCorrectOwnership() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = world(guest, false);
        source.addPlayer(guest);
        var target = home(owner, true);
        owner.getEnterHomeRequests().put(guest.getUid(), new EnterHomeRequest(guest));
        guest.packets.clear();
        homes.acceptEnterHomeRequest(owner, guest.getUid(), true);
        assertSame(target, guest.getWorld());
        var result =
                guest.packets.stream()
                        .filter(p -> p instanceof PacketPlayerApplyEnterHomeResultNotify)
                        .findFirst()
                        .orElseThrow();
        assertTrue(PlayerApplyEnterHomeResultNotify.parseFrom(result.getData()).getIsAgreed());
        assertTrue(new PlayerEnterHomeEvent(guest, owner, owner.getHome()).isOtherHome());
        assertFalse(new PlayerEnterHomeEvent(owner, owner, owner.getHome()).isOtherHome());
    }

    @Test
    void entryCallbackRunsUnderTheTransferGuardAfterSceneAndCoordinatesAreReady() {
        var guest = new Member();
        var source = world(guest, false);
        var target = world(new Member(), false);
        source.addPlayer(guest);
        var previous = guest.getPosition().clone();
        var position = new Position(4, 5, 6);
        var rotation = new Position(0, 90, 0);
        var called = new boolean[1];
        var properties =
                TeleportProperties.builder()
                        .sceneId(3)
                        .teleportTo(position)
                        .teleportRot(rotation)
                        .build();
        assertTrue(
                target.tryAddPlayer(
                        guest,
                        source,
                        properties,
                        context -> {
                            called[0] = true;
                            assertTrue(Thread.holdsLock(guest.getSceneTransferLock()));
                            assertSame(target, guest.getWorld());
                            assertTrue(guest.getScene().getPlayers().contains(guest));
                            assertNotNull(guest.getTeamManager().getEntity());
                            samePosition(position, guest.getPosition());
                            samePosition(rotation, guest.getRotation());
                            assertSame(source, context.previousWorld());
                            samePosition(previous, context.previousPosition());
                        }));
        assertTrue(called[0]);
        position.set(8, 9, 10);
        rotation.set(1, 2, 3);
        samePosition(new Position(4, 5, 6), guest.getPosition());
        samePosition(new Position(0, 90, 0), guest.getRotation());
    }

    @Test
    void changedSourceDoesNotRunTheCallbackOrMutateCoordinates() {
        var guest = new Member();
        var source = world(guest, false);
        var target = world(new Member(), false);
        var expected = world(new Member(), false);
        source.addPlayer(guest);
        var previous = guest.getPosition().clone();
        var called = new boolean[1];
        assertFalse(
                target.tryAddPlayer(
                        guest,
                        expected,
                        TeleportProperties.builder()
                                .sceneId(3)
                                .teleportTo(new Position(4, 5, 6))
                                .build(),
                        c -> called[0] = true));
        assertFalse(called[0]);
        assertSame(source, guest.getWorld());
        samePosition(previous, guest.getPosition());
    }

    @Test
    void callbackFailureRestoresCoordinatesAndClearsIncompleteDestinationMembership() {
        var guest = new Member();
        var source = world(guest, false);
        var target = world(new Member(), false);
        source.addPlayer(guest);
        var position = guest.getPosition().clone();
        var rotation = guest.getRotation().clone();
        int peer = guest.getPeerId();
        assertThrows(
                IllegalStateException.class,
                () ->
                        target.tryAddPlayer(
                                guest,
                                source,
                                TeleportProperties.builder()
                                        .sceneId(3)
                                        .teleportTo(new Position(4, 5, 6))
                                        .teleportRot(new Position(0, 90, 0))
                                        .build(),
                                c -> {
                                    throw new IllegalStateException("caller callback failure");
                                }));
        samePosition(position, guest.getPosition());
        samePosition(rotation, guest.getRotation());
        assertEquals(peer, guest.getPeerId());
        assertTrue(target.getPlayers().isEmpty());
        assertTrue(target.getSceneById(3).getPlayers().isEmpty());
        assertNull(guest.getWorld());
    }

    @Test
    void sceneEntryPacketUsesTheDestinationEvenWhenItsHostHasMoved() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var target = world(owner, true);
        target.setWorldLevel(7);
        target.addPlayer(guest);
        var other = world(owner, false);
        other.setWorldLevel(2);
        owner.setWorld(other);
        var props =
                TeleportProperties.builder()
                        .sceneId(3)
                        .teleportTo(guest.getPosition().clone())
                        .enterType(EnterType.EnterType_ENTER_OTHER)
                        .build();
        var packet =
                PlayerEnterSceneNotify.parseFrom(
                        new PacketPlayerEnterSceneNotify(guest, target, props).getData());
        assertEquals(7, (packet.getWorldLevel() - 19873) ^ 31579);
        assertEquals(owner.getUid(), (packet.getTargetUid() ^ 4145) + 30259);
    }

    @Test
    void homeBackHandlerReturnsFailureIfThePersonalSceneIsUnavailable() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = home(owner, true);
        source.addPlayer(owner, 2001);
        source.addPlayer(guest, 2001);
        loaded(source);
        guest.setCurHomeWorld(source);
        guest.setPrevScene(3);
        guest.packets.clear();
        new HandlerBackMyWorldReq().handle(guest.getSession(), new byte[0], new byte[0]);
        var response =
                guest.packets.stream()
                        .filter(p -> p instanceof PacketBackMyWorldRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, BackMyWorldRsp.parseFrom(response.getData()).getRetcode());
        assertSame(source, guest.getWorld());
        noEntry(guest);
    }

    @Test
    void homeKickHandlerReturnsFailureIfThePersonalSceneIsUnavailable() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = home(owner, true);
        source.addPlayer(owner, 2001);
        source.addPlayer(guest, 2001);
        loaded(source);
        owner.setCurHomeWorld(source);
        guest.setCurHomeWorld(source);
        owner.packets.clear();
        guest.packets.clear();
        new HandlerHomeKickPlayerReq()
                .handle(
                        owner.getSession(),
                        new byte[0],
                        HomeKickPlayerReq.newBuilder()
                                .setTargetUid(guest.getUid())
                                .build()
                                .toByteArray());
        var response =
                owner.packets.stream()
                        .filter(p -> p instanceof PacketHomeKickPlayerRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, HomeKickPlayerRsp.parseFrom(response.getData()).getRetcode());
        assertSame(source, guest.getWorld());
        noEntry(guest);
    }

    private CrucibleSceneController.MatchingTeam matchingTeam(Member owner) throws Exception {
        var scene = owner.getScene();
        var controller = scene.getCrucibleSceneController();
        var lifecycle = controller.getLifecycle();
        var schedule = CrucibleSceneLifecycle.class.getDeclaredField("scheduleId");
        schedule.setAccessible(true);
        schedule.set(lifecycle, 5001002);
        var config = new ActivityConfigItem();
        config.setActivityId(5001);
        config.setScheduleId(5001002);
        config.setBeginTime(new Date(0));
        config.setEndTime(new Date(Long.MAX_VALUE));
        var configField = ActivityManager.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        var previous = (ActivityManager.Configuration) configField.get(null);
        ActivityManager.installConfiguration(
                new ActivityManager.Configuration(Map.of(), Map.of(5001002, config)));
        restore.add(() -> ActivityManager.installConfiguration(previous));
        var previousGroup =
                GameData.getMpPlayGroupDataMap()
                        .put(
                                1,
                                new Gson()
                                        .fromJson(
                                                "{\"playId\":1,\"centerPosList\":[0,0,0],\"centerRadius\":100}",
                                                MpPlayGroupData.class));
        restore.add(
                () -> {
                    if (previousGroup == null) GameData.getMpPlayGroupDataMap().remove(1);
                    else GameData.getMpPlayGroupDataMap().put(1, previousGroup);
                });
        owner.getPosition().set(0, 0, 0);
        owner.setSceneLoadState(SceneLoadState.LOADED);
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return owner;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000005;
                    }

                    @Override
                    public IntSet getTalentIdList() {
                        return IntSets.EMPTY_SET;
                    }

                    @Override
                    public void save() {}
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        owner.getTeamManager()
                .getActiveTeam()
                .add(
                        new EntityAvatar(scene, avatar) {
                            @Override
                            public void initAbilities() {}
                        });
        var gadget =
                new EntityGadget(scene, 70330024, new Position()) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public boolean canStartGadgetPlay() {
                        return true;
                    }
                };
        gadget.setGroupId(CrucibleSceneController.MAIN_GROUP);
        gadget.setConfigId(1001);
        scene.getEntities().put(gadget.getId(), gadget);
        var field = CrucibleSceneController.class.getDeclaredField("invitation");
        field.setAccessible(true);
        var invitation = (CrucibleInvitation) field.get(controller);
        assertTrue(
                invitation.start(
                        new CrucibleInvitation.Context(
                                5001002,
                                lifecycle.ticket(),
                                owner.getUid(),
                                Map.of(owner.getUid(), owner.getWorldLevel())),
                        System.currentTimeMillis() / 1000,
                        30,
                        3,
                        false));
        var team = controller.matchingTeam(owner, 1);
        assertNotNull(team);
        assertTrue(controller.isMatching(team));
        return team;
    }

    private CrucibleMatchmaking.Run match(Member owner, Member guest) throws Exception {
        var system = server.getCrucibleMatchSystem();
        matchingTeam(owner);
        matchingTeam(guest);
        int type = emu.grasscutter.net.proto.MatchTypeOuterClass.MatchType.MatchType_MP_PLAY_VALUE;
        system.start(owner, type, 1, 0, 0);
        system.start(guest, type, 1, 0, 0);
        assertTrue(system.isReserved(owner));
        assertTrue(system.isReserved(guest));
        var pool = cruciblePool();
        var field = pool.getClass().getDeclaredField("queue");
        field.setAccessible(true);
        var queue = (CrucibleMatchmaking) field.get(pool);
        long now = System.currentTimeMillis() / 1000 + 5;
        var run = queue.findMatches(now).get(0);
        assertEquals(owner.getUid(), run.hostUid());
        assertEquals(CrucibleMatchmaking.Reply.ACCEPTED, queue.confirm(owner.getUid(), true, now));
        assertEquals(
                CrucibleMatchmaking.Reply.ALL_AGREED, queue.confirm(guest.getUid(), true, now));
        assertTrue(queue.allowEnter(owner.getUid(), owner.getUid(), now));
        assertTrue(queue.allowEnter(guest.getUid(), owner.getUid(), now));
        assertTrue(queue.beginTransfer(run.id(), now));
        return queue.runFor(owner.getUid());
    }

    private Object cruciblePool() throws Exception {
        var field = CrucibleMatchSystem.class.getDeclaredField("pools");
        field.setAccessible(true);
        for (var pool : (List<?>) field.get(server.getCrucibleMatchSystem())) {
            var id = pool.getClass().getDeclaredField("playId");
            id.setAccessible(true);
            if (id.getInt(pool) == 1) return pool;
        }
        throw new AssertionError("Crucible queue missing");
    }

    private void transferMatch(CrucibleMatchmaking.Run run) throws Exception {
        var method =
                CrucibleMatchSystem.class.getDeclaredMethod(
                        "transfer", cruciblePool().getClass(), CrucibleMatchmaking.Run.class);
        method.setAccessible(true);
        try {
            method.invoke(server.getCrucibleMatchSystem(), cruciblePool(), run);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }

    @Test
    void crucibleHostPromotionFailureStopsBeforeReportingEntry() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var original = world(owner, false);
        var source = world(guest, false);
        original.addPlayer(owner);
        source.addPlayer(guest);
        var run = match(owner, guest);
        owner.packets.clear();
        guest.packets.clear();
        assertThrows(IllegalStateException.class, () -> transferMatch(run));
        assertSame(original, owner.getWorld());
        assertSame(source, guest.getWorld());
        noEntry(owner);
        noEntry(guest);
    }

    @Test
    void crucibleGuestRejectedByAClosedDestinationKeepsItsCoordinates() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var target = world(owner, true);
        var source = world(guest, false);
        target.addPlayer(owner);
        source.addPlayer(guest);
        var run = match(owner, guest);
        guest.getPosition().set(0, 5, 0);
        var field = World.class.getDeclaredField("closing");
        field.setAccessible(true);
        field.set(target, true);
        guest.packets.clear();
        assertThrows(IllegalStateException.class, () -> transferMatch(run));
        assertSame(source, guest.getWorld());
        samePosition(new Position(0, 5, 0), guest.getPosition());
        noEntry(guest);
    }

    @Test
    void cruciblePromotionAndGuestTransferReachOneMultiplayerWorld() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var original = world(owner, false);
        var source = world(guest, false);
        original.addPlayer(owner);
        source.addPlayer(guest);
        var run = match(owner, guest);
        personalSceneAvailable();
        owner.packets.clear();
        guest.packets.clear();
        transferMatch(run);
        assertSame(owner.getWorld(), guest.getWorld());
        assertTrue(owner.getWorld().isMultiplayer());
        assertSame(owner.getScene(), guest.getScene());
        assertEquals(1, count(owner, PacketPlayerEnterSceneNotify.class));
        assertEquals(1, count(guest, PacketPlayerEnterSceneNotify.class));
    }
}

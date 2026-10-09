package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.PlayerEnterSceneNotifyOuterClass.PlayerEnterSceneNotify;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldMembershipTest {
    private AbilityTestFixture fixture;
    private GameServer server;
    private final List<Runnable> restore = new ArrayList<>();
    private int uid;

    class Member extends AbilityTestFixture.TestPlayer {
        Runnable serverRead = () -> {};
        boolean loggedIn;
        final GameHome home;
        private final GameSession session =
                new GameSession(null) {
                    @Override
                    public Player getPlayer() {
                        return Member.this;
                    }

                    @Override
                    public void send(BasePacket packet) {
                        packets.add(packet);
                    }
                };

        Member() {
            setUid(++uid);
            home = GameHome.create(getUid());
        }

        @Override
        public GameServer getServer() {
            serverRead.run();
            return server;
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
        public GameSession getSession() {
            return session;
        }

        @Override
        public boolean hasSentLoginPackets() {
            return loggedIn;
        }

        @Override
        public void save() {}
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        server = fixture.scene.getWorld().getServer();
        uid = 17000;
        var previous = GameData.getSceneDataMap().put(3, data(3));
        restore.add(
                () -> {
                    if (previous == null) GameData.getSceneDataMap().remove(3);
                    else GameData.getSceneDataMap().put(3, previous);
                });
    }

    @AfterEach
    void cleanup() {
        for (var action : restore) action.run();
        fixture.close();
    }

    private SceneData data(int id) {
        return new Gson().fromJson("{\"id\":" + id + ",\"type\":\"SCENE_WORLD\"}", SceneData.class);
    }

    private AbilityTestFixture.TestWorld world(Member host) {
        var world = new AbilityTestFixture.TestWorld(server, host);
        scene(world, 3);
        return world;
    }

    private Scene scene(AbilityTestFixture.TestWorld world, int id) {
        var scene =
                new AbilityTestFixture.TestScene(world, data(id)) {
                    @Override
                    public void saveGroups() {}
                };
        scene.setDontDestroyWhenEmpty(true);
        world.registerScene(scene);
        return scene;
    }

    private HomeWorld home(Member owner) {
        var world =
                new HomeWorld(server, owner) {
                    @Override
                    public void refreshModuleManager() {}

                    @Override
                    public void broadcastPacket(BasePacket packet) {
                        owner.packets.add(packet);
                    }
                };
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
        return world;
    }

    private CompletableFuture<Void> worker(Runnable action) {
        var result = new CompletableFuture<Void>();
        var thread =
                new Thread(
                        () -> {
                            try {
                                action.run();
                                result.complete(null);
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        },
                        "world-membership-test");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(3, TimeUnit.SECONDS), "Barrier timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void finished(CompletableFuture<Void>... results) throws Exception {
        try {
            CompletableFuture.allOf(results).get(4, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            var bean = ManagementFactory.getThreadMXBean();
            var ids = bean.findMonitorDeadlockedThreads();
            if (ids != null)
                for (var info : bean.getThreadInfo(ids, true, true)) System.err.println(info);
            fail("World transfers did not finish; monitor diagnostics recorded", timeout);
        }
    }

    @Test
    void guestsCanSwapWorldsWhileEachDestinationMonitorIsHeld() throws Exception {
        var first = new Member();
        var second = new Member();
        var left = world(new Member());
        var right = world(new Member());
        left.addPlayer(first);
        right.addPlayer(second);
        var ready = new CountDownLatch(2);
        finished(
                worker(
                        () -> {
                            synchronized (right) {
                                ready.countDown();
                                await(ready);
                                right.addPlayer(first);
                            }
                        }),
                worker(
                        () -> {
                            synchronized (left) {
                                ready.countDown();
                                await(ready);
                                left.addPlayer(second);
                            }
                        }));
        assertSame(right, first.getWorld());
        assertSame(left, second.getWorld());
        assertEquals(List.of(second), left.getPlayers());
        assertEquals(List.of(first), right.getPlayers());
        assertSame(right, first.getScene().getWorld());
        assertSame(left, second.getScene().getWorld());
    }

    @Test
    void sourceCleanupDoesNotHoldTheDestinationWorldMonitor() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        var oldScene =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        assertFalse(Thread.holdsLock(target));
                        super.removePlayer(leaving);
                    }

                    @Override
                    public void saveGroups() {}
                };
        oldScene.setDontDestroyWhenEmpty(true);
        source.registerScene(oldScene);
        source.addPlayer(player);
        target.addPlayer(player, 3);
        assertSame(target, player.getWorld());
    }

    @Test
    void samePlayerConcurrentTransfersHaveOneWorldAndScene() throws Exception {
        var player = new Member();
        var source = world(new Member());
        var first = world(new Member());
        var second = world(new Member());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var removals = new AtomicInteger();
        var oldScene =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        if (removals.incrementAndGet() == 1) {
                            entered.countDown();
                            await(release);
                        }
                        super.removePlayer(leaving);
                    }

                    @Override
                    public void saveGroups() {}
                };
        oldScene.setDontDestroyWhenEmpty(true);
        source.registerScene(oldScene);
        source.addPlayer(player);
        var moving = worker(() -> first.addPlayer(player));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var another = worker(() -> second.addPlayer(player));
        try {
            Thread.sleep(100);
            assertFalse(another.isDone());
            assertTrue(second.getPlayers().isEmpty());
        } finally {
            release.countDown();
            finished(moving, another);
        }
        assertSame(second, player.getWorld());
        assertSame(second, player.getScene().getWorld());
        assertTrue(source.getPlayers().isEmpty());
        assertTrue(first.getPlayers().isEmpty());
        assertTrue(first.getSceneById(3).getPlayers().isEmpty());
        assertEquals(List.of(player), second.getPlayers());
    }

    @Test
    void staleRemovalAndRepeatedEntryPreserveTheNewMembership() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        source.addPlayer(player);
        target.addPlayer(player);
        int peer = player.getPeerId();
        var entity = player.getTeamManager().getEntity();
        source.removePlayer(player);
        target.addPlayer(player);
        target.addPlayer(player, 3);
        assertSame(target, player.getWorld());
        assertSame(target, player.getScene().getWorld());
        assertEquals(peer, player.getPeerId());
        assertSame(entity, player.getTeamManager().getEntity());
        assertEquals(List.of(player), target.getPlayers());
    }

    @Test
    void unavailableDestinationPreservesTheSourceWorldAndScene() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        source.addPlayer(player);
        var previous = player.getScene();
        int peer = player.getPeerId();
        assertDoesNotThrow(() -> target.addPlayer(player, 987654));
        assertSame(source, player.getWorld());
        assertSame(previous, player.getScene());
        assertEquals(peer, player.getPeerId());
        assertTrue(target.getPlayers().isEmpty());
    }

    @Test
    void unavailableForcedTpsPartyPreservesTheSource() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        source.addPlayer(player);
        var previous = player.getScene();
        var tps =
                new Gson()
                        .fromJson(
                                "{\"id\":51334,\"type\":\"SCENE_WORLD\",\"specifiedAvatarList\":[10000134,10000135]}",
                                SceneData.class);
        var scene =
                new AbilityTestFixture.TestScene(target, tps) {
                    @Override
                    public void saveGroups() {}
                };
        scene.setDontDestroyWhenEmpty(true);
        target.registerScene(scene);
        var saved = GameData.getTpsWeaponDataMap().remove(224001);
        restore.add(
                () -> {
                    if (saved != null) GameData.getTpsWeaponDataMap().put(224001, saved);
                });
        assertDoesNotThrow(() -> target.addPlayer(player, 51334));
        assertSame(source, player.getWorld());
        assertSame(previous, player.getScene());
        assertTrue(target.getPlayers().isEmpty());
    }

    @Test
    void departureUsesTheActualSceneDespiteAChangedSceneId() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        scene(target, 4);
        source.addPlayer(player);
        var previous = player.getScene();
        player.setSceneId(4);
        assertDoesNotThrow(() -> target.addPlayer(player));
        assertTrue(previous.getPlayers().isEmpty());
        assertSame(target.getSceneById(4), player.getScene());
    }

    @Test
    void homePeerIdsReserveTheOwnerAndStayStableAcrossDepartures() {
        var owner = new Member();
        var first = new Member();
        var second = new Member();
        var third = new Member();
        var home = home(owner);
        home.addPlayer(first, 2001);
        assertEquals(2, first.getPeerId());
        home.addPlayer(owner, 2001);
        assertEquals(1, owner.getPeerId());
        assertEquals(2, first.getPeerId());
        home.addPlayer(second, 2001);
        assertEquals(3, second.getPeerId());
        home.removePlayer(first);
        home.addPlayer(third, 2001);
        assertNotEquals(second.getPeerId(), third.getPeerId());
        assertEquals(1, owner.getPeerId());
    }

    @Test
    void homeDefaultEntryAlsoKeepsExistingGuestPeerIds() {
        var owner = new Member();
        var guest = new Member();
        var home = home(owner);
        guest.setSceneId(2001);
        home.addPlayer(guest);
        int peer = guest.getPeerId();
        owner.setSceneId(2001);
        home.addPlayer(owner);
        assertEquals(1, owner.getPeerId());
        assertEquals(peer, guest.getPeerId());
    }

    @Test
    void homeOwnerMayLeaveAndReturnWithoutDisbandingGuests() {
        var owner = new Member();
        var guest = new Member();
        var home = home(owner);
        home.addPlayer(owner, 2001);
        home.addPlayer(guest, 2001);
        home.removePlayer(owner);
        assertSame(home, guest.getWorld());
        home.addPlayer(owner, 2001);
        assertSame(home, owner.getWorld());
        assertEquals(1, owner.getPeerId());
        assertEquals(2, home.getPlayerCount());
    }

    @Test
    void hostDisbandTransfersGuestsOutsideTheWorldAndHostMonitors() {
        var owner = new Member();
        var guest = new Member();
        var source = world(owner);
        source.addPlayer(owner);
        source.addPlayer(guest);
        guest.serverRead =
                () -> {
                    assertFalse(Thread.holdsLock(source));
                    assertFalse(Thread.holdsLock(owner.getSceneTransferLock()));
                };
        var previousData = GameData.getSceneDataMap().put(3, data(3));
        restore.add(
                () -> {
                    if (previousData == null) GameData.getSceneDataMap().remove(3);
                    else GameData.getSceneDataMap().put(3, previousData);
                });
        source.removePlayer(owner);
        assertNull(owner.getWorld());
        assertNull(owner.getScene());
        assertNotSame(source, guest.getWorld());
        assertSame(guest, guest.getWorld().getHost());
        assertSame(guest.getWorld(), guest.getScene().getWorld());
        assertTrue(source.getPlayers().isEmpty());
    }

    @Test
    void closedWorldRejectsNewMembersWithoutRemovingTheirOldWorld() {
        var owner = new Member();
        var source = world(owner);
        source.addPlayer(owner);
        source.removePlayer(owner);
        var guest = new Member();
        var previous = world(new Member());
        previous.addPlayer(guest);
        source.addPlayer(guest);
        assertSame(previous, guest.getWorld());
        assertTrue(source.getPlayers().isEmpty());
    }

    @Test
    void guestMayLeaveWhileHostSceneCleanupIsPaused() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = world(owner);
        var target = world(new Member());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var oldScene =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        if (leaving == owner) {
                            entered.countDown();
                            await(release);
                        }
                        super.removePlayer(leaving);
                    }

                    @Override
                    public void saveGroups() {}
                };
        oldScene.setDontDestroyWhenEmpty(true);
        source.registerScene(oldScene);
        source.addPlayer(owner);
        source.addPlayer(guest);
        var leaving = worker(() -> source.removePlayer(owner));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var moving = worker(() -> target.addPlayer(guest));
        try {
            moving.get(1, TimeUnit.SECONDS);
            assertSame(target, guest.getWorld());
        } finally {
            release.countDown();
            finished(leaving, moving);
        }
        assertSame(target, guest.getWorld());
        assertSame(target, guest.getScene().getWorld());
        assertTrue(source.getPlayers().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void guestsCanSwapBetweenHomeAndOrdinaryWorld(boolean explicit) throws Exception {
        var first = new Member();
        var second = new Member();
        var left = world(new Member());
        var right = home(new Member());
        left.addPlayer(first);
        right.addPlayer(second, 2001);
        var ready = new CountDownLatch(2);
        finished(
                worker(
                        () -> {
                            synchronized (right) {
                                ready.countDown();
                                await(ready);
                                if (explicit) right.addPlayer(first, 2001);
                                else {
                                    first.setSceneId(2001);
                                    right.addPlayer(first);
                                }
                            }
                        }),
                worker(
                        () -> {
                            synchronized (left) {
                                ready.countDown();
                                await(ready);
                                if (explicit) left.addPlayer(second, 3);
                                else {
                                    second.setSceneId(3);
                                    left.addPlayer(second);
                                }
                            }
                        }));
        assertSame(right, first.getWorld());
        assertSame(left, second.getWorld());
        assertEquals(List.of(second), left.getPlayers());
        assertEquals(List.of(first), right.getPlayers());
        assertSame(right.getSceneById(2001), first.getScene());
        assertSame(left.getSceneById(3), second.getScene());
        assertTrue(first.getPeerId() > 1);
    }

    private void sceneData(int id) {
        var previous = GameData.getSceneDataMap().put(id, data(id));
        restore.add(
                () -> {
                    if (previous == null) GameData.getSceneDataMap().remove(id);
                    else GameData.getSceneDataMap().put(id, previous);
                });
    }

    private TeleportProperties teleport(int id, float x) {
        return TeleportProperties.builder()
                .sceneId(id)
                .teleportTo(new Position(x, 1, 2))
                .teleportType(TeleportType.INTERNAL)
                .enterReason(EnterReason.TransPoint)
                .enterType(EnterType.EnterType_ENTER_JUMP)
                .build();
    }

    @Test
    void completeTeleportSerializesMembershipPositionAndNotifications() throws Exception {
        var player = new Member();
        var world = world(new Member());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstScene =
                new AbilityTestFixture.TestScene(world, data(4)) {
                    @Override
                    public void addPlayer(Player joining) {
                        super.addPlayer(joining);
                        entered.countDown();
                        await(release);
                    }

                    @Override
                    public void saveGroups() {}
                };
        firstScene.setDontDestroyWhenEmpty(true);
        world.registerScene(firstScene);
        var secondScene = scene(world, 5);
        sceneData(4);
        sceneData(5);
        world.addPlayer(player);
        var first = worker(() -> assertTrue(world.transferPlayerToScene(player, teleport(4, 111))));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var second = worker(() -> assertTrue(world.transferPlayerToScene(player, teleport(5, 222))));
        try {
            Thread.sleep(100);
            assertFalse(second.isDone());
            assertSame(firstScene, player.getScene());
        } finally {
            release.countDown();
            finished(first, second);
        }
        assertSame(secondScene, player.getScene());
        assertEquals(222, player.getPosition().getX());
        assertTrue(firstScene.getPlayers().isEmpty());
        var messages =
                player.packets.stream().filter(p -> p instanceof PacketPlayerEnterSceneNotify).toList();
        assertEquals(2, messages.size());
        var firstPacket = PlayerEnterSceneNotify.parseFrom(messages.get(0).getData());
        var secondPacket = PlayerEnterSceneNotify.parseFrom(messages.get(1).getData());
        assertEquals(4, (firstPacket.getSceneId() ^ 11523) + 49379);
        assertEquals(5, (secondPacket.getSceneId() ^ 11523) + 49379);
        assertEquals(111, firstPacket.getPos().getX());
        assertEquals(222, secondPacket.getPos().getX());
    }

    @Test
    void staleWorldCannotTeleportAPlayerBackFromTheNewWorld() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        source.addPlayer(player);
        target.addPlayer(player);
        var previous = player.getScene();
        scene(source, 4);
        sceneData(4);
        assertFalse(source.transferPlayerToScene(player, teleport(4, 999)));
        assertSame(target, player.getWorld());
        assertSame(previous, player.getScene());
        assertTrue(source.getSceneById(4).getPlayers().isEmpty());
    }

    @Test
    void voluntaryDepartureWinsWhilePersonalWorldIsBeingCreated() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = world(owner);
        var target = world(new Member());
        source.addPlayer(owner);
        source.addPlayer(guest);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        guest.serverRead =
                () -> {
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        await(release);
                    }
                };
        var leaving = worker(() -> source.removePlayer(owner));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var moving = worker(() -> target.addPlayer(guest));
        try {
            moving.get(1, TimeUnit.SECONDS);
            assertSame(target, guest.getWorld());
        } finally {
            release.countDown();
            finished(leaving, moving);
        }
        assertSame(target, guest.getWorld());
        assertSame(target, guest.getScene().getWorld());
        assertTrue(source.getPlayers().isEmpty());
        assertTrue(guest.packets.stream().noneMatch(p -> p instanceof PacketPlayerEnterSceneNotify));
    }

    @Test
    void rosterUpdatesRunOutsideTheWorldAndTransferringPlayerMonitors() {
        var player = new Member();
        var other = new Member();
        class CheckedWorld extends AbilityTestFixture.TestWorld {
            CheckedWorld() {
                super(WorldMembershipTest.this.server, new Member());
            }

            @Override
            protected void updatePlayerInfos(Player changed) {
                assertFalse(Thread.holdsLock(this));
                assertFalse(Thread.holdsLock(changed.getSceneTransferLock()));
            }
        }
        var source = new CheckedWorld();
        scene(source, 3);
        var target = new CheckedWorld();
        scene(target, 3);
        source.addPlayer(player);
        source.addPlayer(other);
        target.addPlayer(player);
        assertSame(source, other.getWorld());
        assertSame(target, player.getWorld());
    }

    @Test
    void cleanupFailureAfterHostRemovalStillReleasesTheGuests() {
        var owner = new Member();
        var guest = new Member();
        var source = world(owner);
        var oldScene =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        super.removePlayer(leaving);
                        if (leaving == owner) throw new IllegalStateException("cleanup failure");
                    }

                    @Override
                    public void saveGroups() {}
                };
        oldScene.setDontDestroyWhenEmpty(true);
        source.registerScene(oldScene);
        source.addPlayer(owner);
        source.addPlayer(guest);
        assertThrows(IllegalStateException.class, () -> source.removePlayer(owner));
        assertNull(owner.getWorld());
        assertNull(owner.getScene());
        assertNotSame(source, guest.getWorld());
        assertSame(guest, guest.getWorld().getHost());
        assertSame(guest.getWorld(), guest.getScene().getWorld());
        assertTrue(source.getPlayers().isEmpty());
    }

    @Test
    void rosterRefreshWaitsForTheAffectedPlayersTransfer() throws Exception {
        var existing = new Member();
        var joining = new Member();
        var refreshing = new CountDownLatch(1);
        var world =
                new AbilityTestFixture.TestWorld(server, new Member()) {
                    @Override
                    protected void updatePlayerInfos(Player changed) {
                        refreshing.countDown();
                        super.updatePlayerInfos(changed);
                    }
                };
        scene(world, 3);
        world.addPlayer(existing);
        existing.loggedIn = true;
        CompletableFuture<Void> future;
        synchronized (existing.getSceneTransferLock()) {
            future = worker(() -> world.addPlayer(joining));
            assertTrue(refreshing.await(2, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse(future.isDone(), "An affected party must not be rebuilt during its transfer");
        }
        finished(future);
        assertSame(world, existing.getWorld());
        assertSame(world, joining.getWorld());
    }

    @Test
    void concurrentJoinsShareOneSceneAndUniquePeers() throws Exception {
        var owner = new Member();
        var world = new AbilityTestFixture.TestWorld(server, owner);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var members = new ArrayList<Member>();
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 8; i++) {
            var member = new Member();
            members.add(member);
            futures.add(
                    worker(
                            () -> {
                                ready.countDown();
                                await(start);
                                world.addPlayer(member);
                            }));
        }
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        finished(futures.toArray(CompletableFuture[]::new));
        assertEquals(8, world.getPlayerCount());
        assertEquals(1, world.getScenes().size());
        var scene = world.getSceneById(3);
        var peers = new HashSet<Integer>();
        for (var member : members) {
            assertSame(world, member.getWorld());
            assertSame(scene, member.getScene());
            assertTrue(peers.add(member.getPeerId()));
        }
        assertEquals(8, scene.getPlayerCount());
    }

    @Test
    void failedDepartureAfterSceneRemovalDoesNotLeaveATargetMember() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        var previous =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        super.removePlayer(leaving);
                        throw new IllegalStateException("after cleanup");
                    }

                    @Override
                    public void saveGroups() {}
                };
        previous.setDontDestroyWhenEmpty(true);
        source.registerScene(previous);
        source.addPlayer(player);
        assertThrows(IllegalStateException.class, () -> target.addPlayer(player));
        assertTrue(target.getPlayers().isEmpty());
        assertTrue(source.getPlayers().isEmpty());
        assertNull(player.getWorld());
        assertNull(player.getScene());
    }

    @Test
    void failedDepartureBeforeSceneRemovalPreservesTheSourceMembership() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        var previous =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        throw new IllegalStateException("before cleanup");
                    }

                    @Override
                    public void saveGroups() {}
                };
        previous.setDontDestroyWhenEmpty(true);
        source.registerScene(previous);
        source.addPlayer(player);
        assertThrows(IllegalStateException.class, () -> target.addPlayer(player));
        assertTrue(target.getPlayers().isEmpty());
        assertEquals(List.of(player), source.getPlayers());
        assertSame(source, player.getWorld());
        assertSame(previous, player.getScene());
    }

    @Test
    void failedHostDepartureBeforeSceneRemovalDoesNotDisbandGuests() {
        var owner = new Member();
        var guest = new Member();
        var source = world(owner);
        var previous =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void removePlayer(Player leaving) {
                        if (leaving == owner) throw new IllegalStateException("before host cleanup");
                        super.removePlayer(leaving);
                    }

                    @Override
                    public void saveGroups() {}
                };
        previous.setDontDestroyWhenEmpty(true);
        source.registerScene(previous);
        source.addPlayer(owner);
        source.addPlayer(guest);
        assertThrows(IllegalStateException.class, () -> source.removePlayer(owner));
        assertSame(source, owner.getWorld());
        assertSame(previous, owner.getScene());
        assertSame(source, guest.getWorld());
        assertEquals(2, source.getPlayerCount());
        var another = new Member();
        source.addPlayer(another);
        assertSame(source, another.getWorld());
    }

    @Test
    void failedEntryAfterSceneRegistrationRemovesBothTargetMemberships() {
        var player = new Member();
        var source = world(new Member());
        var target = world(new Member());
        var destination =
                new AbilityTestFixture.TestScene(target, data(3)) {
                    @Override
                    public void addPlayer(Player joining) {
                        super.addPlayer(joining);
                        throw new IllegalStateException("after entry");
                    }

                    @Override
                    public void saveGroups() {}
                };
        destination.setDontDestroyWhenEmpty(true);
        target.registerScene(destination);
        source.addPlayer(player);
        assertThrows(IllegalStateException.class, () -> target.addPlayer(player));
        assertTrue(target.getPlayers().isEmpty());
        assertTrue(source.getPlayers().isEmpty());
        assertTrue(destination.getPlayers().isEmpty());
        assertNull(player.getWorld());
        assertNull(player.getScene());
    }
}

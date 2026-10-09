package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldGuestRecoveryTest {
    private AbilityTestFixture fixture;
    private GameServer server;
    private SceneData previous;
    private int uid;

    class Member extends AbilityTestFixture.TestPlayer {
        Runnable readingServer = () -> {};
        boolean online = true;
        final GameSession session =
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
        }

        @Override
        public GameServer getServer() {
            readingServer.run();
            return server;
        }

        @Override
        public GameSession getSession() {
            return session;
        }

        @Override
        public boolean isOnline() {
            return online;
        }

        @Override
        public void save() {}
    }

    class Source extends AbilityTestFixture.TestWorld {
        long now;

        Source(Member owner) {
            super(server, owner);
        }
        // The baseline has no retry clock; this method becomes its clock when recovery is added.
        protected long evacuationTimeMillis() {
            return now;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        server = fixture.scene.getWorld().getServer();
        uid = 19000;
        previous = GameData.getSceneDataMap().put(3, data(3));
    }

    @AfterEach
    void cleanup() {
        if (previous == null) GameData.getSceneDataMap().remove(3);
        else GameData.getSceneDataMap().put(3, previous);
        fixture.close();
    }

    private SceneData data(int id) {
        return new Gson().fromJson("{\"id\":" + id + ",\"type\":\"SCENE_WORLD\"}", SceneData.class);
    }

    private Source source(Member owner, int id) {
        var source = new Source(owner);
        var scene =
                new AbilityTestFixture.TestScene(source, data(id)) {
                    @Override
                    public void saveGroups() {}

                    @Override
                    public void onTick() {}
                };
        scene.setDontDestroyWhenEmpty(true);
        source.registerScene(scene);
        source.addPlayer(owner, id);
        return source;
    }

    private long entries(Member player) {
        return player.packets.stream().filter(p -> p instanceof PacketPlayerEnterSceneNotify).count();
    }

    private void removeEmptyWorld(World world) throws Exception {
        var method = GameServer.class.getDeclaredMethod("removeWorldIfEmpty", World.class);
        method.setAccessible(true);
        method.invoke(server, world);
    }

    @Test
    void oneGuestCreationFailureDoesNotPreventLaterGuestsFromLeaving() {
        var owner = new Member();
        var first = new Member();
        var second = new Member();
        var source = source(owner, 3);
        source.addPlayer(first);
        source.addPlayer(second);
        first.readingServer =
                () -> {
                    throw new IllegalStateException("first personal world failure");
                };
        assertDoesNotThrow(() -> source.removePlayer(owner));
        assertSame(source, first.getWorld());
        assertNotSame(source, second.getWorld());
        assertSame(second, second.getWorld().getHost());
        assertEquals(1, entries(second));
    }

    @Test
    void missingPreviousSceneFallsBackToThePersonalOverworld() {
        var owner = new Member();
        var guest = new Member();
        var source = source(owner, 700007);
        source.addPlayer(guest, 700007);
        guest.getPosition().set(new Position(4, 5, 6));
        guest.packets.clear();
        source.removePlayer(owner);
        assertNotSame(source, guest.getWorld());
        assertSame(guest, guest.getWorld().getHost());
        assertEquals(3, guest.getSceneId());
        assertEquals(GameConstants.START_POSITION.toProto(), guest.getPosition().toProto());
        assertEquals(1, entries(guest));
    }

    @Test
    void unavailableSceneIsRetriedAfterResourcesBecomeAvailable() {
        var owner = new Member();
        var guest = new Member();
        var source = source(owner, 3);
        source.addPlayer(guest);
        GameData.getSceneDataMap().remove(3);
        source.removePlayer(owner);
        guest.packets.clear();
        assertSame(source, guest.getWorld());
        assertFalse(source.onTick());
        GameData.getSceneDataMap().put(3, data(3));
        source.now = 1000;
        source.onTick();
        assertNotSame(source, guest.getWorld());
        assertEquals(1, entries(guest));
        assertTrue(source.onTick());
    }

    @Test
    void retriesAreLimitedToOncePerSecond() {
        var owner = new Member();
        var guest = new Member();
        var source = source(owner, 3);
        source.addPlayer(guest);
        GameData.getSceneDataMap().remove(3);
        var calls = new int[1];
        guest.readingServer = () -> calls[0]++;
        source.removePlayer(owner);
        int first = calls[0];
        assertTrue(first > 0);
        source.onTick();
        source.now = 999;
        source.onTick();
        assertEquals(first, calls[0]);
        source.now = 1000;
        source.onTick();
        assertTrue(calls[0] > first);
    }

    @Test
    void aGuestWhoMovedVoluntarilyIsNeverTakenBackByTheRetry() {
        var owner = new Member();
        var guest = new Member();
        var source = source(owner, 3);
        source.addPlayer(guest);
        GameData.getSceneDataMap().remove(3);
        source.removePlayer(owner);
        GameData.getSceneDataMap().put(3, data(3));
        var destination = new World(guest);
        destination.addPlayer(guest);
        guest.packets.clear();
        source.now = 1000;
        source.onTick();
        assertSame(destination, guest.getWorld());
        assertEquals(0, entries(guest));
        assertTrue(source.onTick());
    }

    @Test
    void aFailureAfterSceneCleanupRetainsAnOnlineGuestForRecovery() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var source = new Source(owner);
        var fail = new boolean[] {true};
        var scene =
                new AbilityTestFixture.TestScene(source, data(3)) {
                    @Override
                    public void saveGroups() {}

                    @Override
                    public void removePlayer(Player player) {
                        super.removePlayer(player);
                        if (player == guest && fail[0]) {
                            fail[0] = false;
                            throw new IllegalStateException("after guest cleanup");
                        }
                    }
                };
        scene.setDontDestroyWhenEmpty(true);
        source.registerScene(scene);
        source.addPlayer(owner);
        source.addPlayer(guest);
        assertDoesNotThrow(() -> source.removePlayer(owner));
        assertNull(guest.getWorld());
        assertFalse(source.onTick());
        assertEquals(0, source.getPlayerCount());
        removeEmptyWorld(source);
        assertTrue(server.getWorlds().contains(source));
        source.now = 1000;
        source.onTick();
        assertNotNull(guest.getWorld());
        assertEquals(1, entries(guest));
        assertTrue(source.onTick());
    }

    @Test
    void disconnectedGuestsAreRemovedFromTheRecoveryQueue() {
        var owner = new Member();
        var guest = new Member();
        var source = source(owner, 3);
        source.addPlayer(guest);
        GameData.getSceneDataMap().remove(3);
        source.removePlayer(owner);
        guest.online = false;
        source.removePlayer(guest);
        source.now = 1000;
        assertTrue(source.onTick());
        assertNull(guest.getWorld());
        assertEquals(0, entries(guest));
    }

    @Test
    void hostDepartureWaitsForAPreRegisteredGuestAndThenReturnsItSafely() throws Exception {
        var owner = new Member();
        var guest = new Member();
        var target = source(owner, 3);
        var original = new Source(guest);
        var cleaning = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var scene =
                new AbilityTestFixture.TestScene(original, data(3)) {
                    @Override
                    public void saveGroups() {}

                    @Override
                    public void removePlayer(Player player) {
                        cleaning.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS))
                                throw new IllegalStateException("cleanup gate timeout");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        super.removePlayer(player);
                    }
                };
        scene.setDontDestroyWhenEmpty(true);
        original.registerScene(scene);
        original.addPlayer(guest);
        var joining = CompletableFuture.runAsync(() -> target.addPlayer(guest));
        assertTrue(cleaning.await(2, TimeUnit.SECONDS));
        assertTrue(target.getPlayers().contains(guest));
        assertNull(guest.getWorld());
        var departing = CompletableFuture.runAsync(() -> target.removePlayer(owner));
        try {
            Thread.sleep(100);
            assertFalse(departing.isDone());
        } finally {
            release.countDown();
        }
        joining.get(3, TimeUnit.SECONDS);
        departing.get(3, TimeUnit.SECONDS);
        assertNotSame(target, guest.getWorld());
        assertNotSame(original, guest.getWorld());
        assertSame(guest, guest.getWorld().getHost());
        assertTrue(target.getPlayers().isEmpty());
        assertEquals(1, entries(guest));
    }

    @Test
    void aRetiredEmptyWorldRejectsAJoinThroughAStaleReference() throws Exception {
        var owner = new Member();
        var empty = new World(owner);
        server.registerWorld(empty);
        assertTrue(server.getWorlds().contains(empty));
        removeEmptyWorld(empty);
        assertFalse(server.getWorlds().contains(empty));
        empty.addPlayer(owner);
        assertNull(owner.getWorld());
        assertTrue(empty.getPlayers().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void temporaryDepartureCannotRetireTheSourceBeforeSuccessOrRollback(boolean fail)
            throws Exception {
        var owner = new Member();
        var original = new Source(owner);
        var cleaning = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var scene =
                new AbilityTestFixture.TestScene(original, data(3)) {
                    @Override
                    public void saveGroups() {}

                    @Override
                    public void removePlayer(Player player) {
                        cleaning.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS))
                                throw new IllegalStateException("source gate timeout");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        if (fail) throw new IllegalStateException("before cleanup");
                        super.removePlayer(player);
                    }
                };
        scene.setDontDestroyWhenEmpty(true);
        original.registerScene(scene);
        original.addPlayer(owner);
        var target = new World(owner);
        var moving = CompletableFuture.runAsync(() -> target.addPlayer(owner));
        try {
            assertTrue(cleaning.await(2, TimeUnit.SECONDS));
            assertEquals(0, original.getPlayerCount());
            assertFalse(original.onTick());
            removeEmptyWorld(original);
            assertTrue(server.getWorlds().contains(original));
        } finally {
            release.countDown();
        }
        if (fail) {
            assertInstanceOf(
                    IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> moving.get(3, TimeUnit.SECONDS)).getCause());
            assertSame(original, owner.getWorld());
            assertTrue(server.getWorlds().contains(original));
            assertFalse(original.onTick());
        } else {
            moving.get(3, TimeUnit.SECONDS);
            assertSame(target, owner.getWorld());
            assertTrue(original.onTick());
            removeEmptyWorld(original);
            assertFalse(server.getWorlds().contains(original));
        }
    }
}

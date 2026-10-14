package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.managers.mapmark.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class MaterialTrackingTest {
    @TempDir Path directory;
    NatsaurusPossessionTest environment;
    Object tracking;
    Path file;
    AtomicInteger saves = new AtomicInteger();
    Consumer<Player> persistence = p -> saves.incrementAndGet();

    Object call(String method, Class<?>[] types, Object... args) {
        try {
            return tracking.getClass().getMethod(method, types).invoke(tracking, args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        file = directory.resolve("points.json");
        write(
                "[{itemId:101232,x:100,y:1,z:100},{itemId:101232,x:10,y:2,z:10},{itemId:101235,x:20,y:3,z:20}]");
        environment.fixture.player.getPosition().set(0, 0, 0);
        try {
            tracking =
                    Class.forName("emu.grasscutter.game.managers.mapmark.MaterialTracking")
                            .getConstructor(Path.class, Consumer.class)
                            .newInstance(file, (Consumer<Player>) p -> persistence.accept(p));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        environment.fixture.player.packets.clear();
    }

    @AfterEach
    void close() {
        if (environment != null) environment.close();
    }

    void write(String content) throws Exception {
        Files.writeString(file, content);
    }

    Player player() {
        return environment.fixture.player;
    }

    Map<String, MapMark> marks() {
        return player().getMapMarks();
    }

    int track(int id) {
        return (int) call("track", new Class<?>[] {Player.class, int.class}, player(), id);
    }

    int clear() {
        return (int) call("clear", new Class<?>[] {Player.class}, player());
    }

    int resolve(String value) {
        return (int) call("resolve", new Class<?>[] {String.class}, value);
    }

    MapMark manual(float x, float z, String name) {
        return new MapMark(
                MapMarkPointOuterClass.MapMarkPoint.newBuilder()
                        .setSceneId(3)
                        .setName(name)
                        .setPos(new Position(x, 0, z).toProto())
                        .setPointType(
                                MapMarkPointTypeOuterClass.MapMarkPointType
                                        .MapMarkPointType_COLLECTION)
                        .build());
    }

    @Test
    void configuredMaterialCreatesNormalMapPinsAndSavesOnce() {
        assertEquals(2, track(101232));
        assertEquals(2, marks().size());
        assertEquals(1, saves.get());
        assertEquals(1, environment.fixture.player.packets.size());
        assertEquals(
                emu.grasscutter.net.packet.PacketOpcodes.MarkMapRsp,
                environment.fixture.player.packets.get(0).getOpcode());
    }

    @Test
    void personalMarksWithMaterialLikeNamesAreNeverCleared() {
        var mark = manual(50, 50, "Material Pine Specialty: personal");
        marks().put("x50z50", mark);
        track(101232);
        assertEquals(2, clear());
        assertEquals(1, marks().size());
        assertSame(mark, marks().get("x50z50"));
    }

    @Test
    void materialPointsNeverOverwritePersonalCoordinateKeys() {
        var mark = manual(10, 10, "home");
        marks().put("x10z10", mark);
        assertEquals(1, track(101232));
        assertSame(mark, marks().get("x10z10"));
    }

    @Test
    void repeatingTrackingDoesNotAccumulatePins() {
        assertEquals(2, track(101232));
        assertEquals(2, track(101232));
        assertEquals(2, marks().size());
    }

    @Test
    void selectingAnotherMaterialReplacesOnlyThePreviousGeneratedPins() {
        var mark = manual(50, 50, "personal");
        marks().put("x50z50", mark);
        track(101232);
        assertEquals(1, track(101235));
        assertEquals(2, marks().size());
        assertSame(mark, marks().get("x50z50"));
    }

    @Test
    void markerLimitSelectsTheNearestRemainingPoint() {
        for (int i = 0; i < 149; i++)
            marks().put("personal" + i, manual(300 + i, 300 + i, "personal"));
        assertEquals(1, track(101232));
        assertEquals(150, marks().size());
        assertTrue(marks().containsKey("x10z10"));
        assertFalse(marks().containsKey("x100z100"));
    }

    @Test
    void fullPersonalMapLeavesAllPinsUntouched() {
        for (int i = 0; i < 150; i++)
            marks().put("personal" + i, manual(300 + i, 300 + i, "personal"));
        var old = new HashMap<>(marks());
        assertEquals(0, track(101232));
        assertEquals(old, marks());
        assertEquals(0, saves.get());
    }

    @Test
    void unknownMaterialDoesNotClearThePreviousTracking() {
        track(101232);
        var old = new HashMap<>(marks());
        assertEquals(0, track(999999));
        assertEquals(old, marks());
    }

    @Test
    void sceneMismatchDoesNotMovePointsToAnotherScene() throws Exception {
        write("[{itemId:101232,sceneId:4,x:1,z:2}]");
        assertEquals(0, track(101232));
        assertTrue(marks().isEmpty());
    }

    @Test
    void duplicateLegacyCoordinateKeysAreCountedOnce() throws Exception {
        write("[{itemId:101232,x:1.1,z:2.1},{itemId:101232,x:1.2,z:2.2}]");
        assertEquals(1, track(101232));
        assertEquals(1, marks().size());
    }

    @Test
    void changedDataFileIsReadOnTheNextRequest() throws Exception {
        track(101232);
        write("[{itemId:101232,x:7,z:8}]");
        assertEquals(1, track(101232));
        assertTrue(marks().containsKey("x7z8"));
        assertEquals(1, marks().size());
    }

    @Test
    void malformedUpdatePreservesTheCurrentMap() throws Exception {
        track(101232);
        var old = new HashMap<>(marks());
        write("not-json");
        assertThrows(IllegalStateException.class, () -> track(101232));
        assertEquals(old, marks());
    }

    @Test
    void nonFiniteCoordinatesCannotCreateOrReplacePins() throws Exception {
        track(101232);
        var old = new HashMap<>(marks());
        write("[{itemId:101232,x:NaN,z:2}]");
        assertThrows(IllegalStateException.class, () -> track(101232));
        assertEquals(old, marks());
    }

    @Test
    void saveQueueRejectionRollsBackWithoutSendingASuccessPacket() {
        var mark = manual(50, 50, "personal");
        marks().put("x50z50", mark);
        var old = new HashMap<>(marks());
        persistence =
                p -> {
                    throw new IllegalStateException("queue full");
                };
        assertThrows(IllegalStateException.class, () -> track(101232));
        assertEquals(old, marks());
        assertTrue(environment.fixture.player.packets.isEmpty());
    }

    @Test
    void clearQueueRejectionDoesNotLoseExistingGeneratedPins() {
        track(101232);
        var old = new HashMap<>(marks());
        environment.fixture.player.packets.clear();
        persistence =
                p -> {
                    throw new IllegalStateException("queue full");
                };
        assertThrows(IllegalStateException.class, this::clear);
        assertEquals(old, marks());
        assertTrue(environment.fixture.player.packets.isEmpty());
    }

    @Test
    void namesResolveExactlyAndAmbiguityDoesNotPickAnArbitraryMaterial() throws Exception {
        write("[{itemId:101232,name:'Conch#1',x:1,z:2},{itemId:101235,name:'Conch#2',x:3,z:4}]");
        assertEquals(0, resolve("Conch"));
        assertEquals(101232, resolve("101232"));
        assertEquals(0, resolve("Con"));
    }

    @Test
    void configuredEnglishNamesResolveIgnoringCase() throws Exception {
        write("[{itemId:101232,name:'Beryl Conch#1',x:1,z:2}]");
        assertEquals(101232, resolve("beryl conch"));
    }

    @Test
    void clearedEmptyMapDoesNotEnqueueRedundantWrites() {
        assertEquals(0, clear());
        assertEquals(0, saves.get());
        assertTrue(environment.fixture.player.packets.isEmpty());
    }

    @Test
    void concurrentTrackAndClearDoNotLeaveOverlappingPartialMaps() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 12; i++) {
                final int n = i;
                jobs.add(
                        pool.submit(
                                () -> {
                                    if (n % 3 == 0) clear();
                                    else track(n % 3 == 1 ? 101232 : 101235);
                                }));
            }
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
            assertTrue(marks().size() <= 2);
            var ids = new HashSet<Integer>();
            for (var mark : marks().values())
                ids.add((int) mark.getClass().getMethod("getTrackingItemId").invoke(mark));
            assertTrue(ids.size() <= 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aManualEditCannotBeLostWhenTrackingSaveIsRejected() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        persistence =
                p -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    throw new IllegalStateException("queue full");
                };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var trackingJob =
                    pool.submit(
                            () -> assertThrows(IllegalStateException.class, () -> track(101232)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var editJob =
                    pool.submit(
                            () -> {
                                attempting.countDown();
                                new MapMarksManager(player())
                                        .handleMapMarkReq(
                                                MarkMapReqOuterClass.MarkMapReq.newBuilder()
                                                        .setOp(
                                                                MarkMapReqOuterClass.MarkMapReq
                                                                        .Operation.OPERATION_ADD)
                                                        .setMark(
                                                                MapMarkPointOuterClass.MapMarkPoint
                                                                        .newBuilder()
                                                                        .setSceneId(3)
                                                                        .setName(
                                                                                "personal after"
                                                                                    + " rejection")
                                                                        .setPos(
                                                                                new Position(
                                                                                                80,
                                                                                                0,
                                                                                                80)
                                                                                        .toProto())
                                                                        .build())
                                                        .build());
                            });
            assertTrue(attempting.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> editJob.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            trackingJob.get(5, TimeUnit.SECONDS);
            editJob.get(5, TimeUnit.SECONDS);
            assertEquals("personal after rejection", marks().get("x80z80").getName());
            assertEquals(1, marks().size());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void unavailableFileDoesNotClearExistingPins() throws Exception {
        track(101232);
        var old = new HashMap<>(marks());
        Files.delete(file);
        assertThrows(IllegalStateException.class, () -> track(101232));
        assertEquals(old, marks());
    }

    @Test
    void disconnectedTargetCannotClearOrCreatePins() {
        track(101232);
        var old = new HashMap<>(marks());
        player().setSession(null);
        assertThrows(IllegalStateException.class, () -> track(101232));
        assertThrows(IllegalStateException.class, this::clear);
        assertEquals(old, marks());
    }

    @Test
    void normalCommandUsesTheBundledMaterialPointsAndClearRemovesOnlyItsPins() throws Exception {
        var command =
                (emu.grasscutter.command.CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.TrackMatCommand")
                                .getConstructor()
                                .newInstance();
        var mark = manual(50, 50, "personal");
        marks().put("x50z50", mark);
        command.execute(null, player(), List.of("101232"));
        assertEquals(26, marks().size());
        command.execute(null, player(), List.of("clear"));
        assertEquals(1, marks().size());
        assertSame(mark, marks().get("x50z50"));
    }

    @Test
    void ordinaryClientGetIncludesPinsWithoutSavingAgain() {
        track(101232);
        var calls = new AtomicInteger();
        var manager =
                new MapMarksManager(player()) {
                    @Override
                    public void save() {
                        calls.incrementAndGet();
                    }
                };
        manager.handleMapMarkReq(
                MarkMapReqOuterClass.MarkMapReq.newBuilder()
                        .setOp(MarkMapReqOuterClass.MarkMapReq.Operation.OPERATION_GET)
                        .build());
        assertEquals(0, calls.get());
        assertEquals(2, marks().size());
        assertEquals(2, environment.fixture.player.packets.size());
    }

    @Test
    void editedGeneratedPinBecomesAPersonalPinAndSurvivesClear() throws Exception {
        track(101232);
        new MapMarksManager(player())
                .handleMapMarkReq(
                        MarkMapReqOuterClass.MarkMapReq.newBuilder()
                                .setOp(MarkMapReqOuterClass.MarkMapReq.Operation.OPERATION_MOD)
                                .setOld(
                                        MapMarkPointOuterClass.MapMarkPoint.newBuilder()
                                                .setSceneId(3)
                                                .setPos(new Position(10, 2, 10).toProto()))
                                .setMark(
                                        MapMarkPointOuterClass.MapMarkPoint.newBuilder()
                                                .setSceneId(3)
                                                .setName("my edited pin")
                                                .setPos(new Position(10, 2, 10).toProto()))
                                .build());
        assertEquals(1, clear());
        assertEquals(1, marks().size());
        assertEquals("my edited pin", marks().get("x10z10").getName());
        assertEquals(
                0,
                marks().get("x10z10")
                        .getClass()
                        .getMethod("getTrackingItemId")
                        .invoke(marks().get("x10z10")));
    }
}

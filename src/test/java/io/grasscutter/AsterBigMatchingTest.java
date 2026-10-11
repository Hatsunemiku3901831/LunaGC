package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.aster.AsterBigSceneController;
import emu.grasscutter.game.activity.crucible.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MatchTypeOuterClass.MatchType;
import emu.grasscutter.net.proto.PlayerAllowEnterMpAfterAgreeMatchNotifyOuterClass.PlayerAllowEnterMpAfterAgreeMatchNotify;
import emu.grasscutter.net.proto.PlayerConfirmMatchReqOuterClass.PlayerConfirmMatchReq;
import emu.grasscutter.net.proto.PlayerConfirmMatchRspOuterClass.PlayerConfirmMatchRsp;
import emu.grasscutter.net.proto.PlayerGuestConfirmMatchReqOuterClass.PlayerGuestConfirmMatchReq;
import emu.grasscutter.net.proto.PlayerGuestConfirmMatchRspOuterClass.PlayerGuestConfirmMatchRsp;
import emu.grasscutter.net.proto.PlayerMatchInfoNotifyOuterClass.PlayerMatchInfoNotify;
import emu.grasscutter.net.proto.PlayerMatchStopNotifyOuterClass.PlayerMatchStopNotify;
import emu.grasscutter.net.proto.PlayerMatchSuccNotifyOuterClass.PlayerMatchSuccNotify;
import emu.grasscutter.net.proto.PlayerStartMatchReqOuterClass.PlayerStartMatchReq;
import emu.grasscutter.net.proto.PlayerStartMatchRspOuterClass.PlayerStartMatchRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.SceneMeta;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real entrances, native requests and production world admission, with no live database or client.
 */
class AsterBigMatchingTest {
    private static final int TYPE = MatchType.MatchType_MP_PLAY_VALUE,
            MAIN = AsterBigSceneController.MAIN;
    private AsterBigEntranceTest fixture;
    private CrucibleMatchSystem matching;
    private GameServer server;
    private AtomicLong clock;
    private final List<AsterBigEntranceTest.TrackedScripts> extraScripts = new ArrayList<>();
    private final List<Scene> extraScenes = new ArrayList<>();
    private final Set<Integer> offline = new HashSet<>();
    private final List<Player> players = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AsterBigEntranceTest();
        fixture.setup();
        server = fixture.host.getServer();
        clock = new AtomicLong(System.currentTimeMillis() / 1000);
        matching = new CrucibleMatchSystem(server, clock::get);
        AsterBigEntranceTest.field(GameServer.class, "crucibleMatchSystem").set(server, matching);
        AsterBigEntranceTest.field(GameServer.class, "scheduler")
                .set(server, new ServerTaskScheduler());
        AsterBigEntranceTest.field(GameServer.class, "homeWorlds")
                .set(server, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        preparePlayer(fixture.host);
        fixture.scene.getWorld().setWorldLevel(fixture.host.getWorldLevel());
        fixture.scene.getWorld().registerScene(fixture.scene);
        server.getWorlds().add(fixture.scene.getWorld());
        fixture.update(fixture.resources.now);
    }

    private void preparePlayer(AsterMidSceneTest.RecoveryPlayer player) {
        player.setSceneId(3);
        var avatar = ((EntityAvatar) player.getTeamManager().getActiveTeam().get(0)).getAvatar();
        player.getAvatars().getAvatars().put(avatar.getAvatarId(), avatar);
        player.getTeamManager()
                .getCurrentSinglePlayerTeamInfo()
                .getAvatars()
                .add(avatar.getAvatarId());
        player.setSession(
                new GameSession(server) {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public boolean isActive() {
                        return !offline.contains(player.getUid());
                    }

                    @Override
                    public void send(BasePacket packet) {
                        player.packets.add(packet);
                    }
                });
        player.setNickname("match-" + player.getUid());
        players.add(player);
    }

    private AsterMidSceneTest.RecoveryPlayer solo(int uid, int worldLevel) throws Exception {
        var player = new AsterMidSceneTest.RecoveryPlayer();
        var world = new AbilityTestFixture.TestWorld(server, player);
        var scene = new AbilityTestFixture.TestScene(world, fixture.scene.getSceneData());
        var scripts = new AsterBigEntranceTest.TrackedScripts(scene);
        AsterBigEntranceTest.field(Scene.class, "scriptManager").set(scene, scripts);
        AsterBigEntranceTest.field(SceneScriptManager.class, "meta").set(scripts, SceneMeta.of(3));
        AsterBigEntranceTest.field(SceneScriptManager.class, "isInit").set(scripts, true);
        AsterBigEntranceTest.field(SceneScriptManager.class, "initAttempted").set(scripts, true);
        fixture.ready(player, fixture.resources.data(uid), 1, scene);
        player.worldLevel = worldLevel;
        world.setWorldLevel(worldLevel);
        world.registerScene(scene);
        server.getWorlds().add(world);
        preparePlayer(player);
        extraScenes.add(scene);
        extraScripts.add(scripts);
        scene.getAsterBigSceneController().update(fixture.resources.now);
        scripts.awaitAll();
        return player;
    }

    private AsterMidSceneTest.RecoveryPlayer guest(Player owner, int uid) {
        var player = new AsterMidSceneTest.RecoveryPlayer();
        fixture.ready(
                player,
                fixture.resources.data(uid),
                owner.getWorld().getPlayerCount() + 1,
                owner.getScene());
        preparePlayer(player);
        return player;
    }

    @AfterEach
    void close() throws Exception {
        for (Player player : players)
            if (player.getScene() != null
                    && player.getScene() != fixture.scene
                    && !extraScenes.contains(player.getScene())) extraScenes.add(player.getScene());
        for (Scene scene : extraScenes) scene.getScenePlayBattleManager().close();
        for (var scripts : extraScripts) {
            scripts.awaitAll();
            scripts.onDestroy();
            scripts.awaitAll();
        }
        if (fixture != null) fixture.cleanup();
    }

    private void start(Player owner) throws Exception {
        new HandlerPlayerStartMatchReq()
                .handle(
                        owner.getSession(),
                        new byte[0],
                        PlayerStartMatchReq.newBuilder()
                                .setMatchType(MatchType.MatchType_MP_PLAY)
                                .setMpPlayId(2)
                                .build()
                                .toByteArray());
    }

    private void confirm(Player player, boolean agree) throws Exception {
        if (player.getWorld().getHost() != player)
            new HandlerPlayerGuestConfirmMatchReq()
                    .handle(
                            player.getSession(),
                            new byte[0],
                            PlayerGuestConfirmMatchReq.newBuilder()
                                    .setMatchType(MatchType.MatchType_MP_PLAY)
                                    .setIsAgreed(agree)
                                    .build()
                                    .toByteArray());
        else
            new HandlerPlayerConfirmMatchReq()
                    .handle(
                            player.getSession(),
                            new byte[0],
                            PlayerConfirmMatchReq.newBuilder()
                                    .setMatchType(MatchType.MatchType_MP_PLAY)
                                    .setIsAgreed(agree)
                                    .build()
                                    .toByteArray());
    }

    private void allow(Player player, int host) throws Exception {
        new HandlerPlayerAllowEnterMpAfterAgreeMatchNotify()
                .handle(
                        player.getSession(),
                        new byte[0],
                        PlayerAllowEnterMpAfterAgreeMatchNotify.newBuilder()
                                .setTargetUid(host)
                                .build()
                                .toByteArray());
    }

    private void match(Player... owners) throws Exception {
        for (var owner : owners) {
            start(owner);
            assertEquals(
                    0,
                    PlayerStartMatchRsp.parseFrom(last(owner, PacketOpcodes.PlayerStartMatchRsp))
                            .getRetcode());
        }
        clock.addAndGet(5);
        matching.onTick();
    }

    private CrucibleMatchmaking queue(int playId) throws Exception {
        var pools =
                (List<?>)
                        AsterBigEntranceTest.field(CrucibleMatchSystem.class, "pools")
                                .get(matching);
        for (var pool : pools)
            if (AsterBigEntranceTest.field(pool.getClass(), "playId").getInt(pool) == playId)
                return (CrucibleMatchmaking)
                        AsterBigEntranceTest.field(pool.getClass(), "queue").get(pool);
        throw new AssertionError("Missing play queue " + playId);
    }

    @Test
    void nativePlayTwoRequestQueuesTheActualEligibleSoloParty() throws Exception {
        start(fixture.host);
        var rsp =
                PlayerStartMatchRsp.parseFrom(
                        last(fixture.host, PacketOpcodes.PlayerStartMatchRsp));
        assertEquals(0, rsp.getRetcode());
        assertEquals(2, rsp.getMpPlayId());
        assertTrue(matching.isReserved(fixture.host));
        assertEquals(
                2,
                PlayerMatchInfoNotify.parseFrom(
                                last(fixture.host, PacketOpcodes.PlayerMatchInfoNotify))
                        .getMpPlayId());
        assertEquals(0, fixture.scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    @Test
    void anUninvitedPartyCannotBeEnqueuedByAHostRequestOrTick() throws Exception {
        guest(fixture.host, 10002);
        start(fixture.host);
        assertFalse(matching.isReserved(fixture.host));
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        fixture.scene.getAsterBigSceneController().startInvitation(fixture.host, 2, false);
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
    }

    @Test
    void everyGuestMustAgreeBeforeTheTickQueuesTheirWholeParty() throws Exception {
        var guest = guest(fixture.host, 10002);
        var controller = fixture.scene.getAsterBigSceneController();
        controller.startInvitation(fixture.host, 2, false);
        controller.replyInvitation(guest, 2, true);
        matching.onTick();
        assertTrue(matching.isReserved(fixture.host));
        assertTrue(matching.isReserved(guest));
        assertEquals(2, queue(2).runFor(guest.getUid()).members().size());
        assertEquals(0, fixture.scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    @Test
    void aRejectedInvitationDoesNotEnterAnyQueue() throws Exception {
        var guest = guest(fixture.host, 10002);
        var controller = fixture.scene.getAsterBigSceneController();
        controller.startInvitation(fixture.host, 2, false);
        controller.replyInvitation(guest, 2, false);
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        assertFalse(matching.isReserved(guest));
    }

    @Test
    void duplicateStartsAndAnotherActivityCannotReserveTheSamePlayer() throws Exception {
        start(fixture.host);
        start(fixture.host);
        assertEquals(
                Retcode.RET_MATCH_ALREADY_IN_MATCH_VALUE,
                PlayerStartMatchRsp.parseFrom(last(fixture.host, PacketOpcodes.PlayerStartMatchRsp))
                        .getRetcode());
        matching.start(fixture.host, TYPE, 1, 0, 0);
        assertEquals(
                Retcode.RET_MATCH_ALREADY_IN_MATCH_VALUE,
                PlayerStartMatchRsp.parseFrom(last(fixture.host, PacketOpcodes.PlayerStartMatchRsp))
                        .getRetcode());
        assertEquals(1, queue(2).runs().size());
        assertTrue(queue(1).runs().isEmpty());
    }

    @Test
    void aGuestReservedByCruciblePreventsTheEntireAsterPartyFromEntering() throws Exception {
        var guest = guest(fixture.host, 10002);
        assertTrue(
                queue(1).enqueue(
                                new CrucibleMatchmaking.Party(
                                        guest.getUid(), 5001001, Map.of(guest.getUid(), 1)),
                                clock.get()));
        var controller = fixture.scene.getAsterBigSceneController();
        controller.startInvitation(fixture.host, 2, false);
        controller.replyInvitation(guest, 2, true);
        start(fixture.host);
        assertNotEquals(
                0,
                PlayerStartMatchRsp.parseFrom(last(fixture.host, PacketOpcodes.PlayerStartMatchRsp))
                        .getRetcode());
        assertFalse(matching.isReserved(fixture.host));
        assertTrue(matching.isReserved(guest));
        assertNull(controller.matchingTeam(fixture.host, 2, false));
    }

    @Test
    void aCruciblePartyDoesNotFillAnAsterQueue() throws Exception {
        start(fixture.host);
        assertTrue(
                queue(1).enqueue(
                                new CrucibleMatchmaking.Party(
                                        10002,
                                        fixture.resources.config.getScheduleId(),
                                        Map.of(10002, 1)),
                                clock.get()));
        clock.addAndGet(5);
        assertTrue(queue(1).findMatches(clock.get()).isEmpty());
        assertTrue(queue(2).findMatches(clock.get()).isEmpty());
        assertEquals(
                CrucibleMatchmaking.Stage.QUEUED, queue(2).runFor(fixture.host.getUid()).stage());
    }

    @Test
    void aGuestCanCancelTheWholeConsentedPartyAndFreshConsentSurvivesOldRelease() throws Exception {
        var guest = guest(fixture.host, 10002);
        var controller = fixture.scene.getAsterBigSceneController();
        controller.startInvitation(fixture.host, 2, false);
        controller.replyInvitation(guest, 2, true);
        matching.onTick();
        var old = controller.matchingTeam(fixture.host, 2, false);
        matching.cancelRequest(guest, TYPE);
        assertFalse(matching.isReserved(fixture.host));
        assertFalse(matching.isReserved(guest));
        controller.startInvitation(fixture.host, 2, false);
        controller.replyInvitation(guest, 2, true);
        var fresh = controller.matchingTeam(fixture.host, 2, false);
        controller.releaseMatching(old.serial());
        assertTrue(controller.isMatching(fresh));
    }

    @Test
    void timeoutOrLogoutReleasesReservationsWithoutStartingTheChallenge() throws Exception {
        start(fixture.host);
        clock.addAndGet(300);
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        assertEquals(
                3,
                PlayerMatchStopNotify.parseFrom(
                                last(fixture.host, PacketOpcodes.PlayerMatchStopNotify))
                        .getReason());
        start(fixture.host);
        offline.add(fixture.host.getUid());
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        assertEquals(0, fixture.scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    @Test
    void changedWorldLevelOrReplacedPlayerObjectInvalidatesItsFrozenParty() throws Exception {
        start(fixture.host);
        fixture.host.worldLevel++;
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        fixture.host.worldLevel--;
        start(fixture.host);
        var replacement = new AsterMidSceneTest.RecoveryPlayer();
        replacement.setUid(fixture.host.getUid());
        server.getPlayers().put(fixture.host.getUid(), replacement);
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
    }

    @Test
    void activityClosureInvalidatesQueuedAndConfirmedParties() throws Exception {
        var other = solo(10002, 2);
        match(fixture.host, other);
        fixture.resources.config.setDisabled(true);
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        assertFalse(matching.isReserved(other));
        assertSame(fixture.scene, fixture.host.getScene());
    }

    @Test
    void lowestWorldHostAndCorrectPlayIdAreSentToBothSourceWorlds() throws Exception {
        var lower = solo(10002, 0);
        match(fixture.host, lower);
        for (var player : List.of(fixture.host, lower)) {
            var notify =
                    PlayerMatchSuccNotify.parseFrom(
                            last(player, PacketOpcodes.PlayerMatchSuccNotify));
            assertEquals(2, notify.getMpPlayId());
            assertEquals(lower.getUid(), notify.getHostUid());
        }
        matching.confirm(lower, TYPE, true, true);
        assertEquals(
                Retcode.RET_MATCH_NOT_IN_MATCH_VALUE,
                PlayerGuestConfirmMatchRsp.parseFrom(
                                last(lower, PacketOpcodes.PlayerGuestConfirmMatchRsp))
                        .getRetcode());
        assertEquals(CrucibleMatchmaking.Stage.CONFIRMING, queue(2).runFor(lower.getUid()).stage());
        confirm(lower, false);
        assertFalse(matching.isReserved(fixture.host));
        assertFalse(matching.isReserved(lower));
    }

    @Test
    void transferWaitsForEveryConfirmationAndEveryCorrectEntryPermission() throws Exception {
        var other = solo(10002, 2);
        AsterBigEntranceTest.field(World.class, "isMultiplayer")
                .setBoolean(fixture.scene.getWorld(), true);
        match(fixture.host, other);
        confirm(fixture.host, true);
        matching.onTick();
        assertNotSame(fixture.host.getWorld(), other.getWorld());
        confirm(other, true);
        matching.onTick();
        assertNotSame(fixture.host.getWorld(), other.getWorld());
        allow(fixture.host, fixture.host.getUid());
        allow(other, other.getUid());
        matching.onTick();
        assertNotSame(fixture.host.getWorld(), other.getWorld());
        allow(other, fixture.host.getUid());
        matching.onTick();
        assertSame(fixture.host.getWorld(), other.getWorld());
        assertEquals(Player.SceneLoadState.LOADING, other.getSceneLoadState());
        assertTrue(matching.isReserved(other));
        assertEquals(0, fixture.scene.getScenePlayBattleManager().getInfo(MAIN).getState());
        other.setSceneLoadState(Player.SceneLoadState.LOADED);
        matching.onTick();
        fixture.scripts.awaitAll();
        assertFalse(matching.isReserved(other));
        assertFalse(matching.isReserved(fixture.host));
        assertEquals(2, fixture.scene.getScenePlayBattleManager().getInfo(MAIN).getState());
        assertEquals(
                ScenePlayBattleManager.MATCH,
                fixture.scene.getScenePlayBattleManager().getType(MAIN));
        var battles = fixture.scene.getScenePlayBattleManager();
        var battleClock = new AtomicLong(battles.getInfo(MAIN).getPrepareEndTime());
        AsterBigEntranceTest.field(ScenePlayBattleManager.class, "clock")
                .set(battles, (java.util.function.LongSupplier) battleClock::get);
        battles.tick();
        fixture.scripts.awaitAll();
        assertEquals(4, battles.getInfo(MAIN).getState());
        assertTrue(Set.of(201, 202, 203).contains(battles.getInfo(MAIN).getMode()));
        assertEquals(2, battles.getPlayTeamEntities().size());
        assertTrue(
                battles.getPlayTeamEntities().stream()
                        .noneMatch(e -> e.getGadgetConfigId() == 70370004));
    }

    @Test
    void twoConsentedPartiesTransferGuestsBeforeTheirOriginalHosts() throws Exception {
        var firstGuest = guest(fixture.host, 10002);
        var other = solo(10003, 2);
        var secondGuest = guest(other, 10004);
        AsterBigEntranceTest.field(World.class, "isMultiplayer")
                .setBoolean(fixture.host.getWorld(), true);
        AsterBigEntranceTest.field(World.class, "isMultiplayer").setBoolean(other.getWorld(), true);
        for (var owner : List.of(fixture.host, other)) {
            var controller = owner.getScene().getAsterBigSceneController();
            controller.startInvitation(owner, 2, false);
            controller.replyInvitation(owner == fixture.host ? firstGuest : secondGuest, 2, true);
        }
        var oldWorld = fixture.host.getWorld();
        match(fixture.host, other);
        for (var player : List.of(fixture.host, firstGuest, other, secondGuest))
            confirm(player, true);
        for (var player : List.of(fixture.host, firstGuest, other, secondGuest))
            allow(player, fixture.host.getUid());
        matching.onTick();
        assertSame(oldWorld, fixture.host.getWorld());
        for (var player : List.of(fixture.host, firstGuest, other, secondGuest)) {
            assertSame(oldWorld, player.getWorld());
            assertSame(fixture.scene, player.getScene());
        }
        assertEquals(4, oldWorld.getPlayerCount());
    }

    @Test
    void actualSoloHostPromotionCreatesAMultiplayerDestinationAndKeepsReservationsUntilLoaded()
            throws Exception {
        var other = solo(10002, 2);
        var oldWorld = fixture.host.getWorld();
        var previousScene = GameData.getSceneDataMap().put(3, fixture.scene.getSceneData());
        try {
            match(fixture.host, other);
            confirm(fixture.host, true);
            confirm(other, true);
            allow(fixture.host, fixture.host.getUid());
            allow(other, fixture.host.getUid());
            matching.onTick();
            assertNotSame(oldWorld, fixture.host.getWorld());
            assertTrue(fixture.host.getWorld().isMultiplayer());
            assertSame(fixture.host.getWorld(), other.getWorld());
            assertSame(fixture.host.getScene(), other.getScene());
            assertTrue(matching.isReserved(fixture.host));
            assertTrue(matching.isReserved(other));
            assertEquals(Player.SceneLoadState.LOADING, fixture.host.getSceneLoadState());
            var destination = fixture.host.getScene();
            var scripts = new AsterBigEntranceTest.TrackedScripts(destination);
            extraScripts.add(scripts);
            AsterBigEntranceTest.field(Scene.class, "scriptManager").set(destination, scripts);
            AsterBigEntranceTest.field(SceneScriptManager.class, "meta")
                    .set(scripts, SceneMeta.of(3));
            AsterBigEntranceTest.field(SceneScriptManager.class, "isInit").set(scripts, true);
            AsterBigEntranceTest.field(SceneScriptManager.class, "initAttempted")
                    .set(scripts, true);
            fixture.host.setSceneLoadState(Player.SceneLoadState.LOADED);
            other.setSceneLoadState(Player.SceneLoadState.LOADED);
            destination.getAsterBigSceneController().update(fixture.resources.now);
            scripts.awaitAll();
            matching.onTick();
            scripts.awaitAll();
            assertFalse(matching.isReserved(fixture.host));
            assertFalse(matching.isReserved(other));
            assertEquals(2, destination.getScenePlayBattleManager().getInfo(MAIN).getState());
            assertEquals(
                    ScenePlayBattleManager.MATCH,
                    destination.getScenePlayBattleManager().getType(MAIN));
        } finally {
            if (previousScene == null) GameData.getSceneDataMap().remove(3);
            else GameData.getSceneDataMap().put(3, previousScene);
        }
    }

    @Test
    void aLateConfirmationEndsTheWholeMatchWithoutMovingAnyone() throws Exception {
        var other = solo(10002, 2);
        match(fixture.host, other);
        clock.addAndGet(30);
        confirm(fixture.host, true);
        assertEquals(
                Retcode.RET_MP_REPLY_TIMEOUT_VALUE,
                PlayerConfirmMatchRsp.parseFrom(
                                last(fixture.host, PacketOpcodes.PlayerConfirmMatchRsp))
                        .getRetcode());
        assertFalse(matching.isReserved(fixture.host));
        assertFalse(matching.isReserved(other));
        assertNotSame(fixture.host.getWorld(), other.getWorld());
        assertEquals(
                9,
                PlayerMatchStopNotify.parseFrom(last(other, PacketOpcodes.PlayerMatchStopNotify))
                        .getReason());
    }

    @Test
    void incompleteLoadingExpiresWithoutStartingTheOriginalBattle() throws Exception {
        var other = solo(10002, 2);
        AsterBigEntranceTest.field(World.class, "isMultiplayer")
                .setBoolean(fixture.scene.getWorld(), true);
        match(fixture.host, other);
        confirm(fixture.host, true);
        confirm(other, true);
        allow(fixture.host, fixture.host.getUid());
        allow(other, fixture.host.getUid());
        matching.onTick();
        assertSame(fixture.host.getWorld(), other.getWorld());
        assertTrue(matching.isReserved(other));
        clock.addAndGet(60);
        matching.onTick();
        assertFalse(matching.isReserved(fixture.host));
        assertFalse(matching.isReserved(other));
        assertEquals(0, fixture.scene.getScenePlayBattleManager().getInfo(MAIN).getState());
    }

    private byte[] last(Player player, int opcode) {
        return ((AsterMidSceneTest.RecoveryPlayer) player)
                .packets.stream()
                        .filter(p -> p.getOpcode() == opcode)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData();
    }
}

package emu.grasscutter.game;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.AvatarStorage;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.game.PlayerCreationEvent;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Login entry points and password resets use only isolated Mongo fixtures and a fake KCP tunnel.
 */
class LocalLoginAdmissionMongoTest {
    private static final String ID = "login-admission-fixture", TOKEN = "fixture-login-token";
    private static final int AVATAR = 10000007;
    private MongoClient mongo;
    private Datastore game, accounts;
    private Object oldGame, oldAccounts, oldServer, oldPlugins;
    private boolean oldLoaded;
    private AvatarData oldAvatar;
    private ServerRunMode oldMode;
    private GameServer server;
    private PluginManager plugins;
    private ProbeSession session;
    private ExecutorService jobs;
    private final List<ProbeSession> extraSessions = new ArrayList<>();
    private final CountDownLatch releasePreparation = new CountDownLatch(1);

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        oldGame = field(DatabaseManager.class, "gameDatastore").get(null);
        oldAccounts = field(DatabaseManager.class, "dispatchDatastore").get(null);
        oldServer = field(Grasscutter.class, "gameServer").get(null);
        oldPlugins = field(Grasscutter.class, "pluginManager").get(null);
        oldLoaded = ResourceLoader.isLoadedAll();
        oldMode = Grasscutter.getRunMode();
        mongo = MongoClients.create(uri);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(mongo, "lunagc_login_game_" + suffix);
        accounts = Morphia.createDatastore(mongo, "lunagc_login_accounts_" + suffix);
        game.getMapper().map(Account.class, Player.class);
        accounts.getMapper().map(Account.class);
        field(DatabaseManager.class, "gameDatastore").set(null, game);
        field(DatabaseManager.class, "dispatchDatastore").set(null, accounts);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        game.getDatabase()
                .getCollection("accounts")
                .insertOne(
                        new Document("_id", ID)
                                .append("username", ID)
                                .append("token", TOKEN)
                                .append("sessionKey", "fixture-sdk-key")
                                .append("permissions", List.of()));
        server = new GameServer();
        field(GameServer.class, "players").set(server, new ConcurrentHashMap<>());
        field(Grasscutter.class, "gameServer").set(null, server);
        plugins = new PluginManager();
        field(Grasscutter.class, "pluginManager").set(null, plugins);
        field(ResourceLoader.class, "loadedAll").set(null, false);
        oldAvatar = GameData.getAvatarDataMap().put(AVATAR, new AvatarData());
        session = new ProbeSession(server);
        jobs = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void close() throws Exception {
        if (mongo == null) return;
        releasePreparation.countDown();
        try {
            jobs.shutdownNow();
            assertTrue(jobs.awaitTermination(5, TimeUnit.SECONDS));
            session.close();
            extraSessions.forEach(ProbeSession::close);
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        } finally {
            field(DatabaseManager.class, "gameDatastore").set(null, oldGame);
            field(DatabaseManager.class, "dispatchDatastore").set(null, oldAccounts);
            field(Grasscutter.class, "gameServer").set(null, oldServer);
            field(Grasscutter.class, "pluginManager").set(null, oldPlugins);
            field(ResourceLoader.class, "loadedAll").set(null, oldLoaded);
            Grasscutter.setRunModeOverride(oldMode);
            if (oldAvatar == null) GameData.getAvatarDataMap().remove(AVATAR);
            else GameData.getAvatarDataMap().put(AVATAR, oldAvatar);
            game.getDatabase().drop();
            accounts.getDatabase().drop();
            mongo.close();
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private Account account() {
        return DatabaseHelper.getAccountById(ID);
    }

    private void reset() {
        AccountPasswordResetService.resetPassword(account(), "fixture-hash");
    }

    private void cachedPlayer() throws Exception {
        session.setAccount(account());
        var player = new PreparationProbePlayer();
        field(Player.class, "accountId").set(player, ID);
        session.setPlayer(player);
        session.setState(GameSession.SessionState.WAITING_FOR_LOGIN);
    }

    private Player playerFor(ProbeSession target, String id) throws Exception {
        var player = new Player();
        player.setUid(74401 + extraSessions.size());
        field(Player.class, "accountId").set(player, id);
        target.setPlayer(player);
        return player;
    }

    private void managed() throws Exception {
        assertTrue(AccountLoginAdmission.begin(session, ID, TOKEN, account -> {}));
    }

    private ProbeSession another() {
        var next = new ProbeSession(server);
        extraSessions.add(next);
        return next;
    }

    @Test
    void aResetWaitsForTheAuthenticatedTokenPreparationToFinish() throws Exception {
        var entered = new CountDownLatch(1);
        plugins.registerListener(
                new EventHandler<>(PlayerCreationEvent.class)
                        .listener(
                                event -> {
                                    if (event.getSession() != session) return;
                                    entered.countDown();
                                    try {
                                        assertTrue(releasePreparation.await(4, TimeUnit.SECONDS));
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                        throw new IllegalStateException(interrupted);
                                    }
                                    throw new PreparationStopped();
                                }));
        var login =
                jobs.submit(
                        () -> {
                            assertThrows(
                                    PreparationStopped.class,
                                    () ->
                                            new HandlerGetPlayerTokenReq()
                                                    .handle(
                                                            session,
                                                            new byte[0],
                                                            GetPlayerTokenReq.newBuilder()
                                                                    .setAccountUid(ID)
                                                                    .setAccountToken(TOKEN)
                                                                    .build()
                                                                    .toByteArray()));
                        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        var reset = jobs.submit(this::reset);
        try {
            assertThrows(TimeoutException.class, () -> reset.get(120, TimeUnit.MILLISECONDS));
            assertEquals(TOKEN, account().getToken());
        } finally {
            releasePreparation.countDown();
        }
        login.get(3, TimeUnit.SECONDS);
        reset.get(3, TimeUnit.SECONDS);
        assertNull(account().getToken());
    }

    @Test
    void aStaleSessionCannotEnterPlayerLoginAfterReset() throws Exception {
        cachedPlayer();
        reset();
        assertDoesNotThrow(
                () -> new HandlerPlayerLoginReq().handle(session, new byte[0], new byte[0]));
        assertEquals(1, session.closed.get());
    }

    @Test
    void aStaleSessionCannotMutateCharacterCreationAfterReset() throws Exception {
        cachedPlayer();
        session.setState(GameSession.SessionState.PICKING_CHARACTER);
        reset();
        assertDoesNotThrow(
                () ->
                        new HandlerSetPlayerBornDataReq()
                                .handle(
                                        session,
                                        new byte[0],
                                        SetPlayerBornDataReq.newBuilder()
                                                .setAvatarId(AVATAR)
                                                .setNickName("fixture")
                                                .build()
                                                .toByteArray()));
        assertEquals(1, session.closed.get());
    }

    @Test
    void repeatedCloseRequestsOnlyCloseTheTransportOnce() {
        session.close();
        session.close();
        assertEquals(1, session.closed.get());
    }

    @Test
    void validCredentialsCanPrepareAndEnterAStage() throws Exception {
        managed();
        var reached = new AtomicInteger();
        assertTrue(
                AccountLoginAdmission.step(
                        session,
                        () -> {
                            reached.incrementAndGet();
                            var player = playerFor(session, ID);
                            session.setState(GameSession.SessionState.ACTIVE);
                            server.registerPlayer(player);
                        }));
        assertEquals(1, reached.get());
        assertTrue(session.isActive());
        assertEquals(0, session.closed.get());
    }

    @Test
    void theCompleteTokenHandlerCanCreateAProfileAndReplyInsideAdmission() throws Exception {
        game.getMapper().map(NoLoadPlayer.class);
        plugins.registerListener(
                new EventHandler<>(PlayerCreationEvent.class)
                        .listener(event -> event.setPlayerClass(NoLoadPlayer.class)));
        new HandlerGetPlayerTokenReq()
                .handle(
                        session,
                        new byte[0],
                        GetPlayerTokenReq.newBuilder()
                                .setAccountUid(ID)
                                .setAccountToken(TOKEN)
                                .build()
                                .toByteArray());
        assertEquals(GameSession.SessionState.WAITING_FOR_LOGIN, session.getState());
        assertTrue(session.getPlayer().getUid() > 0);
        assertEquals(
                1,
                game.getDatabase()
                        .getCollection("players")
                        .countDocuments(new Document("accountId", ID)));
        assertTrue(session.sent.get() > 0);
        assertTrue(AccountLoginAdmission.step(session, () -> {}));
        reset();
        assertTrue(session.isLoginRevoked());
        assertEquals(1, session.closed.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING_FOR_LOGIN", "PICKING_CHARACTER", "ACTIVE"})
    void resetRevokesPendingAndActiveManagedSessions(String state) throws Exception {
        managed();
        var player = playerFor(session, ID);
        session.setState(GameSession.SessionState.valueOf(state));
        if (state.equals("ACTIVE")) server.registerPlayer(player);
        reset();
        assertTrue(session.isLoginRevoked());
        assertEquals(GameSession.SessionState.INACTIVE, session.getState());
        assertEquals(1, session.closed.get());
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Revoked stage ran")));
    }

    @Test
    void resetWaitsUntilAnAdmittedStageHasPublishedItsPlayer() throws Exception {
        managed();
        var entered = new CountDownLatch(1);
        var stage =
                jobs.submit(
                        () ->
                                AccountLoginAdmission.step(
                                        session,
                                        () -> {
                                            entered.countDown();
                                            assertTrue(
                                                    releasePreparation.await(4, TimeUnit.SECONDS));
                                            var player = playerFor(session, ID);
                                            server.registerPlayer(player);
                                            session.setState(GameSession.SessionState.ACTIVE);
                                        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var reset = jobs.submit(this::reset);
        try {
            assertThrows(TimeoutException.class, () -> reset.get(120, TimeUnit.MILLISECONDS));
            assertEquals(TOKEN, account().getToken());
        } finally {
            releasePreparation.countDown();
        }
        assertTrue(stage.get(3, TimeUnit.SECONDS));
        reset.get(3, TimeUnit.SECONDS);
        assertFalse(session.isActive());
        assertEquals(1, session.closed.get());
        assertNull(account().getToken());
    }

    @Test
    void aCompletedResetRejectsTheOldTokenAndAcceptsANewTokenOnANewConnection() throws Exception {
        managed();
        reset();
        var current = account();
        current.setToken("new-token");
        DatabaseHelper.saveAccountSync(current);
        assertFalse(
                AccountLoginAdmission.begin(
                        session, ID, "new-token", account -> fail("Closed connection reused")));
        var oldToken = another();
        assertFalse(
                AccountLoginAdmission.begin(
                        oldToken, ID, TOKEN, account -> fail("Old token accepted")));
        var fresh = another();
        assertTrue(AccountLoginAdmission.begin(fresh, ID, "new-token", account -> {}));
        assertTrue(AccountLoginAdmission.step(fresh, () -> {}));
    }

    @Test
    void refreshingTheCachedAccountCannotReplaceTheOriginallyPresentedToken() throws Exception {
        managed();
        var current = account();
        current.setToken("rotated-token");
        DatabaseHelper.saveAccountSync(current);
        session.setAccount(account());
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Adopted cached token")));
        assertEquals(1, session.closed.get());
    }

    @Test
    void ordinaryAccountUpdatesDoNotInvalidateAnUnchangedToken() throws Exception {
        managed();
        var current = account();
        current.setBanReason("profile-only update");
        DatabaseHelper.saveAccountSync(current);
        assertTrue(
                AccountLoginAdmission.step(
                        session,
                        () ->
                                assertEquals(
                                        "profile-only update",
                                        session.getAccount().getBanReason())));
    }

    @Test
    void aDeletedAccountCannotEnterAStageOrRecreateItsRow() throws Exception {
        managed();
        game.getDatabase().getCollection("accounts").deleteOne(new Document("_id", ID));
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Deleted account entered")));
        assertEquals(0, game.getDatabase().getCollection("accounts").countDocuments());
    }

    @Test
    void aNewBanIsCheckedBeforeTheStageBody() throws Exception {
        managed();
        var current = account();
        current.setBanned(true);
        DatabaseHelper.saveAccountSync(current);
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Banned account entered")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"mode", "accounts", "game", "account-id", "player-id", "player-session"})
    void aChangedSourceOrSessionIdentityCannotEnterAStage(String change) throws Exception {
        if (change.equals("game")) {
            accounts.getDatabase()
                    .getCollection("accounts")
                    .insertOne(
                            new Document("_id", ID)
                                    .append("username", ID)
                                    .append("token", TOKEN)
                                    .append("permissions", List.of()));
            Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        }
        managed();
        var player = playerFor(session, ID);
        switch (change) {
            case "mode" -> Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
            case "accounts" -> field(DatabaseManager.class, "gameDatastore").set(null, accounts);
            case "game" -> field(DatabaseManager.class, "gameDatastore").set(null, accounts);
            case "account-id" -> {
                var different = new Account();
                different.setId("other-account");
                session.setAccount(different);
            }
            case "player-id" -> field(Player.class, "accountId").set(player, "other-player");
            case "player-session" -> player.setSession(another());
        }
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Changed identity entered")));
    }

    @Test
    void aNewHandshakeRevokesAnOlderPendingHandshakeForTheSameAccount() throws Exception {
        managed();
        var newer = another();
        assertTrue(AccountLoginAdmission.begin(newer, ID, TOKEN, account -> {}));
        assertEquals(1, session.closed.get());
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Replaced handshake entered")));
        assertTrue(AccountLoginAdmission.step(newer, () -> {}));
    }

    @Test
    void replacingAHandshakeWaitsForItsCurrentStageBody() throws Exception {
        managed();
        var entered = new CountDownLatch(1);
        var stage =
                jobs.submit(
                        () ->
                                AccountLoginAdmission.step(
                                        session,
                                        () -> {
                                            entered.countDown();
                                            assertTrue(
                                                    releasePreparation.await(4, TimeUnit.SECONDS));
                                        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var next = another();
        var replacement =
                jobs.submit(() -> AccountLoginAdmission.begin(next, ID, TOKEN, account -> {}));
        try {
            assertThrows(TimeoutException.class, () -> replacement.get(120, TimeUnit.MILLISECONDS));
        } finally {
            releasePreparation.countDown();
        }
        assertTrue(stage.get(3, TimeUnit.SECONDS));
        assertTrue(replacement.get(3, TimeUnit.SECONDS));
        assertTrue(session.isLoginRevoked());
        assertFalse(next.isLoginRevoked());
    }

    @Test
    void resettingOneAccountDoesNotRevokeAnotherAccount() throws Exception {
        managed();
        game.getDatabase()
                .getCollection("accounts")
                .insertOne(
                        new Document("_id", "second")
                                .append("username", "second")
                                .append("token", "second-token")
                                .append("permissions", List.of()));
        var other = another();
        assertTrue(AccountLoginAdmission.begin(other, "second", "second-token", account -> {}));
        reset();
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Reset account entered")));
        assertTrue(AccountLoginAdmission.step(other, () -> {}));
        assertEquals(0, other.closed.get());
    }

    @Test
    void resettingTheSameIdInAnotherDatastoreDoesNotRevokeThisProof() throws Exception {
        managed();
        accounts.getDatabase()
                .getCollection("accounts")
                .insertOne(
                        new Document("_id", ID)
                                .append("username", ID)
                                .append("token", "another-source")
                                .append("permissions", List.of()));
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        reset();
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        assertTrue(AccountLoginAdmission.step(session, () -> {}));
        assertEquals(TOKEN, account().getToken());
        assertEquals(0, session.closed.get());
    }

    @Test
    void aCloseInsideAdmissionIsMarkedImmediatelyAndDeferredUntilTheReadScopeEnds()
            throws Exception {
        managed();
        assertFalse(
                AccountLoginAdmission.step(
                        session,
                        () -> {
                            session.close();
                            assertTrue(session.isLoginRevoked());
                            session.setState(GameSession.SessionState.ACTIVE);
                            assertFalse(session.isActive());
                            assertEquals(0, session.closed.get());
                        }));
        assertEquals(1, session.closed.get());
    }

    @Test
    void aFailingStageReleasesItsGateAndRevokesTheProof() throws Exception {
        managed();
        assertThrows(
                PreparationStopped.class,
                () ->
                        AccountLoginAdmission.step(
                                session,
                                () -> {
                                    throw new PreparationStopped();
                                }));
        assertFalse(AccountLoginAdmission.step(session, () -> fail("Failed stage reused")));
        var replacement = another();
        assertTrue(AccountLoginAdmission.begin(replacement, ID, TOKEN, account -> {}));
        assertTrue(AccountLoginAdmission.step(replacement, () -> {}));
    }

    @Test
    void closingDuringCredentialLookupCannotRunThePreparationBody() throws Exception {
        var read = new CountDownLatch(1);
        var finds = new AtomicInteger();
        var routed =
                (Datastore)
                        java.lang.reflect.Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    Object result;
                                    try {
                                        result = method.invoke(game, args);
                                    } catch (java.lang.reflect.InvocationTargetException failed) {
                                        throw failed.getCause();
                                    }
                                    if (!method.getName().equals("find")
                                            || args[0] != Account.class) return result;
                                    var query = result;
                                    return java.lang.reflect.Proxy.newProxyInstance(
                                            dev.morphia.query.Query.class.getClassLoader(),
                                            new Class<?>[] {dev.morphia.query.Query.class},
                                            (wrapped, operation, values) -> {
                                                Object answer;
                                                try {
                                                    answer = operation.invoke(query, values);
                                                } catch (
                                                        java.lang.reflect.InvocationTargetException
                                                                failed) {
                                                    throw failed.getCause();
                                                }
                                                if (operation.getName().equals("first")
                                                        && finds.incrementAndGet() == 2) {
                                                    read.countDown();
                                                    assertTrue(
                                                            releasePreparation.await(
                                                                    4, TimeUnit.SECONDS));
                                                }
                                                return answer == query ? wrapped : answer;
                                            });
                                });
        field(DatabaseManager.class, "gameDatastore").set(null, routed);
        var preparations = new AtomicInteger();
        var login =
                jobs.submit(
                        () ->
                                AccountLoginAdmission.begin(
                                        session,
                                        ID,
                                        TOKEN,
                                        account -> preparations.incrementAndGet()));
        try {
            assertTrue(read.await(2, TimeUnit.SECONDS));
            session.close();
        } finally {
            releasePreparation.countDown();
        }
        assertFalse(login.get(3, TimeUnit.SECONDS));
        assertEquals(0, preparations.get());
        assertEquals(1, session.closed.get());
    }

    @Test
    void validManagedCredentialsReachBothRealHandlerBodies() throws Exception {
        managed();
        var player = new PreparationProbePlayer();
        field(Player.class, "accountId").set(player, ID);
        session.setPlayer(player);
        assertThrows(
                PreparationTouched.class,
                () -> new HandlerPlayerLoginReq().handle(session, new byte[0], new byte[0]));
        var bornSession = another();
        assertTrue(AccountLoginAdmission.begin(bornSession, ID, TOKEN, account -> {}));
        var bornPlayer = new PreparationProbePlayer();
        field(Player.class, "accountId").set(bornPlayer, ID);
        bornSession.setPlayer(bornPlayer);
        assertThrows(
                PreparationTouched.class,
                () ->
                        new HandlerSetPlayerBornDataReq()
                                .handle(
                                        bornSession,
                                        new byte[0],
                                        SetPlayerBornDataReq.newBuilder()
                                                .setAvatarId(AVATAR)
                                                .setNickName("fixture")
                                                .build()
                                                .toByteArray()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "rotated"})
    void theActuallyScheduledCharacterFallbackRevalidatesItsToken(String token) throws Exception {
        managed();
        var reads = new AtomicInteger();
        var player =
                new Player() {
                    final AvatarStorage storage =
                            new AvatarStorage(this) {
                                @Override
                                public int getAvatarCount() {
                                    if (reads.incrementAndGet() == 2) throw new FallbackReached();
                                    return 0;
                                }
                            };

                    @Override
                    public AvatarStorage getAvatars() {
                        return storage;
                    }
                };
        field(Player.class, "accountId").set(player, ID);
        session.setPlayer(player);
        session.setState(GameSession.SessionState.PICKING_CHARACTER);
        var scheduler = new ServerTaskScheduler();
        field(GameServer.class, "scheduler").set(server, scheduler);
        int oldDelay = GAME_OPTIONS.newAccountIntro.fallbackSeconds;
        try {
            GAME_OPTIONS.newAccountIntro.fallbackSeconds = 1;
            var schedule =
                    HandlerPlayerLoginReq.class.getDeclaredMethod(
                            "scheduleFallback", GameSession.class, Player.class);
            schedule.setAccessible(true);
            schedule.invoke(new HandlerPlayerLoginReq(), session, player);
        } finally {
            GAME_OPTIONS.newAccountIntro.fallbackSeconds = oldDelay;
        }
        if (token.equals("rotated")) {
            var current = account();
            current.setToken("rotated-token");
            DatabaseHelper.saveAccountSync(current);
            assertDoesNotThrow(() -> scheduler.getTask(0).run());
            assertEquals(1, reads.get());
        } else {
            assertThrows(FallbackReached.class, () -> scheduler.getTask(0).run());
            assertEquals(2, reads.get());
        }
        assertEquals(1, session.closed.get());
    }

    @Test
    void evenPingRoutingCannotRunAHandlerAfterRevocation() throws Exception {
        var router = new GameServerPacketHandler(PacketHandler.class);
        @SuppressWarnings("unchecked")
        var handlers =
                (it.unimi.dsi.fastutil.ints.Int2ObjectMap<PacketHandler>)
                        field(GameServerPacketHandler.class, "handlers").get(router);
        var calls = new AtomicInteger();
        handlers.put(
                PacketOpcodes.PingReq,
                new PacketHandler() {
                    public void handle(GameSession session, byte[] header, byte[] payload) {
                        calls.incrementAndGet();
                    }
                });
        session.close();
        router.handle(session, PacketOpcodes.PingReq, new byte[0], new byte[0]);
        assertEquals(0, calls.get());
        assertEquals(1, session.closed.get());
    }

    @Test
    void aRevokedSessionCannotSendAnotherOrdinaryPacket() {
        var writes = new AtomicInteger();
        var plain = new GameSession(server);
        plain.onConnected(
                new GameSessionManager.KcpTunnel() {
                    public InetSocketAddress getAddress() {
                        return new InetSocketAddress("127.0.0.1", 12345);
                    }

                    public void writeData(byte[] bytes) {
                        writes.incrementAndGet();
                    }

                    public void close() {}

                    public int getSrtt() {
                        return 0;
                    }
                });
        plain.close();
        var packet = new BasePacket(PacketOpcodes.PingRsp);
        packet.shouldEncrypt = false;
        plain.send(packet);
        assertEquals(0, writes.get());
    }

    static final class PreparationStopped extends RuntimeException {}

    static final class PreparationTouched extends RuntimeException {}

    static final class FallbackReached extends Error {}

    @dev.morphia.annotations.Entity(value = "players", useDiscriminator = false)
    public static class NoLoadPlayer extends Player {
        public NoLoadPlayer() {
            super();
        }

        public NoLoadPlayer(GameSession session) {
            super(session);
        }

        @Override
        public void loadFromDatabase() {}
    }

    static final class PreparationProbePlayer extends Player {
        @Override
        public int getUid() {
            throw new PreparationTouched();
        }

        @Override
        public void setNickname(String name) {
            throw new PreparationTouched();
        }
    }

    static final class ProbeSession extends GameSession {
        final AtomicInteger closed = new AtomicInteger();
        final AtomicInteger sent = new AtomicInteger();

        ProbeSession(GameServer server) {
            super(server);
            onConnected(
                    new GameSessionManager.KcpTunnel() {
                        public InetSocketAddress getAddress() {
                            return new InetSocketAddress("127.0.0.1", 12345);
                        }

                        public void writeData(byte[] bytes) {}

                        public void close() {
                            closed.incrementAndGet();
                        }

                        public int getSrtt() {
                            return 0;
                        }
                    });
        }

        @Override
        public void send(BasePacket packet) {
            sent.incrementAndGet();
        }
    }
}

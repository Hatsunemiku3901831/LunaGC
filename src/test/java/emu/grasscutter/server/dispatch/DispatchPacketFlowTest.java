package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.objects.LoginTokenRequestJson;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.objects.HandbookBody;

import org.java_websocket.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.*;
import java.net.URI;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Real dispatch encoders and handlers; only the byte transport and accounts are isolated. */
class DispatchPacketFlowTest {
    private MongoClient mongo;
    Datastore game, accounts;
    private Object oldGame, oldAccounts, oldServer, oldDispatch;
    private ServerRunMode oldMode;
    DispatchServer server;
    DispatchClient client;
    CaptureEngine engine;
    private WebSocket peer;
    Account first, second;
    final List<byte[]> serverPackets = Collections.synchronizedList(new ArrayList<>());
    ExecutorService jobs;
    private boolean failServerSend;

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        oldGame = field(DatabaseManager.class, "gameDatastore").get(null);
        oldAccounts = field(DatabaseManager.class, "dispatchDatastore").get(null);
        oldServer = field(Grasscutter.class, "gameServer").get(null);
        oldDispatch = field(Grasscutter.class, "dispatchServer").get(null);
        oldMode = Grasscutter.getRunMode();
        mongo = MongoClients.create(uri);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        game = Morphia.createDatastore(mongo, "lunagc_dispatch_game_" + suffix);
        accounts = Morphia.createDatastore(mongo, "lunagc_dispatch_accounts_" + suffix);
        game.getMapper().map(Account.class, DatabaseCounter.class, Player.class);
        accounts.getMapper().map(Account.class);
        field(DatabaseManager.class, "gameDatastore").set(null, game);
        field(DatabaseManager.class, "dispatchDatastore").set(null, accounts);
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        first = Objects.requireNonNull(DatabaseHelper.createAccount("dispatch-account-a"));
        first.setToken("fixture-token-a");
        DatabaseHelper.saveAccountSync(first);
        second = Objects.requireNonNull(DatabaseHelper.createAccount("dispatch-account-b"));
        second.setToken("fixture-token-b");
        DatabaseHelper.saveAccountSync(second);
        server = new DispatchServer("127.0.0.1", 0);
        client = new DispatchClient(URI.create("ws://127.0.0.1:1"));
        engine = new CaptureEngine(client);
        field(WebSocketClient.class, "engine").set(client, engine);
        client.setAttachment(true);
        peer =
                (WebSocket)
                        Proxy.newProxyInstance(
                                WebSocket.class.getClassLoader(),
                                new Class<?>[] {WebSocket.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getAttachment" -> true;
                                            case "send" -> {
                                                if (failServerSend)
                                                    throw new IllegalStateException(
                                                            "Injected server send failure");
                                                serverPackets.add(((byte[]) args[0]).clone());
                                                yield null;
                                            }
                                            case "toString" -> "dispatch-peer-fixture";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            default -> throw new UnsupportedOperationException(
                                                    method.getName());
                                        });
        @SuppressWarnings("unchecked")
        var connections =
                (Collection<WebSocket>) field(WebSocketServer.class, "connections").get(server);
        connections.add(peer);
        var local = new GameServer();
        field(GameServer.class, "players").set(local, new ConcurrentHashMap<>());
        local.setDispatchClient(client);
        field(Grasscutter.class, "gameServer").set(null, local);
        field(Grasscutter.class, "dispatchServer").set(null, server);
        jobs = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void close() throws Exception {
        if (mongo == null) return;
        try {
            client.failPendingRequests(new CancellationException("Fixture finished"));
            server.failPendingRequests(new CancellationException("Fixture finished"));
            jobs.shutdownNow();
            assertTrue(jobs.awaitTermination(3, TimeUnit.SECONDS));
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        } finally {
            field(DatabaseManager.class, "gameDatastore").set(null, oldGame);
            field(DatabaseManager.class, "dispatchDatastore").set(null, oldAccounts);
            field(Grasscutter.class, "gameServer").set(null, oldServer);
            field(Grasscutter.class, "dispatchServer").set(null, oldDispatch);
            Grasscutter.setRunModeOverride(oldMode);
            game.getDatabase().drop();
            accounts.getDatabase().drop();
            mongo.close();
        }
    }

    static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private void waitPackets(List<byte[]> packets, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (packets.size() < count && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(count, packets.size());
    }

    JsonObject packet(byte[] bytes) {
        return client.decodeMessage(bytes.clone());
    }

    static byte[] encode(JsonObject packet) {
        byte[] bytes = IDispatcher.JSON.toJson(packet).getBytes(StandardCharsets.UTF_8);
        Crypto.xor(bytes, emu.grasscutter.config.Configuration.DISPATCH_INFO.encryptionKey);
        return bytes;
    }

    void toServer(int index) {
        server.onMessage(peer, ByteBuffer.wrap(engine.sent.get(index).clone()));
    }

    void toClient(int index) {
        client.onMessage(ByteBuffer.wrap(serverPackets.get(index).clone()));
    }

    Account authenticate(Account account, String token) {
        return new DefaultAuthenticators.SessionTokenValidator()
                .authenticate(
                        AuthenticationSystem.AuthenticationRequest.builder()
                                .tokenRequest(
                                        LoginTokenRequestJson.builder()
                                                .uid(account.getId())
                                                .token(token)
                                                .build())
                                .build());
    }

    @Test
    void concurrentTokenValidationKeepsBothAccountsSeparateWithReverseReplies() throws Exception {
        var a = jobs.submit(() -> authenticate(first, "fixture-token-a"));
        var b = jobs.submit(() -> authenticate(second, "fixture-token-b"));
        waitPackets(engine.sent, 2);
        for (int i = 1; i >= 0; i--) {
            var request = packet(engine.sent.get(i));
            toServer(i);
            var reply = packet(serverPackets.get(1 - i));
            assertEquals(request.get("requestId"), reply.get("responseTo"));
            assertEquals(PacketIds.TokenValidateRsp, reply.get("packetId").getAsInt());
            toClient(1 - i);
        }
        assertEquals(first.getId(), a.get(2, TimeUnit.SECONDS).getId());
        assertEquals(second.getId(), b.get(2, TimeUnit.SECONDS).getId());
        assertTrue(client.getCallbacks().isEmpty());
    }

    @Test
    void aRejectedTokenCannotConsumeTheSuccessfulAccountsReply() throws Exception {
        var valid = jobs.submit(() -> authenticate(first, "fixture-token-a"));
        var rejected = jobs.submit(() -> authenticate(second, "wrong-fixture-token"));
        waitPackets(engine.sent, 2);
        for (int i = 0; i < 2; i++) {
            toServer(i);
            toClient(i);
        }
        assertEquals(first.getId(), valid.get(2, TimeUnit.SECONDS).getId());
        assertNull(rejected.get(2, TimeUnit.SECONDS));
        assertTrue(client.getCallbacks().isEmpty());
    }

    @Test
    void accountLookupUsesItsOwnRequestAndTheAccountDatabase() throws Exception {
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
        var requested = jobs.submit(() -> DispatchUtils.getAccountById(second.getId()));
        waitPackets(engine.sent, 1);
        toServer(0);
        toClient(0);
        assertEquals(second.getId(), requested.get(2, TimeUnit.SECONDS).getId());
        assertEquals(0, game.getDatabase().getCollection("accounts").countDocuments());
    }

    @Test
    void gameClientHandlerEchoesTheServerRequestIdentityForGachaHistory() throws Exception {
        var requested = jobs.submit(() -> DispatchUtils.fetchGachaRecords(first.getId(), 1, 200));
        waitPackets(serverPackets, 1);
        var request = packet(serverPackets.get(0));
        toClient(0);
        waitPackets(engine.sent, 1);
        var response = packet(engine.sent.get(0));
        assertEquals(request.get("requestId"), response.get("responseTo"));
        toServer(0);
        assertEquals(1, requested.get(2, TimeUnit.SECONDS).get("retcode").getAsInt());
        assertTrue(server.getCallbacks().isEmpty());
    }

    @Test
    void gmHandbookActionWaitsForItsOwnResponse() throws Exception {
        client.getHandlers()
                .put(
                        PacketIds.GmTalkReq,
                        (socket, data) ->
                                client.sendMessage(
                                        PacketIds.GmTalkRsp,
                                        HandbookBody.Response.builder()
                                                .status(203)
                                                .message("Fixture GM transport response")
                                                .build()));
        var requested =
                jobs.submit(
                        () ->
                                DispatchUtils.performHandbookAction(
                                        HandbookBody.Action.GIVE_ITEM, Map.of("uid", 74401)));
        waitPackets(serverPackets, 1);
        toClient(0);
        waitPackets(engine.sent, 1);
        toServer(0);
        assertEquals(203, requested.get(2, TimeUnit.SECONDS).getStatus());
        assertTrue(server.getCallbacks().isEmpty());
    }

    @Test
    void aServerSendFailureUsesTheExistingHandbookErrorResponseWithoutWaiting() throws Exception {
        failServerSend = true;
        var requested =
                jobs.submit(
                        () ->
                                DispatchUtils.performHandbookAction(
                                        HandbookBody.Action.GIVE_ITEM, Map.of("uid", 74401)));
        assertEquals(400, requested.get(2, TimeUnit.SECONDS).getStatus());
        assertTrue(server.getCallbacks().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "token"})
    void aCorrelatedValidFlagCannotOverrideTheRequestedAccountOrToken(String field)
            throws Exception {
        var requested = jobs.submit(() -> authenticate(first, "fixture-token-a"));
        waitPackets(engine.sent, 1);
        toServer(0);
        var response = packet(serverPackets.get(0));
        var body = IDispatcher.decode(response.get("message"));
        body.getAsJsonObject("account")
                .addProperty(field, field.equals("id") ? second.getId() : "other-fixture-token");
        response.addProperty("message", IDispatcher.JSON.toJson(body));
        client.onMessage(ByteBuffer.wrap(encode(response)));
        assertNull(requested.get(2, TimeUnit.SECONDS));
        assertTrue(client.getCallbacks().isEmpty());
    }

    @Test
    void malformedTokenResponseCompletesWithoutStrandingOtherRequests() throws Exception {
        var broken = jobs.submit(() -> authenticate(first, "fixture-token-a"));
        waitPackets(engine.sent, 1);
        var other = jobs.submit(() -> authenticate(second, "fixture-token-b"));
        waitPackets(engine.sent, 2);
        toServer(0);
        var response = packet(serverPackets.get(0));
        response.addProperty("message", "{}");
        client.onMessage(ByteBuffer.wrap(encode(response)));
        assertNull(broken.get(2, TimeUnit.SECONDS));
        assertFalse(other.isDone());
        toServer(1);
        toClient(1);
        assertEquals(second.getId(), other.get(2, TimeUnit.SECONDS).getId());
        assertTrue(client.getCallbacks().isEmpty());
    }

    @Test
    void absentTokenFieldsDoNotSendAnRpcRequest() {
        assertNull(authenticate(first, null));
        assertNull(authenticate(first, ""));
        assertTrue(engine.sent.isEmpty());
        assertTrue(client.getCallbacks().isEmpty());
    }

    static final class CaptureEngine extends WebSocketImpl {
        final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());

        CaptureEngine(DispatchClient client) {
            super(client, new Draft_6455());
        }

        @Override
        public void send(byte[] bytes) {
            sent.add(bytes.clone());
        }
    }
}

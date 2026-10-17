package emu.grasscutter.server.dispatch;

import static emu.grasscutter.server.dispatch.DispatchPacketFlowTest.field;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.objects.HandbookBody;

import org.bson.Document;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One receive thread must remain available for the nested authoritative account reply. */
class DispatchHandbookAsyncTest {
    private static final String KEY = "handbook-worker-fixture-key";
    private DispatchPacketFlowTest wire;
    private Engine engine;
    private ExecutorService reader;
    private WebSocket peer;
    private Player player;
    private GameServer owner;

    @BeforeEach
    void setup() throws Exception {
        wire = new DispatchPacketFlowTest();
        wire.setup();
        wire.first.setSessionKey(KEY);
        wire.first.getSaveGuard().save(wire.accounts);
        wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .updateOne(
                        new Document("_id", wire.first.getId()),
                        new Document("$set", new Document("permissions", List.of())));
        Account cached = IDispatcher.decode(IDispatcher.JSON.toJsonTree(wire.first), Account.class);
        player = new Player();
        player.setUid(74401);
        field(Player.class, "accountId").set(player, cached.getId());
        player.setAccount(cached);
        owner = Grasscutter.getGameServer();
        owner.getPlayers().put(player.getUid(), player);
        peer = (WebSocket) field(DispatchPacketFlowTest.class, "peer").get(wire);
        engine = new Engine(wire.client);
        field(WebSocketClient.class, "engine").set(wire.client, engine);
        wire.client.setAttachment(true);
        wire.client.onOpen(null);
        engine.sent.clear();
        field(DatabaseManager.class, "dispatchDatastore").set(null, wire.game);
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
        reader = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void close() throws Exception {
        if (wire == null) return;
        try {
            if (reader != null) {
                wire.client.failPendingRequests(new CancellationException("Fixture finished"));
                reader.shutdownNow();
                assertTrue(reader.awaitTermination(3, TimeUnit.SECONDS));
            }
        } finally {
            wire.close();
        }
    }

    private JsonObject action() {
        var data = new JsonObject();
        data.addProperty("player", "74401");
        data.addProperty("playerToken", KEY);
        data.addProperty("item", Integer.toString(Integer.MAX_VALUE));
        var request = new JsonObject();
        request.addProperty("action", "GIVE_ITEM");
        request.add("data", data);
        return request;
    }

    private byte[] sent(int id) throws Exception {
        var bytes = engine.sent.poll(2, TimeUnit.SECONDS);
        assertNotNull(bytes, "Missing client packet " + id);
        assertEquals(id, wire.packet(bytes).get("packetId").getAsInt());
        return bytes;
    }

    private void toOwner(byte[] bytes) throws Exception {
        try {
            field(DatabaseManager.class, "dispatchDatastore").set(null, wire.accounts);
            wire.server.onMessage(peer, ByteBuffer.wrap(bytes.clone()));
        } finally {
            field(DatabaseManager.class, "dispatchDatastore").set(null, wire.game);
        }
    }

    @Test
    void defaultHandbookHandlerDoesNotBlockTheThreadNeededForItsAccountReply() throws Exception {
        var result =
                wire.server.async(
                        action(),
                        PacketIds.GmTalkReq,
                        PacketIds.GmTalkRsp,
                        packet -> IDispatcher.decode(packet, HandbookBody.Response.class));
        var handling = reader.submit(() -> wire.toClient(0));
        byte[] accountRequest = sent(PacketIds.GetAccountReq);
        toOwner(accountRequest);
        assertEquals(
                PacketIds.GetAccountRsp,
                wire.packet(wire.serverPackets.get(1)).get("packetId").getAsInt());
        var accountReply = reader.submit(() -> wire.toClient(1));
        assertDoesNotThrow(() -> accountReply.get(1, TimeUnit.SECONDS));
        handling.get(1, TimeUnit.SECONDS);
        byte[] response = sent(PacketIds.GmTalkRsp);
        wire.server.onMessage(peer, ByteBuffer.wrap(response));
        assertEquals(400, result.get(1, TimeUnit.SECONDS).getStatus());
        assertTrue(wire.client.getCallbacks().isEmpty());
        assertTrue(wire.server.getCallbacks().isEmpty());
        assertEquals(0, wire.game.getDatabase().getCollection("accounts").countDocuments());
    }

    private CompletableFuture<HandbookBody.Response> request(JsonObject message) {
        return wire.server.async(
                message,
                PacketIds.GmTalkReq,
                PacketIds.GmTalkRsp,
                packet -> IDispatcher.decode(packet, HandbookBody.Response.class));
    }

    private HandbookBody.Response finish(CompletableFuture<HandbookBody.Response> result)
            throws Exception {
        wire.server.onMessage(peer, ByteBuffer.wrap(sent(PacketIds.GmTalkRsp)));
        return result.get(1, TimeUnit.SECONDS);
    }

    private void useExecutor(Executor executor) throws Exception {
        wire.client = new DispatchClient(URI.create("ws://127.0.0.1:1"), executor);
        owner.setDispatchClient(wire.client);
        engine = new Engine(wire.client);
        field(WebSocketClient.class, "engine").set(wire.client, engine);
        wire.client.setAttachment(true);
        wire.client.onOpen(null);
        engine.sent.clear();
    }

    @Test
    void aBusyExecutorReturnsACorrelatedFailureWithoutRunningOnTheReceiveThread() throws Exception {
        var reject = new AtomicBoolean(true);
        var queued = new ControlledExecutor();
        useExecutor(
                action -> {
                    if (reject.get()) throw new RejectedExecutionException("Injected worker limit");
                    queued.execute(action);
                });
        var result = request(action());
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        assertEquals(503, finish(result).getStatus());
        assertTrue(wire.client.getCallbacks().isEmpty());
        assertTrue(wire.server.getCallbacks().isEmpty());
        assertTrue(engine.sent.isEmpty());
        reject.set(false);
        var message = action();
        message.addProperty("action", "UNKNOWN_ACTION");
        var next = request(message);
        reader.submit(() -> wire.toClient(1)).get(1, TimeUnit.SECONDS);
        var work = queued.tasks.poll(1, TimeUnit.SECONDS);
        assertNotNull(work);
        work.run();
        assertEquals(400, finish(next).getStatus());
    }

    @Test
    void aBroadcastForANonlocalPlayerDoesNotConsumeWorkerCapacityOrReply() throws Exception {
        var queued = new ControlledExecutor();
        useExecutor(queued);
        owner.getPlayers().clear();
        var result = request(action());
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        assertTrue(queued.tasks.isEmpty());
        assertTrue(engine.sent.isEmpty());
        assertFalse(result.isDone());
        result.cancel(false);
    }

    @Test
    void theSamePlayerCannotHaveOverlappingActionsAndIsReleasedAfterCompletion() throws Exception {
        var queued = new ControlledExecutor();
        useExecutor(queued);
        var first = request(action());
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        var second = request(action());
        reader.submit(() -> wire.toClient(1)).get(1, TimeUnit.SECONDS);
        assertEquals(1, queued.tasks.size());
        assertEquals(503, finish(second).getStatus());
        assertFalse(first.isDone());
        var work = queued.tasks.poll(1, TimeUnit.SECONDS);
        assertNotNull(work);
        var running = wire.jobs.submit(work);
        toOwner(sent(PacketIds.GetAccountReq));
        reader.submit(() -> wire.toClient(2)).get(1, TimeUnit.SECONDS);
        running.get(1, TimeUnit.SECONDS);
        assertEquals(400, finish(first).getStatus());
        var message = action();
        message.addProperty("action", "UNKNOWN_ACTION");
        var next = request(message);
        reader.submit(() -> wire.toClient(3)).get(1, TimeUnit.SECONDS);
        var nextWork = queued.tasks.poll(1, TimeUnit.SECONDS);
        assertNotNull(nextWork);
        nextWork.run();
        assertEquals(400, finish(next).getStatus());
        assertTrue(queued.tasks.isEmpty());
        assertTrue(wire.client.getCallbacks().isEmpty());
        assertTrue(wire.server.getCallbacks().isEmpty());
    }

    @Test
    void anUnownedClientDoesNotExecuteTheHandbookRequest() throws Exception {
        var queued = new ControlledExecutor();
        useExecutor(queued);
        owner.setDispatchClient(new DispatchClient(URI.create("ws://127.0.0.1:1")));
        var result = request(action());
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        assertEquals(503, finish(result).getStatus());
        assertTrue(queued.tasks.isEmpty());
        assertTrue(engine.sent.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mode", "game", "accounts", "owner", "client", "player", "connection"})
    void deferredWorkRechecksTheConnectionAndGameContextBeforeAnyAccountQuery(String change)
            throws Exception {
        var queued = new ControlledExecutor();
        useExecutor(queued);
        var result = request(action());
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        var work = queued.tasks.poll(1, TimeUnit.SECONDS);
        assertNotNull(work);
        assertTrue(engine.sent.isEmpty());
        switch (change) {
            case "mode" -> Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
            case "game" -> field(DatabaseManager.class, "gameDatastore").set(null, wire.accounts);
            case "accounts" -> field(DatabaseManager.class, "dispatchDatastore")
                    .set(null, wire.accounts);
            case "owner" -> field(Grasscutter.class, "gameServer").set(null, new GameServer());
            case "client" -> owner.setDispatchClient(
                    new DispatchClient(URI.create("ws://127.0.0.1:1")));
            case "player" -> owner.getPlayers().put(74401, new Player());
            case "connection" -> {
                wire.client.onOpen(null);
                engine.sent.clear();
            }
        }
        work.run();
        if (change.equals("connection")) {
            assertTrue(engine.sent.isEmpty());
            assertFalse(result.isDone());
            result.cancel(false);
            var message = action();
            message.addProperty("action", "UNKNOWN_ACTION");
            var next = request(message);
            reader.submit(() -> wire.toClient(1)).get(1, TimeUnit.SECONDS);
            var nextWork = queued.tasks.poll(1, TimeUnit.SECONDS);
            assertNotNull(nextWork);
            nextWork.run();
            assertEquals(400, finish(next).getStatus());
        } else {
            assertEquals(503, finish(result).getStatus());
            assertTrue(engine.sent.isEmpty());
        }
        assertTrue(wire.client.getCallbacks().isEmpty());
        assertEquals(0, wire.game.getDatabase().getCollection("accounts").countDocuments());
    }

    @Test
    void aMalformedPlayerIdentifierReturnsWithoutSchedulingOrQueryingAccounts() throws Exception {
        var queued = new ControlledExecutor();
        useExecutor(queued);
        var message = action();
        message.getAsJsonObject("data").addProperty("player", "invalid-player");
        var result = request(message);
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        assertEquals(400, finish(result).getStatus());
        assertTrue(queued.tasks.isEmpty());
        assertTrue(engine.sent.isEmpty());
    }

    @Test
    void anUnknownActionReturnsACorrelatedInputFailureFromTheWorker() throws Exception {
        var message = action();
        message.addProperty("action", "UNKNOWN_ACTION");
        var result = request(message);
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        assertEquals(400, finish(result).getStatus());
        assertTrue(engine.sent.isEmpty());
        assertTrue(wire.client.getCallbacks().isEmpty());
    }

    @Test
    void passwordResetBeforeDeferredExecutionCannotBeBypassedByTheCachedAccount() throws Exception {
        var queued = new ControlledExecutor();
        useExecutor(queued);
        var result = request(action());
        reader.submit(() -> wire.toClient(0)).get(1, TimeUnit.SECONDS);
        var work = queued.tasks.poll(1, TimeUnit.SECONDS);
        assertNotNull(work);
        wire.first.getSaveGuard().resetPassword(wire.accounts, "fixture-reset-hash");
        var running = wire.jobs.submit(work);
        toOwner(sent(PacketIds.GetAccountReq));
        reader.submit(() -> wire.toClient(1)).get(1, TimeUnit.SECONDS);
        running.get(1, TimeUnit.SECONDS);
        assertTrue(engine.sent.isEmpty());
        assertFalse(result.isDone());
        result.cancel(false);
        assertFalse(player.getAccount().matchesSessionKey(KEY));
        assertTrue(wire.client.getCallbacks().isEmpty());
    }

    @Test
    void twoDefaultHandlersKeepTheirOwnRepliesWhenNestedAccountRepliesArriveInReverseOrder()
            throws Exception {
        wire.second.setSessionKey(KEY + "-b");
        wire.second.getSaveGuard().save(wire.accounts);
        var second = new Player();
        second.setUid(74402);
        field(Player.class, "accountId").set(second, wire.second.getId());
        second.setAccount(
                IDispatcher.decode(IDispatcher.JSON.toJsonTree(wire.second), Account.class));
        owner.getPlayers().put(second.getUid(), second);
        var secondAction = action();
        secondAction.getAsJsonObject("data").addProperty("player", "74402");
        secondAction.getAsJsonObject("data").addProperty("playerToken", KEY + "-b");
        var a = request(action());
        var b = request(secondAction);
        var handlingA = reader.submit(() -> wire.toClient(0));
        var handlingB = reader.submit(() -> wire.toClient(1));
        byte[] accountA = sent(PacketIds.GetAccountReq), accountB = sent(PacketIds.GetAccountReq);
        toOwner(accountA);
        toOwner(accountB);
        reader.submit(() -> wire.toClient(3)).get(1, TimeUnit.SECONDS);
        reader.submit(() -> wire.toClient(2)).get(1, TimeUnit.SECONDS);
        handlingA.get(1, TimeUnit.SECONDS);
        handlingB.get(1, TimeUnit.SECONDS);
        byte[] responseA = sent(PacketIds.GmTalkRsp), responseB = sent(PacketIds.GmTalkRsp);
        wire.server.onMessage(peer, ByteBuffer.wrap(responseA));
        wire.server.onMessage(peer, ByteBuffer.wrap(responseB));
        assertEquals(400, a.get(1, TimeUnit.SECONDS).getStatus());
        assertEquals(400, b.get(1, TimeUnit.SECONDS).getStatus());
        assertTrue(wire.client.getCallbacks().isEmpty());
        assertTrue(wire.server.getCallbacks().isEmpty());
    }

    private static final class ControlledExecutor implements Executor {
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }
    }

    private static final class Engine extends WebSocketImpl {
        final BlockingQueue<byte[]> sent = new LinkedBlockingQueue<>();

        Engine(DispatchClient client) {
            super(client, new Draft_6455());
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void send(byte[] bytes) {
            sent.add(bytes.clone());
        }
    }
}

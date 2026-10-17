package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.Crypto;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual dispatch framing and handlers, with independently controlled byte transports. */
class DispatchPeerRequestsTest {
    private static final int REQUEST = 1501, RESPONSE = 1502;
    private DispatchServer server;
    private Peer first, second;
    private ExecutorService jobs;

    @BeforeEach
    void setup() throws Exception {
        Grasscutter.getConfig();
        server = new DispatchServer("127.0.0.1", 0);
        first = new Peer();
        second = new Peer();
        var field = WebSocketServer.class.getDeclaredField("connections");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var connections = (Collection<WebSocket>) field.get(server);
        connections.add(first.socket);
        connections.add(second.socket);
        server.onOpen(first.socket, null);
        server.onOpen(second.socket, null);
        jobs = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void close() throws Exception {
        server.failPendingRequests(new CancellationException("Fixture finished"));
        jobs.shutdownNow();
        assertTrue(jobs.awaitTermination(3, TimeUnit.SECONDS));
    }

    private JsonObject request(String id) {
        return server.encodeMessage(
                REQUEST, new DispatchRequests.Outbound(id, RESPONSE, new JsonObject()));
    }

    private void receive(Peer peer, JsonObject packet) {
        byte[] bytes = IDispatcher.JSON.toJson(packet).getBytes(StandardCharsets.UTF_8);
        Crypto.xor(bytes, emu.grasscutter.config.Configuration.DISPATCH_INFO.encryptionKey);
        server.handleMessage(peer.socket, bytes);
    }

    private JsonObject packet(byte[] bytes) {
        return server.decodeMessage(bytes.clone());
    }

    private Runnable deferred(WebSocket peer, JsonObject data) {
        var reply = server.captureReply();
        return () -> reply.send(data);
    }

    @Test
    void aReplyOnAWorkerRetainsTheIncomingRequestIdentity() throws Exception {
        var queued = new ArrayList<Runnable>();
        server.registerHandler(
                REQUEST, (peer, data) -> queued.add(deferred(peer, new JsonObject())));
        String id = UUID.randomUUID().toString();
        receive(first, request(id));
        assertTrue(first.sent.isEmpty());
        jobs.submit(queued.get(0)).get(2, TimeUnit.SECONDS);
        assertEquals(new JsonPrimitive(id), packet(first.sent.get(0)).get("responseTo"));
        assertTrue(second.sent.isEmpty());
    }

    @Test
    void repliesFinishedInReverseOrderRetainTheirOwnPeerAndRequestIdentity() throws Exception {
        var queued = new ArrayList<Runnable>();
        server.registerHandler(
                REQUEST, (peer, data) -> queued.add(deferred(peer, new JsonObject())));
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        receive(first, request(a));
        receive(second, request(b));
        jobs.submit(queued.get(1)).get(2, TimeUnit.SECONDS);
        jobs.submit(queued.get(0)).get(2, TimeUnit.SECONDS);
        assertEquals(new JsonPrimitive(a), packet(first.sent.get(0)).get("responseTo"));
        assertEquals(new JsonPrimitive(b), packet(second.sent.get(0)).get("responseTo"));
        assertEquals(1, first.sent.size());
        assertEquals(1, second.sent.size());
    }

    private CompletableFuture<JsonObject> targeted(Peer peer) {
        return server.asyncTo(peer.socket, new JsonObject(), REQUEST, RESPONSE);
    }

    private JsonObject response(JsonObject request, String value) {
        var body = new JsonObject();
        body.addProperty("value", value);
        var packet = server.encodeMessage(request.get("replyId").getAsInt(), body);
        packet.add("responseTo", request.get("requestId"));
        return packet;
    }

    private int callbacks() {
        synchronized (server.getCallbacks()) {
            return server.getCallbacks().values().stream().mapToInt(List::size).sum();
        }
    }

    @Test
    void targetedRequestDoesNotBroadcastAndAnotherPeerCannotSupplyItsReply() throws Exception {
        var parsed = new AtomicInteger();
        var future =
                server.asyncTo(
                        first.socket,
                        new JsonObject(),
                        REQUEST,
                        RESPONSE,
                        data -> {
                            parsed.incrementAndGet();
                            return IDispatcher.decode(data);
                        },
                        Duration.ofSeconds(3));
        assertEquals(1, first.sent.size());
        assertTrue(second.sent.isEmpty());
        var reply = response(packet(first.sent.get(0)), "first-only");
        receive(second, reply);
        assertFalse(future.isDone());
        assertEquals(0, parsed.get());
        assertEquals(1, callbacks());
        receive(first, reply);
        assertEquals("first-only", future.get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals(1, parsed.get());
        assertEquals(0, callbacks());
    }

    @Test
    void closingAPeerFailsOnlyItsRequestsAndLeavesTheOtherPeerAndNotifications() throws Exception {
        var a = targeted(first);
        var b = targeted(second);
        var notified = new AtomicInteger();
        server.registerCallback(1599, data -> notified.incrementAndGet());
        first.open = false;
        server.onClose(first.socket, 1000, "fixture", false);
        assertTrue(a.isCompletedExceptionally());
        assertFalse(b.isDone());
        assertEquals(2, callbacks());
        receive(second, response(packet(second.sent.get(0)), "live"));
        assertEquals("live", b.get(1, TimeUnit.SECONDS).get("value").getAsString());
        receive(second, server.encodeMessage(1599, new JsonObject()));
        assertEquals(1, notified.get());
        assertEquals(0, callbacks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"closed", "false", "missing", "text", "foreign"})
    void aPeerMustBelongToThisEndpointAndBeOpenAndAuthenticated(String condition) {
        Peer peer = first;
        switch (condition) {
            case "closed" -> first.open = false;
            case "false" -> first.authenticated = false;
            case "missing" -> first.authenticated = null;
            case "text" -> first.authenticated = "true";
            case "foreign" -> peer = new Peer();
        }
        var future = targeted(peer);
        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, callbacks());
        assertTrue(first.sent.isEmpty());
        assertTrue(second.sent.isEmpty());
    }

    @Test
    void connectionReplacementFailsOldRequestsAndLateRepliesCannotCompleteANewOne()
            throws Exception {
        var old = targeted(first);
        var oldRequest = packet(first.sent.get(0));
        server.onOpen(first.socket, null);
        assertTrue(old.isCompletedExceptionally());
        var next = targeted(first);
        receive(first, response(oldRequest, "old"));
        assertFalse(next.isDone());
        receive(first, response(packet(first.sent.get(1)), "new"));
        assertEquals("new", next.get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals(0, callbacks());
    }

    @Test
    void aConnectionLostBetweenRegistrationAndSendNeverFallsBackToBroadcast() {
        first.closeAfterOpenCheck = true;
        var future = targeted(first);
        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, callbacks());
        assertTrue(first.sent.isEmpty());
        assertTrue(second.sent.isEmpty());
    }

    @Test
    void sendFailureCleansItsCallbackAndDoesNotFailAnotherPeer() throws Exception {
        var other = targeted(second);
        first.failSend = true;
        var failed = targeted(first);
        assertTrue(failed.isCompletedExceptionally());
        assertFalse(other.isDone());
        assertEquals(1, callbacks());
        receive(second, response(packet(second.sent.get(0)), "other"));
        assertEquals("other", other.get(1, TimeUnit.SECONDS).get("value").getAsString());
    }

    @Test
    void cancellingOneTargetedRequestDoesNotConsumeTheNextOrAnotherPeer() throws Exception {
        var old = targeted(first);
        var other = targeted(second);
        old.cancel(false);
        var next = targeted(first);
        receive(first, response(packet(first.sent.get(0)), "old"));
        assertFalse(next.isDone());
        assertFalse(other.isDone());
        assertEquals(2, callbacks());
        receive(first, response(packet(first.sent.get(1)), "new"));
        receive(second, response(packet(second.sent.get(0)), "other"));
        assertEquals("new", next.get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals("other", other.get(1, TimeUnit.SECONDS).get("value").getAsString());
    }

    @Test
    void aTimedOutTargetedReplyCannotCompleteTheNextRequest() throws Exception {
        var old =
                server.asyncTo(
                        first.socket,
                        new JsonObject(),
                        REQUEST,
                        RESPONSE,
                        IDispatcher.DEFAULT_PARSER,
                        Duration.ofMillis(40));
        var failure = assertThrows(ExecutionException.class, () -> old.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, failure.getCause());
        var next = targeted(first);
        receive(first, response(packet(first.sent.get(0)), "old"));
        assertFalse(next.isDone());
        receive(first, response(packet(first.sent.get(1)), "next"));
        assertEquals("next", next.get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals(0, callbacks());
    }

    @Test
    void aParserFailureDoesNotDiscardAnotherTargetedRequest() throws Exception {
        var broken =
                server.asyncTo(
                        first.socket,
                        new JsonObject(),
                        REQUEST,
                        RESPONSE,
                        data -> {
                            throw new IllegalArgumentException("Injected parser failure");
                        },
                        Duration.ofSeconds(3));
        var other = targeted(first);
        receive(first, response(packet(first.sent.get(0)), "broken"));
        var failure = assertThrows(ExecutionException.class, () -> broken.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertFalse(other.isDone());
        receive(first, response(packet(first.sent.get(1)), "other"));
        assertEquals("other", other.get(1, TimeUnit.SECONDS).get("value").getAsString());
    }

    @Test
    void aCompletionCallbackCanStartAnotherTargetedRequest() throws Exception {
        var chained = targeted(first).thenCompose(data -> targeted(second));
        receive(first, response(packet(first.sent.get(0)), "first"));
        assertFalse(chained.isDone());
        assertEquals(1, second.sent.size());
        receive(second, response(packet(second.sent.get(0)), "second"));
        assertEquals("second", chained.get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals(0, callbacks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"close", "reopen", "unauthenticate"})
    void aDeferredReplyCannotUseAClosedReplacedOrUnauthenticatedConnection(String change) {
        var reply = new AtomicReference<DispatchReply>();
        server.registerHandler(REQUEST, (peer, data) -> reply.set(server.captureReply()));
        receive(first, request(UUID.randomUUID().toString()));
        switch (change) {
            case "close" -> {
                first.open = false;
                server.onClose(first.socket, 1000, "fixture", false);
            }
            case "reopen" -> server.onOpen(first.socket, null);
            case "unauthenticate" -> first.authenticated = false;
        }
        assertFalse(reply.get().send(new JsonObject()));
        assertTrue(first.sent.isEmpty());
        assertTrue(second.sent.isEmpty());
    }

    @Test
    void capturingAfterTheConnectionChangesDuringAHandlerRejectsTheOldRequest() {
        var rejected = new AtomicInteger();
        server.registerHandler(
                REQUEST,
                (peer, data) -> {
                    server.onOpen(peer, null);
                    assertThrows(IllegalStateException.class, server::captureReply);
                    rejected.incrementAndGet();
                });
        receive(first, request(UUID.randomUUID().toString()));
        assertEquals(1, rejected.get());
    }

    @Test
    void onlyOneConcurrentAttemptCanSendACapturedReply() throws Exception {
        var reply = new AtomicReference<DispatchReply>();
        server.registerHandler(REQUEST, (peer, data) -> reply.set(server.captureReply()));
        String id = UUID.randomUUID().toString();
        receive(first, request(id));
        var start = new CountDownLatch(1);
        var sent = new AtomicInteger();
        var a =
                jobs.submit(
                        () -> {
                            start.await();
                            if (reply.get().send(new JsonObject())) sent.incrementAndGet();
                            return null;
                        });
        var b =
                jobs.submit(
                        () -> {
                            start.await();
                            if (reply.get().send(new JsonObject())) sent.incrementAndGet();
                            return null;
                        });
        start.countDown();
        a.get(2, TimeUnit.SECONDS);
        b.get(2, TimeUnit.SECONDS);
        assertEquals(1, sent.get());
        assertEquals(1, first.sent.size());
        assertEquals(new JsonPrimitive(id), packet(first.sent.get(0)).get("responseTo"));
    }

    @Test
    void aFailedReplySendCannotBeRetriedWithAnAmbiguousDuplicate() {
        var reply = new AtomicReference<DispatchReply>();
        server.registerHandler(REQUEST, (peer, data) -> reply.set(server.captureReply()));
        receive(first, request(UUID.randomUUID().toString()));
        first.failSend = true;
        assertThrows(IllegalStateException.class, () -> reply.get().send(new JsonObject()));
        first.failSend = false;
        assertFalse(reply.get().send(new JsonObject()));
        assertTrue(first.sent.isEmpty());
    }

    @Test
    void replyCaptureRequiresThisEndpointAndACorrelatedRequest() {
        assertThrows(IllegalStateException.class, server::captureReply);
        var foreign = new DispatchServer("127.0.0.1", 0);
        var checked = new AtomicInteger();
        server.registerHandler(
                REQUEST,
                (peer, data) -> {
                    assertThrows(IllegalStateException.class, foreign::captureReply);
                    checked.incrementAndGet();
                });
        receive(first, request(UUID.randomUUID().toString()));
        assertEquals(1, checked.get());
        server.getHandlers()
                .put(
                        REQUEST,
                        (peer, data) -> {
                            assertThrows(IllegalStateException.class, server::captureReply);
                            checked.incrementAndGet();
                        });
        receive(first, server.encodeMessage(REQUEST, new JsonObject()));
        assertEquals(2, checked.get());
        assertThrows(IllegalStateException.class, server::captureReply);
    }

    @Test
    void aCapturedReplyKeepsItsOwnIdentityInsideAnotherRequestScope() {
        var reply = new AtomicReference<DispatchReply>();
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        server.registerHandler(REQUEST, (peer, data) -> reply.set(server.captureReply()));
        receive(first, request(a));
        server.getHandlers()
                .put(REQUEST, (peer, data) -> assertTrue(reply.get().send(new JsonObject())));
        receive(second, request(b));
        assertEquals(new JsonPrimitive(a), packet(first.sent.get(0)).get("responseTo"));
        assertTrue(second.sent.isEmpty());
    }

    @Test
    void concurrentTargetedRepliesInReverseOrderStayBoundToEachPeer() throws Exception {
        var futures = new ArrayList<CompletableFuture<JsonObject>>();
        var peers = new ArrayList<Peer>();
        var requests = new ArrayList<JsonObject>();
        for (int i = 0; i < 48; i++) {
            var peer = i % 2 == 0 ? first : second;
            futures.add(targeted(peer));
            peers.add(peer);
            requests.add(packet(peer.sent.get(peer.sent.size() - 1)));
        }
        var replies = new ArrayList<Future<?>>();
        for (int i = 47; i >= 0; i--) {
            int index = i;
            replies.add(
                    jobs.submit(
                            () -> {
                                var peer = peers.get(index);
                                var reply = response(requests.get(index), Integer.toString(index));
                                receive(peer == first ? second : first, reply);
                                receive(peer, reply);
                            }));
        }
        for (var reply : replies) reply.get(3, TimeUnit.SECONDS);
        for (int i = 0; i < 48; i++)
            assertEquals(
                    Integer.toString(i),
                    futures.get(i).get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals(0, callbacks());
    }

    @Test
    void targetedPacketIdsMayBeNegativeAndTheCapturedReplyUsesTheDeclaredResponseId()
            throws Exception {
        int requestId = -1501, responseId = -1502;
        var future = server.asyncTo(first.socket, new JsonObject(), requestId, responseId);
        var frame = packet(first.sent.get(0));
        assertEquals(requestId, frame.get("packetId").getAsInt());
        receive(first, response(frame, "signed"));
        assertEquals("signed", future.get(1, TimeUnit.SECONDS).get("value").getAsString());
        var reply = new AtomicReference<DispatchReply>();
        server.registerHandler(requestId, (peer, data) -> reply.set(server.captureReply()));
        receive(
                second,
                server.encodeMessage(
                        requestId,
                        new DispatchRequests.Outbound(
                                UUID.randomUUID().toString(), responseId, new JsonObject())));
        assertTrue(reply.get().send(new JsonObject()));
        assertEquals(responseId, packet(second.sent.get(0)).get("packetId").getAsInt());
    }

    @Test
    void invalidTargetedArgumentsDoNotRegisterOrSend() {
        assertThrows(
                NullPointerException.class,
                () -> server.asyncTo(null, new JsonObject(), REQUEST, RESPONSE));
        assertThrows(
                IllegalArgumentException.class,
                () -> server.asyncTo(first.socket, new JsonObject(), 0, RESPONSE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        server.asyncTo(
                                first.socket,
                                new JsonObject(),
                                REQUEST,
                                RESPONSE,
                                IDispatcher.DEFAULT_PARSER,
                                Duration.ZERO));
        assertEquals(0, callbacks());
        assertTrue(first.sent.isEmpty());
        assertTrue(second.sent.isEmpty());
    }

    @Test
    void actualClientCapturesAWorkerReplyAndTheServerCompletesOnlyItsTargetedRequest()
            throws Exception {
        var client = new DispatchClient(URI.create("ws://127.0.0.1:1"));
        var engine = new ClientEngine(client);
        var field = WebSocketClient.class.getDeclaredField("engine");
        field.setAccessible(true);
        field.set(client, engine);
        client.setAttachment(true);
        client.onOpen(null);
        engine.sent.clear();
        var reply = new AtomicReference<DispatchReply>();
        client.registerHandler(REQUEST, (peer, data) -> reply.set(client.captureReply()));
        var future = targeted(first);
        client.onMessage(ByteBuffer.wrap(first.sent.get(0).clone()));
        var body = new JsonObject();
        body.addProperty("value", "worker-client");
        assertTrue(jobs.submit(() -> reply.get().send(body)).get(2, TimeUnit.SECONDS));
        assertEquals(1, engine.sent.size());
        receive(second, packet(engine.sent.get(0)));
        assertFalse(future.isDone());
        receive(first, packet(engine.sent.get(0)));
        assertEquals("worker-client", future.get(1, TimeUnit.SECONDS).get("value").getAsString());
        assertEquals(0, callbacks());
    }

    @Test
    void aReopenedClientCannotSendAnOldWorkerReplyOrKeepAnOldPendingRequest() throws Exception {
        var client = new DispatchClient(URI.create("ws://127.0.0.1:1"));
        var engine = new ClientEngine(client);
        var field = WebSocketClient.class.getDeclaredField("engine");
        field.setAccessible(true);
        field.set(client, engine);
        client.setAttachment(true);
        client.onOpen(null);
        var reply = new AtomicReference<DispatchReply>();
        client.registerHandler(REQUEST, (peer, data) -> reply.set(client.captureReply()));
        byte[] frame =
                IDispatcher.JSON
                        .toJson(request(UUID.randomUUID().toString()))
                        .getBytes(StandardCharsets.UTF_8);
        Crypto.xor(frame, emu.grasscutter.config.Configuration.DISPATCH_INFO.encryptionKey);
        client.onMessage(ByteBuffer.wrap(frame));
        var old = client.async(new JsonObject(), REQUEST, RESPONSE);
        client.onOpen(null);
        assertTrue(old.isCompletedExceptionally());
        engine.sent.clear();
        assertFalse(reply.get().send(new JsonObject()));
        assertTrue(engine.sent.isEmpty());
        assertTrue(client.getCallbacks().isEmpty());
    }

    @Test
    void closingAnUnownedClientInvalidatesCapturedRepliesAndPendingRequests() throws Exception {
        var client = new DispatchClient(URI.create("ws://127.0.0.1:1"));
        var engine = new ClientEngine(client);
        var engineField = WebSocketClient.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        engineField.set(client, engine);
        client.setAttachment(true);
        client.onOpen(null);
        var reply = new AtomicReference<DispatchReply>();
        client.registerHandler(REQUEST, (peer, data) -> reply.set(client.captureReply()));
        byte[] frame =
                IDispatcher.JSON
                        .toJson(request(UUID.randomUUID().toString()))
                        .getBytes(StandardCharsets.UTF_8);
        Crypto.xor(frame, emu.grasscutter.config.Configuration.DISPATCH_INFO.encryptionKey);
        client.onMessage(ByteBuffer.wrap(frame));
        var pending = client.async(new JsonObject(), REQUEST, RESPONSE);
        var ownerField = Grasscutter.class.getDeclaredField("gameServer");
        ownerField.setAccessible(true);
        var previous = ownerField.get(null);
        try {
            ownerField.set(null, null);
            assertDoesNotThrow(() -> client.onClose(1000, "fixture", false));
        } finally {
            ownerField.set(null, previous);
        }
        assertTrue(pending.isCompletedExceptionally());
        assertNull(client.connectionIdentity(client));
        engine.sent.clear();
        assertFalse(reply.get().send(new JsonObject()));
        assertTrue(engine.sent.isEmpty());
        assertTrue(client.getCallbacks().isEmpty());
    }

    private static final class ClientEngine extends WebSocketImpl {
        final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());

        ClientEngine(DispatchClient client) {
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

    private static final class Peer implements InvocationHandler {
        final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());
        final WebSocket socket =
                (WebSocket)
                        Proxy.newProxyInstance(
                                WebSocket.class.getClassLoader(),
                                new Class<?>[] {WebSocket.class},
                                this);
        boolean open = true;
        Object authenticated = true;
        boolean failSend;
        boolean closeAfterOpenCheck;

        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getAttachment" -> authenticated;
                case "setAttachment" -> {
                    authenticated = args[0];
                    yield null;
                }
                case "isOpen" -> {
                    boolean result = open;
                    if (closeAfterOpenCheck) {
                        open = false;
                        closeAfterOpenCheck = false;
                    }
                    yield result;
                }
                case "close" -> {
                    open = false;
                    yield null;
                }
                case "getRemoteSocketAddress" -> new InetSocketAddress("127.0.0.1", 22101);
                case "send" -> {
                    if (failSend) throw new IllegalStateException("Injected targeted send failure");
                    sent.add(((byte[]) args[0]).clone());
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "peer-request-fixture";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}

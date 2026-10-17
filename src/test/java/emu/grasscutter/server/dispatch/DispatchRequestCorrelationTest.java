package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.Crypto;

import org.java_websocket.WebSocket;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

class DispatchRequestCorrelationTest {
    private static final int REQUEST = PacketIds.GetAccountReq, RESPONSE = PacketIds.GetAccountRsp;
    private Fake dispatcher;

    @BeforeEach
    void setup() {
        Grasscutter.getConfig();
        dispatcher = new Fake();
    }

    private CompletableFuture<String> request(String account) {
        var data = new JsonObject();
        data.addProperty("accountId", account);
        return dispatcher.async(
                data, REQUEST, RESPONSE, p -> IDispatcher.decode(p).get("accountId").getAsString());
    }

    private JsonObject response(JsonObject request, int id, String account) {
        var data = new JsonObject();
        data.addProperty("accountId", account);
        var packet = dispatcher.encodeMessage(id, data);
        packet.addProperty(
                "responseTo",
                request.has("requestId")
                        ? request.get("requestId").getAsString()
                        : UUID.randomUUID().toString());
        return packet;
    }

    private int callbackCount() {
        synchronized (dispatcher.callbacks) {
            return dispatcher.callbacks.values().stream().mapToInt(List::size).sum();
        }
    }

    @Test
    void concurrentAccountsDoNotShareTheFirstResponse() throws Exception {
        var a = request("account-a");
        var b = request("account-b");
        dispatcher.receive(response(dispatcher.sent.get(1), RESPONSE, "account-b"));
        assertFalse(a.isDone());
        assertEquals("account-b", b.get(1, TimeUnit.SECONDS));
        dispatcher.receive(response(dispatcher.sent.get(0), RESPONSE, "account-a"));
        assertEquals("account-a", a.get(1, TimeUnit.SECONDS));
        assertEquals(0, callbackCount());
    }

    @Test
    void everyRequestHasItsOwnWireIdentityAndExpectedResponse() {
        request("a");
        request("b");
        for (var packet : dispatcher.sent) {
            assertTrue(packet.has("requestId"));
            assertDoesNotThrow(() -> UUID.fromString(packet.get("requestId").getAsString()));
            assertEquals(RESPONSE, packet.get("replyId").getAsInt());
        }
        assertNotEquals(
                dispatcher.sent.get(0).get("requestId"), dispatcher.sent.get(1).get("requestId"));
    }

    @Test
    void aReplyWithAnotherRequestIdentityCannotCompleteTheFuture() {
        var future = request("a");
        var packet = response(dispatcher.sent.get(0), RESPONSE, "wrong");
        packet.addProperty("responseTo", UUID.randomUUID().toString());
        dispatcher.receive(packet);
        assertFalse(future.isDone());
    }

    @Test
    void anUncorrelatedReplyCannotCompleteACorrelatedRequest() {
        var future = request("a");
        var packet = response(dispatcher.sent.get(0), RESPONSE, "wrong");
        packet.remove("responseTo");
        dispatcher.receive(packet);
        assertFalse(future.isDone());
    }

    @Test
    void aCancelledRequestDoesNotLeaveACallback() {
        var future = request("a");
        assertEquals(1, callbackCount());
        future.cancel(false);
        assertEquals(0, callbackCount());
    }

    @Test
    void aLateCancelledReplyCannotCompleteTheNextAccountRequest() {
        var old = request("old");
        var oldPacket = dispatcher.sent.get(0);
        old.cancel(false);
        var next = request("next");
        dispatcher.receive(response(oldPacket, RESPONSE, "old"));
        assertFalse(next.isDone());
    }

    @Test
    void aDuplicateCompletedReplyCannotCompleteANewRequest() throws Exception {
        var a = request("a");
        var old = response(dispatcher.sent.get(0), RESPONSE, "a");
        dispatcher.receive(old);
        assertEquals("a", a.get(1, TimeUnit.SECONDS));
        var b = request("b");
        dispatcher.receive(old);
        assertFalse(b.isDone());
    }

    @Test
    void parserFailureCompletesExceptionallyAndRemovesOnlyItsCallback() throws Exception {
        var data = new JsonObject();
        data.addProperty("accountId", "broken");
        var broken =
                dispatcher.async(
                        data,
                        REQUEST,
                        RESPONSE,
                        p -> {
                            throw new IllegalArgumentException("Injected parser failure");
                        });
        var other = request("other");
        dispatcher.receive(response(dispatcher.sent.get(0), RESPONSE, "broken"));
        assertTrue(broken.isCompletedExceptionally());
        assertFalse(other.isDone());
        assertEquals(1, callbackCount());
        var error = assertThrows(ExecutionException.class, () -> broken.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void sendFailureReturnsAnExceptionalFutureWithoutLeakingACallback() {
        dispatcher.failSend = true;
        var future = assertDoesNotThrow(() -> request("a"));
        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, callbackCount());
    }

    @Test
    void aLegacyCallbackMayRegisterTheNextOneWithoutBeingRepeated() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        dispatcher.registerCallback(
                RESPONSE,
                p -> {
                    first.incrementAndGet();
                    dispatcher.registerCallback(RESPONSE, q -> second.incrementAndGet());
                });
        var data = new JsonObject();
        dispatcher.receive(dispatcher.encodeMessage(RESPONSE, data));
        assertEquals(1, first.get());
        assertEquals(0, second.get());
        dispatcher.receive(dispatcher.encodeMessage(RESPONSE, data));
        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void aLegacyCallbackCannotStealTheResponseForAPendingRequest() {
        var future = request("a");
        var legacy = new AtomicInteger();
        dispatcher.registerCallback(RESPONSE, p -> legacy.incrementAndGet());
        dispatcher.receive(dispatcher.encodeMessage(RESPONSE, new JsonObject()));
        assertEquals(1, legacy.get());
        assertFalse(future.isDone());
    }

    @Test
    void theExpectedResponsePacketIdIsStillRequired() throws Exception {
        var future = request("a");
        var packet = dispatcher.sent.get(0);
        dispatcher.receive(response(packet, RESPONSE + 100, "wrong"));
        assertFalse(future.isDone());
        dispatcher.receive(response(packet, RESPONSE, "a"));
        assertEquals("a", future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void aParserMayReturnNullAsASuccessfulResponse() throws Exception {
        var future = dispatcher.async(new JsonObject(), REQUEST, RESPONSE, p -> null);
        dispatcher.receive(response(dispatcher.sent.get(0), RESPONSE, "none"));
        assertNull(future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void aTimedOutReplyCannotConsumeTheNextRequest() throws Exception {
        var future =
                dispatcher.async(
                        new JsonObject(),
                        REQUEST,
                        RESPONSE,
                        IDispatcher.DEFAULT_PARSER,
                        Duration.ofMillis(40));
        var old = dispatcher.sent.get(0);
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, error.getCause());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (callbackCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(0, callbackCount());
        var next = request("next");
        dispatcher.receive(response(old, RESPONSE, "old"));
        assertFalse(next.isDone());
    }

    @Test
    void anAlreadyInterruptedCallerDoesNotSendARequest() throws Exception {
        var result = new CompletableFuture<Boolean>();
        var thread =
                new Thread(
                        () -> {
                            Thread.currentThread().interrupt();
                            Object value =
                                    dispatcher.await(
                                            new JsonObject(),
                                            REQUEST,
                                            RESPONSE,
                                            IDispatcher.DEFAULT_PARSER);
                            result.complete(
                                    value == null && Thread.currentThread().isInterrupted());
                        });
        thread.start();
        assertTrue(result.get(2, TimeUnit.SECONDS));
        thread.join(1000);
        assertTrue(dispatcher.sent.isEmpty());
        assertEquals(0, callbackCount());
    }

    @Test
    void interruptingAWaitingCallerRetainsTheFlagAndRemovesItsCallback() throws Exception {
        var result = new CompletableFuture<Boolean>();
        var thread =
                new Thread(
                        () -> {
                            Object value =
                                    dispatcher.await(
                                            new JsonObject(),
                                            REQUEST,
                                            RESPONSE,
                                            IDispatcher.DEFAULT_PARSER);
                            result.complete(
                                    value == null && Thread.currentThread().isInterrupted());
                        });
        thread.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (dispatcher.sent.isEmpty() && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(1, dispatcher.sent.size());
            thread.interrupt();
            assertTrue(result.get(2, TimeUnit.SECONDS));
            assertEquals(0, callbackCount());
        } finally {
            thread.interrupt();
            thread.join(1000);
        }
    }

    @Test
    void connectionFailureCompletesOnlyPendingRequestsAndCleansTheirEntries() {
        var a = request("a");
        var b = request("b");
        var notifications = new AtomicInteger();
        dispatcher.registerCallback(
                PacketIds.ServerMessageNotify, p -> notifications.incrementAndGet());
        dispatcher.failPendingRequests(new IllegalStateException("Fixture connection closed"));
        assertTrue(a.isCompletedExceptionally());
        assertTrue(b.isCompletedExceptionally());
        assertEquals(1, callbackCount());
        dispatcher.receive(
                dispatcher.encodeMessage(PacketIds.ServerMessageNotify, new JsonObject()));
        assertEquals(1, notifications.get());
        assertEquals(0, callbackCount());
    }

    @Test
    void completionMayStartAnotherRequestWithTheSameResponsePacketId() throws Exception {
        var result = request("first").thenCompose(value -> request("second"));
        dispatcher.receive(response(dispatcher.sent.get(0), RESPONSE, "first"));
        assertFalse(result.isDone());
        dispatcher.receive(response(dispatcher.sent.get(1), RESPONSE, "second"));
        assertEquals("second", result.get(1, TimeUnit.SECONDS));
    }

    @Test
    void concurrentRegistrationAndReverseConcurrentRepliesKeepEveryAccountSeparate()
            throws Exception {
        var workers = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        var requests = new ConcurrentHashMap<String, CompletableFuture<String>>();
        try {
            List<Future<?>> registrations = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                String account = "account-" + i;
                registrations.add(
                        workers.submit(
                                () -> {
                                    assertTrue(start.await(2, TimeUnit.SECONDS));
                                    requests.put(account, request(account));
                                    return null;
                                }));
            }
            start.countDown();
            for (var registered : registrations) registered.get(5, TimeUnit.SECONDS);
            var sent = new ArrayList<>(dispatcher.sent);
            Collections.reverse(sent);
            List<Future<?>> replies = new ArrayList<>();
            for (var packet : sent) {
                String account =
                        IDispatcher.decode(packet.get("message")).get("accountId").getAsString();
                replies.add(
                        workers.submit(
                                () -> dispatcher.receive(response(packet, RESPONSE, account))));
            }
            for (var reply : replies) reply.get(5, TimeUnit.SECONDS);
            assertEquals(80, requests.size());
            for (var entry : requests.entrySet())
                assertEquals(entry.getKey(), entry.getValue().get(2, TimeUnit.SECONDS));
            assertEquals(0, callbackCount());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void aResponseForAnotherDispatcherInstanceIsIgnored() {
        request("a");
        var other = new Fake();
        var future = other.async(new JsonObject(), REQUEST, RESPONSE, IDispatcher.DEFAULT_PARSER);
        other.receive(response(dispatcher.sent.get(0), RESPONSE, "a"));
        assertFalse(future.isDone());
        future.cancel(false);
    }

    @Test
    void anUnauthenticatedSocketCannotCompleteARequest() {
        var future = request("a");
        dispatcher.authenticated = false;
        dispatcher.receive(response(dispatcher.sent.get(0), RESPONSE, "a"));
        assertFalse(future.isDone());
        assertEquals(1, dispatcher.closes.get());
    }

    @Test
    void aRequestHandlerRepliesWithTheIncomingIdentityOnlyToItsPeerAndExpectedPacket() {
        String id = UUID.randomUUID().toString();
        var other = new Fake();
        dispatcher.registerHandler(
                REQUEST,
                (peer, data) -> {
                    dispatcher.sendMessage(RESPONSE, new JsonObject());
                    dispatcher.sendMessage(PacketIds.ServerMessageNotify, new JsonObject());
                    dispatcher.sent.add(
                            dispatcher.encodeMessage(RESPONSE, new JsonObject(), other.socket));
                });
        dispatcher.receive(
                dispatcher.encodeMessage(
                        REQUEST, new DispatchRequests.Outbound(id, RESPONSE, new JsonObject())));
        assertEquals(id, dispatcher.sent.get(0).get("responseTo").getAsString());
        assertFalse(dispatcher.sent.get(1).has("responseTo"));
        assertFalse(dispatcher.sent.get(2).has("responseTo"));
        dispatcher.sendMessage(RESPONSE, new JsonObject());
        assertFalse(dispatcher.sent.get(3).has("responseTo"));
    }

    @Test
    void nestedRequestsRestoreTheParentReplyContext() {
        String outer = UUID.randomUUID().toString(), inner = UUID.randomUUID().toString();
        dispatcher.registerHandler(
                41, (peer, data) -> dispatcher.sendMessage(42, new JsonObject()));
        dispatcher.registerHandler(
                REQUEST,
                (peer, data) -> {
                    dispatcher.receive(
                            dispatcher.encodeMessage(
                                    41,
                                    new DispatchRequests.Outbound(inner, 42, new JsonObject())));
                    dispatcher.sendMessage(RESPONSE, new JsonObject());
                });
        dispatcher.receive(
                dispatcher.encodeMessage(
                        REQUEST, new DispatchRequests.Outbound(outer, RESPONSE, new JsonObject())));
        assertEquals(inner, dispatcher.sent.get(0).get("responseTo").getAsString());
        assertEquals(outer, dispatcher.sent.get(1).get("responseTo").getAsString());
    }

    @Test
    void aThrowingHandlerCannotLeakItsReplyContextIntoLaterMessages() {
        dispatcher.registerHandler(
                REQUEST,
                (peer, data) -> {
                    throw new IllegalArgumentException("Injected handler failure");
                });
        dispatcher.receive(
                dispatcher.encodeMessage(
                        REQUEST,
                        new DispatchRequests.Outbound(
                                UUID.randomUUID().toString(), RESPONSE, new JsonObject())));
        dispatcher.sendMessage(RESPONSE, new JsonObject());
        assertFalse(dispatcher.sent.get(0).has("responseTo"));
    }

    @Test
    void invalidRequestMetadataCannotExecuteTheHandler() {
        var called = new AtomicInteger();
        dispatcher.registerHandler(REQUEST, (peer, data) -> called.incrementAndGet());
        for (String invalid : List.of("invalid", "1-1-1-1-1", "")) {
            var packet = dispatcher.encodeMessage(REQUEST, new JsonObject());
            packet.addProperty("requestId", invalid);
            packet.addProperty("replyId", RESPONSE);
            dispatcher.receive(packet);
        }
        var both =
                dispatcher.encodeMessage(
                        REQUEST,
                        new DispatchRequests.Outbound(
                                UUID.randomUUID().toString(), RESPONSE, new JsonObject()));
        both.addProperty("responseTo", UUID.randomUUID().toString());
        dispatcher.receive(both);
        assertEquals(0, called.get());
    }

    @Test
    void invalidTimeoutAndPacketIdsFailBeforeSending() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dispatcher.async(
                                new JsonObject(),
                                REQUEST,
                                RESPONSE,
                                IDispatcher.DEFAULT_PARSER,
                                Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dispatcher.async(
                                new JsonObject(),
                                REQUEST,
                                RESPONSE,
                                IDispatcher.DEFAULT_PARSER,
                                Duration.ofMillis(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.async(new JsonObject(), 0, RESPONSE, IDispatcher.DEFAULT_PARSER));
        assertEquals(0, callbackCount());
        assertTrue(dispatcher.sent.isEmpty());
    }

    @Test
    void aFailingLegacyCallbackDoesNotDiscardOtherCallbacksForTheSamePacket() {
        var received = new AtomicInteger();
        dispatcher.registerCallback(
                RESPONSE,
                p -> {
                    throw new IllegalStateException("Injected legacy callback failure");
                });
        dispatcher.registerCallback(RESPONSE, p -> received.incrementAndGet());
        dispatcher.receive(dispatcher.encodeMessage(RESPONSE, new JsonObject()));
        assertEquals(1, received.get());
        assertEquals(0, callbackCount());
    }

    @Test
    void signedInternalPacketIdsRemainUsableForRequestsAndReplies() throws Exception {
        var data = new JsonObject();
        data.addProperty("accountId", "fixture");
        var gm =
                assertDoesNotThrow(
                        () ->
                                dispatcher.async(
                                        data,
                                        PacketIds.GmTalkReq,
                                        PacketIds.GmTalkRsp,
                                        IDispatcher.DEFAULT_PARSER));
        dispatcher.receive(response(dispatcher.sent.get(0), PacketIds.GmTalkRsp, "gm"));
        assertEquals("gm", gm.get(1, TimeUnit.SECONDS).get("accountId").getAsString());
        var negativeReply =
                assertDoesNotThrow(
                        () -> dispatcher.async(data, -899, -1901, IDispatcher.DEFAULT_PARSER));
        dispatcher.receive(response(dispatcher.sent.get(1), -1901, "reply"));
        assertEquals(
                "reply", negativeReply.get(1, TimeUnit.SECONDS).get("accountId").getAsString());
        assertEquals(0, callbackCount());
    }

    static final class Fake implements IDispatcher {
        final Map<Integer, BiConsumer<WebSocket, JsonElement>> handlers = new HashMap<>();
        final Map<Integer, List<Consumer<JsonElement>>> callbacks = new HashMap<>();
        final List<JsonObject> sent = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger closes = new AtomicInteger();
        final WebSocket socket =
                (WebSocket)
                        Proxy.newProxyInstance(
                                WebSocket.class.getClassLoader(),
                                new Class<?>[] {WebSocket.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getAttachment" -> this.authenticated;
                                            case "close" -> {
                                                closes.incrementAndGet();
                                                yield null;
                                            }
                                            case "toString" -> "dispatch-fixture-socket";
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default -> throw new UnsupportedOperationException(
                                                    method.getName());
                                        });
        boolean failSend;
        boolean authenticated = true;

        public Logger getLogger() {
            return Grasscutter.getLogger();
        }

        public Map<Integer, BiConsumer<WebSocket, JsonElement>> getHandlers() {
            return handlers;
        }

        public Map<Integer, List<Consumer<JsonElement>>> getCallbacks() {
            return callbacks;
        }

        public void sendMessage(int id, Object data) {
            if (failSend) throw new IllegalStateException("Injected send failure");
            sent.add(encodeMessage(id, data, socket));
        }

        void receive(JsonObject packet) {
            byte[] bytes = JSON.toJson(packet).getBytes(StandardCharsets.UTF_8);
            Crypto.xor(bytes, emu.grasscutter.config.Configuration.DISPATCH_INFO.encryptionKey);
            handleMessage(socket, bytes);
        }
    }
}

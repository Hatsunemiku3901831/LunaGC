package emu.grasscutter.server.dispatch;

import com.google.gson.*;

import org.java_websocket.WebSocket;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Per-request callbacks and reply context for the internal dispatch protocol. */
final class DispatchRequests {
    private DispatchRequests() {}

    record Outbound(String id, int replyId, JsonObject payload) {}

    private static final class Pending<T> implements Consumer<JsonElement> {
        final String id = UUID.randomUUID().toString();
        final CompletableFuture<T> future = new CompletableFuture<>();
        final Function<JsonElement, T> parser;

        Pending(Function<JsonElement, T> parser) {
            this.parser = parser;
        }

        public void accept(JsonElement value) {
            if (future.isDone()) return;
            try {
                future.complete(parser.apply(value));
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }
    }

    static <T> CompletableFuture<T> request(
            IDispatcher dispatcher,
            JsonObject request,
            int requestId,
            int responseId,
            Function<JsonElement, T> parser,
            Duration timeout) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(timeout, "timeout");
        if (requestId == 0 || responseId == 0 || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Nonzero packet IDs and positive timeout required");
        long nanos = timeout.toNanos();
        var pending = new Pending<>(parser);
        dispatcher.registerCallback(responseId, pending);
        pending.future.whenComplete(
                (value, failure) -> remove(dispatcher.getCallbacks(), responseId, pending));
        pending.future.orTimeout(nanos, TimeUnit.NANOSECONDS);
        try {
            if (!pending.future.isDone())
                dispatcher.sendMessage(
                        requestId, new Outbound(pending.id, responseId, request.deepCopy()));
        } catch (RuntimeException failedSend) {
            pending.future.completeExceptionally(failedSend);
        }
        return pending.future;
    }

    private static void remove(
            Map<Integer, List<Consumer<JsonElement>>> callbacks,
            int id,
            Consumer<JsonElement> callback) {
        synchronized (callbacks) {
            var entries = callbacks.get(id);
            if (entries == null) return;
            entries.remove(callback);
            if (entries.isEmpty()) callbacks.remove(id);
        }
    }

    /** Take only callbacks belonging to this response, before running any user callback. */
    static List<Consumer<JsonElement>> take(
            Map<Integer, List<Consumer<JsonElement>>> callbacks, int id, String responseTo) {
        synchronized (callbacks) {
            var entries = callbacks.get(id);
            if (entries == null) return List.of();
            var selected = new ArrayList<Consumer<JsonElement>>();
            for (var iterator = entries.iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                boolean matches =
                        entry instanceof Pending<?> pending
                                ? pending.id.equals(responseTo)
                                : responseTo == null;
                if (matches) {
                    iterator.remove();
                    selected.add(entry);
                }
            }
            if (entries.isEmpty()) callbacks.remove(id);
            return selected;
        }
    }

    static void failPending(Map<Integer, List<Consumer<JsonElement>>> callbacks, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        var pending = new ArrayList<Pending<?>>();
        synchronized (callbacks) {
            for (var entries : callbacks.values())
                for (var callback : entries)
                    if (callback instanceof Pending<?> request) pending.add(request);
        }
        for (var request : pending) request.future.completeExceptionally(cause);
    }

    static void invoke(
            IDispatcher dispatcher,
            int packetId,
            List<Consumer<JsonElement>> callbacks,
            JsonElement data) {
        for (var callback : callbacks) {
            try {
                callback.accept(data);
            } catch (RuntimeException failure) {
                dispatcher
                        .getLogger()
                        .warn(
                                "Exception occurred in dispatch callback for packet {}.",
                                packetId,
                                failure);
            }
        }
    }

    static String identity(JsonObject packet, String field) {
        var value = packet.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid dispatch request identity");
        String id = value.getAsString();
        if (!UUID.fromString(id).toString().equals(id))
            throw new IllegalArgumentException("Noncanonical dispatch request identity");
        return id;
    }

    static int packetId(JsonObject packet, String field) {
        var value = packet.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid dispatch packet ID");
        int id = value.getAsBigDecimal().intValueExact();
        if (id == 0) throw new IllegalArgumentException("Nonzero dispatch packet ID required");
        return id;
    }

    private static final ThreadLocal<Scope> incoming = new ThreadLocal<>();

    static final class Scope implements AutoCloseable {
        final Scope previous = incoming.get();
        final IDispatcher dispatcher;
        final WebSocket peer;
        final String id;
        final int responseId;

        Scope(IDispatcher dispatcher, WebSocket peer, JsonObject packet) {
            this.dispatcher = dispatcher;
            this.peer = peer;
            if (packet.has("requestId")) {
                if (packet.has("responseTo"))
                    throw new IllegalArgumentException(
                            "Request and response identities cannot be combined");
                id = identity(packet, "requestId");
                responseId = packetId(packet, "replyId");
            } else {
                if (packet.has("replyId"))
                    throw new IllegalArgumentException("Reply ID requires a request identity");
                id = null;
                responseId = 0;
            }
            incoming.set(this);
        }

        public void close() {
            if (previous == null) incoming.remove();
            else incoming.set(previous);
        }
    }

    static String replyTo(IDispatcher dispatcher, WebSocket peer, int packetId) {
        var scope = incoming.get();
        return scope != null
                        && scope.dispatcher == dispatcher
                        && scope.peer == peer
                        && scope.responseId == packetId
                ? scope.id
                : null;
    }
}

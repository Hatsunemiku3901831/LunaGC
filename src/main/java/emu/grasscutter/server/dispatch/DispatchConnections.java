package emu.grasscutter.server.dispatch;

import org.java_websocket.WebSocket;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/** Generation tokens belong to this endpoint and this exact socket instance. */
final class DispatchConnections {
    private final Map<WebSocket, Object> generations = new IdentityHashMap<>();

    synchronized Object opened(WebSocket peer) {
        return generations.put(peer, new Object());
    }

    synchronized Object closed(WebSocket peer) {
        return generations.remove(peer);
    }

    synchronized Object identity(WebSocket peer) {
        Object generation = generations.get(peer);
        return generation != null && authenticated(peer) ? generation : null;
    }

    synchronized Map<WebSocket, Object> snapshot() {
        var result = new IdentityHashMap<WebSocket, Object>();
        generations.forEach(
                (peer, generation) -> {
                    if (authenticated(peer)) result.put(peer, generation);
                });
        return Collections.unmodifiableMap(result);
    }

    synchronized boolean send(WebSocket peer, Object generation, Runnable action) {
        if (generation == null || generations.get(peer) != generation || !authenticated(peer))
            return false;
        action.run();
        return true;
    }

    private boolean authenticated(WebSocket peer) {
        return Boolean.TRUE.equals(peer.getAttachment()) && peer.isOpen();
    }
}

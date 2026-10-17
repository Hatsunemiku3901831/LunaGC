package emu.grasscutter.server.dispatch;

import org.java_websocket.WebSocket;

import java.util.concurrent.atomic.AtomicBoolean;

/** One reply attempt, pinned to the incoming peer, connection generation and request identity. */
public final class DispatchReply {
    private final IDispatcher dispatcher;
    private final WebSocket peer;
    private final Object connection;
    private final String requestId;
    private final int packetId;
    private final AtomicBoolean attempted = new AtomicBoolean();

    DispatchReply(
            IDispatcher dispatcher,
            WebSocket peer,
            Object connection,
            String requestId,
            int packetId) {
        this.dispatcher = dispatcher;
        this.peer = peer;
        this.connection = connection;
        this.requestId = requestId;
        this.packetId = packetId;
    }

    /**
     * Returns false after a prior attempt, connection closure, replacement or loss of
     * authentication.
     */
    public boolean send(Object payload) {
        return attempted.compareAndSet(false, true)
                && dispatcher.sendIfCurrent(
                        peer, connection, packetId, new DispatchRequests.Reply(requestId, payload));
    }

    /** A preflight check for deferred work; it does not replace business admission. */
    public boolean isCurrent() {
        return !attempted.get() && dispatcher.connectionIdentity(peer) == connection;
    }
}

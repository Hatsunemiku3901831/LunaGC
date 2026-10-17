package emu.grasscutter.server.dispatch;

import static emu.grasscutter.config.Configuration.DISPATCH_INFO;

import com.google.gson.*;
import dev.morphia.Datastore;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.AccountRemoteStorage;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.event.dispatch.ServerMessageEvent;
import emu.grasscutter.utils.Crypto;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;

/* Internal communications server. */
public final class DispatchServer extends WebSocketServer implements IDispatcher {
    @Getter private final Logger logger = Grasscutter.getLogger();
    @Getter private final Map<Integer, BiConsumer<WebSocket, JsonElement>> handlers = new HashMap<>();

    @Getter private final Map<Integer, List<Consumer<JsonElement>>> callbacks = new HashMap<>();
    private final DispatchConnections requestConnections = new DispatchConnections();
    private final Datastore accountOwner;

    public boolean ownsAccounts(Datastore source) { return accountOwner != null && accountOwner == source; }

    /** Freeze every authenticated recipient before sending; success requires every exact peer. */
    public void revokeCredentials(Datastore source, String accountId, long throughRevision, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("Positive revocation timeout required");
        if (!ownsAccounts(source)) throw new IllegalStateException("Dispatch account authority changed");
        var request = new CredentialRevocation.Request(accountId, throughRevision);
        var pending = new ArrayList<CompletableFuture<Void>>();
        requestConnections.snapshot().forEach((peer, generation) -> pending.add(
                DispatchRequests.request(this, peer, generation, request.message(),
                        PacketIds.CredentialRevokeReq, PacketIds.CredentialRevokeRsp,
                        packet -> { request.confirm(packet); return null; }, timeout)));
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Password stored; game connection revocation is incomplete", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Password stored; game connection revocation is incomplete", failed.getCause());
        } finally { pending.forEach(future -> future.cancel(false)); }
    }

    public <T> CompletableFuture<T> asyncTo(
            WebSocket peer, JsonObject request, int requestId, int responseId,
            Function<JsonElement, T> parser, Duration timeout) {
        Objects.requireNonNull(peer, "Dispatch peer");
        return DispatchRequests.request(this, peer, connectionIdentity(peer), request,
                requestId, responseId, parser, timeout);
    }

    public CompletableFuture<JsonObject> asyncTo(WebSocket peer, JsonObject request, int requestId, int responseId) {
        return asyncTo(peer, request, requestId, responseId, DEFAULT_PARSER, Duration.ofSeconds(5));
    }

    @Override
    public Object connectionIdentity(WebSocket peer) {
        return requestConnections.identity(peer);
    }

    @Override
    public boolean sendIfCurrent(WebSocket peer, Object connection, int packetId, Object message) {
        return requestConnections.send(peer, connection, () -> sendMessage(peer, packetId, message));
    }

    /**
     * Constructs a new {@code DispatchServer} instance.
     *
     * @param address The address to bind to.
     * @param port The port to bind to.
     */
    public DispatchServer(String address, int port) {
        super(new InetSocketAddress(address, port));
        accountOwner = DatabaseManager.getAccountDatastore();

        this.registerHandler(PacketIds.LoginNotify, this::handleLogin);
        this.registerHandler(PacketIds.TokenValidateReq, this::validateToken);
        this.registerHandler(PacketIds.GetAccountReq, this::fetchAccount);
        this.registerHandler(PacketIds.AccountStorageReq, this::accountStorage);
        this.registerHandler(PacketIds.ServerMessageNotify, ServerMessageEvent::invoke);
    }

    /**
     * Handles the login packet sent by the client.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void handleLogin(WebSocket socket, JsonElement object) {
        var dispatchKey = object.getAsString().replaceAll("\"", "");

        // Check if the dispatch key is valid.
        if (!dispatchKey.equals(DISPATCH_INFO.dispatchKey)) {
            this.getLogger()
                    .warn("Invalid dispatch key received from {}.", socket.getRemoteSocketAddress());
            this.getLogger().debug("Expected: {}, Received: {}", DISPATCH_INFO.dispatchKey, dispatchKey);
            socket.close();
        } else {
            socket.setAttachment(true);
        }
    }

    /**
     * Handles the token validation packet sent by the client.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void validateToken(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("uid").getAsString();
        var token = message.get("token").getAsString();

        // Get the account from the database.
        var account = accountBy("_id", accountId);
        var valid = account != null && account.matchesLoginToken(token);
        // Create the response message.
        var response = new JsonObject();
        response.addProperty("valid", valid);
        if (valid) response.add("account", JSON.toJsonTree(account));

        // Send the response.
        this.sendMessage(socket, PacketIds.TokenValidateRsp, response);
    }

    /**
     * Fetches an account by its ID.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void fetchAccount(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var account = message.size() == 1 && message.has("accountId")
                ? accountBy("_id", message.get("accountId").getAsString())
                : message.size() == 1 && message.has("username")
                    ? accountBy("username", message.get("username").getAsString()) : null;
        // Send the account.
        this.sendMessage(socket, PacketIds.GetAccountRsp, JSON.toJsonTree(account));
    }

    private Account accountBy(String field, String value) {
        return Objects.requireNonNull(accountOwner, "Account authority is unavailable")
                .find(Account.class).filter(Filters.eq(field, value)).first();
    }

    private void accountStorage(WebSocket socket, JsonElement object) {
        var owner = accountOwner;
        JsonObject response;
        try {
            response = AccountRemoteStorage.serve(owner, IDispatcher.decode(object));
        } catch (RuntimeException failed) {
            response = new JsonObject();
            response.addProperty("ok", false);
            this.getLogger().warn("Internal account storage request was not confirmed.");
        }
        this.sendMessage(socket, PacketIds.AccountStorageRsp, response);
    }

    /**
     * Broadcasts an encrypted message to all connected clients.
     *
     * @param message The message to broadcast.
     */
    public void sendMessage(int packetId, Object message) {
        var serverMessage = this.encodeMessage(packetId, message);
        this.getConnections().forEach(socket -> this.sendMessage(socket, serverMessage));
    }

    /**
     * Sends a serialized encrypted message to the client.
     *
     * @param socket The socket to send the message to.
     * @param message The message to send.
     */
    public void sendMessage(WebSocket socket, Object message) {
        // Serialize the message into JSON.
        var serialized = JSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        // Encrypt the message.
        Crypto.xor(serialized, DISPATCH_INFO.encryptionKey);
        // Send the message.
        socket.send(serialized);
    }

    /**
     * Sends a serialized encrypted message to the client.
     *
     * @param socket The socket to send the message to.
     * @param packetId The packet ID to send.
     * @param message The message to send.
     */
    public void sendMessage(WebSocket socket, int packetId, Object message) {
        this.sendMessage(socket, this.encodeMessage(packetId, message, socket));
    }

    @Override
    public void onStart() {
        this.getLogger().info("Dispatch server started on port {}.", this.getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        var previous = requestConnections.opened(conn);
        if (previous != null) DispatchRequests.failPending(getCallbacks(), conn, previous,
                new IllegalStateException("Dispatch peer connection replaced"));
        this.getLogger().debug("Dispatch client connected from {}.", conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        this.getLogger()
                .debug("Received dispatch message from {}:\n{}", conn.getRemoteSocketAddress(), message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        this.handleMessage(conn, message.array());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        var previous = requestConnections.closed(conn);
        if (previous != null) DispatchRequests.failPending(getCallbacks(), conn, previous,
                new IllegalStateException("Dispatch peer connection closed"));
        this.getLogger().debug("Dispatch client disconnected from {}.", conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        this.getLogger().warn("Dispatch server error.", ex);
    }
}

package emu.grasscutter.server.dispatch;

import static emu.grasscutter.config.Configuration.DISPATCH_INFO;

import com.google.gson.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.HandbookActions;
import emu.grasscutter.server.event.dispatch.ServerMessageEvent;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.handlers.GachaHandler;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.objects.HandbookBody;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

public final class DispatchClient extends WebSocketClient implements IDispatcher {
    @Getter private final Logger logger = Grasscutter.getLogger();
    @Getter private final Map<Integer, BiConsumer<WebSocket, JsonElement>> handlers = new HashMap<>();

    @Getter private final Map<Integer, List<Consumer<JsonElement>>> callbacks = new HashMap<>();
    private final DispatchConnections requestConnections = new DispatchConnections();
    private static final AtomicInteger handbookThreadIds = new AtomicInteger();
    private static final Executor handbookTasks = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), action -> {
                var thread = new Thread(action, "dispatch-handbook-" + handbookThreadIds.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
    private final Executor handbookExecutor;
    private final Set<Integer> handbookPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public Object connectionIdentity(WebSocket peer) {
        return peer == this ? requestConnections.identity(peer) : null;
    }

    @Override
    public boolean sendIfCurrent(WebSocket peer, Object connection, int packetId, Object message) {
        return peer == this && requestConnections.send(peer, connection, () -> sendMessage(packetId, message));
    }

    public DispatchClient(URI serverUri) {
        this(serverUri, handbookTasks);
    }

    DispatchClient(URI serverUri, Executor handbookExecutor) {
        super(serverUri);
        this.handbookExecutor = Objects.requireNonNull(handbookExecutor, "Handbook executor");

        // Mark this client as authenticated.
        this.setAttachment(true);

        this.registerHandler(PacketIds.GachaHistoryReq, this::fetchGachaHistory);
        this.registerHandler(PacketIds.GmTalkReq, this::handleHandbookAction);
        this.registerHandler(PacketIds.GetPlayerFieldsReq, this::fetchPlayerFields);
        this.registerHandler(PacketIds.GetPlayerByAccountReq, this::fetchPlayerByAccount);
        this.registerHandler(PacketIds.ServerMessageNotify, ServerMessageEvent::invoke);
    }

    /**
     * Handles the gacha history request packet sent by the client.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void fetchGachaHistory(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("accountId").getAsString();
        var page = message.get("page").getAsInt();
        var type = message.get("gachaType").getAsInt();

        // Create a response object.
        var response = new JsonObject();

        // Find a player with the specified account ID.
        var player = DatabaseHelper.getPlayerByAccount(accountId);
        if (player == null) {
            response.addProperty("retcode", 1);
            this.sendMessage(PacketIds.GachaHistoryRsp, response);
            return;
        }

        // Fetch the gacha records.
        GachaHandler.fetchGachaRecords(player, response, page, type);

        // Send the response.
        this.sendMessage(PacketIds.GachaHistoryRsp, response);
    }

    /**
     * Handles the handbook action packet sent by the client.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void handleHandbookAction(WebSocket socket, JsonElement object) {
        var reply = captureReply();
        var owner = Grasscutter.getGameServer();
        var mode = Grasscutter.getRunMode();
        var game = DatabaseManager.getGameDatastore();
        var accounts = DatabaseManager.getAccountDatastore();
        if (owner == null || owner.getDispatchClient() != this
                || mode == Grasscutter.ServerRunMode.DISPATCH_ONLY) {
            reply.send(handbookError(503, "Game server is unavailable."));
            return;
        }
        final JsonObject message;
        final int playerId;
        try {
            message = IDispatcher.decode(object).deepCopy();
            playerId = Integer.parseInt(message.getAsJsonObject("data").get("player").getAsString());
        } catch (RuntimeException malformed) {
            reply.send(handbookError(400, "Invalid handbook request."));
            return;
        }
        // Broadcasts also reach servers that do not own this online player. They stay silent.
        var player = owner.getPlayerByUid(playerId);
        if (player == null) return;
        if (!handbookPlayers.add(playerId)) {
            reply.send(handbookError(503, "A handbook action for this player is still running."));
            return;
        }
        try {
            handbookExecutor.execute(() -> {
                try {
                    if (!reply.isCurrent()) return;
                    if (Grasscutter.getGameServer() != owner || owner.getDispatchClient() != this
                            || Grasscutter.getRunMode() != mode
                            || DatabaseManager.getGameDatastore() != game
                            || DatabaseManager.getAccountDatastore() != accounts
                            || owner.getPlayerByUid(playerId) != player) {
                        reply.send(handbookError(503, "Game server context changed."));
                        return;
                    }
                    try {
                        var response = performHandbookAction(message);
                        if (response.getStatus() != 1) reply.send(response);
                    } catch (IllegalArgumentException malformed) {
                        reply.send(handbookError(400, "Invalid handbook request."));
                    } catch (RuntimeException failed) {
                        getLogger().warn("Internal handbook action did not complete.");
                        reply.send(handbookError(500, "Handbook action did not complete."));
                    }
                } finally {
                    handbookPlayers.remove(playerId);
                }
            });
        } catch (RejectedExecutionException busy) {
            handbookPlayers.remove(playerId);
            reply.send(handbookError(503, "Game server is busy. Try again."));
        } catch (RuntimeException | Error failed) {
            handbookPlayers.remove(playerId);
            throw failed;
        }
    }

    private HandbookBody.Response handbookError(int status, String message) {
        return HandbookBody.Response.builder().status(status).message(message).build();
    }

    private HandbookBody.Response performHandbookAction(JsonObject message) {
        var data = message.getAsJsonObject("data");
        return switch (HandbookBody.Action.valueOf(message.get("action").getAsString())) {
            case GRANT_AVATAR -> HandbookActions.grantAvatar(JsonUtils.decode(data, HandbookBody.GrantAvatar.class));
            case GIVE_ITEM -> HandbookActions.giveItem(JsonUtils.decode(data, HandbookBody.GiveItem.class));
            case TELEPORT_TO -> HandbookActions.teleportTo(JsonUtils.decode(data, HandbookBody.TeleportTo.class));
            case SPAWN_ENTITY -> HandbookActions.spawnEntity(JsonUtils.decode(data, HandbookBody.SpawnEntity.class));
        };
    }

    /**
     * Fetches the fields of a player.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void fetchPlayerFields(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var playerId = message.get("playerId").getAsInt();
        var fieldsRaw = message.get("fields").getAsJsonArray();

        // Get the player with the specified ID.
        var player = Grasscutter.getGameServer().getPlayerByUid(playerId, true);
        if (player == null) return;

        // Convert the fields array.
        var fieldsList = new ArrayList<String>();
        for (var field : fieldsRaw) fieldsList.add(field.getAsString());
        var fields = fieldsList.toArray(new String[0]);

        // Return the response object.
        this.sendMessage(PacketIds.GetPlayerFieldsRsp, DispatchUtils.getPlayerFields(playerId, fields));
    }

    /**
     * Fetches the fields of a player by the account.
     *
     * @param socket The socket the packet was received from.
     * @param object The packet data.
     */
    private void fetchPlayerByAccount(WebSocket socket, JsonElement object) {
        var message = IDispatcher.decode(object);
        var accountId = message.get("accountId").getAsString();
        var fieldsRaw = message.get("fields").getAsJsonArray();

        // Get the player with the specified ID.
        var player = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
        if (player == null) return;

        // Convert the fields array.
        var fieldsList = new ArrayList<String>();
        for (var field : fieldsRaw) fieldsList.add(field.getAsString());
        var fields = fieldsList.toArray(new String[0]);

        // Return the response object.
        this.sendMessage(
                PacketIds.GetPlayerByAccountRsp, DispatchUtils.getPlayerByAccount(accountId, fields));
    }

    /**
     * Sends a serialized encrypted message to the server.
     *
     * @param message The message to send.
     */
    public void sendMessage(int packetId, Object message) {
        var serverMessage = this.encodeMessage(packetId, message, this);
        // Serialize the message into JSON.
        var serialized = JSON.toJson(serverMessage).getBytes(StandardCharsets.UTF_8);
        // Encrypt the message.
        Crypto.xor(serialized, DISPATCH_INFO.encryptionKey);
        // Send the message.
        this.send(serialized);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        if (requestConnections.opened(this) != null)
            failPendingRequests(new IllegalStateException("Dispatch connection replaced"));
        // Attempt to handshake with the server.
        this.sendMessage(PacketIds.LoginNotify, DISPATCH_INFO.dispatchKey);

        this.getLogger().info("Dispatch connection opened.");
    }

    @Override
    public void onMessage(String message) {
        this.getLogger().debug("Received dispatch message from server:\n{}", message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        this.handleMessage(this, bytes.array());
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        requestConnections.closed(this);
        this.failPendingRequests(new IllegalStateException("Dispatch connection closed"));
        this.getLogger().info("Dispatch connection closed.");

        var owner = Grasscutter.getGameServer();
        if (owner == null || owner.getDispatchClient() != this) return;

        // Attempt to reconnect.
        new Thread(
                        () -> {
                            try {
                                // Wait 5 seconds before reconnecting.
                                Thread.sleep(5000L);
                            } catch (Exception ignored) {
                            }

                            // Attempt to reconnect.
                            if (Grasscutter.getGameServer() != owner || owner.getDispatchClient() != this) return;
                            owner.setDispatchClient(new DispatchClient(GameServer.getDispatchUrl()));
                            owner.getDispatchClient().connect();
                        })
                .start();
    }

    @Override
    public void onError(Exception ex) {
        if (ex instanceof ConnectException) {
            this.getLogger().info("Failed to reconnect, trying again in 5s...");
        } else {
            this.getLogger().error("Dispatch connection error.", ex);
        }
    }
}

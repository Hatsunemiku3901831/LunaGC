package emu.grasscutter.server.dispatch;

import static emu.grasscutter.server.dispatch.DispatchPacketFlowTest.field;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.command.DefaultPermissionHandler;
import emu.grasscutter.database.*;
import emu.grasscutter.game.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.DispatchUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.concurrent.*;

/** Real account owner handlers with deliberate response reordering and local shadow records. */
class DispatchAccountRefreshTest {
    private static final String KEY = "owner-session-key";
    private DispatchPacketFlowTest wire;
    private Player player;
    private Account cached;
    private int sent, received;
    private Document shadow;

    @BeforeEach
    void setup() throws Exception {
        wire = new DispatchPacketFlowTest();
        wire.setup();
        wire.first.setSessionKey(KEY);
        wire.first.getSaveGuard().save(wire.accounts);
        // This fixture mapper omits empty collections; persist [] to avoid the legacy missing-field
        // grant.
        wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .updateOne(
                        new Document("_id", wire.first.getId()),
                        new Document("$set", new Document("permissions", java.util.List.of())));
        cached = IDispatcher.decode(IDispatcher.JSON.toJsonTree(wire.first), Account.class);
        shadow =
                new Document("_id", cached.getId())
                        .append("username", cached.getUsername())
                        .append("sessionKey", "local-shadow-key")
                        .append("permissions", java.util.List.of("*"));
        wire.game.getDatabase().getCollection("accounts").insertOne(shadow);
        player =
                new Player() {
                    @Override
                    public void dropMessage(Object message) {}
                };
        field(Player.class, "accountId").set(player, cached.getId());
        player.setAccount(cached);
        field(DatabaseManager.class, "dispatchDatastore").set(null, wire.game);
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
    }

    @AfterEach
    void close() throws Exception {
        if (wire != null) wire.close();
    }

    private void toOwner(int index) throws Exception {
        try {
            field(DatabaseManager.class, "dispatchDatastore").set(null, wire.accounts);
            wire.toServer(index);
        } finally {
            field(DatabaseManager.class, "dispatchDatastore").set(null, wire.game);
        }
    }

    private void waitSent(int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (wire.engine.sent.size() < count && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(count, wire.engine.sent.size());
    }

    private <T> T complete(Future<T> operation) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!operation.isDone() && System.nanoTime() < deadline) {
            while (sent < wire.engine.sent.size()) toOwner(sent++);
            while (received < wire.serverPackets.size()) wire.toClient(received++);
            Thread.sleep(1);
        }
        return operation.get(1, TimeUnit.SECONDS);
    }

    private void unchangedShadow() {
        assertEquals(
                shadow,
                wire.game
                        .getDatabase()
                        .getCollection("accounts")
                        .find(new Document("_id", cached.getId()))
                        .first());
    }

    @Test
    void aPlayerWithoutACachedAccountLoadsFromTheRemoteOwner() throws Exception {
        player.setAccount(null);
        var account = complete(wire.jobs.submit(player::getAccount));
        assertEquals(KEY, account.getSessionKey());
        assertNotEquals("local-shadow-key", account.getSessionKey());
        unchangedShadow();
    }

    @Test
    void aRemoteKeyRevocationCannotBeBypassedByTheOnlineCacheOrLocalShadow() throws Exception {
        assertTrue(complete(wire.jobs.submit(() -> HandbookActions.isAuthenticated(player, KEY))));
        wire.first.getSaveGuard().resetPassword(wire.accounts, "fixture-hash");
        assertFalse(complete(wire.jobs.submit(() -> HandbookActions.isAuthenticated(player, KEY))));
        assertNull(complete(wire.jobs.submit(player::getSessionKey)));
        unchangedShadow();
    }

    @Test
    void anOfflineUsernameLookupReturnsAWriteBoundOwnerSnapshot() throws Exception {
        var account =
                complete(
                        wire.jobs.submit(
                                () ->
                                        Grasscutter.getGameServer()
                                                .getAccountByName(cached.getUsername())));
        assertEquals(cached.getId(), account.getId());
        assertEquals(KEY, account.getSessionKey());
        account.setBanReason("looked up by name");
        assertDoesNotThrow(
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account))));
        assertEquals(
                "looked up by name",
                wire.accounts
                        .getDatabase()
                        .getCollection("accounts")
                        .find(new Document("_id", account.getId()))
                        .first()
                        .getString("banReason"));
        unchangedShadow();
    }

    @Test
    void anOlderResponseCannotReplaceANewerRefresh() throws Exception {
        var first = wire.jobs.submit(player::refreshAccount);
        waitSent(1);
        toOwner(0);
        wire.first.setSessionKey("new-owner-key");
        wire.first.getSaveGuard().save(wire.accounts);
        var second = wire.jobs.submit(player::refreshAccount);
        waitSent(2);
        toOwner(1);
        wire.toClient(1);
        var newer = second.get(1, TimeUnit.SECONDS);
        wire.toClient(0);
        assertNull(first.get(1, TimeUnit.SECONDS));
        assertSame(newer, player.getAccount());
        assertEquals("new-owner-key", player.getAccount().getSessionKey());
        unchangedShadow();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "client",
                "datastore",
                "mode",
                "session",
                "session-account",
                "explicit-account"
            })
    void changingTheReadContextBeforeTheReplyPreventsPublication(String change) throws Exception {
        var session = new GameSession(Grasscutter.getGameServer());
        session.setAccount(cached);
        session.setPlayer(player);
        var lookup = wire.jobs.submit(player::refreshAccount);
        waitSent(1);
        toOwner(0);
        switch (change) {
            case "client" -> Grasscutter.getGameServer()
                    .setDispatchClient(new DispatchClient(URI.create("ws://127.0.0.1:2")));
            case "datastore" -> field(DatabaseManager.class, "dispatchDatastore")
                    .set(null, wire.accounts);
            case "mode" -> Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
            case "session" -> player.setSession(new GameSession(Grasscutter.getGameServer()));
            case "session-account" -> session.setAccount(wire.second);
            case "explicit-account" -> player.setAccount(wire.second);
        }
        wire.toClient(0);
        assertNull(lookup.get(1, TimeUnit.SECONDS));
        assertSame(change.equals("explicit-account") ? wire.second : cached, player.getAccount());
        if (change.equals("session-account")) assertSame(wire.second, session.getAccount());
        unchangedShadow();
    }

    @Test
    void anUnavailableOwnerCannotAuthorizeFromTheLocalCache() throws Exception {
        var lookup = wire.jobs.submit(() -> HandbookActions.isAuthenticated(player, KEY));
        waitSent(1);
        wire.client.failPendingRequests(new IllegalStateException("Injected unavailable owner"));
        assertFalse(lookup.get(1, TimeUnit.SECONDS));
        assertSame(cached, player.getAccount());
        assertTrue(wire.client.getCallbacks().isEmpty());
        unchangedShadow();
    }

    @Test
    void anOwnerPermissionReadCannotUseTheLocalShadowGrant() throws Exception {
        assertTrue(wire.first.getPermissions().isEmpty(), "Owner fixture permissions");
        assertEquals(
                0,
                emu.grasscutter.config.Configuration.ACCOUNT.defaultPermissions.length,
                "Default fixture permissions");
        var stored =
                wire.accounts
                        .getDatabase()
                        .getCollection("accounts")
                        .find(new Document("_id", cached.getId()))
                        .first();
        assertTrue(
                stored.getList("permissions", String.class, java.util.List.of()).isEmpty(),
                "Stored owner fixture permissions");
        cached.addPermission("*");
        var granted =
                complete(
                        wire.jobs.submit(
                                () ->
                                        new DefaultPermissionHandler()
                                                .checkPermission(
                                                        player, player, "fixture.command", "")));
        var ownerReply = IDispatcher.decode(wire.packet(wire.serverPackets.get(0)).get("message"));
        assertEquals(KEY, ownerReply.get("sessionKey").getAsString(), "Owner response source");
        assertEquals(
                "[]",
                ownerReply.getAsJsonArray("permissions").toString(),
                "Owner response permissions");
        assertFalse(granted);
        unchangedShadow();
    }

    @Test
    void aMissingUsernameHasNoLocalDatabaseFallback() throws Exception {
        assertNull(
                complete(
                        wire.jobs.submit(
                                () -> DispatchUtils.getAccountByName("missing-owner-user"))));
        unchangedShadow();
    }
}

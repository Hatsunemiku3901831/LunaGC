package emu.grasscutter.game;

import com.google.gson.JsonObject;

import dev.morphia.Datastore;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.server.dispatch.*;

/** One authoritative account read, pinned to its mode, datastore and connection generation. */
public final class AccountLookup {
    private final ServerRunMode mode;
    private final Datastore source;
    private final DispatchClient client;
    private final Object connection;

    private AccountLookup(ServerRunMode mode, Datastore source, DispatchClient client) {
        this.mode = mode;
        this.source = source;
        this.client = client;
        this.connection = client == null ? null : client.accountConnection();
    }

    public static AccountLookup capture() {
        var mode = Grasscutter.getRunMode();
        var source = DatabaseManager.getAccountDatastore();
        var server = Grasscutter.getGameServer();
        return new AccountLookup(
                mode,
                source,
                mode == ServerRunMode.GAME_ONLY && server != null
                        ? server.getDispatchClient()
                        : null);
    }

    public boolean isCurrent() {
        if (mode != Grasscutter.getRunMode() || source != DatabaseManager.getAccountDatastore())
            return false;
        var server = Grasscutter.getGameServer();
        return mode != ServerRunMode.GAME_ONLY
                || server != null
                        && client != null
                        && client == server.getDispatchClient()
                        && connection != null
                        && connection == client.accountConnection();
    }

    public Account byId(String id) {
        return read("accountId", id);
    }

    Object authorityIdentity() {
        return mode == ServerRunMode.GAME_ONLY ? connection : source;
    }

    Datastore localAuthority() {
        return mode == ServerRunMode.GAME_ONLY ? null : source;
    }

    public Account byName(String username) {
        return read("username", username);
    }

    private Account read(String field, String value) {
        if (value == null || value.isBlank() || source == null || !isCurrent()) return null;
        if (mode != ServerRunMode.GAME_ONLY) {
            var account =
                    source.find(Account.class)
                            .filter(
                                    Filters.eq(
                                            field.equals("accountId") ? "_id" : "username", value))
                            .first();
            return isCurrent() ? account : null;
        }
        if (client == null) return null;
        var request = new JsonObject();
        request.addProperty(field, value);
        return client.await(
                request,
                PacketIds.GetAccountReq,
                PacketIds.GetAccountRsp,
                packet -> {
                    var account = IDispatcher.decode(packet, Account.class);
                    if (!isCurrent()
                            || account == null
                            || !value.equals(
                                    field.equals("accountId")
                                            ? account.getId()
                                            : account.getUsername())) return null;
                    return AccountRemoteStorage.bind(account, source, client, connection);
                });
    }
}

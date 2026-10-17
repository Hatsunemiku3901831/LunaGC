package emu.grasscutter.game;

import com.google.gson.*;

import dev.morphia.Datastore;

import emu.grasscutter.server.dispatch.*;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Revision-checked account storage on the dispatch client that supplied the snapshot. */
public final class AccountRemoteStorage implements AccountSaveGuard.Storage {
    private static final JsonWriterSettings BSON_JSON =
            JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
    private final DispatchClient origin;
    private final Duration timeout;
    private final Object connection;

    private AccountRemoteStorage(DispatchClient origin, Object connection, Duration timeout) {
        this.origin = Objects.requireNonNull(origin, "Account owner client");
        this.connection = Objects.requireNonNull(connection, "Account owner connection");
        this.timeout = Objects.requireNonNull(timeout, "Request timeout");
        if (timeout.isNegative() || timeout.isZero() || timeout.toNanos() <= 0)
            throw new IllegalArgumentException("Positive request timeout required");
    }

    public static Account bind(Account account, Datastore codec, DispatchClient origin) {
        return bind(account, codec, origin, origin.accountConnection());
    }

    public static Account bind(
            Account account, Datastore codec, DispatchClient origin, Object connection) {
        return bind(account, codec, origin, connection, Duration.ofSeconds(5));
    }

    public static Account bind(
            Account account, Datastore codec, DispatchClient origin, Duration timeout) {
        return bind(account, codec, origin, origin.accountConnection(), timeout);
    }

    private static Account bind(
            Account account,
            Datastore codec,
            DispatchClient origin,
            Object connection,
            Duration timeout) {
        if (account == null) return null;
        Objects.requireNonNull(codec, "Account codec");
        var storage = new AccountRemoteStorage(origin, connection, timeout);
        synchronized (account) {
            account.bindRemoteSaveState(codec.getMapper().toDocument(account), codec, storage);
        }
        return account;
    }

    private static String text(JsonObject object, String field) {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string field " + field);
        return value.getAsString();
    }

    private static void keys(JsonObject object, String... names) {
        if (!object.keySet().equals(Set.of(names)))
            throw new IllegalArgumentException("Unexpected account storage fields");
    }

    private JsonObject request(String operation, String id) {
        var data = new JsonObject();
        data.addProperty("operation", operation);
        data.addProperty("accountId", id);
        return data;
    }

    private JsonObject exchange(JsonObject data) {
        if (origin.accountConnection() != connection)
            throw new IllegalStateException("Account owner connection changed");
        if (Thread.currentThread().isInterrupted())
            throw new CompletionException(
                    new InterruptedException("Account owner request interrupted"));
        var future =
                origin.asyncOnConnection(
                        connection,
                        data,
                        PacketIds.AccountStorageReq,
                        PacketIds.AccountStorageRsp,
                        IDispatcher.DEFAULT_PARSER,
                        timeout);
        final JsonObject reply;
        try {
            reply = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw new CompletionException(interrupted);
        } catch (ExecutionException failed) {
            throw new CompletionException(failed.getCause());
        }
        if (origin.accountConnection() != connection)
            throw new IllegalStateException("Account owner connection changed");
        if (reply == null
                || !reply.has("ok")
                || !reply.get("ok").isJsonPrimitive()
                || !reply.get("ok").getAsJsonPrimitive().isBoolean()
                || !reply.get("ok").getAsBoolean())
            throw new IllegalStateException("Account owner did not confirm the operation");
        return reply;
    }

    @Override
    public Document read(String id) {
        var reply = exchange(request("read", id));
        if (!reply.has("row")) throw new IllegalStateException("Missing account read result");
        if (reply.get("row").isJsonNull()) return null;
        var row = Document.parse(text(reply, "row"));
        if (!id.equals(row.get("_id")))
            throw new IllegalStateException("Account read identity differs");
        AccountSaveStamp.from(row);
        return row;
    }

    @Override
    public void insert(Document document) {
        throw new UnsupportedOperationException("Remote account saves cannot insert accounts");
    }

    private boolean write(String operation, Document filter, String field, Document document) {
        var data = request(operation, filter.getString("_id"));
        data.addProperty("filter", filter.toJson(BSON_JSON));
        data.addProperty(field, document.toJson(BSON_JSON));
        var reply = exchange(data);
        var modified = reply.get("modified");
        if (modified == null
                || !modified.isJsonPrimitive()
                || !modified.getAsJsonPrimitive().isBoolean())
            throw new IllegalStateException("Missing account write result");
        return modified.getAsBoolean();
    }

    @Override
    public boolean replace(Document filter, Document document) {
        return write("replace", filter, "document", document);
    }

    @Override
    public boolean advance(Document filter, AccountSaveStamp stamp) {
        return write("advance", filter, "stamp", stamp.document());
    }

    /** Called only by the authenticated internal dispatch handler, with its captured datastore. */
    public static JsonObject serve(Datastore owner, JsonObject request) {
        Objects.requireNonNull(owner, "Account owner datastore");
        String operation = text(request, "operation"), id = text(request, "accountId");
        if (id.isBlank()) throw new IllegalArgumentException("Account ID required");
        var storage = new AccountSaveGuard.MongoStorage(owner);
        var response = new JsonObject();
        if (operation.equals("read")) {
            keys(request, "operation", "accountId");
            var row = storage.read(id);
            response.add(
                    "row",
                    row == null ? JsonNull.INSTANCE : new JsonPrimitive(row.toJson(BSON_JSON)));
        } else {
            boolean replace = operation.equals("replace");
            if (!replace && !operation.equals("advance"))
                throw new IllegalArgumentException("Unsupported account storage operation");
            keys(request, "operation", "accountId", "filter", replace ? "document" : "stamp");
            var filter = Document.parse(text(request, "filter"));
            var before = before(id, filter);
            var document = Document.parse(text(request, replace ? "document" : "stamp"));
            var after =
                    replace
                            ? AccountSaveStamp.from(document)
                            : AccountSaveStamp.from(new Document("saveStamp", document));
            if (after == null || (replace && !id.equals(document.get("_id"))))
                throw new IllegalArgumentException("Account write identity or stamp differs");
            var stamp = after.document();
            long expected =
                    before == null ? 1 : Math.addExact(before.document().getLong("revision"), 1);
            if (stamp.getLong("revision") != expected
                    || (before != null
                            && stamp.getString("token")
                                    .equals(before.document().getString("token"))))
                throw new IllegalArgumentException(
                        "Account revision must advance once with a fresh token");
            response.addProperty(
                    "modified",
                    replace ? storage.replace(filter, document) : storage.advance(filter, after));
        }
        response.addProperty("ok", true);
        return response;
    }

    private static AccountSaveStamp before(String id, Document filter) {
        if (!id.equals(filter.get("_id")))
            throw new IllegalArgumentException("Account filter identity differs");
        if (filter.keySet().equals(Set.of("_id", "saveStamp")) && filter.get("saveStamp") == null)
            return null;
        if (!filter.keySet().equals(Set.of("_id", "saveStamp.revision", "saveStamp.token")))
            throw new IllegalArgumentException("Exact account version filter required");
        return AccountSaveStamp.from(
                new Document(
                        "saveStamp",
                        new Document("revision", filter.get("saveStamp.revision"))
                                .append("token", filter.get("saveStamp.token"))));
    }
}

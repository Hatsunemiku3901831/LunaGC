package emu.grasscutter.server.dispatch;

import static emu.grasscutter.server.dispatch.DispatchPacketFlowTest.field;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.AccountRemoteStorage;
import emu.grasscutter.utils.DispatchUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real RPC handlers and separate Mongo stores; transport delivery is controlled by the test. */
class DispatchRemoteAccountTest {
    private DispatchPacketFlowTest wire;
    private int sent, received;
    private Document localBefore;

    @BeforeEach
    void setup() throws Exception {
        wire = new DispatchPacketFlowTest();
        wire.setup();
        localBefore = row(wire.first.getId());
        localBefore.put("username", "local-shadow-account");
        localBefore.put("banReason", "local-only");
        wire.game.getDatabase().getCollection("accounts").insertOne(localBefore);
        field(DatabaseManager.class, "dispatchDatastore").set(null, wire.game);
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
    }

    @AfterEach
    void close() throws Exception {
        if (wire != null) wire.close();
    }

    private Document row(String id) {
        return wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .find(new Document("_id", id))
                .first();
    }

    private void deliver() throws Exception {
        while (sent < wire.engine.sent.size()) {
            toOwner(sent++);
        }
        while (received < wire.serverPackets.size()) wire.toClient(received++);
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (wire.engine.sent.size() < count && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(count, wire.engine.sent.size());
    }

    private Account shortDeadlineSnapshot() {
        var account = IDispatcher.decode(IDispatcher.JSON.toJsonTree(wire.first), Account.class);
        return AccountRemoteStorage.bind(account, wire.game, wire.client, Duration.ofMillis(500));
    }

    private void timedOut(Future<?> operation) {
        var failed =
                assertThrows(ExecutionException.class, () -> operation.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, failed.getCause().getCause());
        assertTrue(wire.client.getCallbacks().isEmpty());
    }

    private <T> T complete(Future<T> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!future.isDone() && System.nanoTime() < deadline) {
            deliver();
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private Account lookup() throws Exception {
        return complete(wire.jobs.submit(() -> DispatchUtils.getAccountById(wire.first.getId())));
    }

    private void save(Account account) throws Exception {
        assertDoesNotThrow(
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account))));
    }

    private void unchangedLocal() {
        assertEquals(
                localBefore,
                wire.game
                        .getDatabase()
                        .getCollection("accounts")
                        .find(new Document("_id", wire.first.getId()))
                        .first());
    }

    @Test
    void lookupSnapshotSavesOnTheAccountOwnerAndPreservesTheLocalShadow() throws Exception {
        var account = lookup();
        String reason = "quoted \"value\" at C:\\fixture\\file \\n 中文";
        account.setBanReason(reason);
        save(account);
        assertEquals(reason, row(account.getId()).getString("banReason"));
        unchangedLocal();
        assertTrue(wire.client.getCallbacks().isEmpty());
    }

    @Test
    void aValidatedLoginSnapshotAlsoHasAnAuthoritativeSavePath() throws Exception {
        var account =
                complete(wire.jobs.submit(() -> wire.authenticate(wire.first, "fixture-token-a")));
        assertNotNull(account);
        account.setBanReason("login snapshot");
        save(account);
        assertEquals("login snapshot", row(account.getId()).getString("banReason"));
        unchangedLocal();
    }

    @Test
    void aNewerRemoteSavePermanentlyRejectsTheOldSnapshot() throws Exception {
        var old = lookup();
        var fresh = lookup();
        fresh.setBanReason("fresh");
        save(fresh);
        old.setBanReason("stale");
        assertThrows(
                ExecutionException.class,
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(old))));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveAccountSync(old));
        assertEquals("fresh", row(old.getId()).getString("banReason"));
        unchangedLocal();
    }

    @Test
    void deletingTheOwnerRowCannotTurnASaveIntoAnInsert() throws Exception {
        var account = lookup();
        wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .deleteOne(new Document("_id", account.getId()));
        assertThrows(
                ExecutionException.class,
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account))));
        assertNull(row(account.getId()));
        unchangedLocal();
    }

    @Test
    void changingTheSnapshotIdIsRejectedBeforeSending() throws Exception {
        var account = lookup();
        int count = wire.engine.sent.size();
        account.setId(wire.second.getId());
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveAccountSync(account));
        assertEquals(count, wire.engine.sent.size());
        unchangedLocal();
    }

    @Test
    void changingTheLocalDatastoreCannotRebindAnExistingRemoteSnapshot() throws Exception {
        var account = lookup();
        int count = wire.engine.sent.size();
        field(DatabaseManager.class, "dispatchDatastore").set(null, wire.accounts);
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveAccountSync(account));
        assertEquals(count, wire.engine.sent.size());
        unchangedLocal();
    }

    @Test
    void replacingTheGlobalClientCannotRedirectAnExistingSnapshot() throws Exception {
        var account = lookup();
        var replacement = new DispatchClient(URI.create("ws://127.0.0.1:2"));
        Grasscutter.getGameServer().setDispatchClient(replacement);
        account.setBanReason("original owner");
        save(account);
        assertEquals("original owner", row(account.getId()).getString("banReason"));
        assertTrue(replacement.getCallbacks().isEmpty());
        unchangedLocal();
    }

    @Test
    void aLegacyOwnerRowCanBeVersionedByItsFirstConfirmedRemoteSave() throws Exception {
        wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .updateOne(
                        new Document("_id", wire.first.getId()),
                        new Document("$unset", new Document("saveStamp", 1)));
        var account = lookup();
        account.setBanReason("legacy saved");
        save(account);
        assertEquals(1L, row(account.getId()).get("saveStamp", Document.class).getLong("revision"));
        unchangedLocal();
    }

    @Test
    void aPlainJsonCopyStillCannotAcquireAWriteBinding() throws Exception {
        var account = lookup();
        var copy = IDispatcher.decode(IDispatcher.JSON.toJsonTree(account), Account.class);
        int count = wire.engine.sent.size();
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveAccountSync(copy));
        assertEquals(count, wire.engine.sent.size());
        unchangedLocal();
    }

    @Test
    void anOwnerWriteWithALostReplyIsRecognizedBeforeTheNextSave() throws Exception {
        var account = shortDeadlineSnapshot();
        long before = row(account.getId()).get("saveStamp", Document.class).getLong("revision");
        account.setBanReason("written but no reply");
        var operation = wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account));
        waitSent(1);
        toOwner(sent++);
        received =
                wire.serverPackets.size(); // Drop the acknowledgement, after the real Mongo write.
        timedOut(operation);
        assertEquals("written but no reply", row(account.getId()).getString("banReason"));
        account.setBanReason("next intent");
        save(account);
        assertEquals("next intent", row(account.getId()).getString("banReason"));
        assertEquals(
                before + 2,
                row(account.getId()).get("saveStamp", Document.class).getLong("revision"));
        wire.toClient(0); // The late old acknowledgement cannot complete another operation.
        assertTrue(wire.client.getCallbacks().isEmpty());
        unchangedLocal();
    }

    @Test
    void anUndeliveredWriteIsRetiredBeforeANewIntentAndCannotArriveLate() throws Exception {
        var account = shortDeadlineSnapshot();
        var before = row(account.getId());
        account.setBanReason("late old intent");
        var operation = wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account));
        waitSent(1);
        int delayed = sent++; // Keep the request bytes, without delivering them.
        timedOut(operation);
        assertEquals(before, row(account.getId()));
        account.setBanReason("new intent");
        save(account);
        var confirmed = row(account.getId());
        assertEquals("new intent", confirmed.getString("banReason"));
        assertEquals(
                before.get("saveStamp", Document.class).getLong("revision") + 2,
                confirmed.get("saveStamp", Document.class).getLong("revision"));
        toOwner(delayed);
        var reply =
                IDispatcher.decode(
                        wire.packet(wire.serverPackets.get(wire.serverPackets.size() - 1))
                                .get("message"));
        assertTrue(reply.get("ok").getAsBoolean());
        assertFalse(reply.get("modified").getAsBoolean());
        assertEquals(confirmed, row(account.getId()));
        unchangedLocal();
    }

    @Test
    void anUnavailableReadKeepsTheUnconfirmedAttemptRecoverable() throws Exception {
        var account = shortDeadlineSnapshot();
        var operation = wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account));
        waitSent(1);
        int delayed = sent++;
        timedOut(operation);
        var handler = wire.server.getHandlers().get(PacketIds.AccountStorageReq);
        wire.server
                .getHandlers()
                .put(
                        PacketIds.AccountStorageReq,
                        (peer, data) -> {
                            var reply = new JsonObject();
                            reply.addProperty("ok", false);
                            wire.server.sendMessage(peer, PacketIds.AccountStorageRsp, reply);
                        });
        assertThrows(
                ExecutionException.class,
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account))));
        wire.server.getHandlers().put(PacketIds.AccountStorageReq, handler);
        account.setBanReason("recovered after outage");
        save(account);
        var confirmed = row(account.getId());
        toOwner(delayed);
        assertEquals(confirmed, row(account.getId()));
        assertEquals("recovered after outage", confirmed.getString("banReason"));
        unchangedLocal();
    }

    @Test
    void aForeignVersionDuringReconciliationCannotBeAdopted() throws Exception {
        var account = shortDeadlineSnapshot();
        var operation = wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account));
        waitSent(1);
        int delayed = sent++;
        timedOut(operation);
        wire.first.setBanReason("another writer");
        wire.first.getSaveGuard().save(wire.accounts);
        var fresh = row(account.getId());
        assertThrows(
                ExecutionException.class,
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account))));
        assertThrows(IllegalStateException.class, () -> DatabaseHelper.saveAccountSync(account));
        toOwner(delayed);
        assertEquals(fresh, row(account.getId()));
        unchangedLocal();
    }

    @Test
    void recreatedRowsHaveADifferentStampAndRejectTheOldSnapshot() throws Exception {
        var account = lookup();
        var recreated = row(account.getId());
        recreated.put("saveStamp", row(wire.second.getId()).get("saveStamp"));
        recreated.put("banReason", "recreated account");
        wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .replaceOne(new Document("_id", account.getId()), recreated);
        assertThrows(
                ExecutionException.class,
                () -> complete(wire.jobs.submit(() -> DatabaseHelper.saveAccountSync(account))));
        assertEquals(recreated, row(account.getId()));
        unchangedLocal();
    }

    @Test
    void asynchronousAccountSavesUseTheSameRemoteBinding() throws Exception {
        var account = lookup();
        account.setBanReason("queued remote save");
        DatabaseHelper.saveAccountAsync(account);
        assertTrue(
                complete(wire.jobs.submit(() -> DatabaseHelper.awaitIdle(Duration.ofSeconds(3)))));
        assertEquals("queued remote save", row(account.getId()).getString("banReason"));
        unchangedLocal();
    }

    @Test
    void anExistingBindingCannotBeReplacedWithAnotherClient() throws Exception {
        var account = lookup();
        assertThrows(
                IllegalStateException.class,
                () -> AccountRemoteStorage.bind(account, wire.game, wire.client));
        account.setBanReason("binding retained");
        save(account);
        assertEquals("binding retained", row(account.getId()).getString("banReason"));
    }

    @Test
    void int64VersionsStayExactAndStopAtTheMaximum() throws Exception {
        wire.accounts
                .getDatabase()
                .getCollection("accounts")
                .updateOne(
                        new Document("_id", wire.first.getId()),
                        new Document(
                                "$set", new Document("saveStamp.revision", Long.MAX_VALUE - 1)));
        var account = lookup();
        save(account);
        assertEquals(
                Long.MAX_VALUE,
                row(account.getId()).get("saveStamp", Document.class).getLong("revision"));
        int count = wire.engine.sent.size();
        assertThrows(ArithmeticException.class, () -> DatabaseHelper.saveAccountSync(account));
        assertEquals(count, wire.engine.sent.size());
        unchangedLocal();
    }

    @Test
    void anAccountLookupRejectsAReplyContainingAnotherAccount() throws Exception {
        var request = wire.jobs.submit(() -> DispatchUtils.getAccountById(wire.first.getId()));
        waitSent(1);
        toOwner(sent++);
        var response = wire.packet(wire.serverPackets.get(0));
        response.addProperty("message", IDispatcher.JSON.toJson(wire.second));
        wire.client.onMessage(ByteBuffer.wrap(DispatchPacketFlowTest.encode(response)));
        received++;
        assertNull(request.get(2, TimeUnit.SECONDS));
        assertTrue(wire.client.getCallbacks().isEmpty());
        unchangedLocal();
    }

    @Test
    void anAlreadyInterruptedSaveDoesNotSendAndKeepsItsInterruptFlag() throws Exception {
        var account = shortDeadlineSnapshot();
        var interrupted = new AtomicBoolean();
        var failed =
                wire.jobs.submit(
                        () -> {
                            Thread.currentThread().interrupt();
                            try {
                                DatabaseHelper.saveAccountSync(account);
                                return null;
                            } catch (RuntimeException failure) {
                                interrupted.set(Thread.currentThread().isInterrupted());
                                return failure;
                            } finally {
                                Thread.interrupted();
                            }
                        });
        assertNotNull(failed.get(2, TimeUnit.SECONDS));
        assertTrue(interrupted.get());
        assertTrue(wire.engine.sent.isEmpty());
        assertTrue(wire.client.getCallbacks().isEmpty());
        unchangedLocal();
    }

    @Test
    void interruptingAWaitingSaveCancelsItsCallbackAndKeepsThePendingAttempt() throws Exception {
        var account = shortDeadlineSnapshot();
        var waiter = new AtomicReference<Thread>();
        var interrupted = new AtomicBoolean();
        var failed =
                wire.jobs.submit(
                        () -> {
                            waiter.set(Thread.currentThread());
                            try {
                                DatabaseHelper.saveAccountSync(account);
                                return null;
                            } catch (RuntimeException failure) {
                                interrupted.set(Thread.currentThread().isInterrupted());
                                return failure;
                            } finally {
                                Thread.interrupted();
                            }
                        });
        waitSent(1);
        int delayed = sent++;
        waiter.get().interrupt();
        assertInstanceOf(InterruptedException.class, failed.get(2, TimeUnit.SECONDS).getCause());
        assertTrue(interrupted.get());
        assertTrue(wire.client.getCallbacks().isEmpty());
        account.setBanReason("after interrupted wait");
        save(account);
        var confirmed = row(account.getId());
        toOwner(delayed);
        assertEquals(confirmed, row(account.getId()));
        unchangedLocal();
    }

    private JsonObject storageRequest(String id, Document filter, Document document) {
        var request = new JsonObject();
        request.addProperty("operation", "replace");
        request.addProperty("accountId", id);
        var bson =
                org.bson.json.JsonWriterSettings.builder()
                        .outputMode(org.bson.json.JsonMode.EXTENDED)
                        .build();
        request.addProperty("filter", filter.toJson(bson));
        request.addProperty("document", document.toJson(bson));
        return request;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-version",
                "extra-filter",
                "different-filter-id",
                "numeric-filter-id",
                "int32-revision",
                "non-uuid",
                "missing-after",
                "reused-token",
                "skipped-revision",
                "different-document-id",
                "insert",
                "extra-field",
                "malformed-bson",
                "negative-revision"
            })
    void theOwnerRejectsMalformedOrUnconditionalWritesWithoutMutation(String invalid) {
        var original = row(wire.first.getId());
        var stamp = original.get("saveStamp", Document.class);
        var filter =
                new Document("_id", wire.first.getId())
                        .append("saveStamp.revision", stamp.getLong("revision"))
                        .append("saveStamp.token", stamp.getString("token"));
        var document = new Document(original);
        var after =
                new Document("revision", stamp.getLong("revision") + 1)
                        .append("token", UUID.randomUUID().toString());
        document.put("saveStamp", after);
        switch (invalid) {
            case "missing-version" -> {
                filter.remove("saveStamp.revision");
                filter.remove("saveStamp.token");
            }
            case "extra-filter" -> filter.put("$or", java.util.List.of(new Document()));
            case "different-filter-id" -> filter.put("_id", wire.second.getId());
            case "numeric-filter-id" -> filter.put("_id", 1);
            case "int32-revision" -> filter.put("saveStamp.revision", 2);
            case "non-uuid" -> filter.put("saveStamp.token", "not-a-write-token");
            case "missing-after" -> document.remove("saveStamp");
            case "reused-token" -> after.put("token", stamp.getString("token"));
            case "skipped-revision" -> after.put("revision", stamp.getLong("revision") + 2);
            case "different-document-id" -> document.put("_id", wire.second.getId());
            case "negative-revision" -> after.put("revision", -1L);
        }
        var request = storageRequest(wire.first.getId(), filter, document);
        switch (invalid) {
            case "insert" -> request.addProperty("operation", "insert");
            case "extra-field" -> request.addProperty("upsert", true);
            case "malformed-bson" -> request.addProperty("document", "{broken");
        }
        assertThrows(
                RuntimeException.class, () -> AccountRemoteStorage.serve(wire.accounts, request));
        assertEquals(original, row(wire.first.getId()));
        unchangedLocal();
    }
}

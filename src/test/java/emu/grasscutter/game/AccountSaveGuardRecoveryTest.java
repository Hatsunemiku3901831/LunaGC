package emu.grasscutter.game;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.Grasscutter;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

/** Wrap actual Mongo CAS operations, retaining the original filters for delayed writes. */
class AccountSaveGuardRecoveryTest {
    private static final String ID = "recovery-fixture";
    private MongoClient client;
    private Datastore store;
    private FaultStorage fault;

    @BeforeEach
    void setup() {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_account_recovery_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(Account.class);
        fault = new FaultStorage(new AccountSaveGuard.MongoStorage(store));
        rows().insertOne(
                        new Document("_id", ID)
                                .append("username", ID)
                                .append("token", "original-token")
                                .append("permissions", List.of("original.permission"))
                                .append("saveStamp", AccountSaveStamp.next(null).document()));
    }

    @AfterEach
    void close() {
        if (client != null) {
            store.getDatabase().drop();
            client.close();
        }
    }

    private MongoCollection<Document> rows() {
        return store.getDatabase().getCollection("accounts");
    }

    private Document row() {
        return rows().find(new Document("_id", ID)).first();
    }

    private Account read() {
        return store.find(Account.class).filter(Filters.eq("_id", ID)).first();
    }

    private Account instrument() {
        var account = read();
        account.installSaveGuard(new AccountSaveGuard(account, row(), store, ignored -> fault));
        return account;
    }

    private void save(Account account) {
        account.getSaveGuard().save(store);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lostAcknowledgementRecognizesOnlyTheOriginalAccountWrite(boolean committed) {
        var account = instrument();
        account.setToken("first-token");
        fault.next = committed ? Fault.AFTER : Fault.BEFORE;
        assertThrows(IllegalStateException.class, () -> save(account));
        assertEquals(committed ? "first-token" : "original-token", row().getString("token"));
        account.setToken("second-token");
        save(account);
        assertEquals("second-token", row().getString("token"));
        assertEquals(committed ? 0 : 1, fault.advances);
        assertEquals(3L, row().get("saveStamp", Document.class).getLong("revision"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainMetadataAdvanceCannotLetTheOriginalLateReplacementWin(boolean committed) {
        var account = instrument();
        account.setToken("first-token");
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(account));
        account.setToken("second-token");
        fault.nextAdvance = committed ? Fault.AFTER : Fault.BEFORE;
        assertThrows(IllegalStateException.class, () -> save(account));
        assertEquals("original-token", row().getString("token"));
        save(account);
        var confirmed = row();
        assertFalse(fault.flush());
        assertEquals(confirmed, row());
        assertEquals("second-token", row().getString("token"));
    }

    @Test
    void anOriginalWinningJustBeforeMetadataAdvanceIsRecognized() {
        var account = instrument();
        account.setToken("first-token");
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(account));
        account.setToken("second-token");
        fault.beforeAdvance = () -> assertTrue(fault.flush());
        save(account);
        assertEquals("second-token", row().getString("token"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aForeignRevisionDuringUncertaintyCannotBeAdopted(boolean committed) {
        var account = instrument();
        account.setToken("uncertain-token");
        fault.next = committed ? Fault.AFTER : Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(account));
        var foreign = read();
        foreign.setPassword("new-credential-fixture");
        foreign.setToken("foreign-token");
        save(foreign);
        var confirmed = row();
        assertThrows(IllegalStateException.class, () -> save(account));
        assertThrows(IllegalStateException.class, () -> save(account));
        if (!committed) assertFalse(fault.flush());
        assertEquals(confirmed, row());
    }

    @Test
    void unreadablePendingStateNeverPermitsAnotherWrite() {
        var account = instrument();
        fault.next = Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(account));
        int writes = fault.writes;
        var before = row();
        fault.unreadable = true;
        assertThrows(IllegalStateException.class, () -> save(account));
        assertEquals(writes, fault.writes);
        assertEquals(before, row());
        fault.unreadable = false;
        save(account);
        assertFalse(fault.flush());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletionDuringAnUncertainWriteCannotRecreateTheAccount(boolean committed) {
        var account = instrument();
        fault.next = committed ? Fault.AFTER : Fault.DEFER;
        assertThrows(IllegalStateException.class, () -> save(account));
        rows().deleteOne(new Document("_id", ID));
        assertThrows(IllegalStateException.class, () -> save(account));
        if (!committed) assertFalse(fault.flush());
        assertNull(row());
    }

    @Test
    void twoIndependentLoadedCopiesHaveOnlyOneWinner() throws Exception {
        var a = read();
        var b = read();
        a.setToken("a-token");
        b.setToken("b-token");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (var account : List.of(a, b))
                futures.add(
                        pool.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    try {
                                        save(account);
                                        return true;
                                    } catch (IllegalStateException conflict) {
                                        return false;
                                    }
                                }));
            start.countDown();
            assertNotEquals(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));
            assertTrue(Set.of("a-token", "b-token").contains(row().getString("token")));
            assertEquals(2L, row().get("saveStamp", Document.class).getLong("revision"));
        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"int-revision", "zero", "negative", "bad-token", "extra", "string"})
    void malformedStoredSaveMetadataIsRejectedWithoutMutation(String kind) {
        Object stamp =
                switch (kind) {
                    case "int-revision" -> new Document("revision", 1)
                            .append("token", UUID.randomUUID().toString());
                    case "zero" -> new Document("revision", 0L)
                            .append("token", UUID.randomUUID().toString());
                    case "negative" -> new Document("revision", -1L)
                            .append("token", UUID.randomUUID().toString());
                    case "bad-token" -> new Document("revision", 1L).append("token", "invalid");
                    case "extra" -> new Document("revision", 1L)
                            .append("token", UUID.randomUUID().toString())
                            .append("unexpected", true);
                    default -> "invalid";
                };
        rows().updateOne(
                        new Document("_id", ID),
                        new Document("$set", new Document("saveStamp", stamp)));
        var before = row();
        assertThrows(RuntimeException.class, this::read);
        assertEquals(before, row());
    }

    @Test
    void revisionExhaustionCannotWrapOrWriteAnAccount() {
        rows().updateOne(
                        new Document("_id", ID),
                        new Document(
                                "$set",
                                new Document(
                                        "saveStamp",
                                        new Document("revision", Long.MAX_VALUE)
                                                .append("token", UUID.randomUUID().toString()))));
        var account = read();
        var before = row();
        account.setToken("overflow-token");
        assertThrows(ArithmeticException.class, () -> save(account));
        assertEquals(before, row());
    }

    enum Fault {
        NONE,
        BEFORE,
        AFTER,
        DEFER
    }

    static final class FaultStorage implements AccountSaveGuard.Storage {
        final AccountSaveGuard.Storage delegate;
        Fault next = Fault.NONE, nextAdvance = Fault.NONE;
        Document delayedFilter, delayedDocument;
        int writes, advances;
        boolean unreadable;
        Runnable beforeAdvance;

        FaultStorage(AccountSaveGuard.Storage delegate) {
            this.delegate = delegate;
        }

        public Document read(String id) {
            if (unreadable) throw new IllegalStateException("Injected account read failure");
            return delegate.read(id);
        }

        public void insert(Document document) {
            throw new AssertionError("Ordinary saves cannot insert");
        }

        public boolean replace(Document filter, Document document) {
            writes++;
            var fault = next;
            next = Fault.NONE;
            if (fault == Fault.DEFER) {
                delayedFilter = filter;
                delayedDocument = document;
            }
            if (fault == Fault.BEFORE || fault == Fault.DEFER)
                throw new IllegalStateException("Injected before account replacement");
            boolean result = delegate.replace(filter, document);
            if (fault == Fault.AFTER)
                throw new IllegalStateException("Injected lost account acknowledgement");
            return result;
        }

        public boolean advance(Document filter, AccountSaveStamp stamp) {
            advances++;
            if (beforeAdvance != null) {
                beforeAdvance.run();
                beforeAdvance = null;
            }
            var fault = nextAdvance;
            nextAdvance = Fault.NONE;
            if (fault == Fault.BEFORE)
                throw new IllegalStateException("Injected before account metadata advance");
            boolean result = delegate.advance(filter, stamp);
            if (fault == Fault.AFTER)
                throw new IllegalStateException("Injected lost account metadata acknowledgement");
            return result;
        }

        boolean flush() {
            return delegate.replace(delayedFilter, delayedDocument);
        }
    }
}

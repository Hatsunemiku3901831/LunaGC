package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Indexes.ascending;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;

import dev.morphia.Datastore;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.GameConstants;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;

import org.bson.BsonType;
import org.bson.Document;

import java.util.*;
import java.util.function.Supplier;

/** Durable UID ownership, independent of whether the first Player insert has completed. */
public final class PlayerUidAllocator {
    private static final int MAX_PROBES = 10000;
    private static final String CLAIMS = "player_uid_reservations";
    private static final String OWNERS = "account_uid_owners";

    /** Only this allocator can construct a ticket; both datastore destinations remain pinned. */
    public static final class Reservation {
        private final Context context;
        private final int uid;
        private final String accountId, ownerId, operationId, kind;

        private Reservation(Context context, Document row, String accountId) {
            validateClaim(row);
            this.context = context;
            this.uid = PlayerUidAllocator.uid(row.get("_id"));
            this.ownerId = row.getString("ownerId");
            this.operationId = row.getString("operationId");
            this.kind = row.getString("kind");
            this.accountId = accountId;
        }

        public int uid() {
            return uid;
        }
    }

    private PlayerUidAllocator() {}

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    private static boolean uuid(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            return UUID.fromString(text).toString().equals(text);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static int uid(Object value) {
        require(
                (value instanceof Integer || value instanceof Long)
                        && ((Number) value).longValue() > 0
                        && ((Number) value).longValue() <= Integer.MAX_VALUE,
                "Invalid player UID");
        int result = ((Number) value).intValue();
        require(
                result != GameConstants.SERVER_CONSOLE_UID,
                "The console UID cannot belong to a player");
        return result;
    }

    private static void validateClaim(Document row) {
        require(
                row != null
                        && uuid(row.get("ownerId"))
                        && uuid(row.get("operationId"))
                        && ("account".equals(row.get("kind"))
                                || "anonymous".equals(row.get("kind"))),
                "Invalid UID reservation");
        uid(row.get("_id"));
    }

    private static boolean duplicate(MongoWriteException error) {
        return error.getError() != null && error.getError().getCode() == 11000;
    }

    private static final class Context {
        final Datastore game, accounts;

        Context(Datastore game, Datastore accounts) {
            this.game = Objects.requireNonNull(game);
            this.accounts = Objects.requireNonNull(accounts);
        }

        MongoCollection<Document> rows(Datastore store, String name) {
            return store.getDatabase()
                    .getCollection(name)
                    .withReadPreference(ReadPreference.primary())
                    .withReadConcern(ReadConcern.MAJORITY)
                    .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        }

        MongoCollection<Document> players() {
            return rows(game, "players");
        }

        MongoCollection<Document> accountRows() {
            return rows(accounts, "accounts");
        }

        MongoCollection<Document> claims() {
            var rows = rows(game, CLAIMS);
            rows.createIndex(ascending("ownerId"), new IndexOptions().unique(true));
            return rows;
        }

        MongoCollection<Document> owners() {
            var rows = rows(accounts, OWNERS);
            rows.createIndex(ascending("ownerId"), new IndexOptions().unique(true));
            return rows;
        }

        Document account(String id) {
            require(id != null && !id.isBlank(), "An account ID is required");
            var row = accountRows().find(eq("_id", id)).first();
            require(row != null, "The account no longer exists");
            return row;
        }

        String owner(String accountId) {
            account(accountId);
            var rows = owners();
            var known = rows.find(eq("_id", accountId)).first();
            if (known == null) {
                try {
                    rows.insertOne(
                            new Document("_id", accountId)
                                    .append("ownerId", UUID.randomUUID().toString()));
                } catch (MongoWriteException collision) {
                    if (!duplicate(collision)) throw collision;
                }
                known = rows.find(eq("_id", accountId)).first();
            }
            require(known != null && uuid(known.get("ownerId")), "Invalid account UID identity");
            return known.getString("ownerId");
        }

        Document existingPlayer(String accountId) {
            var found =
                    players()
                            .find(eq("accountId", accountId))
                            .projection(new Document("accountId", 1))
                            .limit(2)
                            .into(new ArrayList<>());
            require(
                    found.size() <= 1,
                    "Multiple profiles belong to this account; refusing UID selection");
            return found.isEmpty() ? null : found.get(0);
        }

        Document owned(String ownerId) {
            var row = claims().find(eq("ownerId", ownerId)).first();
            if (row != null) validateClaim(row);
            return row;
        }

        boolean eligible(int candidate, String accountId, boolean existing) {
            if (candidate <= 0 || candidate == GameConstants.SERVER_CONSOLE_UID) return false;
            var player =
                    players()
                            .find(eq("_id", candidate))
                            .projection(new Document("accountId", 1))
                            .first();
            if (player != null
                    && !(existing && Objects.equals(accountId, player.getString("accountId"))))
                return false;
            if (existing) return player != null;
            var filter =
                    accountId == null
                            ? eq("reservedPlayerId", candidate)
                            : and(eq("reservedPlayerId", candidate), ne("_id", accountId));
            return accountRows().find(filter).projection(new Document("_id", 1)).first() == null;
        }

        Document claim(
                int candidate, String ownerId, String kind, String accountId, boolean existing) {
            var previous = owned(ownerId);
            if (previous != null) return previous;
            if (!eligible(candidate, accountId, existing)) return null;
            var row =
                    new Document("_id", candidate)
                            .append("ownerId", ownerId)
                            .append("operationId", UUID.randomUUID().toString())
                            .append("kind", kind);
            try {
                claims().insertOne(row);
                require(
                        eligible(candidate, accountId, existing),
                        "The UID's profile or reservation changed");
                return row;
            } catch (MongoWriteException collision) {
                if (!duplicate(collision)) throw collision;
                return owned(ownerId);
            }
        }

        int floor() {
            var row =
                    players()
                            .find(
                                    and(
                                            gt("_id", 0),
                                            ne("_id", GameConstants.SERVER_CONSOLE_UID),
                                            or(
                                                    type("_id", BsonType.INT32),
                                                    type("_id", BsonType.INT64))))
                            .sort(new Document("_id", -1))
                            .projection(new Document("_id", 1))
                            .first();
            return row == null ? 10000 : Math.max(10000, uid(row.get("_id")));
        }

        Reservation allocate(String ownerId, String kind, String accountId, int requested) {
            var previous = owned(ownerId);
            if (previous != null) return ticket(previous, accountId, kind);
            if (requested > 0 && requested != GameConstants.SERVER_CONSOLE_UID) {
                var preferred = claim(requested, ownerId, kind, accountId, false);
                if (preferred != null) return ticket(preferred, accountId, kind);
            }
            int floor = floor();
            for (int probe = 0; probe < MAX_PROBES; probe++) {
                int candidate = DatabaseManager.reserveNextId(game, "Player", floor);
                floor = candidate;
                var claimed = claim(candidate, ownerId, kind, accountId, false);
                if (claimed != null) return ticket(claimed, accountId, kind);
            }
            throw new IllegalStateException("Could not reserve a free player UID");
        }

        Reservation ticket(Document row, String accountId, String kind) {
            require(kind.equals(row.getString("kind")), "UID reservation kind differs");
            return new Reservation(this, row, accountId);
        }

        Reservation reserveAccount(String accountId) {
            var account = account(accountId);
            var ownerId = owner(accountId);
            var existing = existingPlayer(accountId);
            var previous = owned(ownerId);
            if (previous != null) {
                require(
                        existing == null || uid(existing.get("_id")) == uid(previous.get("_id")),
                        "The account's saved profile differs from its UID reservation");
                return ticket(previous, accountId, "account");
            }
            if (existing != null) {
                var existingUid = uid(existing.get("_id"));
                var claimed = claim(existingUid, ownerId, "account", accountId, true);
                require(claimed != null, "The existing profile UID belongs to another reservation");
                return ticket(claimed, accountId, "account");
            }
            int requested = account.getInteger("reservedPlayerId", 0);
            return allocate(ownerId, "account", accountId, requested);
        }
    }

    /**
     * Called before a new account is inserted, using a stable identity independent of counter IDs.
     */
    static boolean reserveForNewAccount(
            Datastore game, Datastore accounts, int requested, String ownerId) {
        require(uuid(ownerId), "Invalid new account UID identity");
        var context = new Context(game, accounts);
        var row = context.claim(requested, ownerId, "account", null, false);
        return row != null && uid(row.get("_id")) == requested;
    }

    /** Retain account identity tombstones so resetting a counter cannot recycle a deleted owner. */
    static boolean claimNewAccountId(Datastore accounts, String accountId, String ownerId) {
        var rows =
                accounts.getDatabase()
                        .getCollection("accounts")
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY);
        if (rows.find(eq("_id", accountId)).projection(new Document("_id", 1)).first() != null)
            return false;
        var context = new Context(accounts, accounts);
        try {
            context.owners().insertOne(new Document("_id", accountId).append("ownerId", ownerId));
            return true;
        } catch (MongoWriteException collision) {
            if (!duplicate(collision)) throw collision;
            return false;
        }
    }

    public static Reservation reserve(Account account) {
        var context =
                new Context(
                        DatabaseManager.getGameDatastore(), DatabaseManager.getAccountDatastore());
        String id = Objects.requireNonNull(account).getId();
        return DatabaseWriteBarrier.call(() -> context.reserveAccount(id));
    }

    private static void validate(Reservation reservation) {
        var stored = reservation.context.claims().find(eq("_id", reservation.uid)).first();
        validateClaim(stored);
        require(
                reservation.ownerId.equals(stored.getString("ownerId"))
                        && reservation.operationId.equals(stored.getString("operationId"))
                        && reservation.kind.equals(stored.getString("kind")),
                "UID reservation ownership changed");
        if (reservation.accountId != null)
            require(
                    reservation.ownerId.equals(reservation.context.owner(reservation.accountId)),
                    "The account UID identity changed");
    }

    private static Document profile(Reservation reservation) {
        validate(reservation);
        var row =
                reservation
                        .context
                        .players()
                        .find(eq("_id", reservation.uid))
                        .projection(new Document("accountId", 1))
                        .first();
        require(
                row == null || Objects.equals(reservation.accountId, row.getString("accountId")),
                "The reserved UID belongs to another player");
        return row;
    }

    private static Player load(Reservation reservation, Class<? extends Player> type) {
        var player =
                reservation
                        .context
                        .game
                        .find(type)
                        .filter(Filters.eq("_id", reservation.uid))
                        .first();
        require(
                player != null
                        && player.getUid() == reservation.uid
                        && Objects.equals(player.getAccountId(), reservation.accountId),
                "The reserved player profile could not be loaded");
        return player;
    }

    /** Concurrent first logins converge on the one acknowledged, guarded Player document. */
    public static Player createOrLoad(
            Reservation reservation,
            Class<? extends Player> type,
            Supplier<? extends Player> factory) {
        return DatabaseWriteBarrier.call(() -> createOrLoadAdmitted(reservation, type, factory));
    }

    private static Player createOrLoadAdmitted(
            Reservation reservation,
            Class<? extends Player> type,
            Supplier<? extends Player> factory) {
        require(
                reservation.accountId != null && reservation.kind.equals("account"),
                "Account-owned UID reservation required");
        if (profile(reservation) != null) return load(reservation, type);
        var player = Objects.requireNonNull(factory.get());
        require(
                type.isInstance(player) && reservation.accountId.equals(player.getAccountId()),
                "New player does not belong to the reserved account");
        require(
                player.getUid() == 0 || player.getUid() == reservation.uid,
                "New player already has another UID");
        player.setUid(reservation.uid);
        try {
            validate(reservation);
            player.getSaveGuard().save(reservation.context.game);
            return player;
        } catch (RuntimeException uncertain) {
            // A competing insert or our own lost acknowledgement may already have created it.
            // Read the durable identity; never construct a second profile at a different UID.
            if (profile(reservation) != null) return load(reservation, type);
            throw uncertain;
        }
    }

    static int anonymous(int requested) {
        var context =
                new Context(
                        DatabaseManager.getGameDatastore(), DatabaseManager.getAccountDatastore());
        return DatabaseWriteBarrier.call(
                () ->
                        context.allocate(UUID.randomUUID().toString(), "anonymous", null, requested)
                                .uid());
    }

    static void assignLegacy(Player player, int requested) {
        var context =
                new Context(
                        DatabaseManager.getGameDatastore(), DatabaseManager.getAccountDatastore());
        DatabaseWriteBarrier.run(() -> assignLegacyAdmitted(context, player, requested));
    }

    private static void assignLegacyAdmitted(Context context, Player player, int requested) {
        var accountId = player.getAccountId();
        var reservation =
                accountId == null || accountId.isBlank()
                        ? context.allocate(
                                UUID.randomUUID().toString(), "anonymous", null, requested)
                        : context.reserveAccount(accountId);
        require(
                profile(reservation) == null,
                "Use createOrLoad to load an existing account profile");
        player.setUid(reservation.uid);
        player.getSaveGuard().save(context.game);
    }
}

package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;

import dev.morphia.Datastore;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.*;

import org.bson.Document;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Confirm synchronous cleanup on captured stores after logout and admitted write completion. */
public final class AccountDeletionService {
    private static final Map<String, String> OWNED =
            Map.ofEntries(
                    Map.entry("achievements", "uid"), Map.entry("activities", "uid"),
                    Map.entry("homes", "ownerUid"), Map.entry("scene_buildings", "ownerUid"),
                    Map.entry("mail", "ownerUid"), Map.entry("avatars", "ownerId"),
                    Map.entry("gachas", "ownerId"), Map.entry("items", "ownerId"),
                    Map.entry("quests", "ownerUid"), Map.entry("battlepass", "ownerUid"),
                    Map.entry("dailytasks", "ownerUid"), Map.entry("group_instances", "ownerUid"),
                    Map.entry("music_game_beatmaps", "authorUid"),
                            Map.entry("open_world_spawns", "ownerUid"));

    private AccountDeletionService() {}

    public static void delete(Account account) {
        delete(account, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    static synchronized void delete(
            Account account, Duration logoutTimeout, Duration writeTimeout) {
        Objects.requireNonNull(account, "account");
        String accountId = account.getId();
        if (accountId == null || accountId.isBlank())
            throw new IllegalArgumentException("Account ID required");
        DatabaseWriteBarrier.requireMaintenanceCaller();
        var game = Objects.requireNonNull(DatabaseManager.getGameDatastore());
        var accounts = Objects.requireNonNull(DatabaseManager.getAccountDatastore());
        var server = Grasscutter.getGameServer();
        disconnect(server, accountId, logoutTimeout);
        try (var ignored = DatabaseWriteBarrier.acquire(writeTimeout)) {
            if (!online(server, accountId).isEmpty())
                throw new IllegalStateException("The account reconnected before cleanup");
            var accountRows = rows(accounts, "accounts");
            var profiles =
                    rows(game, "players")
                            .find(eq("accountId", accountId))
                            .projection(new Document("accountId", 1))
                            .into(new ArrayList<>());
            if (accountRows.find(eq("_id", accountId)).first() == null) {
                if (profiles.isEmpty()) return;
                throw new IllegalStateException(
                        "Account is missing; refusing orphan profile cleanup");
            }
            Set<Integer> uids = new LinkedHashSet<>();
            for (var profile : profiles) uids.add(uid(profile.get("_id")));
            verifyOwnership(game, accounts, accountId, uids);
            if (!uids.isEmpty()) {
                for (var entry : OWNED.entrySet())
                    rows(game, entry.getKey()).deleteMany(in(entry.getValue(), uids));
                rows(game, "friendships").deleteMany(or(in("ownerId", uids), in("friendId", uids)));
                rows(game, "players").deleteMany(and(eq("accountId", accountId), in("_id", uids)));
            }
            accountRows.deleteOne(eq("_id", accountId));
            if (accountRows.find(eq("_id", accountId)).first() != null
                    || rows(game, "players").find(eq("accountId", accountId)).first() != null)
                throw new IllegalStateException("Account cleanup has not been confirmed");
        }
    }

    private static MongoCollection<Document> rows(Datastore store, String name) {
        return store.getDatabase()
                .getCollection(name)
                .withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
    }

    private static int uid(Object value) {
        if (!(value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() <= 0
                || ((Number) value).longValue() > Integer.MAX_VALUE
                || ((Number) value).intValue() == GameConstants.SERVER_CONSOLE_UID)
            throw new IllegalStateException("Invalid profile UID; refusing cleanup");
        return ((Number) value).intValue();
    }

    private static boolean uuid(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            return UUID.fromString(text).toString().equals(text);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static void verifyOwnership(
            Datastore game, Datastore accounts, String accountId, Set<Integer> uids) {
        var owner = rows(accounts, "account_uid_owners").find(eq("_id", accountId)).first();
        if (owner != null && !uuid(owner.get("ownerId")))
            throw new IllegalStateException("Invalid account UID identity; refusing cleanup");
        if (owner != null) {
            for (var claim :
                    rows(game, "player_uid_reservations").find(eq("ownerId", owner.get("ownerId"))))
                uids.add(uid(claim.get("_id")));
        }
        for (int uid : uids) {
            var profile =
                    rows(game, "players")
                            .find(eq("_id", uid))
                            .projection(new Document("accountId", 1))
                            .first();
            if (profile != null && !accountId.equals(profile.get("accountId")))
                throw new IllegalStateException("Reserved UID has a different saved profile owner");
            var claim = rows(game, "player_uid_reservations").find(eq("_id", uid)).first();
            if (claim == null) continue; // A legacy profile has not established UID metadata yet.
            if (owner == null
                    || !"account".equals(claim.get("kind"))
                    || !Objects.equals(owner.get("ownerId"), claim.get("ownerId"))
                    || !uuid(claim.get("operationId")))
                throw new IllegalStateException(
                        "UID reservation belongs to a different account identity");
        }
    }

    private static List<Player> online(GameServer server, String accountId) {
        if (server == null) return List.of();
        return server.getPlayers().values().stream()
                .filter(player -> accountId.equals(player.getAccountId()))
                .toList();
    }

    private static void disconnect(GameServer server, String accountId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Set<GameSession> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        while (true) {
            var online = online(server, accountId);
            if (online.isEmpty()) return;
            if (GameSessionManager.getLogicThread().inEventLoop())
                throw new IllegalStateException(
                        "Online deletion must run outside the game logic event loop");
            for (var player : online) {
                var session = player.getSession();
                if (session != null && closed.add(session)) session.close();
            }
            if (System.nanoTime() >= deadline)
                throw new IllegalStateException("Timed out waiting for account logout");
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for account logout", interrupted);
            }
        }
    }
}

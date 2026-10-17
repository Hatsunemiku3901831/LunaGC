package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Indexes.ascending;

import com.mongodb.*;
import com.mongodb.client.model.IndexOptions;

import dev.morphia.Datastore;

import emu.grasscutter.GameConstants;
import emu.grasscutter.game.Account;

import org.bson.Document;

import java.util.List;

/** Confirm insert-only creation on the stores selected at admission; never upsert an account. */
final class AccountCreationService {
    private static final int MAX_ID_ATTEMPTS = 64;

    private AccountCreationService() {}

    static Account create(
            Datastore game,
            Datastore accounts,
            String username,
            int reservedUid,
            String password,
            List<String> permissions) {
        if (username == null || username.isBlank()) return null;
        var initialPermissions = List.copyOf(permissions);
        var rows =
                accounts.getDatabase()
                        .getCollection("accounts")
                        .withReadPreference(ReadPreference.primary())
                        .withReadConcern(ReadConcern.MAJORITY)
                        .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        // Index creation is idempotent and must succeed before relying on uniqueness.
        rows.createIndex(ascending("username"), new IndexOptions().unique(true));
        if (rows.find(eq("username", username)).first() != null) return null;
        if (reservedUid > 0) {
            if (reservedUid == GameConstants.SERVER_CONSOLE_UID) return null;
            rows.createIndex(
                    ascending("reservedPlayerId"),
                    new IndexOptions()
                            .unique(true)
                            .partialFilterExpression(gt("reservedPlayerId", 0)));
            if (rows.find(eq("reservedPlayerId", reservedUid)).first() != null
                    || game.getDatabase()
                                    .getCollection("players")
                                    .find(eq("_id", reservedUid))
                                    .projection(new Document("_id", 1))
                                    .first()
                            != null) return null;
        }
        var account = new Account();
        account.setUsername(username);
        account.setPassword(password);
        initialPermissions.forEach(account::addPermission);
        if (reservedUid > 0) account.setReservedPlayerUid(reservedUid);
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            account.setId(Integer.toString(DatabaseManager.reserveNextId(game, "Account", 10000)));
            try {
                rows.insertOne(accounts.getMapper().toDocument(account));
                return account;
            } catch (MongoWriteException duplicate) {
                if (duplicate.getError() == null || duplicate.getError().getCode() != 11000)
                    throw duplicate;
                if (rows.find(eq("username", username)).first() != null
                        || reservedUid > 0
                                && rows.find(eq("reservedPlayerId", reservedUid)).first() != null)
                    return null;
                // An old counter may still point at an existing account. Try a new ID,
                // retaining every field of the account which already owns that ID.
                if (rows.find(eq("_id", account.getId())).first() == null) throw duplicate;
            }
        }
        throw new IllegalStateException("Could not reserve a free account ID");
    }
}

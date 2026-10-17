package emu.grasscutter.database;

import dev.morphia.Datastore;

import emu.grasscutter.game.Account;

import java.time.Duration;
import java.util.Objects;

/** Drain admitted writes, then atomically confirm the password and both token revocations. */
public final class AccountPasswordResetService {
    private AccountPasswordResetService() {}

    public static void resetPassword(Account account, String passwordHash) {
        resetPassword(account, passwordHash, Duration.ofSeconds(30));
    }

    static void resetPassword(Account account, String passwordHash, Duration timeout) {
        Objects.requireNonNull(account, "account");
        if (account.getId() == null || account.getId().isBlank())
            throw new IllegalArgumentException("Account ID required");
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("Password hash required");
        DatabaseWriteBarrier.requireMaintenanceCaller();
        Datastore destination = Objects.requireNonNull(DatabaseManager.getAccountDatastore());
        try (var ignored = DatabaseWriteBarrier.acquire(timeout)) {
            account.getSaveGuard().resetPassword(destination, passwordHash);
        }
    }
}

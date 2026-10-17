package emu.grasscutter.database;

import dev.morphia.Datastore;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.AccountLoginAdmission;
import emu.grasscutter.server.game.GameSession;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

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
        Set<GameSession> revoked;
        long revision;
        try (var ignored = DatabaseWriteBarrier.acquire(timeout)) {
            account.getSaveGuard().resetPassword(destination, passwordHash);
            revoked = AccountLoginAdmission.revokeLocal(destination, account.getId());
            revision = account.getSaveRevision();
        }
        AccountLoginAdmission.closeRevoked(revoked);
        var dispatch = Grasscutter.getDispatchServer();
        if (Grasscutter.getRunMode() != Grasscutter.ServerRunMode.GAME_ONLY && dispatch != null)
            dispatch.revokeCredentials(destination, account.getId(), revision, timeout);
    }
}

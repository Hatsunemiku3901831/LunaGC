package emu.grasscutter.game;

import dev.morphia.Datastore;

import emu.grasscutter.*;
import emu.grasscutter.database.*;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.DispatchUtils;

import java.util.*;
import java.util.concurrent.*;

/**
 * Built-in login stages keep the presented token and serialize local admission with maintenance.
 */
public final class AccountLoginAdmission {
    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface AuthenticatedAction {
        void run(Account account) throws Exception;
    }

    @FunctionalInterface
    private interface Check {
        boolean run() throws Exception;
    }

    private record Outcome(boolean admitted, Exception failure) {}

    private static final class Key {
        final Object authority;
        final String id;

        Key(Object authority, String id) {
            this.authority = authority;
            this.id = id;
        }

        public int hashCode() {
            return 31 * System.identityHashCode(authority) + id.hashCode();
        }

        public boolean equals(Object other) {
            return other instanceof Key key && authority == key.authority && id.equals(key.id);
        }
    }

    private static final class Gate {
        int users; // Changed only by the keyed map computation.
        final Set<Proof> sessions = ConcurrentHashMap.newKeySet();
    }

    private static final class Proof {
        final GameSession session;
        final Key key;
        final Gate gate;
        final AccountLookup lookup;
        final Datastore game;
        final String token;
        volatile boolean revoked;

        Proof(
                GameSession session,
                Key key,
                Gate gate,
                AccountLookup lookup,
                Datastore game,
                String token) {
            this.session = session;
            this.key = key;
            this.gate = gate;
            this.lookup = lookup;
            this.game = game;
            this.token = token;
        }
    }

    private static final ConcurrentMap<Key, Gate> gates = new ConcurrentHashMap<>();
    private static final ConcurrentMap<GameSession, Proof> proofs = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<GameSession>> deferredClosures = new ThreadLocal<>();

    private AccountLoginAdmission() {}

    private static Gate retain(Key key) {
        return gates.compute(
                key,
                (ignored, gate) -> {
                    if (gate == null) gate = new Gate();
                    gate.users++;
                    return gate;
                });
    }

    private static void release(Key key, Gate gate) {
        gates.computeIfPresent(
                key,
                (ignored, current) -> {
                    if (current != gate) throw new IllegalStateException("Login gate changed");
                    current.users--;
                    return current.users == 0 && current.sessions.isEmpty() ? null : current;
                });
    }

    private static boolean admitted(Check action) throws Exception {
        boolean outer = deferredClosures.get() == null;
        if (outer) deferredClosures.set(Collections.newSetFromMap(new IdentityHashMap<>()));
        try {
            var result =
                    DatabaseWriteBarrier.call(
                            () -> {
                                try {
                                    return new Outcome(action.run(), null);
                                } catch (Exception failed) {
                                    return new Outcome(false, failed);
                                }
                            });
            if (result.failure() != null) throw result.failure();
            return result.admitted();
        } finally {
            if (outer) {
                var pending = deferredClosures.get();
                deferredClosures.remove();
                closeRevoked(pending);
            }
        }
    }

    public static boolean begin(
            GameSession session, String id, String token, AuthenticatedAction action)
            throws Exception {
        boolean success = false;
        try {
            if (id == null
                    || id.isBlank()
                    || !DebugConstants.ACCEPT_CLIENT_TOKEN && (token == null || token.isBlank())
                    || session.isLoginRevoked()
                    || proofs.containsKey(session)) return false;
            var lookup = AccountLookup.capture();
            var source = lookup.authorityIdentity();
            if (source == null) return false;
            var key = new Key(source, id);
            var game = DatabaseManager.getGameDatastore();
            success =
                    admitted(
                            () -> {
                                var gate = retain(key);
                                try {
                                    synchronized (gate) {
                                        if (session.isLoginRevoked()
                                                || proofs.containsKey(session)
                                                || !lookup.isCurrent()
                                                || game != DatabaseManager.getGameDatastore())
                                            return false;
                                        var authenticated = DispatchUtils.authenticate(id, token);
                                        if (authenticated == null
                                                && !DebugConstants.ACCEPT_CLIENT_TOKEN)
                                            return false;
                                        if (authenticated != null
                                                && !id.equals(authenticated.getId())) return false;
                                        var fresh = lookup.byId(id);
                                        if (!matches(fresh, id, token)
                                                || !lookup.isCurrent()
                                                || game != DatabaseManager.getGameDatastore())
                                            return false;
                                        for (var previous : gate.sessions) {
                                            if (previous.key.authority == key.authority) {
                                                closed(previous.session);
                                                previous.session.close();
                                            }
                                        }
                                        var proof =
                                                new Proof(session, key, gate, lookup, game, token);
                                        // A closer that sees the proof must also be able to remove
                                        // its gate entry.
                                        gate.sessions.add(proof);
                                        if (proofs.putIfAbsent(session, proof) != null) {
                                            gate.sessions.remove(proof);
                                            return false;
                                        }
                                        session.setAccount(fresh);
                                        if (!current(proof)) return false;
                                        action.run(fresh);
                                        return current(proof);
                                    }
                                } finally {
                                    release(key, gate);
                                }
                            });
            return success;
        } finally {
            if (!success) {
                closed(session);
                session.close();
            }
        }
    }

    public static boolean step(GameSession session, Action action) throws Exception {
        boolean success = false;
        try {
            var proof = proofs.get(session);
            if (proof == null) return false;
            success =
                    admitted(
                            () -> {
                                var gate = retain(proof.key);
                                try {
                                    synchronized (gate) {
                                        if (gate != proof.gate || !current(proof)) return false;
                                        var fresh = proof.lookup.byId(proof.key.id);
                                        if (!matches(fresh, proof.key.id, proof.token)
                                                || fresh.isBanActive()
                                                || !current(proof)) return false;
                                        session.setAccount(fresh);
                                        var player = session.getPlayer();
                                        if (player != null) {
                                            if (player.getSession() != session
                                                    || !proof.key.id.equals(player.getAccountId()))
                                                return false;
                                            player.setAccount(fresh);
                                        }
                                        action.run();
                                        return current(proof);
                                    }
                                } finally {
                                    release(proof.key, gate);
                                }
                            });
            return success;
        } finally {
            if (!success) {
                closed(session);
                session.close();
            }
        }
    }

    private static boolean matches(Account account, String id, String token) {
        return account != null
                && id.equals(account.getId())
                && (DebugConstants.ACCEPT_CLIENT_TOKEN || account.matchesLoginToken(token));
    }

    private static boolean current(Proof proof) {
        var account = proof.session.getAccount();
        return !proof.revoked
                && !proof.session.isLoginRevoked()
                && proofs.get(proof.session) == proof
                && proof.lookup.isCurrent()
                && proof.game == DatabaseManager.getGameDatastore()
                && account != null
                && proof.key.id.equals(account.getId());
    }

    public static void closed(GameSession session) {
        session.revokeLogin();
        var proof = proofs.remove(session);
        if (proof == null) return;
        proof.revoked = true;
        proof.gate.sessions.remove(proof);
        gates.computeIfPresent(
                proof.key,
                (ignored, gate) -> gate.users == 0 && gate.sessions.isEmpty() ? null : gate);
    }

    public static boolean deferTransportClose(GameSession session) {
        var pending = deferredClosures.get();
        if (pending == null) return false;
        pending.add(session);
        return true;
    }

    /** The reset caller holds maintenance and has already confirmed the credential replacement. */
    public static Set<GameSession> revokeLocal(Datastore source, String accountId) {
        var sessions = Collections.newSetFromMap(new IdentityHashMap<GameSession, Boolean>());
        for (var proof : proofs.values()) {
            if (proof.lookup.localAuthority() == source && proof.key.id.equals(accountId))
                sessions.add(proof.session);
        }
        var server = Grasscutter.getGameServer();
        if (server != null) {
            for (var player : server.getPlayers().values()) {
                if (!accountId.equals(player.getAccountId()) || player.getSession() == null)
                    continue;
                var proof = proofs.get(player.getSession());
                if (proof != null) continue;
                var account = player.getAccount();
                if (account != null && account.getSaveGuard().belongsTo(source))
                    sessions.add(player.getSession());
            }
        }
        sessions.forEach(AccountLoginAdmission::closed);
        return sessions;
    }

    public static void closeRevoked(Collection<GameSession> sessions) {
        RuntimeException failure = null;
        for (var session : sessions) {
            try {
                session.close();
            } catch (RuntimeException failed) {
                if (failure == null) failure = failed;
                else failure.addSuppressed(failed);
            }
        }
        if (failure != null) throw failure;
    }
}

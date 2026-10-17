package emu.grasscutter.game;

import dev.morphia.Datastore;

import emu.grasscutter.*;
import emu.grasscutter.database.*;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.DispatchUtils;

import java.time.Duration;
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
        int users;
        final Set<Proof> sessions = new HashSet<>();
        final Set<DatabaseWriteFence> writes = new HashSet<>();
        final Set<GameSession> pendingClosures = Collections.newSetFromMap(new IdentityHashMap<>());
        volatile long revokedThrough = -1;
        long completedThrough = -1;
        volatile boolean retired;
    }

    private static final class Proof {
        final GameSession session;
        final Key key;
        final Gate gate;
        final AccountLookup lookup;
        final Datastore game;
        final String token;
        final long revision;
        volatile boolean revoked;

        Proof(
                GameSession session,
                Key key,
                Gate gate,
                AccountLookup lookup,
                Datastore game,
                String token,
                long revision) {
            this.session = session;
            this.key = key;
            this.gate = gate;
            this.lookup = lookup;
            this.game = game;
            this.token = token;
            this.revision = revision;
        }
    }

    // Only metadata is protected here. Never hold this lock while doing I/O, waiting for a
    // login stage, or requesting a transport close.
    private static final Object registry = new Object();
    private static final Map<Key, Gate> gates = new HashMap<>();
    private static final ConcurrentMap<GameSession, Proof> proofs = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<GameSession>> deferredClosures = new ThreadLocal<>();

    private AccountLoginAdmission() {}

    private static Gate retain(Key key) {
        synchronized (registry) {
            var gate = gates.computeIfAbsent(key, ignored -> new Gate());
            gate.users++;
            return gate;
        }
    }

    private static void release(Key key, Gate gate) {
        synchronized (registry) {
            if (gates.get(key) != gate) throw new IllegalStateException("Login gate changed");
            gate.users--;
            prune(key, gate);
        }
    }

    private static void prune(Key key, Gate gate) {
        if (gate.users == 0
                && gate.sessions.isEmpty()
                && gate.writes.isEmpty()
                && gate.pendingClosures.isEmpty()
                && (gate.revokedThrough < 0 || gate.retired)) gates.remove(key, gate);
    }

    private static boolean admitted(Key key, Gate gate, Check action) throws Exception {
        var fence = new DatabaseWriteFence();
        synchronized (registry) {
            gate.writes.add(fence);
        }
        fence.whenDrained(
                () -> {
                    synchronized (registry) {
                        gate.writes.remove(fence);
                        prune(key, gate);
                    }
                });
        boolean outer = deferredClosures.get() == null;
        if (outer) deferredClosures.set(Collections.newSetFromMap(new IdentityHashMap<>()));
        try {
            var result =
                    fence.call(
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
            var gate = retain(key);
            try {
                success =
                        admitted(
                                key,
                                gate,
                                () -> {
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
                                        long revision =
                                                authenticated == null
                                                        ? -1
                                                        : authenticated.getSaveRevision();
                                        var fresh = lookup.byId(id);
                                        if (!matches(fresh, id, token)
                                                || !lookup.isCurrent()
                                                || game != DatabaseManager.getGameDatastore())
                                            return false;
                                        if (revision < 0) revision = fresh.getSaveRevision();
                                        var proof =
                                                new Proof(
                                                        session, key, gate, lookup, game, token,
                                                        revision);
                                        List<GameSession> previous;
                                        synchronized (registry) {
                                            if (gate.retired
                                                    || revision <= gate.revokedThrough
                                                    || !lookup.isCurrent()
                                                    || session.isLoginRevoked()) return false;
                                            previous =
                                                    gate.sessions.stream()
                                                            .map(p -> p.session)
                                                            .toList();
                                            for (var old : previous) markClosed(old);
                                            gate.sessions.add(proof);
                                            if (proofs.putIfAbsent(session, proof) != null) {
                                                gate.sessions.remove(proof);
                                                return false;
                                            }
                                        }
                                        previous.forEach(GameSession::close);
                                        session.setAccount(fresh);
                                        if (!current(proof)) return false;
                                        action.run(fresh);
                                        return current(proof);
                                    }
                                });
            } finally {
                release(key, gate);
            }
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
            var gate = retain(proof.key);
            try {
                success =
                        admitted(
                                proof.key,
                                gate,
                                () -> {
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
                                });
            } finally {
                release(proof.key, gate);
            }
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
                && !proof.gate.retired
                && proof.revision > proof.gate.revokedThrough
                && !proof.session.isLoginRevoked()
                && proofs.get(proof.session) == proof
                && proof.lookup.isCurrent()
                && proof.game == DatabaseManager.getGameDatastore()
                && account != null
                && proof.key.id.equals(account.getId());
    }

    public static void closed(GameSession session) {
        synchronized (registry) {
            var proof = markClosed(session);
            if (proof != null) prune(proof.key, proof.gate);
        }
    }

    private static Proof markClosed(GameSession session) {
        session.revokeLogin();
        var proof = proofs.remove(session);
        if (proof == null) return null;
        proof.revoked = true;
        proof.gate.sessions.remove(proof);
        return proof;
    }

    /** Publish the cutoff without waiting on the login action monitor or the database barrier. */
    public static Revocation revokeRemote(
            Object authority, String accountId, long throughRevision) {
        Objects.requireNonNull(authority, "Connection authority");
        if (accountId == null || accountId.isBlank() || throughRevision <= 0)
            throw new IllegalArgumentException("Account and positive revision required");
        synchronized (registry) {
            var key = new Key(authority, accountId);
            var gate = gates.computeIfAbsent(key, ignored -> new Gate());
            if (throughRevision <= gate.completedThrough)
                return new Revocation(key, gate, throughRevision, List.of(), List.of());
            gate.revokedThrough = Math.max(gate.revokedThrough, throughRevision);
            for (var proof : List.copyOf(gate.sessions)) {
                if (proof.revision <= throughRevision) {
                    gate.pendingClosures.add(proof.session);
                    markClosed(proof.session);
                }
            }
            return new Revocation(
                    key,
                    gate,
                    throughRevision,
                    List.copyOf(gate.writes),
                    List.copyOf(gate.pendingClosures));
        }
    }

    /** An old connection may never admit another proof, even when the client object is reused. */
    public static List<Revocation> retireRemote(Object authority) {
        if (authority == null) return List.of();
        var result = new ArrayList<Revocation>();
        synchronized (registry) {
            for (var entry : List.copyOf(gates.entrySet())) {
                if (entry.getKey().authority != authority) continue;
                var gate = entry.getValue();
                gate.retired = true;
                for (var proof : List.copyOf(gate.sessions)) {
                    gate.pendingClosures.add(proof.session);
                    markClosed(proof.session);
                }
                result.add(
                        new Revocation(
                                entry.getKey(),
                                gate,
                                gate.revokedThrough,
                                List.copyOf(gate.writes),
                                List.copyOf(gate.pendingClosures)));
            }
        }
        return List.copyOf(result);
    }

    /** Completion means login work drained and transport close requests were accepted. */
    public static final class Revocation {
        private final Key key;
        private final Gate gate;
        private final long through;
        private final List<DatabaseWriteFence> writes;
        private final List<GameSession> closures;

        private Revocation(
                Key key,
                Gate gate,
                long through,
                List<DatabaseWriteFence> writes,
                List<GameSession> closures) {
            this.key = key;
            this.gate = gate;
            this.through = through;
            this.writes = writes;
            this.closures = closures;
        }

        public boolean await(Duration timeout) throws InterruptedException {
            if (timeout == null || timeout.isNegative())
                throw new IllegalArgumentException("Nonnegative timeout required");
            long deadline = System.nanoTime() + timeout.toNanos();
            for (var fence : writes)
                if (!fence.await(Duration.ofNanos(Math.max(0, deadline - System.nanoTime()))))
                    return false;
            for (var session : closures) {
                session.close();
                synchronized (registry) {
                    gate.pendingClosures.remove(session);
                }
            }
            synchronized (registry) {
                gate.completedThrough = Math.max(gate.completedThrough, through);
                prune(key, gate);
            }
            return true;
        }
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

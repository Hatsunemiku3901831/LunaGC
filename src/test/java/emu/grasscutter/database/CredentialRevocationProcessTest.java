package emu.grasscutter.database;

import static emu.grasscutter.config.Configuration.DISPATCH_INFO;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.DefaultAuthentication;
import emu.grasscutter.game.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.dispatch.*;
import emu.grasscutter.server.game.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/**
 * Isolated Mongo and actual WebSockets across separate owner/game JVMs, without a native client.
 */
class CredentialRevocationProcessTest {
    private static final String ACCOUNT_ID = "401", TOKEN = "revocation-fixture-token";
    private static final int READY = 19000;
    @TempDir Path directory;
    private final List<Child> children = new ArrayList<>();
    private final List<String> databases = new ArrayList<>();
    private String mongoUri;
    private Child owner, game;

    @BeforeEach
    void setup() throws Exception {
        Grasscutter.getConfig();
        mongoUri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(mongoUri != null && !mongoUri.isBlank());
        owner = start("owner", null);
        game =
                start(
                        "game",
                        "ws://127.0.0.1:"
                                + owner.ready.get(10, TimeUnit.SECONDS).get("port").getAsInt());
    }

    @AfterEach
    void close() throws Exception {
        for (int i = children.size() - 1; i >= 0; i--) children.get(i).close();
        String evidence = System.getProperty("lunagc.revocation.evidence");
        if (evidence != null && Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (var path :
                        paths.filter(Files::isRegularFile)
                                .filter(
                                        p ->
                                                Set.of("stdout.log", "stderr.log")
                                                        .contains(p.getFileName().toString()))
                                .toList()) {
                    var target = Path.of(evidence).resolve(directory.relativize(path));
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        if (mongoUri != null)
            try (var mongo = MongoClients.create(mongoUri)) {
                for (String database : databases) {
                    assertTrue(database.startsWith("lunagc_revocation_"));
                    mongo.getDatabase(database).drop();
                }
            }
    }

    private Child start(String role, String url) throws Exception {
        String database =
                "lunagc_revocation_" + role + "_" + UUID.randomUUID().toString().replace("-", "");
        databases.add(database);
        var child = new Child(role, database, url, directory.resolve(role + "-" + children.size()));
        children.add(child);
        child.ready.get(10, TimeUnit.SECONDS);
        return child;
    }

    @Test
    void anOwnerResetRevokesAnAlreadyAdmittedGameConnectionBeforeReportingSuccess()
            throws Exception {
        var begin = game.call("begin", Map.of("name", "old"));
        assertTrue(begin.get("admitted").getAsBoolean());
        owner.call("reset", Map.of());
        var saved = owner.call("status", Map.of());
        assertTrue(saved.get("passwordRequired").getAsBoolean());
        assertFalse(saved.get("tokenPresent").getAsBoolean());
        var state = game.call("status", Map.of("name", "old"));
        assertTrue(state.get("revoked").getAsBoolean());
        assertEquals(1, state.get("closes").getAsInt());
    }

    @Test
    void everyConnectedGameMustAcknowledgeItsOwnRevocation() throws Exception {
        var other = start("game", "ws://127.0.0.1:" + owner.ready.get().get("port").getAsInt());
        game.call("begin", Map.of());
        other.call("begin", Map.of());
        owner.call("reset", Map.of());
        for (var node : List.of(game, other)) {
            var status = node.call("status", Map.of());
            assertTrue(status.get("revoked").getAsBoolean());
            assertEquals(1, status.get("closes").getAsInt());
        }
    }

    @Test
    void anOldStageAndItsDescendantWritesDrainWithoutWaitingForUnrelatedWork() throws Exception {
        game.call("begin", Map.of());
        for (String label : List.of("stage", "child", "grandchild", "unrelated"))
            game.call("hold", Map.of("label", label));
        game.call("unrelated", Map.of("label", "unrelated"));
        var stage =
                game.request(
                        "step",
                        Map.of("label", "stage", "child", "child", "grandchild", "grandchild"));
        entered(game, "stage");
        entered(game, "child");
        entered(game, "unrelated");
        var reset = owner.request("reset", Map.of());
        eventually(() -> uncheckedStatus(game).get("revoked").getAsBoolean());
        assertThrows(TimeoutException.class, () -> reset.get(150, TimeUnit.MILLISECONDS));
        game.call("release", Map.of("label", "stage"));
        assertFalse(
                stage.get(3, TimeUnit.SECONDS)
                        .getAsJsonObject("result")
                        .get("admitted")
                        .getAsBoolean());
        game.call("release", Map.of("label", "child"));
        entered(game, "grandchild");
        assertThrows(TimeoutException.class, () -> reset.get(150, TimeUnit.MILLISECONDS));
        game.call("release", Map.of("label", "grandchild"));
        assertTrue(reset.get(3, TimeUnit.SECONDS).get("ok").getAsBoolean());
        assertFalse(
                game.call("observed", Map.of("label", "unrelated")).get("finished").getAsBoolean());
        game.call("release", Map.of("label", "unrelated"));
    }

    @Test
    void anAccountReplyCapturedBeforeResetCannotAdmitAProofAfterTheCutoff() throws Exception {
        owner.call("hold", Map.of("label", "read"));
        owner.call("hold-read", Map.of("label", "read"));
        var admission = game.request("begin", Map.of());
        entered(owner, "read");
        var reset = owner.request("reset", Map.of());
        eventually(() -> uncheckedStatus(owner).get("passwordRequired").getAsBoolean());
        assertThrows(TimeoutException.class, () -> reset.get(150, TimeUnit.MILLISECONDS));
        owner.call("release", Map.of("label", "read"));
        var result = admission.get(3, TimeUnit.SECONDS).getAsJsonObject("result");
        assertFalse(result.get("admitted").getAsBoolean());
        assertTrue(result.get("revoked").getAsBoolean());
        assertEquals(0, result.get("actions").getAsInt());
        assertTrue(reset.get(3, TimeUnit.SECONDS).get("ok").getAsBoolean());
    }

    @Test
    void repeatingAnOldCutoffPreservesNewCredentialsAndDoesNotWaitForANewStage() throws Exception {
        game.call("begin", Map.of());
        long cutoff = owner.call("reset", Map.of()).get("revision").getAsLong();
        owner.call("new-token", Map.of());
        assertTrue(
                game.call("begin", Map.of("name", "new", "token", "fixture-new-token"))
                        .get("admitted")
                        .getAsBoolean());
        game.call("hold", Map.of("label", "new-stage"));
        var stage = game.request("step", Map.of("name", "new", "label", "new-stage"));
        entered(game, "new-stage");
        owner.call("cutoff", Map.of("revision", cutoff));
        assertFalse(game.call("status", Map.of("name", "new")).get("revoked").getAsBoolean());
        assertFalse(stage.isDone());
        game.call("release", Map.of("label", "new-stage"));
        assertTrue(
                stage.get(3, TimeUnit.SECONDS)
                        .getAsJsonObject("result")
                        .get("admitted")
                        .getAsBoolean());
    }

    @Test
    void aNewProofCanEnterWhileAnOlderRevocationIsDrainingItsChildWrites() throws Exception {
        game.call("begin", Map.of());
        game.call("hold", Map.of("label", "stage"));
        game.call("hold", Map.of("label", "child"));
        var stage = game.request("step", Map.of("label", "stage", "child", "child"));
        entered(game, "stage");
        entered(game, "child");
        game.call("release", Map.of("label", "stage"));
        assertTrue(
                stage.get(3, TimeUnit.SECONDS)
                        .getAsJsonObject("result")
                        .get("admitted")
                        .getAsBoolean());
        var reset = owner.request("reset", Map.of());
        eventually(() -> uncheckedStatus(game).get("revoked").getAsBoolean());
        owner.call("new-token", Map.of());
        assertTrue(
                game.call("begin", Map.of("name", "new", "token", "fixture-new-token"))
                        .get("admitted")
                        .getAsBoolean());
        assertFalse(reset.isDone());
        game.call("release", Map.of("label", "child"));
        assertTrue(reset.get(3, TimeUnit.SECONDS).get("ok").getAsBoolean());
        assertFalse(game.call("status", Map.of("name", "new")).get("revoked").getAsBoolean());
    }

    @Test
    void oneMissingAcknowledgmentCannotBeHiddenByAnotherGamesSuccessfulReply() throws Exception {
        var other = start("game", "ws://127.0.0.1:" + owner.ready.get().get("port").getAsInt());
        game.call("begin", Map.of());
        other.call("begin", Map.of());
        other.call("ack-mode", Map.of("mode", "missing"));
        var reset = owner.request("reset", Map.of("timeout", 600)).get(3, TimeUnit.SECONDS);
        assertFalse(reset.get("ok").getAsBoolean());
        assertTrue(uncheckedStatus(owner).get("passwordRequired").getAsBoolean());
        assertTrue(uncheckedStatus(game).get("revoked").getAsBoolean());
        assertFalse(uncheckedStatus(other).get("revoked").getAsBoolean());
    }

    @Test
    void anAcknowledgmentWithADifferentAccountOrRevisionIsNotSuccess() throws Exception {
        game.call("begin", Map.of());
        game.call("ack-mode", Map.of("mode", "wrong"));
        assertFalse(
                owner.request("reset", Map.of("timeout", 600))
                        .get(3, TimeUnit.SECONDS)
                        .get("ok")
                        .getAsBoolean());
        assertTrue(uncheckedStatus(owner).get("passwordRequired").getAsBoolean());
    }

    @Test
    void aFailedTransportCloseIsRetriedBeforeAcknowledgingTheSameCutoff() throws Exception {
        game.call("begin", Map.of());
        game.call("fail-close", Map.of());
        assertFalse(
                owner.request("reset", Map.of("timeout", 2000))
                        .get(3, TimeUnit.SECONDS)
                        .get("ok")
                        .getAsBoolean());
        assertTrue(uncheckedStatus(game).get("revoked").getAsBoolean());
        assertEquals(0, uncheckedStatus(game).get("closes").getAsInt());
        long revision = uncheckedStatus(owner).get("revision").getAsLong();
        owner.call("cutoff", Map.of("revision", revision));
        assertEquals(1, uncheckedStatus(game).get("closes").getAsInt());
        assertEquals(revision, uncheckedStatus(owner).get("revision").getAsLong());
    }

    @Test
    void reusingTheClientOnANewConnectionRetiresOldProofsAndOldSaveSnapshots() throws Exception {
        game.call("begin", Map.of());
        long revision = uncheckedStatus(owner).get("revision").getAsLong();
        game.call("reconnect", Map.of());
        assertTrue(uncheckedStatus(game).get("revoked").getAsBoolean());
        assertEquals(1, uncheckedStatus(game).get("closes").getAsInt());
        assertFalse(
                game.request("save-old", Map.of())
                        .get(3, TimeUnit.SECONDS)
                        .get("ok")
                        .getAsBoolean());
        assertEquals(revision, uncheckedStatus(owner).get("revision").getAsLong());
        assertTrue(game.call("begin", Map.of("name", "new")).get("admitted").getAsBoolean());
    }

    private static JsonObject uncheckedStatus(Child child) {
        try {
            return child.call("status", Map.of());
        } catch (Exception failed) {
            throw new AssertionError(failed);
        }
    }

    private static void entered(Child child, String label) throws Exception {
        eventually(
                () -> {
                    try {
                        return child.call("observed", Map.of("label", label))
                                .get("entered")
                                .getAsBoolean();
                    } catch (Exception failed) {
                        throw new AssertionError(failed);
                    }
                });
    }

    private static void eventually(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean());
    }

    private static String classpath() throws Exception {
        var paths = new LinkedHashSet<String>();
        for (ClassLoader loader = CredentialRevocationProcessTest.class.getClassLoader();
                loader != null;
                loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls)
                for (var url : urls.getURLs())
                    if (url.getProtocol().equals("file"))
                        paths.add(Path.of(url.toURI()).toString());
        }
        paths.add(
                Path.of(
                                CredentialRevocationProcessTest.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toString());
        paths.addAll(
                List.of(
                        System.getProperty("java.class.path")
                                .split(java.util.regex.Pattern.quote(File.pathSeparator))));
        return String.join(File.pathSeparator, paths);
    }

    private final class Child implements AutoCloseable {
        final Process process;
        final BufferedWriter input;
        final CompletableFuture<JsonObject> ready = new CompletableFuture<>();
        final ConcurrentMap<String, CompletableFuture<JsonObject>> replies =
                new ConcurrentHashMap<>();
        final Thread output;

        Child(String role, String database, String url, Path path) throws Exception {
            Files.createDirectories(path);
            var builder =
                    new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-Xmx512m",
                            "-Dfile.encoding=UTF-8",
                            "-cp",
                            classpath(),
                            CredentialRevocationProcessTest.class.getName(),
                            role);
            builder.directory(path.toFile());
            builder.environment().put("LUNAGC_REVOCATION_DATABASE", database);
            builder.environment()
                    .put(
                            "LUNAGC_RESOURCES_DIR",
                            Path.of(Grasscutter.getConfig().folderStructure.resources)
                                    .toAbsolutePath()
                                    .normalize()
                                    .toString());
            if (url != null) builder.environment().put("LUNAGC_REVOCATION_OWNER_URL", url);
            builder.redirectError(path.resolve("stderr.log").toFile());
            process = builder.start();
            input = process.outputWriter(StandardCharsets.UTF_8);
            output =
                    new Thread(
                            () -> {
                                try (var reader = process.inputReader(StandardCharsets.UTF_8);
                                        var log =
                                                Files.newBufferedWriter(
                                                        path.resolve("stdout.log"),
                                                        StandardCharsets.UTF_8)) {
                                    for (String line; (line = reader.readLine()) != null; ) {
                                        log.write(line);
                                        log.newLine();
                                        log.flush();
                                        if (!line.startsWith("CONTROL ")) continue;
                                        var packet =
                                                JsonParser.parseString(line.substring(8))
                                                        .getAsJsonObject();
                                        if (packet.has("event")) ready.complete(packet);
                                        else if (packet.has("id")) {
                                            var pending =
                                                    replies.remove(packet.get("id").getAsString());
                                            if (pending != null) pending.complete(packet);
                                        }
                                    }
                                    var failure =
                                            new IllegalStateException(
                                                    "Fixture process exited: " + role);
                                    ready.completeExceptionally(failure);
                                    replies.values()
                                            .forEach(
                                                    future ->
                                                            future.completeExceptionally(failure));
                                } catch (Exception failure) {
                                    ready.completeExceptionally(failure);
                                    replies.values()
                                            .forEach(
                                                    future ->
                                                            future.completeExceptionally(failure));
                                }
                            },
                            "revocation-fixture-output-" + role);
            output.setDaemon(true);
            output.start();
        }

        CompletableFuture<JsonObject> request(String command, Map<String, ?> data)
                throws Exception {
            var packet = IDispatcher.JSON.toJsonTree(data).getAsJsonObject();
            String id = UUID.randomUUID().toString();
            packet.addProperty("command", command);
            packet.addProperty("id", id);
            var result = new CompletableFuture<JsonObject>();
            replies.put(id, result);
            synchronized (input) {
                input.write(IDispatcher.JSON.toJson(packet));
                input.newLine();
                input.flush();
            }
            return result;
        }

        JsonObject call(String command, Map<String, ?> data) throws Exception {
            var packet = request(command, data).get(10, TimeUnit.SECONDS);
            assertTrue(packet.get("ok").getAsBoolean(), () -> packet.toString());
            return packet.getAsJsonObject("result");
        }

        @Override
        public void close() throws Exception {
            try {
                if (process.isAlive()) call("quit", Map.of());
            } finally {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS));
                }
                input.close();
                output.join(2000);
            }
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void emit(JsonObject packet) {
        synchronized (System.out) {
            System.out.println("CONTROL " + IDispatcher.JSON.toJson(packet));
            System.out.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        new Fixture(args[0]).serve();
    }

    private static final class Fixture {
        final MongoClient mongo;
        final Datastore store;
        final String role;
        final ExecutorService controls = Executors.newCachedThreadPool();
        final ConcurrentMap<String, ProbeSession> sessions = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Account> snapshots = new ConcurrentHashMap<>();
        final ConcurrentMap<String, CountDownLatch> holds = new ConcurrentHashMap<>();
        final Set<String> entered = ConcurrentHashMap.newKeySet(),
                finished = ConcurrentHashMap.newKeySet();
        final AtomicReference<String> heldRead = new AtomicReference<>();
        final AtomicReference<String> ackMode = new AtomicReference<>("normal");
        final DatabaseWriteQueue causalWrites = new DatabaseWriteQueue("revocation-fixture", 1, 16);
        final DatabaseWriteQueue unrelatedWrites =
                new DatabaseWriteQueue("revocation-unrelated", 1, 16);
        final Semaphore authenticated = new Semaphore(0);
        DispatchServer dispatch;
        DispatchClient client;
        GameServer server;

        Fixture(String role) throws Exception {
            this.role = role;
            Grasscutter.getConfig();
            DISPATCH_INFO.dispatchKey = "fixture-dispatch-key";
            DISPATCH_INFO.encryptionKey = new byte[32];
            Arrays.fill(DISPATCH_INFO.encryptionKey, (byte) 0x42);
            String database = Objects.requireNonNull(System.getenv("LUNAGC_REVOCATION_DATABASE"));
            if (!database.startsWith("lunagc_revocation_"))
                throw new IllegalArgumentException("Isolated database required");
            mongo = MongoClients.create(System.getenv("LUNAGC_TEST_MONGO_URI"));
            store = Morphia.createDatastore(mongo, database);
            store.getMapper().map(Account.class);
            field(DatabaseManager.class, "gameDatastore").set(null, store);
            field(DatabaseManager.class, "dispatchDatastore").set(null, store);
            if (role.equals("owner")) {
                Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
                store.getDatabase()
                        .getCollection("accounts")
                        .insertOne(
                                new Document("_id", ACCOUNT_ID)
                                        .append("username", "revocation-fixture")
                                        .append("token", TOKEN)
                                        .append("sessionKey", "fixture-sdk-key")
                                        .append("permissions", List.of()));
                dispatch = new DispatchServer("127.0.0.1", 0);
                field(Grasscutter.class, "dispatchServer").set(null, dispatch);
                var login = dispatch.getHandlers().get(PacketIds.LoginNotify);
                dispatch.getHandlers()
                        .put(
                                PacketIds.LoginNotify,
                                (socket, data) -> {
                                    login.accept(socket, data);
                                    if (Boolean.TRUE.equals(socket.getAttachment()))
                                        dispatch.sendMessage(socket, READY, new JsonObject());
                                });
                dispatch.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (dispatch.getPort() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
                if (dispatch.getPort() == 0) throw new IllegalStateException("Owner did not bind");
                var getAccount = dispatch.getHandlers().get(PacketIds.GetAccountReq);
                dispatch.getHandlers()
                        .put(
                                PacketIds.GetAccountReq,
                                (socket, data) -> {
                                    var label = heldRead.getAndSet(null);
                                    if (label == null) {
                                        getAccount.accept(socket, data);
                                        return;
                                    }
                                    var reply = dispatch.captureReply();
                                    var captured =
                                            IDispatcher.JSON.toJsonTree(
                                                    DatabaseHelper.getAccountById(ACCOUNT_ID));
                                    controls.execute(
                                            () -> {
                                                awaitHold(label);
                                                reply.send(captured);
                                            });
                                });
            } else {
                Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
                Grasscutter.setAuthenticationSystem(new DefaultAuthentication());
                server = new GameServer();
                field(GameServer.class, "players").set(server, new ConcurrentHashMap<>());
                field(Grasscutter.class, "gameServer").set(null, server);
                client =
                        new DispatchClient(
                                URI.create(System.getenv("LUNAGC_REVOCATION_OWNER_URL")));
                server.setDispatchClient(client);
                client.registerHandler(READY, (socket, data) -> authenticated.release());
                var revoke = client.getHandlers().get(PacketIds.CredentialRevokeReq);
                client.getHandlers()
                        .put(
                                PacketIds.CredentialRevokeReq,
                                (socket, data) -> {
                                    switch (ackMode.get()) {
                                        case "missing" -> {}
                                        case "wrong" -> {
                                            var wrong = IDispatcher.decode(data).deepCopy();
                                            wrong.addProperty("accountId", "wrong-account");
                                            wrong.addProperty("ok", true);
                                            client.captureReply().send(wrong);
                                        }
                                        default -> revoke.accept(socket, data);
                                    }
                                });
                client.connect();
                if (!authenticated.tryAcquire(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Game did not authenticate");
            }
        }

        void serve() throws Exception {
            var ready = new JsonObject();
            ready.addProperty("event", "ready");
            if (dispatch != null) ready.addProperty("port", dispatch.getPort());
            emit(ready);
            try (var reader =
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    var request = JsonParser.parseString(line).getAsJsonObject();
                    if (request.get("command").getAsString().equals("quit")) {
                        holds.values().forEach(CountDownLatch::countDown);
                        causalWrites.shutdown();
                        unrelatedWrites.shutdown();
                        causalWrites.awaitTermination(3, TimeUnit.SECONDS);
                        unrelatedWrites.awaitTermination(3, TimeUnit.SECONDS);
                        if (client != null) {
                            server.setDispatchClient(null);
                            client.closeBlocking();
                        }
                        if (dispatch != null) dispatch.stop(1000);
                        controls.shutdownNow();
                        DatabaseHelper.shutdown();
                        mongo.close();
                        reply(request, new JsonObject(), null);
                        return;
                    }
                    controls.execute(
                            () -> {
                                try {
                                    reply(request, handle(request), null);
                                } catch (Throwable failed) {
                                    reply(request, null, failed);
                                }
                            });
                }
            } finally {
                System.exit(0);
            }
        }

        JsonObject handle(JsonObject request) throws Exception {
            String command = request.get("command").getAsString();
            String label = request.has("label") ? request.get("label").getAsString() : "";
            var empty = new JsonObject();
            switch (command) {
                case "hold" -> {
                    holds.put(label, new CountDownLatch(1));
                    return empty;
                }
                case "release" -> {
                    holds.get(label).countDown();
                    return empty;
                }
                case "observed" -> {
                    empty.addProperty("entered", entered.contains(label));
                    empty.addProperty("finished", finished.contains(label));
                    return empty;
                }
                case "hold-read" -> {
                    heldRead.set(label);
                    return empty;
                }
                case "ack-mode" -> {
                    ackMode.set(request.get("mode").getAsString());
                    return empty;
                }
                case "unrelated" -> {
                    unrelatedWrites.execute(() -> awaitHold(label));
                    return empty;
                }
            }
            if (role.equals("owner")) {
                if (command.equals("reset"))
                    AccountPasswordResetService.resetPassword(
                            DatabaseHelper.getAccountById(ACCOUNT_ID),
                            "fixture-new-hash",
                            Duration.ofMillis(
                                    request.has("timeout")
                                            ? request.get("timeout").getAsLong()
                                            : 30000));
                if (command.equals("new-token")) {
                    var account = DatabaseHelper.getAccountById(ACCOUNT_ID);
                    account.setToken("fixture-new-token");
                    DatabaseHelper.saveAccountSync(account);
                }
                if (command.equals("cutoff"))
                    dispatch.revokeCredentials(
                            store,
                            ACCOUNT_ID,
                            request.get("revision").getAsLong(),
                            Duration.ofSeconds(2));
                var row =
                        store.getDatabase()
                                .getCollection("accounts")
                                .find(new Document("_id", ACCOUNT_ID))
                                .first();
                var result = new JsonObject();
                result.addProperty(
                        "passwordRequired", Boolean.TRUE.equals(row.get("passwordRequired")));
                result.addProperty("tokenPresent", row.get("token") != null);
                result.addProperty(
                        "revision",
                        row.get("saveStamp") instanceof Document stamp
                                ? stamp.getLong("revision")
                                : 0);
                return result;
            }
            String name = request.has("name") ? request.get("name").getAsString() : "old";
            if (command.equals("fail-close")) {
                sessions.get(name).failClose.set(true);
                return empty;
            }
            if (command.equals("reconnect")) {
                if (!client.reconnectBlocking() || !authenticated.tryAcquire(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Fixture did not reconnect");
                return empty;
            }
            if (command.equals("save-old")) {
                DatabaseHelper.saveAccountSync(snapshots.get(name));
                return empty;
            }
            if (command.equals("step")) {
                boolean admitted =
                        AccountLoginAdmission.step(
                                sessions.get(name),
                                () -> {
                                    if (request.has("child"))
                                        causalWrites.execute(
                                                () -> {
                                                    awaitHold(request.get("child").getAsString());
                                                    if (request.has("grandchild"))
                                                        causalWrites.execute(
                                                                () ->
                                                                        awaitHold(
                                                                                request.get(
                                                                                                "grandchild")
                                                                                        .getAsString()));
                                                });
                                    awaitHold(label);
                                });
                var result = status(sessions.get(name));
                result.addProperty("admitted", admitted);
                return result;
            }
            if (command.equals("begin")) {
                var session = new ProbeSession(server);
                sessions.put(name, session);
                boolean admitted =
                        AccountLoginAdmission.begin(
                                session,
                                ACCOUNT_ID,
                                request.has("token") ? request.get("token").getAsString() : TOKEN,
                                account -> {
                                    session.actions.incrementAndGet();
                                    snapshots.put(name, account);
                                    session.setState(GameSession.SessionState.WAITING_FOR_LOGIN);
                                });
                var result = status(session);
                result.addProperty("admitted", admitted);
                return result;
            }
            return status(Objects.requireNonNull(sessions.get(name)));
        }

        void awaitHold(String label) {
            entered.add(label);
            try {
                if (!holds.get(label).await(15, TimeUnit.SECONDS))
                    throw new IllegalStateException("Fixture hold timed out: " + label);
                finished.add(label);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            }
        }

        static JsonObject status(ProbeSession session) {
            var result = new JsonObject();
            result.addProperty("revoked", session.isLoginRevoked());
            result.addProperty("closes", session.closes.get());
            result.addProperty("actions", session.actions.get());
            return result;
        }

        static void reply(JsonObject request, JsonObject result, Throwable failed) {
            var packet = new JsonObject();
            packet.add("id", request.get("id"));
            packet.addProperty("ok", failed == null);
            if (failed == null) packet.add("result", result);
            else packet.addProperty("error", failed.getClass().getSimpleName());
            emit(packet);
        }
    }

    private static final class ProbeSession extends GameSession {
        final AtomicInteger closes = new AtomicInteger(), actions = new AtomicInteger();
        final AtomicBoolean failClose = new AtomicBoolean();

        ProbeSession(GameServer server) {
            super(server);
            onConnected(
                    new GameSessionManager.KcpTunnel() {
                        @Override
                        public InetSocketAddress getAddress() {
                            return new InetSocketAddress("127.0.0.1", 22101);
                        }

                        @Override
                        public void writeData(byte[] bytes) {}

                        @Override
                        public int getSrtt() {
                            return 0;
                        }

                        @Override
                        public void close() {
                            if (failClose.getAndSet(false))
                                throw new IllegalStateException("Injected close failure");
                            closes.incrementAndGet();
                            handleClose();
                        }
                    });
        }

        @Override
        public void send(BasePacket packet) {}
    }
}

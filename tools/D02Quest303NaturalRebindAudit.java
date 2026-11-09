import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.*;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.FileUtils;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.slf4j.LoggerFactory;

/** Local 303 restore only: one natural loadAll, real Morphia/save/new Player/QuestManager. */
public final class D02Quest303NaturalRebindAudit {
    static final String URI = "mongodb://127.0.0.1:27027";
    static final String FIXTURE_SHA = "3da7b80960cb2a09d2d1a0ee00ce7659600f855d635f4144ed23ab57aa4deff1";
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final List<Map<String, Object>> DIAGNOSTICS = Collections.synchronizedList(new ArrayList<>());
    static final List<Map<String, Object>> STAGES = new ArrayList<>();
    static final Map<String, Object> RESULT = new LinkedHashMap<>();
    static volatile String phase = "ENTRY";
    static AppenderBase<ILoggingEvent> diagnosticObserver;
    static long sequence;
    static Path output, run, work;
    static MongoClient client;
    static Datastore store;
    static boolean databaseHelperInitialized;
    static int loadAllCalls;

    static final class BoundaryPlayer extends Player {
        final List<String> packets = new ArrayList<>();
        BoundaryPlayer() { super(); setUid(210001); }
        @Override public void sendPacket(BasePacket packet) { packets.add(packet.getClass().getName()); }
    }

    public static void main(String[] args) {
        int exit = 1;
        try {
            validateEntry(args);
            attachDiagnostics();
            stage("BOOTSTRAP");
            Class.forName("emu.grasscutter.Grasscutter");
            validateBootstrap(args[2], args[4]);
            require(!ResourceLoader.isLoadedAll(), "fresh JVM must start unready");
            RESULT.put("initialLoadedAll", false);
            RESULT.put("threadsBefore", threads());
            Document fixture = validateFixture(Path.of(args[1]));

            stage("SEED_OWNED_DATABASE");
            connectOwnedStore(args[3], args[4]);
            store.getDatabase().getCollection("quests").insertOne(fixture);
            Document seeded = snapshot("seeded");
            require(fixture.equals(seeded), "seeded document differs from owned fixture");
            RESULT.put("fixtureProjection", projection(seeded));

            stage("UNREADY_MANAGER_GATE");
            BoundaryPlayer negative = new BoundaryPlayer();
            require(!ResourceLoader.isLoadedAll(), "Player construction changed loader readiness");
            negative.getQuestManager().loadFromDatabase();
            databaseHelperInitialized = true;
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(20)), "unready gate queue timeout");
            require(negative.getQuestManager().getMainQuests().isEmpty(), "unready manager published quests");
            Document afterGate = snapshot("after-unready-gate");
            require(seeded.equals(afterGate), "unready manager changed BSON");
            require(Objects.equals(RESULT.get("seededBsonSha256"), RESULT.get("after-unready-gateBsonSha256")), "unready raw BSON changed");
            assertQueues("afterUnreadyGate", 0);
            RESULT.put("unreadyGatePassed", true);

            stage("LOADALL");
            require(!ResourceLoader.isLoadedAll(), "loadAll already ready before its only call");
            loadAllCalls++;
            ResourceLoader.loadAll();
            stage("LOADALL_RETURNED");
            RESULT.put("loadAllReturned", true);
            require(ResourceLoader.isLoadedAll(), "natural loadAll returned without readiness flag");
            RESULT.put("loadedAll", true);
            RESULT.put("resources", resourceMaps());

            stage("CHECK_LOCAL_303_DEFINITIONS");
            Set<Integer> ids = necessaryDefinitions();
            validateSavedDefinitions(seeded, ids);
            RESULT.put("localNecessaryResources", true);
            // The three-file snapshot does not establish global resource readiness or D02-R3.
            RESULT.put("globalResourceReady", false);

            stage("REAL_MORPHIA_READ_AND_SAVE");
            BoundaryPlayer saver = new BoundaryPlayer();
            List<GameMainQuest> saved = DatabaseHelper.getAllQuests(saver);
            require(saved.size() == 1, "cold read did not produce exactly one parent");
            GameMainQuest source = saved.get(0);
            assertSavedState(source, ids);
            require(source.getOwner() == null && source.getQuestManager() == null, "cold parent unexpectedly bound");
            for (GameQuest child : source.getChildQuests().values())
                require(child.getMainQuest() == null && child.getQuestData() == null, "cold child unexpectedly bound");
            source.setOwner(saver);
            require(source.getOwner() == saver && source.getQuestManager() == saver.getQuestManager(), "setOwner did not bind real saver");
            source.save();
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(20)), "explicit real save queue timeout");
            assertQueues("afterExplicitSave", 1);
            Document afterSave = snapshot("after-explicit-save");
            assertProjection(seeded, afterSave, "explicitSave");

            stage("NEW_PLAYER_REAL_MANAGER_RESTORE");
            BoundaryPlayer restored = new BoundaryPlayer();
            require(restored != saver && restored != negative && restored.getQuestManager() != saver.getQuestManager(), "new Player identity reused");
            require(restored.getQuestManager().getMainQuests().isEmpty(), "new manager is not empty");
            // This fixed real fixture lacks failConditionKeys. The real rebind initializes them
            // to empty keys and therefore performs exactly one documented parent autosave.
            require(source.getChildQuests().values().stream().allMatch(q -> q.getFailConditionKeys() == null), "fixture metadata changed: reassess expected autosave");
            restored.getQuestManager().loadFromDatabase();
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(20)), "manager rebind save queue timeout");
            require(restored.getQuestManager().getMainQuests().size() == 1, "manager did not publish exactly one parent");
            GameMainQuest rebound = restored.getQuestManager().getMainQuestById(303);
            require(rebound != null && rebound != source, "manager reused source or omitted 303");
            require(rebound.getOwner() == restored && rebound.getQuestManager() == restored.getQuestManager(), "parent owner/manager identity mismatch");
            assertSavedState(rebound, ids);
            for (var entry : rebound.getChildQuests().entrySet()) {
                GameQuest child = entry.getValue();
                require(child.getMainQuest() == rebound && child.getOwner() == restored, "child parent/owner identity mismatch: " + entry.getKey());
                require(child.getQuestData() == GameData.getQuestDataMap().get(entry.getKey().intValue()), "child definition identity mismatch: " + entry.getKey());
                require(Objects.equals(child.getFinishConditionKeys(), QuestProgressBinding.keys(child.getQuestData().getFinishCond())), "finish key identity mismatch: " + entry.getKey());
                require(Objects.equals(child.getFailConditionKeys(), List.of()), "fail keys not rebound: " + entry.getKey());
            }
            assertQueues("afterManagerRestore", 2);
            Document afterRestore = snapshot("after-manager-restore");
            assertProjection(seeded, afterRestore, "managerRestore");
            require(negative.packets.isEmpty() && saver.packets.isEmpty() && restored.packets.isEmpty(), "unexpected packet reached boundary");
            RESULT.put("packetsAtBoundary", 0);
            RESULT.put("restoredChildren", rebound.getChildQuests().size());
            RESULT.put("managerExpectedAutosaves", 1);
            RESULT.put("newObjectBindingsPassed", true);
            require(DIAGNOSTICS.stream().noneMatch(d -> "ERROR".equals(d.get("level")) && !"LOADALL".equals(d.get("phase"))), "ERROR outside bounded loadAll diagnostics");
            stage("LOCAL_303_REBIND_PASSED");
            RESULT.put("outcome", "PASSED_LOCAL_303_REBIND");
            exit = 0;
        } catch (Throwable failure) {
            RESULT.put("outcome", "STOPPED");
            RESULT.put("stoppedAt", phase);
            RESULT.put("throwable", stack(failure));
            failure.printStackTrace();
        } finally {
            try {
                if (databaseHelperInitialized) {
                    stage("DRAIN_OWNED_DATABASE_QUEUES");
                    require(DatabaseHelper.shutdown(), "database queue normal shutdown failed");
                }
                if (client != null) client.close();
                RESULT.put("mongoClientClosed", client != null);
                RESULT.put("databaseRetained", true);
            } catch (Throwable failure) {
                exit = 1; RESULT.put("outcome", "STOPPED"); RESULT.put("cleanupFailure", stack(failure));
            }
            RESULT.put("loadAllCalls", loadAllCalls);
            RESULT.put("stages", STAGES);
            RESULT.put("threadsAfter", threads());
            synchronized (DIAGNOSTICS) { RESULT.put("diagnostics", new ArrayList<>(DIAGNOSTICS)); }
            if (output != null) try { Files.writeString(output, GSON.toJson(RESULT)); }
            catch (Exception failure) { exit = 1; failure.printStackTrace(); }
        }
        System.exit(exit);
    }

    static void validateEntry(String[] args) throws Exception {
        require(args.length == 5, "output, owned fixture, config SHA, fixed URI and unique database required");
        output = Path.of(args[0]).toAbsolutePath().normalize();
        run = output.getParent(); work = run.resolve("work").toRealPath();
        require(output.equals(run.resolve("driver-result.json")) && !Files.exists(output), "output path already exists or is not designated");
        require(Path.of("").toRealPath().equals(work), "CWD outside new work");
        require(args[2].matches("[0-9a-f]{64}"), "invalid config SHA");
        require(URI.equals(args[3]) && args[4].matches("lunagc_d02_quest303_natural_[0-9a-f]{32}"), "database isolation boundary mismatch");
        require(Path.of(args[1]).toRealPath().equals(run.resolve("owned-direct-bson.json").toRealPath()), "fixture outside new run");
        require(hash(Path.of(args[1])).equals(FIXTURE_SHA), "owned fixture hash mismatch");
        require(hash(work.resolve("config.json")).equals(args[2]), "config SHA mismatch");
        RESULT.put("mongoUri", URI); RESULT.put("database", args[4]); RESULT.put("fixtureSha256", FIXTURE_SHA);
    }

    static void attachDiagnostics() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        diagnosticObserver = new AppenderBase<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) {
                if (event.getLevel().isGreaterOrEqual(Level.WARN)) synchronized (DIAGNOSTICS) {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("sequence", ++sequence); row.put("phase", phase); row.put("thread", event.getThreadName());
                    row.put("logger", event.getLoggerName()); row.put("level", event.getLevel().toString()); row.put("message", event.getFormattedMessage());
                    row.put("throwable", event.getThrowableProxy() == null ? null : ThrowableProxyUtil.asString(event.getThrowableProxy())); DIAGNOSTICS.add(row);
                }
            }
        };
        diagnosticObserver.setContext(root.getLoggerContext()); diagnosticObserver.start(); root.addAppender(diagnosticObserver);
    }

    static void validateBootstrap(String configHash, String database) throws Exception {
        require(Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json")), "bootstrap used another config");
        require(hash(Grasscutter.configFile.toPath()).equals(configHash), "bootstrap changed config");
        var cfg = Grasscutter.getConfig();
        require(URI.equals(cfg.databaseInfo.game.connectionUri) && database.equals(cfg.databaseInfo.game.collection)
                && URI.equals(cfg.databaseInfo.server.connectionUri) && database.equals(cfg.databaseInfo.server.collection), "bootstrap database config mismatch");
        var paths = new LinkedHashMap<String, Object>();
        for (String name : List.of("resources", "scripts", "data", "plugins", "cache", "packets")) {
            Path actual = switch (name) {
                case "resources" -> FileUtils.getResourcePath(""); case "scripts" -> FileUtils.getScriptPath("");
                case "data" -> FileUtils.getDataUserPath(""); case "plugins" -> FileUtils.getPluginPath("");
                case "cache" -> FileUtils.getCachePath(""); default -> FileUtils.getPacketPath("");
            };
            Path expected = work.resolve(name.equals("scripts") ? "resources/Scripts" : name);
            require(actual.toRealPath().equals(expected.toRealPath()), "resolved root mismatch: " + name); paths.put(name, actual.toString());
        }
        var data = new LinkedHashMap<String, Object>();
        for (String name : List.of("AbilityEmbryos.json", "OpenConfig.json", "Spawns.json", "GadgetSpawns.json", "QuestEncryptionKeys.json", "BlossomConfig.json", "ActivityConfig.json")) {
            Path path = FileUtils.getDataPath(name); var row = new LinkedHashMap<String, Object>();
            row.put("uri", path.toUri().toString()); row.put("exists", Files.exists(path)); row.put("sha256", Files.exists(path) ? hash(path) : null); data.put(name, row);
        }
        paths.put("dataResolution", data); RESULT.put("paths", paths);
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        require(root.isAttached(diagnosticObserver), "diagnostic observer detached during bootstrap");
        var appenders = new ArrayList<String>(); root.iteratorForAppenders().forEachRemaining(a -> appenders.add(a.getClass().getName()));
        require(appenders.stream().noneMatch(s -> s.contains("Async") || s.contains("FileAppender") || s.contains("ServerLogEvent")), "unexpected log appender");
        RESULT.put("rootAppenders", appenders);
        require(Grasscutter.getPluginManager() == null, "plugin manager already exists");
    }

    static Document validateFixture(Path path) throws Exception {
        Document raw = Document.parse(Files.readString(path));
        require(raw.getObjectId("_id") != null && raw.getInteger("ownerUid") == 210001 && raw.getInteger("parentQuestId") == 303, "fixture identity mismatch");
        require(raw.get("childQuests", Document.class).size() == 63, "fixture child count mismatch");
        require(raw.getList("questVars", Integer.class).equals(List.of(0, 0, 0, 0, 0)), "fixture questVars mismatch");
        require(raw.getList("timeVar", Number.class).stream().allMatch(n -> n.longValue() == -1) && raw.getList("timeVar", Number.class).size() == 10, "fixture timeVar mismatch");
        return raw;
    }

    static void connectOwnedStore(String uri, String database) throws Exception {
        var settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(b -> b.connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)).build();
        client = MongoClients.create(settings);
        store = Morphia.createDatastore(client, database); store.getMapper().map(GameMainQuest.class);
        require(store.getDatabase().listCollectionNames().into(new ArrayList<>()).isEmpty(), "database already used; preserve it and prepare a new UUID");
        var field = DatabaseManager.class.getDeclaredField("gameDatastore"); field.setAccessible(true);
        require(field.get(null) == null, "another game datastore already wired"); field.set(null, store);
    }

    static Set<Integer> necessaryDefinitions() throws Exception {
        require(GameData.getChapterDataMap().size() == 436 && GameData.getQuestDataMap().size() == 63, "necessary Chapter436/Quest63 maps absent");
        var parent = GameData.getMainQuestDataMap().get(303);
        require(parent != null && parent.getId() == 303 && parent.getSubQuests().length == 63, "loaded parent303 children unavailable");
        var raw = JsonParser.parseString(Files.readString(work.resolve("resources/BinOutput/Quest/303.json"))).getAsJsonObject();
        var rows = raw.getAsJsonArray("subQuests"); require(raw.get("id").getAsInt() == 303 && rows.size() == 63, "raw parent303 mismatch");
        Set<Integer> ids = new TreeSet<>(); var observations = new ArrayList<Object>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i).getAsJsonObject(); int id = row.get("subId").getAsInt();
            require(ids.add(id) && parent.getSubQuests()[i].getSubId() == id, "duplicate or reordered parent child: " + id);
            QuestData actual = GameData.getQuestDataMap().get(id);
            require(actual != null && actual.getId() == id && actual.getMainId() == 303 && actual.hasMatchingChapter(), "missing or inconsistent actual definition: " + id);
            require(actual.getOrder() == row.get("order").getAsInt() && !actual.isAutomaticAcceptance(), "fallback order/acceptance mismatch: " + id);
            assertConditions(row.getAsJsonArray("finishCond"), actual.getFinishCond(), id);
            assertExec(row.getAsJsonArray("beginExec"), actual.getBeginExec(), id);
            assertExec(row.getAsJsonArray("finishExec"), actual.getFinishExec(), id);
            assertExec(row.getAsJsonArray("failExec"), actual.getFailExec(), id);
            observations.add(Map.of("id", id, "mainId", actual.getMainId(), "matchingChapter", true, "order", actual.getOrder(), "finishKeys", QuestProgressBinding.keys(actual.getFinishCond())));
        }
        require(GameData.getQuestDataMap().keySet().equals(ids), "extra or missing QuestData IDs");
        QuestData first = GameData.getQuestDataMap().get(30302);
        require(first.getFinishCond().size() == 1 && first.getFinishCond().get(0).getType().name().equals("QUEST_CONTENT_COMPLETE_TALK")
                && Arrays.equals(first.getFinishCond().get(0).getParam(), new int[] {30302, 0}), "30302 finish semantics changed");
        require(first.getFinishExec().size() == 2 && first.getFinishExec().get(0).getType().name().equals("QUEST_EXEC_UNLOCK_POINT")
                && first.getFinishExec().get(1).getType().name().equals("QUEST_EXEC_UNLOCK_AREA"), "30302 finish exec semantics changed");
        RESULT.put("necessaryDefinitions", observations);
        long rejected = DIAGNOSTICS.stream().filter(d -> "LOADALL".equals(d.get("phase")) && ((String) d.get("message")).contains("QuestData rejected 17814 entries")
                && ((String) d.get("message")).contains("BEGIN_OBJECT")).count();
        require(rejected == 1, "expected QuestExcel rejection attribution absent or changed");
        RESULT.put("questExcelRejected", 17814); RESULT.put("questDefinitionsSource", "actual 303 BIN fallback; Excel rejected");
        return ids;
    }

    static void assertConditions(JsonArray rows, List<QuestData.QuestContentCondition> actual, int id) {
        require(actual.size() == (rows == null ? 0 : rows.size()), "finish condition count mismatch: " + id);
        for (int i = 0; i < actual.size(); i++) {
            var row = rows.get(i).getAsJsonObject(); var condition = actual.get(i);
            require(condition.getType().name().equals(row.get("type").getAsString())
                    && Arrays.equals(condition.getParam(), GSON.fromJson(row.get("param"), int[].class))
                    && condition.getCount() == (row.has("count") ? row.get("count").getAsInt() : 0), "finish condition mismatch: " + id);
            require(condition.getParamStr() == null || condition.getParamStr().isEmpty(), "unexpected nonempty finish paramStr: " + id);
        }
    }

    static void assertExec(JsonArray rows, List<QuestData.QuestExecParam> actual, int id) {
        require(actual.size() == (rows == null ? 0 : rows.size()), "exec count mismatch: " + id);
        for (int i = 0; i < actual.size(); i++) {
            var row = rows.get(i).getAsJsonObject(); var action = actual.get(i);
            require(action.getType().name().equals(row.get("type").getAsString())
                    && Arrays.equals(action.getParam(), GSON.fromJson(row.get("param"), String[].class)), "exec mismatch: " + id);
        }
    }

    static void validateSavedDefinitions(Document raw, Set<Integer> ids) {
        Document children = raw.get("childQuests", Document.class);
        require(children.keySet().equals(ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet())), "saved children do not exactly match actual definitions");
        for (var entry : children.entrySet()) {
            int id = Integer.parseInt(entry.getKey()); Document child = (Document) entry.getValue();
            require(child.getInteger("subQuestId") == id && child.getInteger("mainQuestId") == 303, "saved child identity mismatch: " + id);
        }
    }

    static void assertSavedState(GameMainQuest main, Set<Integer> ids) {
        require(main.getParentQuestId() == 303 && main.getOwnerUid() == 210001 && main.getChildQuests().keySet().equals(ids), "mapped parent/children mismatch");
        require(Arrays.equals(main.getQuestVars(), new int[5]) && Arrays.equals(main.getTimeVar(), new long[] {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1}), "mapped vars/timeVar changed");
        require(!main.isFinished() && main.getState().name().equals("PARENT_QUEST_STATE_NONE"), "parent state changed");
        for (var entry : main.getChildQuests().entrySet()) {
            GameQuest child = entry.getValue(); int id = entry.getKey();
            require(child.getSubQuestId() == id && child.getMainQuestId() == 303, "mapped child identity mismatch: " + id);
            require(child.getState() == (id == 30302 ? QuestState.QUEST_STATE_UNFINISHED : QuestState.QUEST_STATE_UNSTARTED), "mapped child state mismatch: " + id);
            require(Arrays.equals(child.getFinishProgressList(), new int[] {0}), "mapped progress changed: " + id);
        }
    }

    static Document snapshot(String name) throws Exception {
        var collection = store.getDatabase().getCollection("quests", RawBsonDocument.class);
        require(collection.countDocuments() == 1, "duplicate or unexpected quest documents");
        var raw = collection.find(new Document("ownerUid", 210001)).first(); require(raw != null, "owned record absent");
        var bytes = raw.getByteBuffer().asNIO().duplicate(); byte[] content = new byte[bytes.remaining()]; bytes.get(content);
        Path folder = run.resolve("snapshots"); Files.createDirectories(folder);
        Files.write(folder.resolve(name + ".bson"), content); Files.writeString(folder.resolve(name + ".json"), raw.toJson() + "\n");
        RESULT.put(name + "BsonSha256", digest(content));
        require(new TreeSet<>(store.getDatabase().listCollectionNames().into(new ArrayList<>())).equals(new TreeSet<>(List.of("quests"))), "unexpected collection written");
        return Document.parse(raw.toJson());
    }

    static Map<String, Object> projection(Document raw) {
        var out = new LinkedHashMap<String, Object>();
        for (String name : List.of("_id", "ownerUid", "parentQuestId", "questVars", "timeVar", "state", "isFinished")) out.put(name, raw.get(name));
        for (String name : List.of("questGroupSuites", "suggestTrackMainQuestList")) out.put(name, raw.getOrDefault(name, List.of()));
        out.put("talks", raw.getOrDefault("talks", new Document()));
        var children = new TreeMap<String, Object>();
        for (var entry : raw.get("childQuests", Document.class).entrySet()) {
            Document child = (Document) entry.getValue(); var fields = new LinkedHashMap<String, Object>();
            for (String name : List.of("subQuestId", "mainQuestId", "state", "startTime", "acceptTime", "finishTime", "startGameDay")) fields.put(name, child.getOrDefault(name, 0));
            for (String name : List.of("finishProgressList", "failProgressList", "finishConditionKeys", "failConditionKeys", "retiredFinishProgress", "retiredFailProgress", "legacyFinishProgressList", "legacyFailProgressList")) fields.put(name, child.getOrDefault(name, List.of()));
            fields.put("triggers", child.getOrDefault("triggers", new Document())); children.put(entry.getKey(), fields);
        }
        out.put("childQuests", children); return out;
    }

    static void assertProjection(Document original, Document current, String label) {
        var before = projection(original); var after = projection(current);
        require(before.equals(after), "controlled BSON projection changed: " + label);
        RESULT.put(label + "ProjectionPreserved", true); RESULT.put(label + "Projection", after);
        var deltas = new ArrayList<Object>(); differences("", original, current, deltas); RESULT.put(label + "RawFieldDifferences", deltas);
    }

    static void differences(String path, Object before, Object after, List<Object> out) {
        if (Objects.equals(before, after)) return;
        if (before instanceof Map<?, ?> a && after instanceof Map<?, ?> b) {
            Set<String> keys = new TreeSet<>(); a.keySet().forEach(k -> keys.add(k.toString())); b.keySet().forEach(k -> keys.add(k.toString()));
            for (String key : keys) differences(path.isEmpty() ? key : path + "." + key, a.get(key), b.get(key), out);
        } else {
            var row = new LinkedHashMap<String, Object>(); row.put("path", path); row.put("before", before); row.put("after", after); out.add(row);
        }
    }

    static void assertQueues(String label, long expectedCompleted) {
        databaseHelperInitialized = true;
        var rows = DatabaseHelper.queueStatus(); RESULT.put(label + "Queues", rows);
        long completed = 0;
        for (var row : rows) {
            require(row.outstanding() == 0 && row.failed() == 0 && row.rejected() == 0 && row.coalesced() == 0 && row.cancelled() == 0, "unexpected database queue work: " + label);
            completed += row.completed();
        }
        require(completed == expectedCompleted, "unexpected number of real queued writes: " + label + " actual=" + completed);
    }

    static List<Object> resourceMaps() {
        var rows = new ArrayList<Object>();
        for (var cls : ResourceLoader.getResourceDefClasses()) {
            var row = new LinkedHashMap<String, Object>(); row.put("class", cls.getName());
            row.put("names", cls.getAnnotation(ResourceType.class).name()); var map = GameData.getMapByResourceDef(cls); row.put("mapSize", map == null ? null : map.size()); rows.add(row);
        }
        return rows;
    }

    static void stage(String name) { phase = name; STAGES.add(Map.of("phase", name, "nanoTime", System.nanoTime())); System.out.println("D02_303_STAGE " + name); System.out.flush(); }
    static List<String> threads() { return Thread.getAllStackTraces().keySet().stream().map(t -> t.getName() + ":" + t.getState()).sorted().toList(); }
    static String hash(Path path) throws Exception { return digest(Files.readAllBytes(path)); }
    static String digest(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String stack(Throwable failure) { var writer = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(writer)); return writer.toString(); }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}

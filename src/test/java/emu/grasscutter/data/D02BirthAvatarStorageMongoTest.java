package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.FetterData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.database.DatabaseWriteQueue;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.managers.cooking.CookingCompoundManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.Crypto;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Partial birth resources: positive AvatarStorage only; empty negative paths also use Inventory. */
class D02BirthAvatarStorageMongoTest {
    private static final int UID = 74451;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static JsonObject projection, mapping, promote;
    private static Field fettersCache, compoundDefaults, compoundGroups;
    @TempDir Path directory;
    private final List<Map<Object, Object>> maps = new ArrayList<>();
    private final List<Map<Object, Object>> originals = new ArrayList<>();
    private final Map<String, AtomicInteger> writes = new ConcurrentHashMap<>();
    private final List<Document> commands = Collections.synchronizedList(new ArrayList<>());
    private MongoClient client;
    private Datastore previousStore, store;
    private Object oldFetters, oldCompoundDefaults, oldCompoundGroups;
    private boolean oldLoadedAll, storeInstalled;
    private byte[] oldDispatchKey, oldDispatchSeed, oldEncryptKey, oldEncryptSeedBuffer;
    private long oldEncryptSeed;
    private java.security.PrivateKey oldSigningKey;
    private Map<Integer, java.security.PublicKey> oldEncryptionKeys, oldEncryptionEntries;
    private String dbName;
    private List<DatabaseWriteQueue.Snapshot> queueBaseline;

    @BeforeAll
    static void inputs() throws Exception {
        Grasscutter.getLogger();
        projection = report("d02.talents.projection", "ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f");
        mapping = report("d02.talents.mapping", "d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e");
        promote = report("d02.talents.promote", "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1");
        assertTrue(projection.get("partial").getAsBoolean());
        assertFalse(projection.get("completeAvatar").getAsBoolean());
        assertFalse(projection.get("resourceReady").getAsBoolean());
        assertFalse(projection.get("d02Complete").getAsBoolean());
        fettersCache = GameData.class.getDeclaredField("fetters");
        fettersCache.setAccessible(true);
        compoundDefaults = CookingCompoundManager.class.getDeclaredField("defaultUnlockedCompounds");
        compoundGroups = CookingCompoundManager.class.getDeclaredField("compoundGroups");
        compoundDefaults.setAccessible(true); compoundGroups.setAccessible(true);
    }

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri, "LUNAGC_TEST_MONGO_URI is required");
        assertFalse(uri.isBlank(), "LUNAGC_TEST_MONGO_URI is required");
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        queueBaseline = DatabaseHelper.queueStatus();
        previousStore = DatabaseManager.getGameDatastore();
        oldLoadedAll = ResourceLoader.isLoadedAll();
        assertFalse(oldLoadedAll, "partial resources must not claim loadedAll");
        oldFetters = fettersCache.get(null);
        oldCompoundDefaults = compoundDefaults.get(null); oldCompoundGroups = compoundGroups.get(null);
        assertTrue(GameData.getSceneTagDataMap().isEmpty(), "new worker must not install unapproved scene tags");
        assertTrue(GameData.getCompoundDataMap().isEmpty(), "new worker must not install unapproved compounds");
        assertNull(Grasscutter.getPluginManager(), "local constructor must not dispatch to plugins");
        oldDispatchKey = Crypto.DISPATCH_KEY; oldDispatchSeed = Crypto.DISPATCH_SEED;
        oldEncryptKey = Crypto.ENCRYPT_KEY; oldEncryptSeed = Crypto.ENCRYPT_SEED;
        oldEncryptSeedBuffer = Crypto.ENCRYPT_SEED_BUFFER; oldSigningKey = Crypto.CUR_SIGNING_KEY;
        oldEncryptionKeys = Crypto.EncryptionKeys; oldEncryptionEntries = new HashMap<>(oldEncryptionKeys);
        Crypto.loadKeys();
        assertNotNull(Crypto.DISPATCH_KEY); assertTrue(Crypto.DISPATCH_KEY.length > 0);
        assertNotNull(Crypto.ENCRYPT_KEY); assertTrue(Crypto.ENCRYPT_KEY.length > 0);
        isolateMap(GameData.getAvatarSkillDataMap());
        isolateMap(GameData.getAvatarSkillDepotDataMap());
        isolateMap(GameData.getAvatarCurveDataMap());
        isolateMap(GameData.getAvatarDataMap());
        isolateMap(promoteMap());
        isolateMap(GameData.getMapByResourceDef(FetterData.class));
        isolateMap(GameData.getFetterCharacterCardDataMap());
        isolateMap(GameData.getRewardDataMap());
        isolateMap(GameData.getAvatarTalentDataMap());
        isolateMap(GameData.getProudSkillDataMap());
        isolateMap(GameData.getOpenConfigEntries());
        isolateMap(GameData.getAbilityEmbryoInfo());
        isolateMap(GameData.getItemDataMap());
        fettersCache.set(null, new HashMap<Integer, List<Integer>>());
        dbName = "lunagc_d02_avatar_local_" + UUID.randomUUID().toString().replace("-", "");
        CommandListener listener = new CommandListener() {
            @Override public void commandStarted(CommandStartedEvent event) {
                String name = event.getCommandName();
                if (dbName.equals(event.getDatabaseName()) && Set.of("insert", "update", "delete", "findAndModify").contains(name)) {
                    String collection = event.getCommand().getString(name).getValue();
                    writes.computeIfAbsent(name + ":" + collection, key -> new AtomicInteger()).incrementAndGet();
                    commands.add(Document.parse(event.getCommand().toJson()));
                }
            }
        };
        client = MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri)).addCommandListener(listener).build());
        store = Morphia.createDatastore(client, dbName, MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
        store.getMapper().map(Avatar.class, GameItem.class);
        setStore(store);
        storeInstalled = true;
    }

    @AfterEach
    void cleanup() throws Exception {
        if (storeInstalled) {
            drain();
            setStore(previousStore);
        }
        for (int i = 0; i < maps.size(); i++) {
            maps.get(i).clear();
            maps.get(i).putAll(originals.get(i));
            assertEquals(originals.get(i), maps.get(i));
            if (maps.get(i) == (Object) GameData.getAvatarCurveDataMap())
                assertEquals(List.copyOf(originals.get(i).keySet()), List.copyOf(maps.get(i).keySet()));
        }
        fettersCache.set(null, oldFetters);
        assertSame(oldFetters, fettersCache.get(null));
        compoundDefaults.set(null, oldCompoundDefaults); compoundGroups.set(null, oldCompoundGroups);
        assertSame(oldCompoundDefaults, compoundDefaults.get(null)); assertSame(oldCompoundGroups, compoundGroups.get(null));
        Crypto.DISPATCH_KEY = oldDispatchKey; Crypto.DISPATCH_SEED = oldDispatchSeed;
        Crypto.ENCRYPT_KEY = oldEncryptKey; Crypto.ENCRYPT_SEED = oldEncryptSeed;
        Crypto.ENCRYPT_SEED_BUFFER = oldEncryptSeedBuffer; Crypto.CUR_SIGNING_KEY = oldSigningKey;
        if (oldEncryptionKeys != null) {
            oldEncryptionKeys.clear(); oldEncryptionKeys.putAll(oldEncryptionEntries); Crypto.EncryptionKeys = oldEncryptionKeys;
        }
        assertEquals(oldLoadedAll, ResourceLoader.isLoadedAll());
        // Keep this case's UUID database in the independent dbpath for main-line review.
        if (client != null) client.close();
    }

    @Test
    void as01TwoBirthAvatarsRebindWithExistingLoadUpdates() throws Exception {
        loadAll();
        Player warm = seed();
        Map<Integer, Document> saved = rows(warm);
        Map<Integer, AvatarData> oldDefinitions = new HashMap<>();
        for (Avatar avatar : warm.getAvatars()) oldDefinitions.put(avatar.getAvatarId(), avatar.getAvatarData());
        GameData.getAvatarDataMap().clear();
        loadAll();
        Map<String, Integer> before = writeSnapshot();
        int commandStart = commands.size();
        Player cold = player(UID);
        cold.getAvatars().loadFromDatabase();
        drain();
        assertTrue(cold.getAvatars().isLoaded());
        assertFalse(cold.getInventory().isLoaded(), "positive scope ends before Inventory/postLoad/default weapon");
        assertEquals(Set.of(10000005, 10000007), cold.getAvatars().getAvatars().keySet());
        assertWrites(before, "update:avatars", 2, "as01-cold-existing-save");
        Set<ObjectId> updatedIds = new HashSet<>();
        for (Document command : commands.subList(commandStart, commands.size()))
            for (Document update : command.getList("updates", Document.class))
                assertTrue(updatedIds.add(update.get("q", Document.class).getObjectId("_id")), "same avatar updated twice");
        assertEquals(saved.values().stream().map(row -> row.getObjectId("_id")).collect(java.util.stream.Collectors.toSet()), updatedIds);
        assertEquals(2, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(0, store.getDatabase().getCollection("items").countDocuments());
        Set<Long> lowGuids = new HashSet<>();
        for (Avatar avatar : cold.getAvatars()) {
            Avatar original = warm.getAvatars().getAvatarById(avatar.getAvatarId());
            Document savedRow = saved.get(avatar.getAvatarId());
            assertNotSame(original, avatar);
            assertNotSame(oldDefinitions.get(avatar.getAvatarId()), avatar.getAvatarData());
            assertSame(GameData.getAvatarDataMap().get(avatar.getAvatarId()), avatar.getAvatarData());
            assertSame(GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId()), avatar.getSkillDepot());
            assertSame(cold, avatar.getPlayer());
            assertState(avatar, savedRow);
            assertSame(avatar, cold.getAvatars().getAvatarByGuid(avatar.getGuid()));
            assertNotEquals(original.getGuid(), avatar.getGuid());
            lowGuids.add(avatar.getGuid() & 0xffffffffL);
            assertSelectedFields(savedRow, row(avatar.getObjectId()));
        }
        assertEquals(Set.of(1L, 2L), lowGuids);
        assertEquals(0, cold.getSession().getPacketsSent());
        snapshot("as01-cold", saved);
    }

    @Test
    void as02MissingAvatarOrStoredDepotDefinitionsPreserveRawBson() throws Exception {
        loadAll();
        Player warm = seed();
        Map<Integer, Document> saved = rows(warm);
        Map<ObjectId, byte[]> raw = rawRows(saved);
        GameData.getAvatarDataMap().clear();
        assertEmptyNaturalRecovery(UID, raw, "as02-missing-avatar");
        loadAll();
        GameData.getAvatarSkillDepotDataMap().remove(501);
        GameData.getAvatarSkillDepotDataMap().remove(701);
        assertTrue(GameData.getAvatarSkillDepotDataMap().containsKey(704));
        assertEmptyNaturalRecovery(UID, raw, "as02-missing-stored-depot");
        snapshot("as02-preserved", saved);
    }

    @Test
    void as03DifferentUidCannotBindSavedAvatars() throws Exception {
        loadAll();
        Player warm = seed();
        Map<Integer, Document> saved = rows(warm);
        assertEmptyNaturalRecovery(UID + 1, rawRows(saved), "as03-other-uid");
        snapshot("as03-preserved", saved);
    }

    private Player seed() throws Exception {
        Player player = player(UID);
        player.getNextGameGuid(); player.getNextGameGuid();
        Map<String, Integer> before = writeSnapshot();
        int exp = 17;
        for (int id : new int[] {10000005, 10000007}) {
            Avatar avatar = new Avatar(GameData.getAvatarDataMap().get(id));
            avatar.setExp(exp);
            assertTrue(avatar.getEquips().isEmpty()); assertNull(avatar.getWeapon());
            assertTrue(player.getAvatars().addAvatar(avatar));
            assertSame(player, avatar.getPlayer());
            exp = 29;
        }
        drain();
        assertWrites(before, "insert:avatars", 2, "seed");
        assertEquals(2, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(0, store.getDatabase().getCollection("items").countDocuments());
        Set<ObjectId> ids = new HashSet<>();
        Set<Long> guids = new HashSet<>();
        for (Avatar avatar : player.getAvatars()) {
            assertNotNull(avatar.getObjectId()); ids.add(avatar.getObjectId());
            guids.add(avatar.getGuid() & 0xffffffffL);
            Document row = row(avatar.getObjectId());
            assertState(avatar, row);
            assertEquals(avatar.getAvatarId() == 10000005 ? 501 : 701, avatar.getSkillDepotId());
            assertEquals(avatar.getAvatarId() == 10000005 ? 17 : 29, row.getInteger("exp"));
            assertEquals(1, row.getInteger("level")); assertEquals(0, row.getInteger("promoteLevel"));
            for (String key : List.of("guid", "owner", "avatarData", "skillDepot", "equips", "fightProperties", "saveGuard", "saveStamp"))
                assertFalse(row.containsKey(key), "transient/unsupported persistence field " + key);
        }
        assertEquals(2, ids.size()); assertEquals(Set.of(3L, 4L), guids);
        assertEquals(0, player.getSession().getPacketsSent());
        snapshot("seed", rows(player));
        return player;
    }

    private void assertEmptyNaturalRecovery(int uid, Map<ObjectId, byte[]> original, String stage) throws Exception {
        Map<String, Integer> before = writeSnapshot();
        List<Long> beforeCompleted = DatabaseHelper.queueStatus().stream().map(DatabaseWriteQueue.Snapshot::completed).toList();
        Player cold = player(uid);
        cold.getAvatars().loadFromDatabase();
        assertTrue(cold.getAvatars().isLoaded()); assertEquals(0, cold.getAvatars().getAvatarCount());
        cold.getInventory().loadFromDatabase();
        assertTrue(cold.getInventory().isLoaded()); assertTrue(cold.getInventory().getItems().isEmpty());
        assertEquals(0, cold.getSession().getPacketsSent());
        drain();
        assertEquals(before, writeSnapshot(), "unexpected business write during " + stage);
        assertEquals(beforeCompleted, DatabaseHelper.queueStatus().stream().map(DatabaseWriteQueue.Snapshot::completed).toList());
        assertEquals(2, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(0, store.getDatabase().getCollection("items").countDocuments());
        for (var entry : original.entrySet()) assertArrayEquals(entry.getValue(), raw(entry.getKey()), stage);
        System.out.println("D02_AVATAR_LOCAL " + stage + " writes=" + writeSnapshot() + " queues=" + DatabaseHelper.queueStatus());
    }

    private static void assertState(Avatar avatar, Document row) {
        assertEquals(row.getObjectId("_id"), avatar.getObjectId());
        assertEquals(UID, avatar.getOwnerId()); assertEquals(UID, row.getInteger("ownerId"));
        assertEquals(row.getInteger("avatarId"), avatar.getAvatarId());
        assertEquals(row.getInteger("skillDepotId"), avatar.getSkillDepotId());
        assertEquals(row.getInteger("level"), avatar.getLevel()); assertEquals(row.getInteger("exp"), avatar.getExp());
        assertEquals(row.getInteger("promoteLevel"), avatar.getPromoteLevel());
        assertEquals(row.getInteger("bornTime"), avatar.getBornTime()); assertEquals(row.getInteger("flyCloak"), avatar.getFlyCloak());
        assertEquals(row.getInteger("fetterLevel"), avatar.getFetterLevel());
        assertEquals(0, avatar.getTrialAvatarId()); assertEquals(0, row.getInteger("trialAvatarId"));
        assertEquals((long) UID, avatar.getGuid() >>> 32);
        assertTrue(avatar.getEquips().isEmpty()); assertNull(avatar.getWeapon());
    }

    private static void assertSelectedFields(Document before, Document after) {
        for (String key : List.of("_id", "ownerId", "avatarId", "skillDepotId", "level", "exp", "promoteLevel", "bornTime", "flyCloak", "fetterLevel", "trialAvatarId", "grantReason", "fromParentQuestId"))
            assertEquals(before.get(key), after.get(key), "persisted field changed: " + key);
    }

    private void drain() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        List<DatabaseWriteQueue.Snapshot> now = DatabaseHelper.queueStatus();
        for (int i = 0; i < now.size(); i++) {
            var before = queueBaseline.get(i); var after = now.get(i);
            assertEquals(before.name(), after.name()); assertEquals(0, after.outstanding()); assertEquals(0, after.queued());
            assertEquals(before.failed(), after.failed()); assertEquals(before.rejected(), after.rejected());
            assertEquals(before.cancelled(), after.cancelled());
        }
    }

    private void assertWrites(Map<String, Integer> before, String expected, int count, String stage) {
        Map<String, Integer> delta = new TreeMap<>();
        writeSnapshot().forEach((key, value) -> { int n = value - before.getOrDefault(key, 0); if (n != 0) delta.put(key, n); });
        assertEquals(Map.of(expected, count), delta, "unexpected command/collection count during " + stage);
        System.out.println("D02_AVATAR_LOCAL " + stage + " delta=" + delta + " queues=" + DatabaseHelper.queueStatus());
    }

    private Map<String, Integer> writeSnapshot() {
        Map<String, Integer> result = new TreeMap<>(); writes.forEach((key, value) -> result.put(key, value.get())); return result;
    }
    private Document row(ObjectId id) {
        Document result = store.getDatabase().getCollection("avatars").find(new Document("_id", id)).first();
        assertNotNull(result); return result;
    }
    private byte[] raw(ObjectId id) {
        RawBsonDocument result = store.getDatabase().getCollection("avatars", RawBsonDocument.class).find(new Document("_id", id)).first();
        assertNotNull(result); var buffer = result.getByteBuffer().asNIO(); byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
    }
    private Map<Integer, Document> rows(Player player) {
        Map<Integer, Document> result = new TreeMap<>();
        for (Avatar avatar : player.getAvatars()) result.put(avatar.getAvatarId(), row(avatar.getObjectId())); return result;
    }
    private Map<ObjectId, byte[]> rawRows(Map<Integer, Document> rows) {
        Map<ObjectId, byte[]> result = new LinkedHashMap<>();
        for (Document row : rows.values()) { ObjectId id = row.getObjectId("_id"); result.put(id, raw(id)); } return result;
    }
    private void snapshot(String stage, Map<Integer, Document> original) throws Exception {
        String value = System.getProperty("d02.avatar.save.evidence");
        if (value != null && !value.isBlank()) {
            Path output = Path.of(value).resolve(dbName); Files.createDirectories(output);
            StringBuilder rows = new StringBuilder();
            for (Document row : original.values()) {
                ObjectId id = row.getObjectId("_id"); rows.append(row(id).toJson()).append('\n');
                Files.write(output.resolve(stage + "-" + row.getInteger("avatarId") + ".bson"), raw(id));
            }
            Files.writeString(output.resolve(stage + ".jsonl"), rows.toString());
            Files.writeString(output.resolve(stage + "-commands.json"), JsonUtils.encode(commands));
        }
        System.out.println("D02_AVATAR_LOCAL " + stage + " database=" + dbName + " writes=" + writeSnapshot());
    }
    private Player player(int uid) throws Exception {
        Map<String, Integer> before = writeSnapshot();
        Account account = new Account(); account.setId(Integer.toString(uid));
        GameSession session = new GameSession(null); session.setAccount(account);
        Player player = new Player(session); player.setUid(uid);
        assertSame(account, player.getAccount()); assertSame(session, player.getSession());
        assertNotNull(player.getTeamManager()); assertSame(player, player.getTeamManager().getPlayer());
        assertNull(session.getServer()); assertFalse(session.isLoggedIn()); assertEquals(0, session.getPacketsSent());
        drain(); assertEquals(before, writeSnapshot(), "public local Player constructor must not persist business data");
        return player;
    }
    private static void setStore(Datastore datastore) throws Exception {
        Field field = DatabaseManager.class.getDeclaredField("gameDatastore"); field.setAccessible(true); field.set(null, datastore);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void isolateMap(Map<?, ?> map) {
        assertNotNull(map); Map<Object, Object> original = (Map) map; maps.add(original); originals.add(new LinkedHashMap<>(original)); original.clear();
    }

    private void loadAll() throws Exception {
        JsonArray skills = new JsonArray();
        for (int id : new int[] {100540, 100550, 10067, 10068}) {
            JsonObject row = fragment(mapping.getAsJsonArray("javaProjectionFragments"), "AvatarSkill", id);
            assertTrue(row.get("fragmentOnly").getAsBoolean());
            JsonObject fields = row.getAsJsonObject("javaFields");
            assertEquals(Set.of("id", "costElemType"), fields.keySet());
            assertEquals(id, fields.get("id").getAsInt());
            skills.add(fields.deepCopy());
        }
        ResourceLoader.loadFromResource(AvatarSkillData.class, write("skills.json", skills), GameData.getAvatarSkillDataMap());
        assertEquals(4, GameData.getAvatarSkillDataMap().size());
        JsonArray depots = projection.getAsJsonArray("mergedDepots").deepCopy();
        JsonArray fragments = projection.getAsJsonArray("depotFragments");
        assertEquals(3, depots.size()); assertEquals(3, fragments.size());
        for (JsonElement element : depots) {
            JsonObject fields = element.getAsJsonObject();
            assertEquals(Set.of("id", "skills", "energySkill", "talents"), fields.keySet());
            int id = fields.get("id").getAsInt();
            JsonObject projected = fragment(fragments, "AvatarSkillDepot", id);
            JsonObject bridge = fragment(mapping.getAsJsonArray("javaProjectionFragments"), "AvatarSkillDepot", id);
            assertEquals(bridge.get("source"), projected.get("source"));
            assertEquals(projected.getAsJsonObject("javaFields").get("talents"), fields.get("talents"));
        }
        ResourceLoader.loadFromResource(AvatarSkillDepotData.class, write("depots.json", depots), GameData.getAvatarSkillDepotDataMap());
        assertEquals(3, GameData.getAvatarSkillDepotDataMap().size());
        JsonArray curves = new JsonArray();
        for (JsonElement element : promote.getAsJsonObject("javaProjection").getAsJsonArray("completeAvatarCurveInputRows"))
            curves.add(element.getAsJsonObject().getAsJsonObject("javaFields").deepCopy());
        assertEquals(100, curves.size());
        ResourceLoader.loadFromResource(AvatarCurveData.class, write("curves.json", curves), GameData.getAvatarCurveDataMap());
        assertEquals(100, GameData.getAvatarCurveDataMap().size());
        JsonObject group = fragment(promote.getAsJsonObject("javaProjection").getAsJsonArray("completeBirthPromoteInputRows"), "AvatarPromote", 12 << 8);
        JsonArray promoteRows = new JsonArray(); promoteRows.add(group.getAsJsonObject("javaFields").deepCopy());
        ResourceLoader.loadFromResource(AvatarPromoteData.class, write("promote.json", promoteRows), promoteMap());
        assertEquals(Set.of(12 << 8), promoteMap().keySet());
        JsonArray avatars = projection.getAsJsonArray("birthAvatarInputs");
        assertEquals(2, avatars.size());
        ResourceLoader.loadFromResource(AvatarData.class, write("avatars.json", avatars), GameData.getAvatarDataMap());
        assertEquals(Set.of(10000005, 10000007), GameData.getAvatarDataMap().keySet());
    }
    private Path write(String name, JsonArray rows) throws Exception {
        return Files.writeString(directory.resolve(name), JsonUtils.encode(rows), StandardCharsets.UTF_8);
    }
    private static JsonObject fragment(JsonArray rows, String table, int id) {
        JsonObject found = null;
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            if (table.equals(row.get("table").getAsString()) && row.get("key").getAsInt() == id) {
                assertNull(found, "duplicate " + table + "/" + id); found = row;
            }
        }
        assertNotNull(found, "missing " + table + "/" + id); return found;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Int2ObjectMap<AvatarPromoteData> promoteMap() {
        return (Int2ObjectMap) GameData.getMapByResourceDef(AvatarPromoteData.class);
    }
    private static JsonObject report(String property, String expected) throws Exception {
        String value = System.getProperty(property); assertNotNull(value, "required property " + property); assertFalse(value.isBlank());
        byte[] bytes = Files.readAllBytes(Path.of(value));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), property);
        return JsonUtils.decode(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
    }
}

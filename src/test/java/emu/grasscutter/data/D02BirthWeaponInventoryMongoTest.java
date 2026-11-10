package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.event.*;
import dev.morphia.*;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Only the approved, unequipped birth-weapon inventory persistence boundary. */
class D02BirthWeaponInventoryMongoTest {
    static final int ITEM = 11101, UID = 74441;
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static JsonObject fields;
    @TempDir Path directory;
    MongoClient client;
    Datastore previous, store;
    Int2ObjectMap<ItemData> previousItems;
    boolean previousLoadedAll, storeInstalled;
    String dbName;
    final Map<String, AtomicInteger> writes = new ConcurrentHashMap<>();

    @BeforeAll
    static void inputs() throws Exception {
        Grasscutter.getLogger();
        JsonObject equipment = fragment(pinned("d02.item.save.equipment", "50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc"));
        JsonObject affix = fragment(pinned("d02.item.save.affix", "9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2"));
        JsonObject bridgeReport = pinned("d02.item.save.gadget", "51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880");
        JsonObject bridge = fragment(bridgeReport);
        for (JsonObject part : List.of(equipment, affix, bridge)) assertTrue(part.get("fragmentOnly").getAsBoolean());
        JsonObject source = bridge.getAsJsonObject("fieldSource").getAsJsonObject("source");
        assertEquals(source, equipment.getAsJsonObject("source"));
        assertEquals(source, affix.getAsJsonObject("source"));
        assertEquals(Set.of("begin", "end", "sha256"), source.keySet());
        assertEquals(4, source.get("begin").getAsInt());
        assertEquals(400, source.get("end").getAsInt());
        assertEquals("c4ef8b265f69900cf3330ea83666d039d7f5d753266994e6056039ca36202e9a", source.get("sha256").getAsString());
        JsonObject fs = bridge.getAsJsonObject("fieldSource");
        assertEquals(88968, fs.get("owner").getAsInt());
        assertEquals(442107, fs.get("fieldIndex").getAsInt());
        assertEquals("0x2c", fs.get("offset").getAsString());
        assertEquals(58209, fs.get("type").getAsInt());
        assertEquals("f294755f", fs.get("encodedBits").getAsString());
        JsonObject eq = equipment.getAsJsonObject("javaFields");
        JsonObject af = affix.getAsJsonObject("javaFields");
        JsonObject ga = bridge.getAsJsonObject("javaFields");
        assertEquals(Set.of("id", "itemType", "weaponPromoteId", "weaponProp"), eq.keySet());
        assertEquals(Set.of("id", "skillAffix"), af.keySet());
        assertEquals(Set.of("id", "gadgetId"), ga.keySet());
        for (JsonObject part : List.of(eq, af, ga)) assertEquals(ITEM, part.get("id").getAsInt());
        assertEquals(50011101, ga.get("gadgetId").getAsInt());
        assertEquals(JsonParser.parseString("[0,0]"), af.get("skillAffix"));
        fields = eq.deepCopy();
        fields.add("skillAffix", af.get("skillAffix").deepCopy());
        fields.add("gadgetId", ga.get("gadgetId").deepCopy());
        assertEquals(Set.of("id", "itemType", "weaponPromoteId", "weaponProp", "skillAffix", "gadgetId"), fields.keySet());
        JsonObject nativeCase = null;
        for (JsonElement e : bridgeReport.getAsJsonArray("cases")) {
            JsonObject c = e.getAsJsonObject();
            if ("original-11101".equals(c.get("name").getAsString())) {
                assertNull(nativeCase);
                nativeCase = c;
            }
        }
        assertNotNull(nativeCase);
        assertEquals(source, nativeCase.getAsJsonObject("source"));
        assertEquals(ITEM, nativeCase.get("weaponId").getAsInt());
        assertEquals(50011101, nativeCase.get("decodedId").getAsInt());
        assertEquals("5f7594f2", nativeCase.get("originalEncodedWord").getAsString());
        assertTrue(nativeCase.get("actualParsedGadgetSamePointer").getAsBoolean());
        assertTrue(nativeCase.get("selectedCacheOnly").getAsBoolean());
        assertFalse(nativeCase.get("expectedMissing").getAsBoolean());
        assertFalse(nativeCase.get("continuousNaturalFactory").getAsBoolean());
    }

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri, "LUNAGC_TEST_MONGO_URI is required");
        assertFalse(uri.isBlank(), "LUNAGC_TEST_MONGO_URI is required");
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        previous = DatabaseManager.getGameDatastore();
        previousItems = new Int2ObjectOpenHashMap<>(GameData.getItemDataMap());
        previousLoadedAll = ResourceLoader.isLoadedAll();
        assertTrue(GameData.getCodexWeaponDataIdMap().isEmpty(), "new worker must not have a Codex weapon index");
        dbName = "lunagc_d02_item_save_" + UUID.randomUUID().toString().replace("-", "");
        CommandListener listener = new CommandListener() {
            @Override public void commandStarted(CommandStartedEvent e) {
                String name = e.getCommandName();
                if (dbName.equals(e.getDatabaseName()) && Set.of("insert", "update", "delete", "findAndModify").contains(name)) {
                    String collection = e.getCommand().getString(name).getValue();
                    writes.computeIfAbsent(name + ":" + collection, k -> new AtomicInteger()).incrementAndGet();
                }
            }
        };
        client = MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri)).addCommandListener(listener).build());
        store = Morphia.createDatastore(client, dbName, MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
        store.getMapper().map(GameItem.class, Avatar.class);
        setStore(store);
        storeInstalled = true;
        GameData.getItemDataMap().clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (storeInstalled) {
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
            setStore(previous);
        }
        if (previousItems != null) {
            GameData.getItemDataMap().clear();
            GameData.getItemDataMap().putAll(previousItems);
            assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
        }
        if (store != null) store.getDatabase().drop();
        if (client != null) client.close();
    }

    @Test
    void is01TwoInstancesRebindAndColdGuardSaves() throws Exception {
        ItemData a = load();
        Player warm = player(UID);
        warm.getNextGameGuid(); warm.getNextGameGuid();
        GameItem first = item(warm, 17, true), second = item(warm, 29, false);
        assertEquals(3, first.getGuid() & 0xffffffffL);
        assertEquals(4, second.getGuid() & 0xffffffffL);
        Map<String, Integer> beforeSave = writeSnapshot();
        first.save(); second.save(); drain();
        assertOnlyItemWrites(beforeSave, "is01-insert");
        assertNotEquals(first.getObjectId(), second.getObjectId());
        assertEquals(2, store.getDatabase().getCollection("items").countDocuments());
        Document firstBson = row(first.getObjectId()), secondBson = row(second.getObjectId());
        assertInitial(firstBson, 17, true); assertInitial(secondBson, 29, false);
        snapshot("is01-saved", firstBson, secondBson);
        GameData.getItemDataMap().clear();
        ItemData b = load();
        assertNotSame(a, b);
        Map<String, Integer> before = writeSnapshot();
        Player cold = recover(UID);
        assertEquals(2, cold.getInventory().getItems().size());
        assertEquals(2, cold.getInventory().getInventoryTab(ItemType.ITEM_WEAPON).getSize());
        assertEquals(2, cold.getInventory().getItemCountById(ITEM));
        GameItem one = byId(cold, first.getObjectId()), two = byId(cold, second.getObjectId());
        assertNotSame(first, one); assertNotSame(second, two);
        assertState(one, firstBson, b); assertState(two, secondBson, b);
        assertEquals(Set.of(1L, 2L), Set.of(one.getGuid() & 0xffffffffL, two.getGuid() & 0xffffffffL));
        assertEquals(firstBson, row(first.getObjectId())); assertEquals(secondBson, row(second.getObjectId()));
        assertNoWrites(before, "is01-cold");
        snapshot("is01-cold", row(first.getObjectId()), row(second.getObjectId()));
        beforeSave = writeSnapshot();
        one.setExp(47); one.setLocked(false); one.save(); drain();
        assertOnlyItemWrites(beforeSave, "is01-resave");
        Document updated = row(one.getObjectId());
        assertEquals(2L, stamp(updated).get("revision"));
        assertNotEquals(stamp(firstBson).getString("token"), stamp(updated).getString("token"));
        assertEquals(47, updated.getInteger("exp")); assertFalse(updated.getBoolean("locked"));
        assertEquals(secondBson, row(two.getObjectId()));
        snapshot("is01-resaved", updated, row(two.getObjectId()));
        before = writeSnapshot();
        Player again = recover(UID);
        assertEquals(2, again.getInventory().getItems().size());
        assertState(byId(again, one.getObjectId()), updated, b);
        assertState(byId(again, two.getObjectId()), secondBson, b);
        assertEquals(updated, row(one.getObjectId())); assertEquals(secondBson, row(two.getObjectId()));
        assertNoWrites(before, "is01-reloaded");
        snapshot("is01-reloaded", row(one.getObjectId()), row(two.getObjectId()));
    }

    @Test
    void is02MissingDefinitionPreservesBsonAndLaterRebinds() throws Exception {
        load(); GameItem item = item(player(UID), 17, true);
        Map<String, Integer> beforeSave = writeSnapshot(); item.save(); drain();
        assertOnlyItemWrites(beforeSave, "is02-insert");
        Document original = row(item.getObjectId()); assertInitial(original, 17, true);
        GameData.getItemDataMap().remove(ITEM);
        Map<String, Integer> before = writeSnapshot();
        Player missing = recover(UID);
        assertTrue(missing.getInventory().getItems().isEmpty());
        assertEquals(original, row(item.getObjectId())); assertNoWrites(before, "is02-missing");
        snapshot("is02-missing", original, row(item.getObjectId()));
        ItemData b = load(); before = writeSnapshot();
        Player restored = recover(UID);
        assertEquals(1, restored.getInventory().getItems().size());
        assertState(byId(restored, item.getObjectId()), original, b);
        assertEquals(original, row(item.getObjectId())); assertNoWrites(before, "is02-restored");
        snapshot("is02", original, row(item.getObjectId()));
    }

    @Test
    void is03OtherOwnerCannotSeeItems() throws Exception {
        ItemData definition = load(); GameItem item = item(player(UID), 29, false);
        Map<String, Integer> beforeSave = writeSnapshot(); item.save(); drain();
        assertOnlyItemWrites(beforeSave, "is03-insert");
        Document original = row(item.getObjectId()); assertInitial(original, 29, false);
        Map<String, Integer> before = writeSnapshot();
        Player other = recover(UID + 1);
        assertTrue(other.getInventory().getItems().isEmpty());
        assertEquals(original, row(item.getObjectId())); assertNoWrites(before, "is03-other");
        snapshot("is03-other", original, row(item.getObjectId()));
        before = writeSnapshot(); Player owner = recover(UID);
        assertEquals(1, owner.getInventory().getItems().size());
        assertState(byId(owner, item.getObjectId()), original, definition);
        assertEquals(original, row(item.getObjectId())); assertNoWrites(before, "is03-owner");
        snapshot("is03", original, row(item.getObjectId()));
    }

    private ItemData load() throws Exception {
        JsonArray rows = new JsonArray(); rows.add(fields.deepCopy());
        Path path = Files.writeString(directory.resolve("WeaponExcelConfigData.json"), rows.toString());
        ResourceLoader.loadFromResource(ItemData.class, path, GameData.getItemDataMap());
        assertEquals(Set.of(ITEM), GameData.getItemDataMap().keySet());
        ItemData d = GameData.getItemDataMap().get(ITEM);
        assertEquals(ITEM, d.getId()); assertEquals("ITEM_WEAPON", d.getItemType().name());
        assertEquals("EQUIP_WEAPON", d.getEquipType().name());
        assertEquals(ITEM, d.getWeaponPromoteId()); assertEquals(50011101, d.getGadgetId());
        assertArrayEquals(new int[] {0, 0}, d.getSkillAffix());
        assertEquals(2, d.getWeaponProperties().length);
        assertEquals("FIGHT_PROP_BASE_ATTACK", d.getWeaponProperties()[0].getPropType().name());
        assertEquals("GROW_CURVE_ATTACK_101", d.getWeaponProperties()[0].getType());
        assertEquals(0x41b9f5c3, Float.floatToRawIntBits(d.getWeaponProperties()[0].getInitValue()));
        assertEquals("FIGHT_PROP_NONE", d.getWeaponProperties()[1].getPropType().name());
        assertEquals("GROW_CURVE_CRITICAL_101", d.getWeaponProperties()[1].getType());
        assertEquals(0, Float.floatToRawIntBits(d.getWeaponProperties()[1].getInitValue()));
        return d;
    }

    private GameItem item(Player p, int exp, boolean locked) {
        GameItem item = new GameItem(ITEM); assertSame(GameData.getItemDataMap().get(ITEM), item.getItemData());
        item.setOwner(p); item.setExp(exp); item.setLocked(locked);
        assertEquals(1, item.getCount()); assertEquals(1, item.getLevel());
        assertEquals(0, item.getPromoteLevel()); assertEquals(0, item.getRefinement());
        assertEquals(List.of(), item.getAffixes()); assertEquals(0, item.getEquipCharacter()); assertNull(item.getWeaponEntity());
        return item;
    }

    private Player recover(int uid) throws Exception {
        assertEquals(0, store.getDatabase().getCollection("avatars").countDocuments());
        Player p = player(uid); p.getAvatars().loadFromDatabase();
        assertTrue(p.getAvatars().isLoaded()); assertEquals(0, p.getAvatars().getAvatarCount());
        p.getInventory().loadFromDatabase(); assertTrue(p.getInventory().isLoaded()); drain(); return p;
    }

    private static Player player(int uid) { Player p = new Player(); p.setUid(uid); return p; }
    private static void drain() throws Exception { assertTrue(DatabaseHelper.awaitIdle(TIMEOUT)); }
    private static void setStore(Datastore ds) throws Exception {
        var f = DatabaseManager.class.getDeclaredField("gameDatastore"); f.setAccessible(true); f.set(null, ds);
    }
    private Document row(ObjectId id) {
        Document result = store.getDatabase().getCollection("items").find(new Document("_id", id)).first();
        assertNotNull(result); return result;
    }
    private static Document stamp(Document row) { return row.get("saveStamp", Document.class); }
    private static void assertInitial(Document row, int exp, boolean locked) {
        assertEquals(UID, row.getInteger("ownerId")); assertEquals(ITEM, row.getInteger("itemId"));
        assertEquals(1, row.getInteger("count")); assertEquals(1, row.getInteger("level"));
        assertEquals(0, row.getInteger("promoteLevel")); assertEquals(0, row.getInteger("refinement"));
        assertEquals(exp, row.getInteger("exp")); assertEquals(locked, row.getBoolean("locked"));
        assertEquals(0, row.getInteger("equipCharacter")); assertEquals(List.of(), row.get("affixes"));
        for (String key : List.of("guid", "itemData", "saveGuard", "weaponEntity", "newItem")) assertFalse(row.containsKey(key));
        assertEquals(1L, stamp(row).get("revision"));
        String token = stamp(row).getString("token"); assertEquals(token, UUID.fromString(token).toString());
    }
    private static void assertState(GameItem item, Document row, ItemData definition) {
        assertEquals(row.getObjectId("_id"), item.getObjectId()); assertEquals(row.getInteger("ownerId"), item.getOwnerId());
        assertEquals(row.getInteger("itemId"), item.getItemId()); assertEquals(row.getInteger("count"), item.getCount());
        assertEquals(row.getInteger("level"), item.getLevel()); assertEquals(row.getInteger("promoteLevel"), item.getPromoteLevel());
        assertEquals(row.getInteger("refinement"), item.getRefinement()); assertEquals(row.getInteger("exp"), item.getExp());
        assertEquals(row.getBoolean("locked"), item.isLocked()); assertEquals(row.get("affixes"), item.getAffixes());
        assertEquals(row.getInteger("equipCharacter"), item.getEquipCharacter()); assertNull(item.getWeaponEntity());
        assertSame(definition, item.getItemData()); assertEquals((long) item.getOwnerId(), item.getGuid() >>> 32);
    }
    private static GameItem byId(Player p, ObjectId id) {
        List<GameItem> matches = p.getInventory().getItems().values().stream().filter(i -> id.equals(i.getObjectId())).toList();
        assertEquals(1, matches.size()); GameItem item = matches.get(0);
        assertSame(item, p.getInventory().getItems().get(item.getGuid())); return item;
    }
    private Map<String, Integer> writeSnapshot() {
        Map<String, Integer> result = new TreeMap<>(); writes.forEach((k, v) -> result.put(k, v.get())); return result;
    }
    private void assertOnlyItemWrites(Map<String, Integer> before, String stage) {
        Map<String, Integer> after = writeSnapshot();
        int count = 0;
        for (var entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta != 0) {
                assertTrue(entry.getKey().endsWith(":items"), "unexpected write: " + entry.getKey());
                count += delta;
            }
        }
        System.out.println("D02_ITEM_SAVE " + stage + " writes-before=" + before + " writes-after=" + after);
        assertTrue(count > 0, "no item write observed");
    }
    private void assertNoWrites(Map<String, Integer> before, String stage) {
        Map<String, Integer> after = writeSnapshot();
        System.out.println("D02_ITEM_SAVE " + stage + " writes-before=" + before + " writes-after=" + after);
        assertEquals(before, after, "unexpected business write during " + stage);
    }
    private void snapshot(String stage, Document... rows) throws Exception {
        String path = System.getProperty("d02.item.save.evidence");
        if (path != null && !path.isBlank()) {
            Path output = Path.of(path); Files.createDirectories(output);
            StringBuilder data = new StringBuilder();
            for (Document row : rows) data.append(row.toJson()).append('\n');
            Files.writeString(output.resolve(stage + ".jsonl"), data.toString());
        }
        System.out.println("D02_ITEM_SAVE " + stage + " writes=" + writeSnapshot());
    }
    private static JsonObject fragment(JsonObject report) {
        JsonObject match = null;
        for (JsonElement e : report.getAsJsonArray("javaProjectionFragments")) {
            JsonObject c = e.getAsJsonObject();
            if ("Weapon".equals(c.get("table").getAsString()) && c.get("key").getAsInt() == ITEM) {
                assertNull(match); match = c;
            }
        }
        assertNotNull(match); return match;
    }
    private static JsonObject pinned(String property, String expected) throws Exception {
        String path = System.getProperty(property); assertNotNull(path, "required " + property); assertFalse(path.isBlank());
        byte[] bytes = Files.readAllBytes(Path.of(path));
        assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return JsonUtils.decode(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
    }
}

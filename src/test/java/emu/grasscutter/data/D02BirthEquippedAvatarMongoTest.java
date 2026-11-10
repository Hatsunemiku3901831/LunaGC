package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.weapon.WeaponCurveData;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.database.DatabaseWriteQueue;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarStorage;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.QuestCond;
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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.types.ObjectId;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Partial resources, real no-World birth equipment save and AvatarStorage/Inventory recovery. */
class D02BirthEquippedAvatarMongoTest {
    private static final int UID = 74461, ITEM = 11101;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static JsonObject projection, mapping, promote, equipmentReport, fields;
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
    private ThreadPoolExecutor questExecutor;
    private Map<String, Long> questBaseline;

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
        weaponInputs();
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
        isolateMap(GameData.getWeaponCurveDataMap());
        isolateMap(weaponPromoteMap());
        assertTrue(GameData.getCodexWeaponDataIdMap().isEmpty(), "new worker must not install a Codex weapon index");
        assertTrue(GameData.getTpsWeaponAccessoryDataMap().isEmpty(), "no approved TPS accessories in this local scope");
        assertNull(GameData.getQuestDataByConditions(QuestCond.QUEST_COND_HISTORY_GOT_ANY_ITEM, ITEM, ""), "history event must naturally end before server use");
        questExecutor = (ThreadPoolExecutor) QuestManager.eventExecutor;
        assertFalse(questExecutor.isShutdown());
        assertEquals(0, questExecutor.getActiveCount()); assertTrue(questExecutor.getQueue().isEmpty());
        questBaseline = questStatus();
        fettersCache.set(null, new HashMap<Integer, List<Integer>>());
        dbName = "lunagc_d02_equipped_" + UUID.randomUUID().toString().replace("-", "");
        CommandListener listener = new CommandListener() {
            @Override public void commandStarted(CommandStartedEvent event) {
                String name = event.getCommandName();
                if (dbName.equals(event.getDatabaseName()) && Set.of("insert", "update", "delete", "findAndModify").contains(name)) {
                    String collection = event.getCommand().getString(name).getValue();
                    writes.computeIfAbsent(name + ":" + collection, key -> new AtomicInteger()).incrementAndGet();
                    commands.add(Document.parse(event.getCommand().toJson(
                            JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build())));
                }
            }
        };
        client = MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri)).addCommandListener(listener).build());
        store = Morphia.createDatastore(client, dbName, MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
        store.getMapper().map(Avatar.class, GameItem.class, Player.class);
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
            if (maps.get(i) == (Object) GameData.getAvatarCurveDataMap() || maps.get(i) == (Object) GameData.getWeaponCurveDataMap())
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
        assertSame(questExecutor, QuestManager.eventExecutor);
        assertFalse(questExecutor.isShutdown());
        awaitQuestIdle();
        assertEquals(0, questExecutor.getActiveCount()); assertTrue(questExecutor.getQueue().isEmpty());
        System.out.println("D02_EQUIPPED executor-before=" + questBaseline + " after=" + questStatus());
        // Keep this case's UUID database in the independent dbpath for main-line review.
        if (client != null) client.close();
    }


    @Test
    void er01MaleBirthEquipmentRebindsTwice() throws Exception {
        equippedRecovery(10000005);
    }

    @Test
    void er02FemaleBirthEquipmentRebindsTwice() throws Exception {
        equippedRecovery(10000007);
    }

    @Test
    void er03MissingWeaponPreservesItemAndRemainsUnusableUntilRebound() throws Exception {
        loadAll();
        Player warm = seed(10000005);
        Avatar avatar = warm.getAvatars().getAvatarById(10000005);
        GameItem weapon = avatar.getWeaponNotNull();
        Document savedAvatar = row("avatars", avatar.getObjectId());
        Document savedItem = row("items", weapon.getObjectId());
        byte[] originalItem = raw("items", weapon.getObjectId()), originalPlayer = raw("players", UID);
        ItemData definition = GameData.getItemDataMap().remove(ITEM);
        assertNotNull(definition); assertTrue(GameData.getItemDataMap().isEmpty());
        assertEquals(100, GameData.getWeaponCurveDataMap().size());
        assertEquals(Set.of(12 << 8), promoteMap().keySet());
        assertEquals(Set.of(ITEM << 8), weaponPromoteMap().keySet());
        Map<String, Integer> before = writeSnapshot();
        int commandStart = commands.size();
        Player missing = player(UID);
        missing.getAvatars().loadFromDatabase();
        NullPointerException missingDefinition = assertThrows(
                NullPointerException.class, () -> missing.getInventory().loadFromDatabase());
        assertEquals(GameItem.class.getName(), missingDefinition.getStackTrace()[0].getClassName());
        assertEquals("<init>", missingDefinition.getStackTrace()[0].getMethodName());
        assertTrue(Arrays.stream(missingDefinition.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(AvatarStorage.class.getName())
                        && frame.getMethodName().equals("addStartingWeapon")));
        drain();
        assertTrue(missing.getAvatars().isLoaded()); assertFalse(missing.getInventory().isLoaded());
        assertEquals(1, missing.getAvatars().getAvatarCount());
        assertTrue(missing.getInventory().getItems().isEmpty());
        Avatar incomplete = missing.getAvatars().getAvatarById(avatar.getAvatarId());
        assertNull(incomplete.getWeapon());
        NullPointerException failure = assertThrows(NullPointerException.class, incomplete::getWeaponNotNull);
        assertEquals("Avatar does not have a weapon.", failure.getMessage());
        assertWrites(before, Map.of("update:avatars", 1), "er03-missing");
        assertUpdateTarget(commandStart, "avatars", avatar.getObjectId(), null);
        assertArrayEquals(originalItem, raw("items", weapon.getObjectId()));
        assertArrayEquals(originalPlayer, raw("players", UID));
        assertAvatarFields(savedAvatar, row("avatars", avatar.getObjectId()));
        assertCounts(); assertLocal(missing);
        snapshot("er03-missing");
        loadAll();
        assertNotSame(definition, GameData.getItemDataMap().get(ITEM));
        Player restored = recover(savedAvatar, savedItem, originalPlayer, "er03-restored");
        assertGraph(restored, savedAvatar, savedItem, warm);
    }

    private void equippedRecovery(int avatarId) throws Exception {
        loadAll();
        Player warm = seed(avatarId);
        Avatar original = warm.getAvatars().getAvatarById(avatarId);
        GameItem originalWeapon = original.getWeaponNotNull();
        AvatarData oldAvatarDefinition = original.getAvatarData();
        AvatarSkillDepotData oldDepotDefinition = original.getSkillDepot();
        ItemData oldItemDefinition = originalWeapon.getItemData();
        Document savedAvatar = row("avatars", original.getObjectId());
        Document savedItem = row("items", originalWeapon.getObjectId());
        byte[] savedPlayer = raw("players", UID);
        loadAll();
        assertNotSame(oldAvatarDefinition, GameData.getAvatarDataMap().get(avatarId));
        assertNotSame(oldDepotDefinition, GameData.getAvatarSkillDepotDataMap().get(original.getSkillDepotId()));
        assertNotSame(oldItemDefinition, GameData.getItemDataMap().get(ITEM));
        Player cold = recover(savedAvatar, savedItem, savedPlayer, "er-cold");
        assertGraph(cold, savedAvatar, savedItem, warm);
        Document firstColdItem = row("items", originalWeapon.getObjectId());
        Avatar coldAvatar = cold.getAvatars().getAvatarById(avatarId);
        ItemData firstColdDefinition = coldAvatar.getWeaponNotNull().getItemData();
        loadAll();
        assertNotSame(coldAvatar.getAvatarData(), GameData.getAvatarDataMap().get(avatarId));
        assertNotSame(firstColdDefinition, GameData.getItemDataMap().get(ITEM));
        Player again = recover(savedAvatar, firstColdItem, savedPlayer, "er-again");
        assertGraph(again, savedAvatar, firstColdItem, warm);
        assertNotSame(coldAvatar, again.getAvatars().getAvatarById(avatarId));
        assertNotSame(coldAvatar.getWeaponNotNull(), again.getAvatars().getAvatarById(avatarId).getWeaponNotNull());
    }

    private Player seed(int avatarId) throws Exception {
        Player warm = player(UID);
        warm.getNextGameGuid(); warm.getNextGameGuid();
        Avatar avatar = new Avatar(GameData.getAvatarDataMap().get(avatarId));
        avatar.setExp(avatarId == 10000005 ? 17 : 29);
        Map<String, Integer> before = writeSnapshot();
        assertTrue(warm.getAvatars().addAvatar(avatar));
        drain();
        assertWrites(before, Map.of("insert:avatars", 1), "seed-avatar");
        assertNull(warm.getBattlePassManager()); assertNull(warm.getActivityManager());
        assertNull(GameData.getQuestDataByConditions(QuestCond.QUEST_COND_HISTORY_GOT_ANY_ITEM, ITEM, ""));
        Map<String, Long> beforeQuest = questStatus();
        before = writeSnapshot();
        warm.getAvatars().addStartingWeapon(avatar);
        // On the original executor, this FIFO barrier follows the natural history event.
        QuestManager.eventExecutor.submit(() -> {}).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        awaitQuestIdle();
        drain();
        Map<String, Integer> delta = writeDelta(before);
        int warmUpdates = delta.getOrDefault("update:items", 0);
        assertTrue(warmUpdates == 0 || warmUpdates == 1, "the two natural item saves may coalesce");
        Map<String, Integer> expected = new TreeMap<>(Map.of("insert:items", 1, "insert:players", 1));
        if (warmUpdates == 1) expected.put("update:items", 1);
        assertEquals(expected, delta, "unapproved collection or repeated grant during default weapon admission");
        assertEquals(beforeQuest.get("tasks") + 2, questStatus().get("tasks"));
        assertEquals(beforeQuest.get("completed") + 2, questStatus().get("completed"));
        assertEquals(0L, questStatus().get("active")); assertEquals(0L, questStatus().get("queued"));
        assertNull(GameData.getQuestDataByConditions(QuestCond.QUEST_COND_HISTORY_GOT_ANY_ITEM, ITEM, ""));
        GameItem weapon = avatar.getWeaponNotNull();
        assertEquals(1L + warmUpdates, stamp(row("items", weapon.getObjectId())).getLong("revision"));
        assertEquals(3L, avatar.getGuid() & 0xffffffffL); assertEquals(4L, weapon.getGuid() & 0xffffffffL);
        assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) > 0.5f);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0.5f);
        weapon.setExp(37); weapon.setLocked(true);
        Document beforeControl = row("items", weapon.getObjectId());
        before = writeSnapshot();
        avatar.save(); weapon.save(); drain();
        assertWrites(before, Map.of("update:avatars", 1, "update:items", 1), "seed-controlled-save");
        assertGuardAdvance(beforeControl, row("items", weapon.getObjectId()));
        Document avatarRow = row("avatars", avatar.getObjectId());
        Document itemRow = row("items", weapon.getObjectId());
        assertEquals(0.5f, ((Number) avatarRow.get("currentHp")).floatValue());
        assertEquals(37, itemRow.getInteger("exp")); assertTrue(itemRow.getBoolean("locked"));
        Document playerRow = row("players", UID);
        assertEquals(UID, playerRow.getInteger("_id"));
        assertEquals(Integer.toString(UID), playerRow.getString("accountId"));
        assertEquals(1L, stamp(playerRow).getLong("revision")); assertStamp(playerRow);
        Document history = playerRow.get("playerProgress", Document.class).get("itemHistory", Document.class);
        assertEquals(Set.of(Integer.toString(ITEM)), history.keySet());
        Document entry = history.get(Integer.toString(ITEM), Document.class);
        assertEquals(ITEM, entry.getInteger("itemId")); assertEquals(1, entry.getInteger("obtainedCount"));
        assertEquals(1, warm.getPlayerProgress().getItemHistory().get(ITEM).getObtainedCount());
        for (String key : List.of("guid", "owner", "avatarData", "skillDepot", "equips", "fightProperties", "saveGuard", "saveStamp"))
            assertFalse(avatarRow.containsKey(key), "transient/unsupported Avatar field " + key);
        for (String key : List.of("guid", "itemData", "saveGuard", "weaponEntity", "newItem"))
            assertFalse(itemRow.containsKey(key), "transient Item field " + key);
        assertCounts(); assertLocal(warm); assertGraph(warm, avatarRow, itemRow, null);
        snapshot("seed");
        System.out.println("D02_EQUIPPED seed-admission-delta=" + delta + " executor-before=" + beforeQuest + " after=" + questStatus());
        return warm;
    }

    private Player recover(Document savedAvatar, Document beforeItem, byte[] savedPlayer, String stage) throws Exception {
        Map<String, Integer> before = writeSnapshot();
        Map<String, Long> beforeQuest = questStatus();
        int commandStart = commands.size();
        Player cold = player(UID);
        cold.getAvatars().loadFromDatabase();
        drain();
        assertTrue(cold.getAvatars().isLoaded()); assertFalse(cold.getInventory().isLoaded());
        assertWrites(before, Map.of("update:avatars", 1), stage + "-avatar");
        assertUpdateTarget(commandStart, "avatars", savedAvatar.getObjectId("_id"), null);
        before = writeSnapshot(); commandStart = commands.size();
        cold.getInventory().loadFromDatabase();
        drain();
        assertTrue(cold.getInventory().isLoaded());
        assertWrites(before, Map.of("update:items", 1), stage + "-inventory");
        assertUpdateTarget(commandStart, "items", beforeItem.getObjectId("_id"), beforeItem);
        Document afterItem = row("items", beforeItem.getObjectId("_id"));
        assertGuardAdvance(beforeItem, afterItem);
        Document beforeBusiness = new Document(beforeItem), afterBusiness = new Document(afterItem);
        beforeBusiness.remove("saveStamp"); afterBusiness.remove("saveStamp");
        assertEquals(beforeBusiness, afterBusiness, "cold equip must retain stored item business fields");
        assertAvatarFields(savedAvatar, row("avatars", savedAvatar.getObjectId("_id")));
        assertArrayEquals(savedPlayer, raw("players", UID), "cold local recovery must not save a new Player copy");
        assertEquals(beforeQuest, questStatus(), "cold equipment binding must not repeat obtain history events");
        assertCounts(); assertLocal(cold); snapshot(stage);
        return cold;
    }

    private void assertGraph(Player owner, Document avatarRow, Document itemRow, Player warm) {
        Avatar avatar = owner.getAvatars().getAvatarById(avatarRow.getInteger("avatarId"));
        GameItem weapon = avatar.getWeaponNotNull();
        assertEquals(1, owner.getAvatars().getAvatarCount()); assertEquals(1, avatar.getEquips().size());
        assertEquals(1, owner.getInventory().getItems().size());
        assertEquals(1, owner.getInventory().getInventoryTab(ItemType.ITEM_WEAPON).getSize());
        assertEquals(1, owner.getInventory().getItemCountById(ITEM));
        assertEquals(avatarRow.getObjectId("_id"), avatar.getObjectId()); assertEquals(UID, avatar.getOwnerId());
        assertEquals(UID, avatarRow.getInteger("ownerId")); assertEquals(UID, itemRow.getInteger("ownerId"));
        assertSame(owner, avatar.getPlayer()); assertSame(avatar, owner.getAvatars().getAvatarByGuid(avatar.getGuid()));
        assertEquals(avatarRow.getInteger("skillDepotId"), avatar.getSkillDepotId());
        assertEquals(avatarRow.getInteger("exp"), avatar.getExp());
        assertEquals(avatarRow.getInteger("level"), avatar.getLevel());
        assertEquals(avatarRow.getInteger("promoteLevel"), avatar.getPromoteLevel());
        assertEquals(1, avatar.getLevel()); assertEquals(0, avatar.getPromoteLevel());
        assertEquals(0.5f, avatar.getCurrentHp()); assertEquals(0.5f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) > 0.5f);
        assertSame(GameData.getAvatarDataMap().get(avatar.getAvatarId()), avatar.getAvatarData());
        assertSame(GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId()), avatar.getSkillDepot());
        assertEquals(itemRow.getObjectId("_id"), weapon.getObjectId()); assertEquals(UID, weapon.getOwnerId());
        assertEquals(ITEM, weapon.getItemId()); assertEquals(avatar.getAvatarId(), weapon.getEquipCharacter());
        assertEquals(itemRow.getInteger("equipCharacter"), weapon.getEquipCharacter());
        assertEquals(37, weapon.getExp()); assertTrue(weapon.isLocked());
        assertEquals(1, weapon.getCount()); assertEquals(1, weapon.getLevel());
        assertEquals(0, weapon.getPromoteLevel()); assertEquals(0, weapon.getRefinement());
        assertEquals(List.of(), weapon.getAffixes()); assertNull(weapon.getWeaponEntity());
        assertSame(GameData.getItemDataMap().get(ITEM), weapon.getItemData());
        assertSame(weapon, avatar.getEquipBySlot(EquipType.EQUIP_WEAPON));
        assertSame(weapon, owner.getInventory().getItemByGuid(weapon.getGuid()));
        assertEquals((long) UID, avatar.getGuid() >>> 32); assertEquals((long) UID, weapon.getGuid() >>> 32);
        assertNotEquals(avatar.getGuid(), weapon.getGuid());
        if (warm != null) {
            Avatar old = warm.getAvatars().getAvatarById(avatar.getAvatarId());
            assertNotSame(old, avatar); assertNotSame(old.getWeaponNotNull(), weapon);
            assertNotEquals(old.getGuid(), avatar.getGuid()); assertNotEquals(old.getWeaponNotNull().getGuid(), weapon.getGuid());
            assertEquals(1L, avatar.getGuid() & 0xffffffffL); assertEquals(2L, weapon.getGuid() & 0xffffffffL);
        }
    }

    private void assertUpdateTarget(int start, String collection, ObjectId id, Document beforeItem) {
        List<Document> selected = commands.subList(start, commands.size()).stream()
                .filter(c -> collection.equals(c.getString("update"))).toList();
        assertEquals(1, selected.size());
        List<Document> updates = selected.get(0).getList("updates", Document.class);
        assertEquals(1, updates.size()); Document update = updates.get(0), query = update.get("q", Document.class);
        assertEquals(id, query.getObjectId("_id"));
        if (beforeItem == null) {
            assertTrue(update.getBoolean("upsert", false));
        } else {
            assertFalse(update.getBoolean("upsert", false));
            assertEquals(UID, query.getInteger("ownerId")); assertEquals(ITEM, query.getInteger("itemId"));
            assertEquals(stamp(beforeItem).get("revision"), query.get("saveStamp.revision"));
            assertEquals(stamp(beforeItem).get("token"), query.get("saveStamp.token"));
        }
    }
    private static void assertAvatarFields(Document before, Document after) {
        for (String key : List.of("_id", "ownerId", "avatarId", "skillDepotId", "level", "exp", "promoteLevel", "bornTime", "flyCloak", "fetterLevel", "trialAvatarId", "grantReason", "fromParentQuestId", "currentHp"))
            assertEquals(before.get(key), after.get(key), "persisted Avatar field changed: " + key);
    }
    private static Document stamp(Document row) { return row.get("saveStamp", Document.class); }
    private static void assertStamp(Document row) {
        assertTrue(stamp(row).getLong("revision") > 0);
        String token = stamp(row).getString("token"); assertEquals(token, UUID.fromString(token).toString());
    }
    private static void assertGuardAdvance(Document before, Document after) {
        assertStamp(after); assertEquals(stamp(before).getLong("revision") + 1, stamp(after).getLong("revision"));
        assertNotEquals(stamp(before).getString("token"), stamp(after).getString("token"));
    }
    private void assertCounts() {
        assertEquals(Set.of("avatars", "items", "players"), new HashSet<>(store.getDatabase().listCollectionNames().into(new ArrayList<>())));
        for (String name : List.of("avatars", "items", "players")) assertEquals(1, store.getDatabase().getCollection(name).countDocuments());
    }
    private static void assertLocal(Player player) {
        assertNull(player.getWorld()); assertNull(player.getScene());
        assertNull(player.getSession().getServer()); assertFalse(player.getSession().isLoggedIn());
        assertFalse(player.hasSentLoginPackets()); assertEquals(0, player.getSession().getPacketsSent());
    }
    private Map<String, Long> questStatus() {
        return Map.of("tasks", questExecutor.getTaskCount(), "completed", questExecutor.getCompletedTaskCount(),
                "active", (long) questExecutor.getActiveCount(), "queued", (long) questExecutor.getQueue().size());
    }
    private void awaitQuestIdle() throws InterruptedException {
        // Future completion precedes ThreadPoolExecutor.afterExecute bookkeeping.
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (questExecutor.getActiveCount() != 0 || !questExecutor.getQueue().isEmpty()
                || questExecutor.getCompletedTaskCount() != questExecutor.getTaskCount()) {
            assertTrue(System.nanoTime() < deadline, "original Quest executor did not become idle");
            Thread.sleep(1);
        }
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
    private Map<String, Integer> writeDelta(Map<String, Integer> before) {
        Map<String, Integer> delta = new TreeMap<>();
        writeSnapshot().forEach((key, value) -> { int n = value - before.getOrDefault(key, 0); if (n != 0) delta.put(key, n); });
        return delta;
    }
    private void assertWrites(Map<String, Integer> before, Map<String, Integer> expected, String stage) {
        Map<String, Integer> delta = writeDelta(before);
        assertEquals(expected, delta, "unexpected command/collection count during " + stage);
        System.out.println("D02_EQUIPPED " + stage + " delta=" + delta + " queues=" + DatabaseHelper.queueStatus());
    }
    private Map<String, Integer> writeSnapshot() {
        Map<String, Integer> result = new TreeMap<>(); writes.forEach((key, value) -> result.put(key, value.get())); return result;
    }
    private Document row(String collection, Object id) {
        Document result = store.getDatabase().getCollection(collection).find(new Document("_id", id)).first();
        assertNotNull(result); return result;
    }
    private byte[] raw(String collection, Object id) {
        RawBsonDocument result = store.getDatabase().getCollection(collection, RawBsonDocument.class).find(new Document("_id", id)).first();
        assertNotNull(result); var buffer = result.getByteBuffer().asNIO(); byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
    }
    private void snapshot(String stage) throws Exception {
        String value = System.getProperty("d02.equipped.save.evidence");
        if (value != null && !value.isBlank()) {
            Path output = Path.of(value).resolve(dbName); Files.createDirectories(output);
            for (String collection : List.of("avatars", "items", "players")) {
                StringBuilder rows = new StringBuilder();
                for (Document row : store.getDatabase().getCollection(collection).find()) {
                    rows.append(row.toJson()).append('\n');
                    Files.write(output.resolve(stage + "-" + collection + "-" + row.get("_id") + ".bson"), raw(collection, row.get("_id")));
                }
                Files.writeString(output.resolve(stage + "-" + collection + ".jsonl"), rows.toString());
            }
            Files.writeString(output.resolve(stage + "-commands.json"), JsonUtils.encode(commands));
        }
        System.out.println("D02_EQUIPPED " + stage + " database=" + dbName + " writes=" + writeSnapshot());
    }
    private Player player(int uid) throws Exception {
        Map<String, Integer> before = writeSnapshot();
        Account account = new Account(); account.setId(Integer.toString(uid));
        GameSession session = new GameSession(null); session.setAccount(account);
        Player player = new Player(session); player.setUid(uid);
        assertSame(account, player.getAccount()); assertSame(session, player.getSession());
        assertNotNull(player.getTeamManager()); assertSame(player, player.getTeamManager().getPlayer());
        assertLocal(player);
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
        loadWeapons();
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

    private static void weaponInputs() throws Exception {
        equipmentReport = report("d02.item.save.equipment", "50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc");
        JsonObject equipment = fragment(equipmentReport.getAsJsonArray("javaProjectionFragments"), "Weapon", ITEM);
        JsonObject affix = fragment(report("d02.item.save.affix", "9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2").getAsJsonArray("javaProjectionFragments"), "Weapon", ITEM);
        JsonObject bridgeReport = report("d02.item.save.gadget", "51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880");
        JsonObject bridge = fragment(bridgeReport.getAsJsonArray("javaProjectionFragments"), "Weapon", ITEM);
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

    private void loadWeapons() throws Exception {
        JsonArray curves = new JsonArray(); Set<Integer> levels = new HashSet<>();
        for (JsonElement element : equipmentReport.getAsJsonArray("javaProjectionFragments")) {
            JsonObject row = element.getAsJsonObject();
            if (!"WeaponCurve".equals(row.get("table").getAsString())) continue;
            JsonObject fields = row.getAsJsonObject("javaFields");
            assertEquals(Set.of("level", "curveInfos"), fields.keySet());
            int level = row.get("key").getAsInt();
            assertTrue(levels.add(level)); assertEquals(level, fields.get("level").getAsInt());
            JsonArray infos = fields.getAsJsonArray("curveInfos"); assertEquals(18, infos.size());
            Set<String> types = new HashSet<>();
            for (JsonElement info : infos) {
                JsonObject value = info.getAsJsonObject();
                assertEquals(Set.of("type", "arith", "value"), value.keySet());
                assertTrue(types.add(value.get("type").getAsString()), "native first-match cannot become Java last-write");
                assertEquals("ARITH_MULTI", value.get("arith").getAsString());
                assertTrue(Float.isFinite(value.get("value").getAsFloat()));
            }
            curves.add(fields.deepCopy());
        }
        assertEquals(100, levels.size()); for (int level = 1; level <= 100; level++) assertTrue(levels.contains(level));
        ResourceLoader.loadFromResource(WeaponCurveData.class, write("weapon-curves.json", curves), GameData.getWeaponCurveDataMap());
        assertEquals(100, GameData.getWeaponCurveDataMap().size());
        JsonObject group = fragment(promote.getAsJsonObject("javaProjection").getAsJsonArray("completeBirthPromoteInputRows"), "WeaponPromote", ITEM << 8);
        JsonArray promoteRows = new JsonArray(); promoteRows.add(group.getAsJsonObject("javaFields").deepCopy());
        ResourceLoader.loadFromResource(WeaponPromoteData.class, write("weapon-promote.json", promoteRows), weaponPromoteMap());
        assertEquals(Set.of(ITEM << 8), weaponPromoteMap().keySet());
        JsonArray items = new JsonArray(); items.add(fields.deepCopy());
        ResourceLoader.loadFromResource(ItemData.class, write("weapon.json", items), GameData.getItemDataMap());
        assertEquals(Set.of(ITEM), GameData.getItemDataMap().keySet());
        ItemData item = GameData.getItemDataMap().get(ITEM);
        assertEquals(ItemType.ITEM_WEAPON, item.getItemType()); assertEquals(EquipType.EQUIP_WEAPON, item.getEquipType());
        assertFalse(item.isUseOnGain(), "keep the ordinary 11101 business path");
        assertEquals(ITEM, item.getWeaponPromoteId()); assertEquals(50011101, item.getGadgetId());
        assertArrayEquals(new int[] {0, 0}, item.getSkillAffix());
        assertEquals(2, item.getWeaponProperties().length);
        assertEquals(FightProperty.FIGHT_PROP_BASE_ATTACK, item.getWeaponProperties()[0].getPropType());
        assertEquals("GROW_CURVE_ATTACK_101", item.getWeaponProperties()[0].getType());
        assertEquals(0x41b9f5c3, Float.floatToRawIntBits(item.getWeaponProperties()[0].getInitValue()));
        assertEquals(FightProperty.FIGHT_PROP_NONE, item.getWeaponProperties()[1].getPropType());
        assertEquals("GROW_CURVE_CRITICAL_101", item.getWeaponProperties()[1].getType());
        assertEquals(0, Float.floatToRawIntBits(item.getWeaponProperties()[1].getInitValue()));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Int2ObjectMap<WeaponPromoteData> weaponPromoteMap() {
        return (Int2ObjectMap) GameData.getMapByResourceDef(WeaponPromoteData.class);
    }
}

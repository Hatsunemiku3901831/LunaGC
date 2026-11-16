package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;
import static emu.grasscutter.data.D02OldSnapshotAdmissionTest.bson;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.event.*;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameSession;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.bson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Complete selected Avatar/Inventory restoration in a new DB, after real resource admission. */
class D02OldSnapshotRestoreMongoTest {
    @TempDir Path temporary;

    @Test
    void fullSelectedSnapshotRetainsEveryItemAndEquipmentAcrossConfirmedSaveAndColdLoad() throws Exception {
        var bundle = D02OldSnapshotResourceLoadTest.loadFixedResources(temporary);
        D02OldSnapshotAdmissionTest.admit(bundle);
        var players = bson(D02OldSnapshotAdmissionTest.fixedInput(bundle, "selected-0/player.bson"));
        var avatars = bson(D02OldSnapshotAdmissionTest.fixedInput(bundle, "selected-0/avatars-0.bson"));
        var items = bson(D02OldSnapshotAdmissionTest.fixedInput(bundle, "selected-0/items-0.bson"));
        assertEquals(1, players.size());
        assertEquals(121, avatars.size());
        assertEquals(12254, items.size());
        var inactive = items.stream().filter(d -> !active(d)).collect(Collectors.toMap(d -> d.getObjectId("_id"), d -> d));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertEquals("mongodb://127.0.0.1:27027", uri);
        String name = "lunagc_old_full_restore_" + UUID.randomUUID().toString().replace("-", "");
        var writes = new AtomicInteger();
        var settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri))
                .addCommandListener(new CommandListener() {
                    @Override public void commandStarted(CommandStartedEvent event) {
                        if (name.equals(event.getDatabaseName()) && Set.of("insert", "update", "delete", "findAndModify").contains(event.getCommandName())) writes.incrementAndGet();
                    }
                }).build();
        var previousStore = DatabaseManager.getGameDatastore();
        try (var client = MongoClients.create(settings)) {
            var store = Morphia.createDatastore(client, name,
                    MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
            store.getMapper().map(Account.class, Player.class, Avatar.class, GameItem.class);
            var datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore");
            datastoreField.setAccessible(true);
            datastoreField.set(null, store);
            var db = store.getDatabase();
            db.getCollection("players").insertMany(players);
            db.getCollection("avatars").insertMany(avatars);
            db.getCollection("items").insertMany(items);
            int importedWrites = writes.get();
            var missing = GameData.getItemDataMap().remove(11101);
            assertNotNull(missing);
            try {
                assertThrows(IllegalStateException.class, () -> D02OldSnapshotAdmissionTest.admit(bundle));
                assertEquals(importedWrites, writes.get(), "failed admission cannot write or enter storage loaders");
                assertEquals(121, db.getCollection("avatars").countDocuments());
                assertEquals(12254, db.getCollection("items").countDocuments());
            } finally { GameData.getItemDataMap().put(11101, missing); }
            var reward = GameData.getRewardDataMap().values().iterator().next();
            GameData.getRewardDataMap().remove(reward.getId());
            try {
                assertThrows(IllegalStateException.class, () -> D02OldSnapshotAdmissionTest.admit(bundle));
                assertEquals(importedWrites, writes.get(), "missing derived namecard resource cannot write");
            } finally { GameData.getRewardDataMap().put(reward.getId(), reward); }
            D02OldSnapshotAdmissionTest.admit(bundle);
            System.out.println("OLD_RESTORE_STAGE admitted=true importedAvatars=121 importedItems=12254 missingResourceWrites=0");
            var player = store.find(Player.class).first();
            var session = new CapturingSession();
            session.setPlayer(player);
            player.getAvatars().loadFromDatabase();
            player.getInventory().loadFromDatabase();
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            assertSnapshot(player, avatars, items);
            assertEquals(12254, db.getCollection("items").countDocuments(), "no default weapons or deleted snapshot rows");
            System.out.println("OLD_RESTORE_STAGE hydratedAvatars=121 activeItems=10727 equipped=471");
            for (var avatar : player.getAvatars()) DatabaseHelper.saveGameSync(avatar);
            var originalItems = items.stream().collect(Collectors.toMap(d -> d.getObjectId("_id"), d -> d));
            for (var item : player.getInventory()) {
                var previous = originalItems.get(item.getObjectId()).get("saveStamp", Document.class);
                DatabaseHelper.saveGameSync(item);
                assertTrue(item.getSaveStamp().getRevision() > (previous == null ? 0 : previous.getLong("revision")));
                if (previous != null) assertNotEquals(previous.getString("token"), item.getSaveStamp().getToken());
            }
            DatabaseHelper.saveGameSync(player);
            assertNotNull(player.getSaveStamp());
            long oldRevision = players.get(0).get("saveStamp", Document.class).getLong("revision");
            assertTrue(player.getSaveStamp().getRevision() > oldRevision);
            assertNotEquals(players.get(0).get("saveStamp", Document.class).getString("token"), player.getSaveStamp().getToken());
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            System.out.println("OLD_RESTORE_STAGE confirmedSave=true");
            var fresh = store.find(Player.class).first();
            var freshSession = new CapturingSession();
            freshSession.setPlayer(fresh);
            fresh.getAvatars().loadFromDatabase();
            fresh.getInventory().loadFromDatabase();
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            assertNotSame(player, fresh);
            assertSnapshot(fresh, avatars, items);
            assertEquals(player.getAccountId(), fresh.getAccountId());
            assertEquals(player.getMainCharacterId(), fresh.getMainCharacterId());
            assertEquals(player.getSceneId(), fresh.getSceneId());
            assertEquals(player.getSaveStamp().getRevision(), fresh.getSaveStamp().getRevision());
            assertEquals(player.getSaveStamp().getToken(), fresh.getSaveStamp().getToken());
            var restoredItems = fresh.getInventory().getItems().values().stream()
                    .collect(Collectors.toMap(GameItem::getObjectId, item -> item));
            for (var item : player.getInventory()) {
                // Runtime GUIDs are regenerated; persisted ObjectId remains the item identity.
                var restored = restoredItems.get(item.getObjectId());
                // Inventory's real equipItem(false) confirms one write for each saved equipped item.
                assertEquals(item.getSaveStamp().getRevision() + (item.isEquipped() ? 1 : 0), restored.getSaveStamp().getRevision());
                if (item.isEquipped()) assertNotEquals(item.getSaveStamp().getToken(), restored.getSaveStamp().getToken());
                else assertEquals(item.getSaveStamp().getToken(), restored.getSaveStamp().getToken());
            }
            for (var avatar : player.getAvatars()) {
                var restored = fresh.getAvatars().getAvatarById(avatar.getAvatarId());
                assertEquals(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP), restored.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
                assertEquals(avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), restored.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
                assertEquals(avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), restored.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
            }
            assertEquals(12254, db.getCollection("items").countDocuments());
            assertEquals(121, db.getCollection("avatars").countDocuments());
            assertStoredFields(db.getCollection("items"), items, Set.of("saveStamp"));
            assertStoredFields(db.getCollection("avatars"), avatars,
                    Set.of("currentHp", "currentEnergy", "currentSpecialEnergy"));
            assertStoredFields(db.getCollection("players"), players, Set.of("saveStamp"));
            for (var raw : db.getCollection("items").find()) {
                var before = inactive.get(raw.getObjectId("_id"));
                if (before != null) assertEquals(before, raw, "inactive rows remain untouched");
            }
            assertFalse(ResourceLoader.isLoadedAll());
            System.out.println("OLD_RESTORE_COMPLETE avatars=121 activeItems=10727 totalItems=12254 inactiveUnchanged=1527 equipped=471 coldRestore=true fullReady=false database=" + name);
        } finally {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            var field = DatabaseManager.class.getDeclaredField("gameDatastore");
            field.setAccessible(true);
            field.set(null, previousStore);
        }
    }

    private static void assertSnapshot(Player player, List<Document> avatars, List<Document> items) {
        assertEquals(121, player.getAvatars().getAvatarCount());
        assertEquals(10727, player.getInventory().getItems().size());
        var actualItems = player.getInventory().getItems().values().stream()
                .collect(Collectors.toMap(GameItem::getObjectId, item -> item));
        var actualAvatars = player.getAvatars().getAvatars().values().stream()
                .collect(Collectors.toMap(Avatar::getObjectId, avatar -> avatar));
        int equipped = 0;
        int hpClamps = 0;
        for (var saved : avatars) {
            var avatar = actualAvatars.get(saved.getObjectId("_id"));
            assertNotNull(avatar, "every saved Avatar is restored");
            assertEquals(saved.getInteger("avatarId"), avatar.getAvatarId());
            assertEquals(saved.getInteger("ownerId"), avatar.getOwnerId());
            assertEquals(saved.getInteger("skillDepotId"), avatar.getSkillDepotId());
            assertEquals(saved.getInteger("level"), avatar.getLevel());
            assertEquals(saved.getInteger("promoteLevel"), avatar.getPromoteLevel());
            assertEquals(saved.get("_id"), avatar.getObjectId());
            assertTrue(Float.isFinite(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)));
            assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) > 0);
            float maximumHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float savedHp = saved.get("currentHp", Number.class).floatValue();
            float expectedHp = Float.isFinite(savedHp) ? Math.max(0f, Math.min(maximumHp, savedHp)) : 0f;
            assertEquals(expectedHp, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), "SavedVitals clamps only to the actual complete equipment cap");
            if (savedHp != expectedHp) {
                hpClamps++;
                System.out.println("OLD_RESTORE_HP_CLAMP saved=" + savedHp + " maximum=" + maximumHp
                        + " expected=" + expectedHp + " actual=" + avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            }
            float savedEnergy = saved.get("currentEnergy", Number.class).floatValue();
            var element = avatar.getSkillDepot().getElementType();
            float maximumEnergy = avatar.getFightProperty(element.getMaxEnergyProp());
            float expectedEnergy = Float.isFinite(savedEnergy) && maximumEnergy > 0f ? Math.max(0f, Math.min(maximumEnergy, savedEnergy)) : 0f;
            assertEquals(expectedEnergy, avatar.getFightProperty(element.getCurEnergyProp()));
            assertNotNull(avatar.getWeapon());
        }
        for (var saved : items) {
            if (!active(saved)) continue;
            var item = actualItems.get(saved.getObjectId("_id"));
            assertNotNull(item, "every active saved item is restored");
            assertEquals(saved.getInteger("ownerId"), item.getOwnerId());
            assertEquals(saved.getInteger("itemId"), item.getItemId());
            assertEquals(saved.getInteger("count"), item.getCount());
            assertEquals(saved.getInteger("level", 0), item.getLevel());
            assertEquals(saved.getInteger("promoteLevel", 0), item.getPromoteLevel());
            assertNotNull(item.getSaveStamp());
            assertTrue(item.getSaveStamp().getRevision() > 0);
            assertEquals(item.getSaveStamp().getToken(), UUID.fromString(item.getSaveStamp().getToken()).toString());
            int owner = saved.getInteger("equipCharacter", 0);
            assertEquals(owner, item.getEquipCharacter());
            if (owner != 0) {
                equipped++;
                var avatar = player.getAvatars().getAvatarById(owner);
                assertSame(item, avatar.getEquips().get(item.getItemData().getEquipType().getValue()));
            }
        }
        assertEquals(471, equipped);
        assertEquals(4, hpClamps, "the fixed snapshot has exactly four HP values above the actual new resource cap");
    }

    private static boolean active(Document item) {
        return !Boolean.TRUE.equals(item.getBoolean("deleted")) && item.getInteger("count") > 0;
    }

    private static void assertStoredFields(com.mongodb.client.MongoCollection<Document> collection,
                                           List<Document> source, Set<String> derivedFields) {
        var saved = collection.find().into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(d -> d.get("_id"), d -> d));
        var changed = new TreeMap<String, Integer>();
        for (var original : source) {
            var restored = saved.get(original.get("_id"));
            assertNotNull(restored);
            for (var key : original.keySet()) {
                if (key.equals("fetters")) {
                    var before = new ArrayList<>(original.getList(key, Integer.class));
                    var after = new ArrayList<>(restored.getList(key, Integer.class));
                    Collections.sort(before);
                    Collections.sort(after);
                    if (!before.equals(after)) changed.merge(key, 1, Integer::sum);
                    continue;
                }
                if (!derivedFields.contains(key) && !Objects.equals(original.get(key), restored.get(key))) {
                    changed.merge(key, 1, Integer::sum);
                }
            }
        }
        System.out.println("OLD_RESTORE_PERSISTED_FIELD_DIAGNOSTIC collection="
                + collection.getNamespace().getCollectionName() + " changed=" + changed);
        assertTrue(changed.isEmpty(), "saved source fields must survive: " + changed);
    }

    private static final class CapturingSession extends GameSession {
        CapturingSession() { super(null); }
        @Override public void send(BasePacket packet) {}
    }
}

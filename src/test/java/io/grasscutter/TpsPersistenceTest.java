package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.tps.TpsWeaponAccessoryData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TpsPersistenceTest {
    @Entity(value = "tps_snapshots")
    public static class Snapshot {
        @Id private String id = "equipment";
        private Player player;
        private Avatar avatar;
        private GameItem weapon;

        public Snapshot() {}
    }

    @Test
    void oldMongoDocumentKeepsManualAccessoriesWithoutProvenance() throws Exception {
        Grasscutter.getLogger();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_tps_legacy_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
                datastore.getMapper().map(Snapshot.class, GameItem.class);
                var snapshot = new Snapshot();
                snapshot.weapon = new GameItem();
                snapshot.weapon.getTpsAccessoryIds().addAll(List.of(224505, 224506));
                datastore.save(snapshot);
                client
                        .getDatabase(database)
                        .getCollection("tps_snapshots")
                        .updateOne(
                                new Document("_id", "equipment"),
                                new Document("$unset", new Document("weapon.tpsMaterialAccessoryIds", "")));
                var loaded = datastore.find(Snapshot.class).first().weapon;
                assertEquals(List.of(224505, 224506), loaded.getTpsAccessoryIds());
                assertTrue(loaded.getTpsMaterialAccessoryIds().isEmpty());
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualInventoryLoadReconcilesAfterAllMongoItems(boolean materialFirst) throws Exception {
        Grasscutter.getLogger();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_tps_inventory_" + UUID.randomUUID().toString().replace("-", "");
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var original = field.get(null);
        var restore = new ArrayList<Runnable>();
        for (int id : new int[] {224001, 223112}) {
            var row =
                    JsonUtils.decode(
                            "{\"id\":"
                                    + id
                                    + ",\"itemType\":\""
                                    + (id == 224001 ? "ITEM_TPS_WEAPON" : "ITEM_MATERIAL")
                                    + "\",\"stackLimit\":1}",
                            ItemData.class);
            row.onLoad();
            var old = GameData.getItemDataMap().put(id, row);
            restore.add(
                    () -> {
                        if (old == null) GameData.getItemDataMap().remove(id);
                        else GameData.getItemDataMap().put(id, old);
                    });
        }
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/TpsWeaponAccessoryExcelConfigData.json"),
                        TpsWeaponAccessoryData.class)) {
            var old = GameData.getTpsWeaponAccessoryDataMap().put(row.getId(), row);
            restore.add(
                    () -> {
                        if (old == null) GameData.getTpsWeaponAccessoryDataMap().remove(row.getId());
                        else GameData.getTpsWeaponAccessoryDataMap().put(row.getId(), old);
                    });
        }
        try (var client = MongoClients.create(uri)) {
            try {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
                var datastore =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
                datastore.getMapper().map(GameItem.class, Avatar.class);
                field.set(null, datastore);
                var player = new TpsEquipmentTest.RecordingPlayer();
                player.setUid(74010);
                player.getAvatars().loadFromDatabase();
                var gun = new GameItem(224001, 1);
                gun.setOwner(player);
                gun.getTpsAccessoryIds().addAll(List.of(224505, 224507));
                gun.getTpsMaterialAccessoryIds().add(224507);
                var material = new GameItem(223112, 1);
                material.setOwner(player);
                datastore.save(materialFirst ? material : gun);
                datastore.save(materialFirst ? gun : material);
                player.getInventory().loadFromDatabase();
                var loaded = player.getInventory().getItemById(224001);
                assertEquals(List.of(224505, 224506), loaded.getTpsAccessoryIds());
                assertEquals(List.of(224506), loaded.getTpsMaterialAccessoryIds());
                assertTrue(player.getInventory().isLoaded());
                assertTrue(player.packets.isEmpty());
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
                var saved =
                        datastore
                                .find(GameItem.class)
                                .filter(dev.morphia.query.experimental.filters.Filters.eq("itemId", 224001))
                                .first();
                assertEquals(loaded.getTpsAccessoryIds(), saved.getTpsAccessoryIds());
                assertEquals(loaded.getTpsMaterialAccessoryIds(), saved.getTpsMaterialAccessoryIds());
                assertTrue(player.getInventory().removeItem(player.getInventory().getItemById(223112)));
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
                saved =
                        datastore
                                .find(GameItem.class)
                                .filter(dev.morphia.query.experimental.filters.Filters.eq("itemId", 224001))
                                .first();
                assertEquals(List.of(224505), saved.getTpsAccessoryIds());
                assertTrue(saved.getTpsMaterialAccessoryIds().isEmpty());
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
                field.set(null, original);
                client.getDatabase(database).drop();
                Collections.reverse(restore);
                restore.forEach(Runnable::run);
            }
        }
    }

    @Test
    void mongoRoundTripKeepsItemIdsLoadoutAccessoriesAndAnEmptyReserve() throws Exception {
        Grasscutter.getLogger();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_tps_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
                datastore.getMapper().map(Snapshot.class, Player.class, Avatar.class, GameItem.class);
                var snapshot = new Snapshot();
                snapshot.player = new Player();
                var team = Player.class.getDeclaredField("teamManager");
                team.setAccessible(true);
                team.set(snapshot.player, new TeamManager(snapshot.player));
                snapshot.player.getTpsLoadout().addAll(List.of(224001, 224004));
                snapshot.player.setTpsLoadoutInitialized(true);
                snapshot.player.getTpsAmmunition().put(1001, 0);
                snapshot.player.getTpsAmmunition().put(1003, 5);
                snapshot.avatar = new Avatar();
                snapshot.avatar.getTpsWeaponIds().addAll(List.of(224001, 224004));
                snapshot.weapon = new GameItem();
                snapshot.weapon.setItemId(224001);
                snapshot.weapon.setCount(1);
                snapshot.weapon.getTpsAccessoryIds().add(224506);
                snapshot.weapon.getTpsMaterialAccessoryIds().add(224506);
                datastore.save(snapshot);
                var loaded = datastore.find(Snapshot.class).first();
                assertEquals(List.of(224001, 224004), loaded.player.getTpsLoadout());
                assertTrue(loaded.player.isTpsLoadoutInitialized());
                assertEquals(0, loaded.player.getTpsAmmunition().get(1001));
                assertEquals(5, loaded.player.getTpsAmmunition().get(1003));
                assertEquals(List.of(224001, 224004), loaded.avatar.getTpsWeaponIds());
                assertEquals(List.of(224506), loaded.weapon.getTpsAccessoryIds());
                assertEquals(List.of(224506), loaded.weapon.getTpsMaterialAccessoryIds());
                var raw = client.getDatabase(database).getCollection("tps_snapshots").find().first();
                assertFalse(raw.get("weapon", Document.class).containsKey("guid"));
                assertFalse(raw.get("weapon", Document.class).containsKey("weaponEntity"));
                assertEquals(
                        List.of(224506),
                        raw.get("weapon", Document.class).getList("tpsMaterialAccessoryIds", Integer.class));
                assertEquals(
                        0,
                        raw.get("player", Document.class)
                                .get("tpsAmmunition", Document.class)
                                .getInteger("1001"));
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }

    @Test
    void mongoRoundTripKeepsAnIntentionallyEmptyLoadoutButNeverTheSceneParty() throws Exception {
        Grasscutter.getLogger();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_tps_scene_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
                datastore.getMapper().map(Snapshot.class, Player.class, Avatar.class, GameItem.class);
                var snapshot = new Snapshot();
                snapshot.player = new Player();
                snapshot.player.setTpsLoadoutInitialized(true);
                var team = new TeamManager(snapshot.player);
                team.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(List.of(10000005, 10000015));
                team.setCurrentCharacterIndex(1);
                var field = Player.class.getDeclaredField("teamManager");
                field.setAccessible(true);
                field.set(snapshot.player, team);
                var borrowed = new Avatar();
                borrowed.setTrialAvatarId(10064);
                team.beginTpsSceneTeam(null, borrowed);
                datastore.save(snapshot);
                var raw =
                        client
                                .getDatabase(database)
                                .getCollection("tps_snapshots")
                                .find()
                                .first()
                                .get("player", Document.class)
                                .get("teamManager", Document.class);
                for (String name : List.of("tpsSceneAvatar", "tpsSceneTeam", "tpsScene"))
                    assertFalse(raw.containsKey(name), name);
                assertEquals(1, raw.getInteger("tpsPreviousIndex"));
                var loaded = datastore.find(Snapshot.class).first().player;
                assertTrue(loaded.isTpsLoadoutInitialized());
                assertTrue(loaded.getTpsLoadout().isEmpty());
                assertNull(loaded.getTeamManager().getTpsSceneAvatar());
                assertEquals(
                        List.of(10000005, 10000015),
                        loaded.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars());
                assertFalse(loaded.getTeamManager().isUsingTrialTeam());
                // Reconnect in the same mode, then leave it: recover the slot selected before entry.
                loaded.getTeamManager().setPlayer(loaded);
                loaded.getTeamManager().beginTpsSceneTeam(null, borrowed);
                loaded.getTeamManager().endTpsSceneTeam(null);
                assertEquals(1, loaded.getTeamManager().getCurrentCharacterIndex());
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }
}

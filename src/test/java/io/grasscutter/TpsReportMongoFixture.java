package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;

import emu.grasscutter.database.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.tps.TpsWeaponSystem;

import org.bson.Document;
import org.junit.jupiter.api.Assumptions;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

final class TpsReportMongoFixture implements AutoCloseable {
    final TpsAmmunitionSupplyTest state = new TpsAmmunitionSupplyTest();
    final Field datastoreField;
    final Object previousStore;
    final MongoClient client;
    final Datastore store;
    final Player player;
    final EntityAvatar entity;
    final GameItem gun;

    TpsReportMongoFixture(int type) throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastoreField.setAccessible(true);
        previousStore = datastoreField.get(null);
        state.setup();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_tps_report_fence_" + UUID.randomUUID().toString().replace("-", ""),
                        MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
        try {
            store.getMapper().map(Player.class);
            datastoreField.set(null, store);
            player = new Player();
            player.setUid(74112);
            player.setSession(
                    new emu.grasscutter.server.game.GameSession(null) {
                        @Override
                        public void send(emu.grasscutter.net.packet.BasePacket packet) {}
                    });
            var team = new TeamManager(player);
            var field = Player.class.getDeclaredField("teamManager");
            field.setAccessible(true);
            field.set(player, team);
            player.setScene(state.fixture.scene);
            player.setWorld(state.fixture.scene.getWorld());
            var avatar = new TpsEquipmentTest.RecordingAvatar(10000005);
            avatar.setFightProperty(
                    emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP, 100);
            avatar.setFightProperty(
                    emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP, 100);
            player.getAvatars().addAvatar(avatar);
            entity =
                    new EntityAvatar(state.fixture.scene, avatar) {
                        @Override
                        public void initAbilities() {}
                    };
            team.getActiveTeam().add(entity);
            team.setCurrentCharacterIndex(0);
            state.fixture.scene.getEntities().put(entity.getId(), entity);
            gun = new GameItem(224003);
            assertTrue(player.getInventory().addItem(gun));
            assertEquals(0, TpsWeaponSystem.wear(player, avatar.getGuid(), List.of(gun.getGuid())));
            TpsWeaponSystem.ensureWeaponEntities(avatar, state.fixture.scene);
            player.getTpsAmmunition().put(1003, 3);
            player.getTpsMagazineAmmunition().put(301, type == 0 ? 3 : 1);
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
            player.saveTpsAmmunition();
        } catch (Throwable failure) {
            close();
            throw failure;
        }
    }

    Document row() {
        return store.getDatabase()
                .getCollection("players")
                .find(new Document("_id", player.getUid()))
                .first();
    }

    @Override
    public void close() throws Exception {
        try {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
            datastoreField.set(null, previousStore);
            store.getDatabase().drop();
        } finally {
            client.close();
            state.close();
        }
    }
}

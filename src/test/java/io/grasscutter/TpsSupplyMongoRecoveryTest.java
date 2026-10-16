package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;

import emu.grasscutter.database.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.tps.*;

import org.bson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.*;

class TpsSupplyMongoRecoveryTest {
    @ParameterizedTest
    @CsvSource({
        "1,true,resume",
        "2,false,resume",
        "2,true,resume",
        "3,false,resume",
        "3,true,resume",
        "1,true,cancel"
    })
    void aNewPlayerLoadedFromMongoCanResolveEveryPersistedCheckpoint(
            int checkpoint, boolean committed, String command) throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        Object original = field.get(null);
        String database = "lunagc_tps_supply_" + UUID.randomUUID().toString().replace("-", "");
        var state = new TpsAmmunitionSupplyTest();
        state.setup();
        try (var client = MongoClients.create(uri)) {
            try {
                var store =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder()
                                        .storeEmpties(true)
                                        .storeNulls(false)
                                        .build());
                store.getMapper().map(Player.class);
                field.set(null, store);
                var player = new Player();
                player.setUid(73001);
                var session =
                        new emu.grasscutter.server.game.GameSession(null) {
                            @Override
                            public void send(emu.grasscutter.net.packet.BasePacket packet) {}
                        };
                player.setSession(session);
                var team = new TeamManager(player);
                var teamField = Player.class.getDeclaredField("teamManager");
                teamField.setAccessible(true);
                teamField.set(player, team);
                player.setScene(state.fixture.scene);
                player.setWorld(state.fixture.scene.getWorld());
                var avatar = new TpsEquipmentTest.RecordingAvatar(10000005);
                player.getAvatars().addAvatar(avatar);
                var entity =
                        new EntityAvatar(state.fixture.scene, avatar) {
                            @Override
                            public void initAbilities() {}
                        };
                team.getActiveTeam().add(entity);
                team.setCurrentCharacterIndex(0);
                state.fixture.scene.getEntities().put(entity.getId(), entity);
                var gun = new TpsEquipmentTest.RecordingItem(224003);
                player.getInventory().addItem(gun);
                avatar.getTpsWeaponIds().add(224003);
                player.getTpsAmmunition().putAll(state.player.getTpsAmmunition());
                player.saveTpsAmmunition();
                int[] writes = {0};
                var transaction =
                        new TpsSupplyTransaction(
                                player,
                                () -> {
                                    int count = ++writes[0];
                                    if (count == checkpoint && !committed)
                                        throw new IllegalStateException("before Mongo write");
                                    player.saveTpsAmmunition();
                                    if (count == checkpoint)
                                        throw new IllegalStateException("after Mongo write");
                                });
                var plan =
                        List.of(
                                new TpsSupplyTransaction.Amount(1001, 75),
                                new TpsSupplyTransaction.Amount(1003, 6),
                                new TpsSupplyTransaction.Amount(1001, 75));
                assertThrows(IllegalStateException.class, () -> transaction.grant(entity, plan));
                String id = player.getPendingTpsSupply().getOperationId();
                var collection =
                        store.getMapper()
                                .getCollection(Player.class)
                                .withDocumentClass(BsonDocument.class);
                var filter = new BsonDocument("_id", new BsonInt32(player.getUid()));
                var beforeAudit = collection.find(filter).first().toJson();
                player.getTpsSupplyTransaction().audit(id);
                assertEquals(
                        beforeAudit,
                        collection.find(filter).first().toJson(),
                        "audit must only read the document");

                var cold = store.find(Player.class).first();
                assertNotSame(player, cold);
                cold.setSession(session);
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                if (checkpoint == 3 && committed) assertNull(cold.getPendingTpsSupply());
                else {
                    assertNotNull(cold.getPendingTpsSupply());
                    assertEquals(id, cold.getPendingTpsSupply().getOperationId());
                    new emu.grasscutter.command.commands.TpsCommand()
                            .execute(null, cold, List.of(command, id));
                    assertNull(
                            cold.getPendingTpsSupply(),
                            "the management command must finish its operation");
                    assertThrows(
                            IllegalStateException.class,
                            () -> cold.getTpsSupplyTransaction().resume(id));
                }
                assertEquals(command.equals("cancel") ? 0 : 150, cold.getTpsAmmunition().get(1001));
                assertEquals(command.equals("cancel") ? 0 : 6, cold.getTpsAmmunition().get(1003));
                var result = collection.find(filter).first();
                assertFalse(result.containsKey("pendingTpsSupply"));
                assertEquals(
                        command.equals("cancel") ? 0 : 150,
                        result.getDocument("tpsAmmunition").getInt32("1001").getValue());
                assertEquals(
                        command.equals("cancel") ? 0 : 6,
                        result.getDocument("tpsAmmunition").getInt32("1003").getValue());
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                field.set(null, original);
                client.getDatabase(database).drop();
            }
        } finally {
            state.close();
        }
    }
}

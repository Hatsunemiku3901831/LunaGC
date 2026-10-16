package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;

import emu.grasscutter.database.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.tps.*;

import org.bson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.*;

class TpsReportMongoRecoveryTest {
    @ParameterizedTest
    @CsvSource({
        "0,true,accept",
        "1,true,accept",
        "0,false,resume",
        "0,true,resume",
        "1,false,resume",
        "1,true,resume",
        "0,false,cancel",
        "1,false,cancel"
    })
    void aRealAtomicPlayerWriteAndColdReadPreserveTheCompleteReportResult(
            int type, boolean committed, String action) throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        Object previous = field.get(null);
        String database = "lunagc_tps_report_" + UUID.randomUUID().toString().replace("-", "");
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
                player.setUid(74111);
                player.setSession(
                        new emu.grasscutter.server.game.GameSession(null) {
                            @Override
                            public void send(emu.grasscutter.net.packet.BasePacket packet) {}
                        });
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
                var gun = new GameItem(224003);
                assertTrue(player.getInventory().addItem(gun));
                assertEquals(
                        0, TpsWeaponSystem.wear(player, avatar.getGuid(), List.of(gun.getGuid())));
                TpsWeaponSystem.ensureWeaponEntities(avatar, state.fixture.scene);
                player.getTpsAmmunition().put(1003, 3);
                player.getTpsMagazineAmmunition().put(301, type == 0 ? 3 : 1);
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                player.saveTpsAmmunition();
                int[] writes = {0};
                boolean[] fail = {!action.equals("accept")};
                var transaction =
                        new TpsReportTransaction(
                                player,
                                () -> {
                                    writes[0]++;
                                    if (fail[0] && !committed)
                                        throw new IllegalStateException(
                                                "before Mongo report write");
                                    player.saveTpsAmmunition();
                                    if (fail[0])
                                        throw new IllegalStateException("after Mongo report write");
                                });
                var transactionField = Player.class.getDeclaredField("tpsReportTransaction");
                transactionField.setAccessible(true);
                transactionField.set(player, transaction);
                var report =
                        TpsReportRecoveryTest.reportFor(
                                entity.getId(), gun.getWeaponEntity().getId(), type);
                if (action.equals("accept")) {
                    long started = System.nanoTime();
                    assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, report));
                    System.out.printf(
                            Locale.ROOT,
                            "TPS acknowledged report type=%d ms=%.3f%n",
                            type,
                            (System.nanoTime() - started) / 1e6);
                } else
                    assertThrows(
                            IllegalStateException.class,
                            () -> TpsWeaponSystem.onAmmunitionInvoke(player, report));
                String id = player.getLastTpsReport().getOperationId();
                var collection =
                        store.getMapper()
                                .getCollection(Player.class)
                                .withDocumentClass(BsonDocument.class);
                var filter = new BsonDocument("_id", new BsonInt32(player.getUid()));
                var beforeAudit = collection.find(filter).first().toJson();
                if (!action.equals("accept")) transaction.audit(id);
                assertEquals(beforeAudit, collection.find(filter).first().toJson());
                var cold = store.find(Player.class).first();
                assertNotSame(player, cold);
                assertFalse(cold.getTpsReportTransaction().isPending());
                assertEquals(
                        committed ? (type == 0 ? 2 : 3) : 3, cold.getTpsAmmunition().get(1003));
                assertEquals(
                        committed ? (type == 0 ? 2 : 3) : (type == 0 ? 3 : 1),
                        cold.getTpsMagazineAmmunition().get(301));
                if (committed) {
                    assertEquals(id, cold.getLastTpsReport().getOperationId());
                    cold.getLastTpsReport().validate();
                } else assertNull(cold.getLastTpsReport());
                fail[0] = false;
                if (!action.equals("accept"))
                    new emu.grasscutter.command.commands.TpsCommand()
                            .execute(null, player, List.of(action, id));
                assertFalse(transaction.isPending());
                var result = store.find(Player.class).first();
                assertEquals(
                        action.equals("cancel") ? 3 : (type == 0 ? 2 : 3),
                        result.getTpsAmmunition().get(1003));
                assertEquals(
                        action.equals("cancel") ? (type == 0 ? 3 : 1) : (type == 0 ? 2 : 3),
                        result.getTpsMagazineAmmunition().get(301));
                assertEquals(committed ? 1 : 2, writes[0]);
                if (!action.equals("cancel"))
                    assertEquals(id, result.getLastTpsReport().getOperationId());
                else assertNull(result.getLastTpsReport());
                assertThrows(IllegalStateException.class, () -> transaction.resume(id));
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                field.set(null, previous);
                client.getDatabase(database).drop();
            }
        } finally {
            state.close();
        }
    }
}

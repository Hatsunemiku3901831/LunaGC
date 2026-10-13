package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.FurnitureMakeStartBatchRspOuterClass.FurnitureMakeStartBatchRsp;
import emu.grasscutter.net.proto.FurnitureMakeStartInfoOuterClass.FurnitureMakeStartInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

/** Starts the actual payment path, interrupts the home write, then queries the actual manager. */
class FurnitureStartRecoveryTest {
    final Gson gson = new Gson();
    FurnitureStartFlowTest fixture;
    FurnitureStartFlowTest.CraftPlayer player;

    Object fixtureField(String name) throws Exception {
        var field = FurnitureStartFlowTest.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fixture);
    }

    void fixtureField(String name, Object value) throws Exception {
        var field = FurnitureStartFlowTest.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(fixture, value);
    }

    int saves() throws Exception {
        return (Integer) fixtureField("saves");
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new FurnitureStartFlowTest();
        fixture.setup();
        player = (FurnitureStartFlowTest.CraftPlayer) fixtureField("player");
    }

    @AfterEach
    void cleanup() throws Exception {
        fixture.cleanup();
    }

    void queued(int failure) throws Exception {
        fixtureField("failSave", failure);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, fixture.start().getRetcode());
        assertEquals(3, player.home.getPendingFurnitureStart().getState());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        snapshot();
        fixtureField("failSave", 0);
    }

    void snapshot() {
        player.home.setPendingFurnitureStart(
                gson.fromJson(
                        gson.toJson(player.home.getPendingFurnitureStart()),
                        FurnitureStartReceipt.class));
        player.home.setFurnitureMakeSlotItemList(
                new ArrayList<>(
                        Arrays.asList(
                                gson.fromJson(
                                        gson.toJson(player.home.getFurnitureMakeSlotItemList()),
                                        FurnitureMakeSlotItem[].class))));
    }

    FurnitureMakeRsp query() throws Exception {
        player.packets.clear();
        player.getFurnitureManager().queryStatus();
        var packets =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeRsp)
                        .toList();
        assertEquals(1, packets.size());
        return FurnitureMakeRsp.parseFrom(packets.get(0).getData());
    }

    void costsStillPaidOnce(int count) {
        assertEquals(40 - 4 * count, player.inventory.getItemCountById(101311));
        assertEquals(20 - 2 * count, player.inventory.getItemCountById(101312));
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertFalse(
                player.home.getCraftedFurnitureIds() != null
                        && player.home.getCraftedFurnitureIds().contains(360101));
        assertEquals(0, player.home.getExp());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6})
    void confirmedQueuedStartIsReleasedWithoutChargingOrResettingTime(int failure)
            throws Exception {
        queued(failure);
        var slot = player.home.getFurnitureMakeSlotItemList().get(0);
        var before = gson.toJson(slot);
        int writes = saves();
        var result = query();
        assertEquals(0, result.getRetcode());
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(before, gson.toJson(player.home.getFurnitureMakeSlotItemList().get(0)));
        assertEquals(slot.toProto(), result.getFurnitureMakeSlot().getFurnitureMakeDataList(0));
        assertEquals(writes + 1, saves());
        costsStillPaidOnce(1);
    }

    @Test
    void repeatedQueriesDoNotWriteOrDuplicateAgain() throws Exception {
        queued(6);
        assertEquals(0, query().getRetcode());
        assertNull(player.home.getPendingFurnitureStart());
        int writes = saves();
        assertEquals(0, query().getRetcode());
        assertEquals(writes, saves());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        costsStillPaidOnce(1);
    }

    @Test
    void newStartCanResumeAfterTheConfirmedPreviousQueue() throws Exception {
        queued(6);
        assertEquals(0, query().getRetcode());
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(0, fixture.start().getRetcode());
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        costsStillPaidOnce(2);
    }

    @Test
    void directRetryCannotBecomeAnotherPaymentBeforeStatusReconciliation() throws Exception {
        queued(6);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, fixture.start().getRetcode());
        assertNotNull(player.home.getPendingFurnitureStart());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
        costsStillPaidOnce(1);
    }

    @Test
    void actualBatchKeepsEveryFrozenJobWhenItsFinalClearFails() throws Exception {
        fixtureField("failSave", 6);
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        session.responses.clear();
        var entry = FurnitureMakeStartInfo.newBuilder().setMakeId(38).build();
        player.getFurnitureManager().startBatch(List.of(entry, entry));
        var response =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.FurnitureMakeStartBatchRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                FurnitureMakeStartBatchRsp.parseFrom(response.getData()).getRetcode());
        snapshot();
        fixtureField("failSave", 0);
        assertEquals(0, query().getRetcode());
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        costsStillPaidOnce(2);
    }

    @Test
    void unrelatedExistingJobsArePreserved() throws Exception {
        queued(6);
        var other = FurnitureMakeSlotItem.of().index(7).makeId(999).beginTime(1).durTime(1).build();
        player.home.getFurnitureMakeSlotItemList().add(other);
        assertEquals(0, query().getRetcode());
        assertSame(other, player.home.getFurnitureMakeSlotItemList().get(1));
        assertNull(player.home.getPendingFurnitureStart());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
        costsStillPaidOnce(1);
    }

    @Test
    void changedCurrentRecipeDoesNotRewriteAnAlreadyQueuedPlan() throws Exception {
        queued(6);
        var original = gson.toJson(player.home.getFurnitureMakeSlotItemList());
        GameData.getFurnitureMakeConfigDataMap().clear();
        assertEquals(0, query().getRetcode());
        assertEquals(original, gson.toJson(player.home.getFurnitureMakeSlotItemList()));
        assertNull(player.home.getPendingFurnitureStart());
        costsStillPaidOnce(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 4})
    void otherPhasesCannotBeInferredAsAConfirmedQueue(int state) throws Exception {
        queued(6);
        var field = FurnitureStartReceipt.class.getDeclaredField("state");
        field.setAccessible(true);
        field.setInt(player.home.getPendingFurnitureStart(), state);
        refused();
    }

    void refused() throws Exception {
        var receipt = player.home.getPendingFurnitureStart();
        var queue = gson.toJson(player.home.getFurnitureMakeSlotItemList());
        int writes = saves();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
        assertEquals(queue, gson.toJson(player.home.getFurnitureMakeSlotItemList()));
        assertEquals(writes, saves());
        costsStillPaidOnce(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "duplicateIndex",
                "makeId",
                "avatarId",
                "beginTime",
                "durTime",
                "accelerateTime",
                "plan",
                "legacyReceipt",
                "costAmount",
                "costIdentity",
                "duplicateCost",
                "scalar",
                "operation"
            })
    void conflictingOrIncompleteSavedEvidenceCannotBeCleared(String kind) throws Exception {
        queued(6);
        var slot = player.home.getFurnitureMakeSlotItemList().get(0);
        var receipt = gson.toJsonTree(player.home.getPendingFurnitureStart()).getAsJsonObject();
        switch (kind) {
            case "missing" -> player.home.getFurnitureMakeSlotItemList().clear();
            case "duplicateIndex" -> player.home
                    .getFurnitureMakeSlotItemList()
                    .add(gson.fromJson(gson.toJson(slot), FurnitureMakeSlotItem.class));
            case "makeId" -> slot.setMakeId(39);
            case "avatarId" -> slot.setAvatarId(10000049);
            case "beginTime" -> slot.setBeginTime(slot.getBeginTime() + 1);
            case "durTime" -> slot.setDurTime(slot.getDurTime() + 1);
            case "accelerateTime" -> slot.setAccelerateTime(1);
            case "plan" -> slot.setPlan(null);
            case "legacyReceipt" -> receipt.remove("jobs");
            case "costAmount" -> receipt.getAsJsonArray("costs")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("count", 3);
            case "costIdentity" -> receipt.getAsJsonArray("costs")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("objectId", "bad");
            case "duplicateCost" -> receipt.getAsJsonArray("costs")
                    .add(receipt.getAsJsonArray("costs").get(0).deepCopy());
            case "scalar" -> receipt.addProperty("makeId", 39);
            case "operation" -> receipt.addProperty("operationId", "");
        }
        player.home.setPendingFurnitureStart(gson.fromJson(receipt, FurnitureStartReceipt.class));
        refused();
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk"})
    void anotherPendingOperationBlocksRelease(String kind) throws Exception {
        queued(6);
        switch (kind) {
            case "take" -> player.home.setPendingFurnitureTake(new FurnitureTakeReceipt());
            case "cancel" -> player.home.setPendingFurnitureCancel(new FurnitureCancelReceipt());
            case "bulk" -> player.home.setPendingFurnitureBulk(new FurnitureBulkReceipt());
        }
        refused();
    }

    @Test
    void failedReleaseSaveKeepsEvidenceAndCanRetryWithoutASecondPayment() throws Exception {
        queued(6);
        var receipt = player.home.getPendingFurnitureStart();
        fixtureField("failSave", saves() + 1);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
        costsStillPaidOnce(1);
        fixtureField("failSave", 0);
        assertEquals(0, query().getRetcode());
        assertNull(player.home.getPendingFurnitureStart());
        costsStillPaidOnce(1);
    }

    @Test
    void foreignHomeCannotReleaseAnotherOwnersEvidence() throws Exception {
        queued(6);
        var receipt = player.home.getPendingFurnitureStart();
        player.home.setOwnerUid(player.getUid() + 1);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, query().getRetcode());
        assertSame(receipt, player.home.getPendingFurnitureStart());
    }

    @Test
    void ordinaryQueryWithoutAReceiptDoesNotWrite() throws Exception {
        int writes = saves();
        assertEquals(0, query().getRetcode());
        assertEquals(writes, saves());
        assertNull(player.home.getPendingFurnitureStart());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void actualMongoReloadAndAmbiguousReleaseKeepOnePaidQueue(int ambiguity) throws Exception {
        queued(6);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        var name = "lunagc_test_fstart190_" + UUID.randomUUID().toString().replace("-", "");
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var previous = field.get(null);
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(GameHome.class);
            try {
                player.home.setId("recovery-test-home");
                store.save(player.home);
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                field.set(null, store);
                if (ambiguity == 1)
                    field.set(
                            null,
                            Proxy.newProxyInstance(
                                    dev.morphia.Datastore.class.getClassLoader(),
                                    new Class<?>[] {dev.morphia.Datastore.class},
                                    (proxy, method, args) -> {
                                        Object result;
                                        try {
                                            result = method.invoke(store, args);
                                        } catch (InvocationTargetException failure) {
                                            throw failure.getCause();
                                        }
                                        if (method.getName().equals("save") && args.length == 2)
                                            throw new IllegalStateException(
                                                    "Mongo accepted release; acknowledgement lost");
                                        return result;
                                    }));
                assertEquals(
                        ambiguity == 0 ? 0 : Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
                if (ambiguity == 1) assertNotNull(player.home.getPendingFurnitureStart());
                player.home = store.find(GameHome.class).first();
                assertNotNull(player.home);
                assertNull(player.home.getPendingFurnitureStart());
                field.set(null, store);
                assertEquals(0, query().getRetcode());
                assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
                costsStillPaidOnce(1);
            } finally {
                field.set(null, previous);
                assertTrue(name.startsWith("lunagc_test_fstart190_"));
                client.getDatabase(name).drop();
            }
        }
    }
}

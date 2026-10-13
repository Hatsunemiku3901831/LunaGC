package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

class FurnitureBulkResumeTest {
    FurnitureCompletedRecoveryTest common = new FurnitureCompletedRecoveryTest();
    final Gson gson = new Gson();
    FurnitureBulkFlowTest.BatchPlayer player;

    @AfterEach
    void cleanup() throws Exception {
        common.cleanup();
    }

    void stopped(int stage) throws Exception {
        common.bulkFailure(stage);
        player = (FurnitureBulkFlowTest.BatchPlayer) common.player;
    }

    void completed() {
        assertNull(common.home.getPendingFurnitureBulk());
        assertNull(common.home.getPendingFurnitureTake());
        assertTrue(common.home.getFurnitureMakeSlotItemList().isEmpty());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(1, player.inventory.getItemCountById(360102));
        assertEquals(2, player.missions);
        assertEquals(120, common.home.getExp());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 9})
    void confirmedParentResumesEveryUnclaimedEntryOnce(int stage) throws Exception {
        stopped(stage);
        assertEquals(0, common.query().getRetcode());
        completed();
    }

    @Test
    void aSecondQueryCannotReplayTheFinishedBatch() throws Exception {
        stopped(9);
        assertEquals(0, common.query().getRetcode());
        completed();
        int writes = common.writes(), grants = player.grants;
        assertEquals(0, common.query().getRetcode());
        completed();
        assertEquals(writes, common.writes());
        assertEquals(grants, player.grants);
    }

    @Test
    void explicitBatchRetryStaysBlockedUntilStatusReconciliation() throws Exception {
        stopped(9);
        var f = (FurnitureBulkFlowTest) common.fixture;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, f.take(f.two()).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(360102));
        assertEquals(0, common.query().getRetcode());
        completed();
    }

    @Test
    void removedRecipesDoNotReplaceTheStoredOutputs() throws Exception {
        stopped(9);
        GameData.getFurnitureMakeConfigDataMap().clear();
        assertEquals(0, common.query().getRetcode());
        completed();
    }

    @Test
    void changedLiveRecipeCannotChangeTheRemainingItem() throws Exception {
        stopped(9);
        ((FurnitureBulkFlowTest) common.fixture).recipe(39, 360101);
        assertEquals(0, common.query().getRetcode());
        completed();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    void frozenSelectionSurvivesAChangedLivePlanAndLaterAcknowledgementLoss(int fault)
            throws Exception {
        stopped(9);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, clearWithoutResume());
        common.home
                .getFurnitureMakeSlotItemList()
                .get(0)
                .setPlan(
                        gson.fromJson(
                                "{format:1,makeId:39,itemId:360101,count:9,makeTime:3600,trustExp:1,materials:[]}",
                                FurnitureMakePlan.class));
        if (fault != 0) common.field("failSave", common.writes() + fault);
        assertEquals(fault == 0 ? 0 : Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        common.field("failSave", 0);
        assertEquals(0, common.query().getRetcode());
        completed();
    }

    @Test
    void completedEntryBalancesMayChangeWithoutASecondGrant() throws Exception {
        stopped(9);
        assertTrue(player.inventory.removeItem(player.inventory.getItemById(360101), 1));
        assertEquals(0, common.query().getRetcode());
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertEquals(1, player.inventory.getItemCountById(360102));
        assertEquals(2, player.missions);
        assertNull(common.home.getPendingFurnitureBulk());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "make",
                "begin",
                "duration",
                "accelerate",
                "avatar",
                "snapshot",
                "duplicate",
                "completedSource"
            })
    void remainingJobIdentityIsCheckedBeforeAnyNewGrant(String kind) throws Exception {
        stopped(1);
        var slots = common.home.getFurnitureMakeSlotItemList();
        var slot = slots.get(1);
        switch (kind) {
            case "missing" -> slots.remove(1);
            case "make" -> slot.setMakeId(38);
            case "begin" -> slot.setBeginTime(slot.getBeginTime() + 1);
            case "duration" -> slot.setDurTime(slot.getDurTime() + 1);
            case "accelerate" -> slot.setAccelerateTime(1);
            case "avatar" -> slot.setAvatarId(10000049);
            case "snapshot" -> {
                var json = gson.toJsonTree(common.home.getPendingFurnitureBulk()).getAsJsonObject();
                for (var e : json.getAsJsonArray("entries"))
                    e.getAsJsonObject().remove("sourceSlot");
                common.home.setPendingFurnitureBulk(
                        gson.fromJson(json, FurnitureBulkReceipt.class));
            }
            case "duplicate" -> slots.add(slots.get(0));
            case "completedSource" -> {
                var json = gson.toJsonTree(common.home.getPendingFurnitureBulk()).getAsJsonObject();
                json.getAsJsonArray("entries")
                        .get(0)
                        .getAsJsonObject()
                        .addProperty("completed", true);
                common.home.setPendingFurnitureBulk(
                        gson.fromJson(json, FurnitureBulkReceipt.class));
            }
        }
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(0, player.grants);
        assertEquals(0, player.missions);
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertNotNull(common.home.getPendingFurnitureBulk());
    }

    @Test
    void allRemainingStackLimitsAreCheckedBeforeTheFirstResumedGrant() throws Exception {
        stopped(1);
        ((FurnitureBulkFlowTest) common.fixture).item(360102, "ITEM_FURNITURE", 1);
        assertTrue(player.inventory.addItem(360102, 1));
        int grants = player.grants;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(grants, player.grants);
        assertEquals(0, player.inventory.getItemCountById(360101));
        assertEquals(1, player.inventory.getItemCountById(360102));
    }

    @Test
    void invalidLaterItemCannotAllowTheEarlierEntryToRun() throws Exception {
        stopped(1);
        GameData.getItemDataMap().remove(360102);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(0, player.grants);
        assertEquals(0, player.missions);
    }

    @Test
    void missingLaterTrustLevelCannotGrantTheEarlierEntry() throws Exception {
        stopped(1);
        common.home.setExp(250);
        GameData.getHomeWorldLevelDataMap().remove(2);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(0, player.grants);
        assertEquals(250, common.home.getExp());
    }

    @Test
    void partialDeliveryWithAnUnconfirmedChildIsNotResumed() throws Exception {
        stopped(5);
        assertNotNull(common.home.getPendingFurnitureTake());
        assertNotEquals(5, common.home.getPendingFurnitureTake().getState());
        int grants = player.grants;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(grants, player.grants);
        assertEquals(0, player.inventory.getItemCountById(360102));
    }

    @Test
    void aLegacyBatchWithoutSourceSnapshotsCannotGuessRemainingJobs() throws Exception {
        stopped(9);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, clearWithoutResume());
        var json = gson.toJsonTree(common.home.getPendingFurnitureBulk()).getAsJsonObject();
        for (var entry : json.getAsJsonArray("entries"))
            entry.getAsJsonObject().remove("sourceSlot");
        common.home.setPendingFurnitureBulk(gson.fromJson(json, FurnitureBulkReceipt.class));
        int grants = player.grants;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(grants, player.grants);
        assertEquals(0, player.inventory.getItemCountById(360102));
    }

    int clearWithoutResume() throws Exception {
        // An unavailable remaining product allows confirmed child cleanup while blocking delivery.
        var data = GameData.getItemDataMap().remove(360102);
        try {
            return common.query().getRetcode();
        } finally {
            GameData.getItemDataMap().put(360102, data);
        }
    }

    @Test
    void aRefusedRemainingGrantStaysReservedWithoutReplayingTheCompletedOne() throws Exception {
        stopped(9);
        player.rejectId = 360102;
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        assertEquals(0, player.inventory.getItemCountById(360102));
        assertEquals(1, player.missions);
        assertNotNull(common.home.getPendingFurnitureTake());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void everyResumedChildWriteFailurePreventsAnUnsafeReplay(int stage) throws Exception {
        stopped(9);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, clearWithoutResume());
        int writes = common.writes();
        common.field("failSave", writes + stage);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(1, player.inventory.getItemCountById(360101));
        int second = player.inventory.getItemCountById(360102), events = player.missions;
        common.field("failSave", 0);
        var result = common.query();
        assertEquals(1, player.inventory.getItemCountById(360101));
        if (stage <= 6) {
            assertEquals(Retcode.RET_SVR_ERROR_VALUE, result.getRetcode());
            assertEquals(second, player.inventory.getItemCountById(360102));
            assertEquals(events, player.missions);
        } else {
            assertEquals(0, result.getRetcode());
            completed();
        }
    }

    @Test
    void unselectedJobsArePreservedDuringResumption() throws Exception {
        stopped(9);
        var other = FurnitureMakeSlotItem.of().index(7).makeId(88).beginTime(1).durTime(1).build();
        common.home.getFurnitureMakeSlotItemList().add(other);
        assertEquals(0, common.query().getRetcode());
        assertEquals(1, common.home.getFurnitureMakeSlotItemList().size());
        assertSame(other, common.home.getFurnitureMakeSlotItemList().get(0));
        assertNull(common.home.getPendingFurnitureBulk());
        assertEquals(2, player.missions);
    }

    @Test
    void eachRemainingFastJobUsesOnlyItsOwnVial() throws Exception {
        common.setup("bulk");
        player = (FurnitureBulkFlowTest.BatchPlayer) common.player;
        var f = (FurnitureBulkFlowTest) common.fixture;
        f.job(0, 38, false);
        f.job(1, 39, false);
        assertTrue(player.inventory.addItem(107013, 2));
        common.field("failSave", 10);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                f.take(List.of(f.choose(0, 38, true), f.choose(1, 39, true))).getRetcode());
        assertEquals(5, common.home.getPendingFurnitureTake().getState());
        assertEquals(1, player.inventory.getItemCountById(107013));
        common.field("failSave", 0);
        assertEquals(0, common.query().getRetcode());
        completed();
        assertEquals(0, player.inventory.getItemCountById(107013));
    }

    @Test
    void missingRemainingVialCannotGrantAnyUnclaimedJob() throws Exception {
        common.setup("bulk");
        player = (FurnitureBulkFlowTest.BatchPlayer) common.player;
        var f = (FurnitureBulkFlowTest) common.fixture;
        f.job(0, 38, false);
        f.job(1, 39, false);
        assertTrue(player.inventory.addItem(107013, 2));
        common.field("failSave", 1);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                f.take(List.of(f.choose(0, 38, true), f.choose(1, 39, true))).getRetcode());
        common.field("failSave", 0);
        assertTrue(player.inventory.payItem(107013, 1));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, common.query().getRetcode());
        assertEquals(0, player.grants);
        assertEquals(1, player.inventory.getItemCountById(107013));
    }

    @Test
    void elapsedFastFlagDoesNotSpendALaterVial() throws Exception {
        common.setup("bulk");
        player = (FurnitureBulkFlowTest.BatchPlayer) common.player;
        var f = (FurnitureBulkFlowTest) common.fixture;
        f.ready();
        common.field("failSave", 1);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                f.take(List.of(f.choose(0, 38, true), f.choose(1, 39, true))).getRetcode());
        common.field("failSave", 0);
        assertEquals(0, common.query().getRetcode());
        completed();
        assertEquals(0, player.inventory.getItemCountById(107013));
    }

    @Test
    void remainingDefinitionsSurviveActualMongoReload() throws Exception {
        stopped(9);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, clearWithoutResume());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String db = "lunagc_test_resume193_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store =
                    dev.morphia.Morphia.createDatastore(
                            client,
                            db,
                            dev.morphia.mapping.MapperOptions.builder()
                                    .storeEmpties(true)
                                    .storeNulls(false)
                                    .build());
            store.getMapper().map(GameHome.class);
            try {
                common.home.setId("resume");
                store.save(common.home);
                var loaded = store.find(GameHome.class).first();
                assertNotNull(loaded);
                player.home = loaded;
                common.home = loaded;
                assertEquals(0, common.query().getRetcode());
                completed();
            } finally {
                assertTrue(db.startsWith("lunagc_test_resume193_"));
                client.getDatabase(db).drop();
            }
        }
    }
}

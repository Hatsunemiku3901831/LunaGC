package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.database.*;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

/** Interrupts the actual claim/refund paths after their economic confirmation. */
class FurnitureCompletedRecoveryTest {
    Object fixture;
    Player player;
    GameHome home;
    final Gson gson = new Gson();

    Object field(String name) throws Exception {
        var f = fixture.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(fixture);
    }

    void field(String name, Object value) throws Exception {
        var f = fixture.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(fixture, value);
    }

    Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var m = fixture.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(fixture, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    void setup(String kind) throws Exception {
        fixture =
                switch (kind) {
                    case "take" -> new FurnitureTakeFlowTest();
                    case "cancel" -> new FurnitureCancelFlowTest();
                    default -> new FurnitureBulkFlowTest();
                };
        call("setup", new Class<?>[] {});
        player = (Player) field("player");
        home = player.getHome();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (fixture != null) call("cleanup", new Class<?>[] {});
    }

    int writes() throws Exception {
        return (Integer) field("saves");
    }

    Object source(Object receipt) throws Exception {
        try {
            return receipt.getClass().getMethod("getSourceSlot").invoke(receipt);
        } catch (NoSuchMethodException old) {
            return null;
        }
    }

    String economics() {
        var inventory = player.getInventory();
        return List.of(
                        inventory.getItemCountById(360101),
                        inventory.getItemCountById(360102),
                        inventory.getItemCountById(101311),
                        inventory.getItemCountById(101312),
                        inventory.getItemCountById(107013),
                        home.getLevel(),
                        home.getExp())
                .toString();
    }

    int missions() {
        if (player instanceof FurnitureTakeFlowTest.CraftPlayer p) return p.missions;
        if (player instanceof FurnitureCancelFlowTest.CraftPlayer p) return p.missions;
        return ((FurnitureBulkFlowTest.BatchPlayer) player).missions;
    }

    FurnitureMakeRsp query() throws Exception {
        var p = (AbilityTestFixture.TestPlayer) player;
        p.packets.clear();
        player.getFurnitureManager().queryStatus();
        var packets =
                p.packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.FurnitureMakeRsp)
                        .toList();
        assertEquals(1, packets.size());
        return FurnitureMakeRsp.parseFrom(packets.get(0).getData());
    }

    FurnitureMakeSlotItem takeFailure(int stage) throws Exception {
        setup("take");
        var slot = (FurnitureMakeSlotItem) call("slot", new Class<?>[] {boolean.class}, true);
        field("failSave", stage);
        var result =
                (emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp)
                        call("take", new Class<?>[] {boolean.class}, false);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, result.getRetcode());
        field("failSave", 0);
        return slot;
    }

    FurnitureMakeSlotItem cancelFailure(int stage) throws Exception {
        setup("cancel");
        var f = (FurnitureCancelFlowTest) fixture;
        var slot = f.start();
        field("failSave", stage);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, f.cancel(slot).getRetcode());
        field("failSave", 0);
        return slot;
    }

    void bulkFailure(int stage) throws Exception {
        setup("bulk");
        var f = (FurnitureBulkFlowTest) fixture;
        f.ready();
        field("failSave", stage);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, f.take(f.two()).getRetcode());
        field("failSave", 0);
    }

    void noReplay(String before, int events, int saves) throws Exception {
        assertEquals(before, economics());
        assertEquals(events, missions());
        assertEquals(saves + 1, writes());
    }

    void noMarkers() {
        assertNull(home.getPendingFurnitureTake());
        assertNull(home.getPendingFurnitureCancel());
        assertNull(home.getPendingFurnitureBulk());
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 8})
    void completedSingleClaimRemovesTheRestoredSourceWithoutRepeatingRewards(int stage)
            throws Exception {
        var slot = takeFailure(stage);
        assertEquals(5, home.getPendingFurnitureTake().getState());
        assertTrue(home.getFurnitureMakeSlotItemList().contains(slot));
        String before = economics();
        int events = missions(), saves = writes();
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertTrue(home.getFurnitureMakeSlotItemList().isEmpty());
        noReplay(before, events, saves);
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA_VALUE,
                ((emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp)
                                call("take", new Class<?>[] {boolean.class}, false))
                        .getRetcode());
        assertEquals(before, economics());
        assertEquals(events, missions());
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 8})
    void confirmedRefundFinishesRemovalWithoutRefundingAgain(int stage) throws Exception {
        var slot = cancelFailure(stage);
        assertTrue(home.getPendingFurnitureCancel().getState() >= 2);
        String before = economics();
        int events = missions(), saves = writes();
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertTrue(home.getFurnitureMakeSlotItemList().isEmpty());
        noReplay(before, events, saves);
        assertNotEquals(0, ((FurnitureCancelFlowTest) fixture).cancel(slot).getRetcode());
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(ints = {18, 19})
    void finishedBulkReceiptClearsOnlyAfterAllJobsWereCompleted(int stage) throws Exception {
        bulkFailure(stage);
        assertEquals(2, home.getPendingFurnitureBulk().getState());
        assertNull(home.getPendingFurnitureTake());
        String before = economics();
        int events = missions(), saves = writes();
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertTrue(home.getFurnitureMakeSlotItemList().isEmpty());
        noReplay(before, events, saves);
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 17})
    void completedLastChildAndItsCompleteParentAreRecoveredTogether(int stage) throws Exception {
        bulkFailure(stage);
        assertEquals(5, home.getPendingFurnitureTake().getState());
        assertEquals(1, home.getPendingFurnitureBulk().getState());
        assertTrue(
                home.getPendingFurnitureBulk().getEntries().stream()
                        .allMatch(FurnitureBulkReceipt.Entry::isCompleted));
        String before = economics();
        int events = missions(), saves = writes();
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertTrue(home.getFurnitureMakeSlotItemList().isEmpty());
        noReplay(before, events, saves);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    void completedChildCanBeReleasedWhenTheRemainingProductIsUnavailable(int stage)
            throws Exception {
        bulkFailure(stage);
        assertEquals(5, home.getPendingFurnitureTake().getState());
        assertFalse(home.getPendingFurnitureBulk().getEntries().get(1).isCompleted());
        var parent = home.getPendingFurnitureBulk();
        emu.grasscutter.data.GameData.getItemDataMap().remove(360102);
        String before = economics();
        int events = missions(), saves = writes();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertNull(home.getPendingFurnitureTake());
        assertSame(parent, home.getPendingFurnitureBulk());
        assertEquals(1, home.getFurnitureMakeSlotItemList().size());
        assertEquals(1, home.getFurnitureMakeSlotItemList().get(0).getIndex());
        noReplay(before, events, saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel"})
    void receiptCapturesAnIndependentSourceSnapshot(String kind) throws Exception {
        var slot = kind.equals("take") ? takeFailure(8) : cancelFailure(8);
        var receipt =
                kind.equals("take")
                        ? home.getPendingFurnitureTake()
                        : home.getPendingFurnitureCancel();
        var snapshot = source(receipt);
        assertNotNull(snapshot);
        assertNotSame(slot, snapshot);
        int original = slot.getDurTime();
        slot.setDurTime(original + 1);
        assertEquals(original, ((FurnitureMakeSlotItem) snapshot).getDurTime());
        String before = economics();
        int events = missions();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertEquals(before, economics());
        assertEquals(events, missions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk"})
    void aFailedCleanupSaveRetainsTheSameQueueAndReceiptForRetry(String kind) throws Exception {
        if (kind.equals("take")) takeFailure(8);
        else if (kind.equals("cancel")) cancelFailure(8);
        else bulkFailure(19);
        var slots = home.getFurnitureMakeSlotItemList();
        var take = home.getPendingFurnitureTake();
        var cancel = home.getPendingFurnitureCancel();
        var bulk = home.getPendingFurnitureBulk();
        String before = economics();
        int events = missions();
        field("failSave", writes() + 1);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(slots, home.getFurnitureMakeSlotItemList());
        assertSame(take, home.getPendingFurnitureTake());
        assertSame(cancel, home.getPendingFurnitureCancel());
        assertSame(bulk, home.getPendingFurnitureBulk());
        assertEquals(before, economics());
        field("failSave", 0);
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertEquals(before, economics());
        assertEquals(events, missions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk"})
    void laterInventoryChangesCannotBecomeAnotherDelivery(String kind) throws Exception {
        if (kind.equals("take")) takeFailure(8);
        else if (kind.equals("cancel")) cancelFailure(8);
        else bulkFailure(19);
        if (kind.equals("cancel")) assertTrue(player.getInventory().payItem(101311, 1));
        else
            assertTrue(
                    player.getInventory().removeItem(player.getInventory().getItemById(360101), 1));
        String before = economics();
        int events = missions();
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertEquals(before, economics());
        assertEquals(events, missions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk"})
    void repeatedQueryDoesNotRepeatTheCleanupWrite(String kind) throws Exception {
        if (kind.equals("take")) takeFailure(8);
        else if (kind.equals("cancel")) cancelFailure(8);
        else bulkFailure(19);
        assertEquals(0, query().getRetcode());
        int saves = writes();
        String before = economics();
        assertEquals(0, query().getRetcode());
        assertEquals(saves, writes());
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void incompleteSingleClaimRemainsReserved(int stage) throws Exception {
        takeFailure(stage);
        var receipt = home.getPendingFurnitureTake();
        var slots = home.getFurnitureMakeSlotItemList();
        String before = economics();
        int events = missions(), saves = writes();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, home.getPendingFurnitureTake());
        assertSame(slots, home.getFurnitureMakeSlotItemList());
        assertEquals(saves, writes());
        assertEquals(before, economics());
        assertEquals(events, missions());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void incompleteRefundRemainsReserved(int stage) throws Exception {
        cancelFailure(stage);
        var receipt = home.getPendingFurnitureCancel();
        var slots = home.getFurnitureMakeSlotItemList();
        String before = economics();
        int saves = writes();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, home.getPendingFurnitureCancel());
        assertSame(slots, home.getFurnitureMakeSlotItemList());
        assertEquals(saves, writes());
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", "mission", "operation", "snapshot", "phase"})
    void conflictingSingleClaimEvidenceDoesNotEraseItsMarker(String kind) throws Exception {
        takeFailure(8);
        var json = gson.toJsonTree(home.getPendingFurnitureTake()).getAsJsonObject();
        switch (kind) {
            case "product" -> json.addProperty("productObjectId", "bad");
            case "mission" -> json.addProperty("missionApplied", false);
            case "operation" -> json.addProperty("operationId", "");
            case "snapshot" -> {
                if (json.has("sourceSlot"))
                    json.getAsJsonObject("sourceSlot").addProperty("makeId", 39);
                else json.addProperty("makeId", 39);
            }
            case "phase" -> json.addProperty("state", 4);
        }
        home.setPendingFurnitureTake(gson.fromJson(json, FurnitureTakeReceipt.class));
        var receipt = home.getPendingFurnitureTake();
        int saves = writes();
        String before = economics();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, home.getPendingFurnitureTake());
        assertEquals(saves, writes());
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivered", "identity", "amount", "operation", "snapshot"})
    void conflictingRefundEvidenceDoesNotRemoveTheSource(String kind) throws Exception {
        cancelFailure(8);
        var json = gson.toJsonTree(home.getPendingFurnitureCancel()).getAsJsonObject();
        switch (kind) {
            case "delivered" -> json.getAsJsonArray("refunds")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("delivered", false);
            case "identity" -> json.getAsJsonArray("refunds")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("objectId", "bad");
            case "amount" -> json.getAsJsonArray("refunds")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("count", 3);
            case "operation" -> json.addProperty("operationId", "");
            case "snapshot" -> {
                if (json.has("sourceSlot"))
                    json.getAsJsonObject("sourceSlot").addProperty("makeId", 39);
                else json.addProperty("makeId", 39);
            }
        }
        home.setPendingFurnitureCancel(gson.fromJson(json, FurnitureCancelReceipt.class));
        var receipt = home.getPendingFurnitureCancel();
        String before = economics();
        int saves = writes();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, home.getPendingFurnitureCancel());
        assertEquals(before, economics());
        assertEquals(saves, writes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unfinished", "duplicate", "liveSlot", "operation"})
    void inconsistentBulkCompletionCannotBeCleared(String kind) throws Exception {
        bulkFailure(19);
        var json = gson.toJsonTree(home.getPendingFurnitureBulk()).getAsJsonObject();
        switch (kind) {
            case "unfinished" -> json.getAsJsonArray("entries")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("completed", false);
            case "duplicate" -> json.getAsJsonArray("entries")
                    .add(json.getAsJsonArray("entries").get(0).deepCopy());
            case "liveSlot" -> home.getFurnitureMakeSlotItemList()
                    .add(
                            FurnitureMakeSlotItem.of()
                                    .index(0)
                                    .makeId(38)
                                    .beginTime(Utils.getCurrentSeconds())
                                    .durTime(3600)
                                    .build());
            case "operation" -> json.addProperty("operationId", "");
        }
        home.setPendingFurnitureBulk(gson.fromJson(json, FurnitureBulkReceipt.class));
        var receipt = home.getPendingFurnitureBulk();
        String before = economics();
        int saves = writes();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(receipt, home.getPendingFurnitureBulk());
        assertEquals(saves, writes());
        assertEquals(before, economics());
    }

    @Test
    void mismatchedParentCannotConsumeACompletedChild() throws Exception {
        bulkFailure(17);
        var json = gson.toJsonTree(home.getPendingFurnitureBulk()).getAsJsonObject();
        json.addProperty("operationId", UUID.randomUUID().toString());
        home.setPendingFurnitureBulk(gson.fromJson(json, FurnitureBulkReceipt.class));
        String before = economics();
        var child = home.getPendingFurnitureTake();
        var parent = home.getPendingFurnitureBulk();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertSame(child, home.getPendingFurnitureTake());
        assertSame(parent, home.getPendingFurnitureBulk());
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel"})
    void legacyCompletedRecordCanClearWhenItsSourceWasAlreadyRemoved(String kind) throws Exception {
        if (kind.equals("take")) takeFailure(8);
        else cancelFailure(8);
        home.getFurnitureMakeSlotItemList().clear();
        var receipt =
                kind.equals("take")
                        ? home.getPendingFurnitureTake()
                        : home.getPendingFurnitureCancel();
        var json = gson.toJsonTree(receipt).getAsJsonObject();
        json.remove("sourceSlot");
        if (kind.equals("take"))
            home.setPendingFurnitureTake(gson.fromJson(json, FurnitureTakeReceipt.class));
        else home.setPendingFurnitureCancel(gson.fromJson(json, FurnitureCancelReceipt.class));
        String before = economics();
        assertEquals(0, query().getRetcode());
        noMarkers();
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel"})
    void legacyCompletedRecordCannotGuessTheIdentityOfARestoredSource(String kind)
            throws Exception {
        if (kind.equals("take")) takeFailure(8);
        else cancelFailure(8);
        var receipt =
                kind.equals("take")
                        ? home.getPendingFurnitureTake()
                        : home.getPendingFurnitureCancel();
        var json = gson.toJsonTree(receipt).getAsJsonObject();
        json.remove("sourceSlot");
        if (kind.equals("take"))
            home.setPendingFurnitureTake(gson.fromJson(json, FurnitureTakeReceipt.class));
        else home.setPendingFurnitureCancel(gson.fromJson(json, FurnitureCancelReceipt.class));
        String before = economics();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
        assertEquals(1, home.getFurnitureMakeSlotItemList().size());
        assertEquals(before, economics());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cancel", "bulk"})
    void actualMongoCleanupAndLostAcknowledgementKeepEconomicResults(String kind) throws Exception {
        if (kind.equals("take")) takeFailure(8);
        else if (kind.equals("cancel")) cancelFailure(8);
        else bulkFailure(19);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String db = "lunagc_test_done192_" + UUID.randomUUID().toString().replace("-", "");
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var previous = field.get(null);
        String before = economics();
        int events = missions();
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
                home.setId("completed");
                store.save(home);
                field.set(
                        null,
                        Proxy.newProxyInstance(
                                dev.morphia.Datastore.class.getClassLoader(),
                                new Class<?>[] {dev.morphia.Datastore.class},
                                (object, method, args) -> {
                                    Object result;
                                    try {
                                        result = method.invoke(store, args);
                                    } catch (InvocationTargetException e) {
                                        throw e.getCause();
                                    }
                                    if (method.getName().equals("save") && args.length == 2)
                                        throw new IllegalStateException(
                                                "Completed recovery stored; acknowledgement lost");
                                    return result;
                                }));
                assertEquals(Retcode.RET_SVR_ERROR_VALUE, query().getRetcode());
                assertEquals(before, economics());
                var restored = store.find(GameHome.class).first();
                assertNotNull(restored);
                var f = player.getClass().getDeclaredField("home");
                f.setAccessible(true);
                f.set(player, restored);
                home = restored;
                field.set(null, store);
                assertEquals(0, query().getRetcode());
                noMarkers();
                assertTrue(home.getFurnitureMakeSlotItemList().isEmpty());
                assertEquals(before, economics());
                assertEquals(events, missions());
            } finally {
                field.set(null, previous);
                assertTrue(db.startsWith("lunagc_test_done192_"));
                client.getDatabase(db).drop();
            }
        }
    }
}

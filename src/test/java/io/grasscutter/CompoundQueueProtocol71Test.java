package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.CodedInputStream;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.managers.cooking.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CompoundDataNotifyOuterClass.CompoundDataNotify;
import emu.grasscutter.net.proto.CompoundQueueDataOuterClass.CompoundQueueData;
import emu.grasscutter.net.proto.GetCompoundDataReqOuterClass.GetCompoundDataReq;
import emu.grasscutter.net.proto.GetCompoundDataRspOuterClass.GetCompoundDataRsp;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq;
import emu.grasscutter.net.proto.PlayerCompoundMaterialRspOuterClass.PlayerCompoundMaterialRsp;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;

/** Native parser -> controller conversion -> countdown/fetch use, independent wire literals. */
class CompoundQueueProtocol71Test {
    private static final byte[] NATIVE_QUEUE = {8, 100, 16, 3, 56, 2, 80, (byte) 0xe9, 7};
    private AbilityTestFixture environment;
    private CompoundQueueFlowTest.ProcessPlayer player;
    private Map<Integer, CompoundData> oldRecipes;
    private Map<Integer, ItemData> oldItems;
    private Object oldDatastore;
    private java.lang.reflect.Field datastore;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldRecipes = new HashMap<>(GameData.getCompoundDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        GameData.getCompoundDataMap().clear();
        GameData.getItemDataMap().clear();
        var gson = new Gson();
        for (int id : List.of(1001, 1002))
            GameData.getCompoundDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",groupID:1,isDefaultUnlocked:true,costTime:60,queueSize:99,inputVec:[{id:100017,count:1}],outputVec:[{id:110001,count:1}]}",
                                    CompoundData.class));
        for (int id : List.of(100017, 110001))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                                    ItemData.class));
        CookingCompoundManager.initialize();
        player = new CompoundQueueFlowTest.ProcessPlayer();
        player.packets.clear();
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        GameData.getCompoundDataMap().clear();
        GameData.getCompoundDataMap().putAll(oldRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        CookingCompoundManager.initialize();
        environment.close();
    }

    private static Map<Integer, Long> uintFields(byte[] body) throws Exception {
        var input = CodedInputStream.newInstance(body);
        var result = new LinkedHashMap<Integer, Long>();
        int tag;
        while ((tag = input.readTag()) != 0) {
            assertEquals(0, tag & 7);
            result.put(tag >>> 3, Integer.toUnsignedLong(input.readUInt32()));
        }
        return result;
    }

    private static int fieldNumber(String name) {
        var field = CompoundQueueData.getDescriptor().findFieldByName(name);
        assertNotNull(field, "Missing verified field " + name);
        return field.getNumber();
    }

    private static int fieldValue(CompoundQueueData message, String name) {
        var field = message.getDescriptorForType().findFieldByName(name);
        assertNotNull(field, "Missing verified field " + name);
        return (int) message.getField(field);
    }

    private static CompoundQueueData nativeQueue() throws Exception {
        return CompoundQueueData.parseFrom(NATIVE_QUEUE);
    }

    private List<CompoundQueueData> query() throws Exception {
        player.packets.clear();
        player.getCookingCompoundManager()
                .handleGetCompoundDataReq(GetCompoundDataReq.getDefaultInstance());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.GetCompoundDataRsp)
                        .findFirst()
                        .orElseThrow();
        return GetCompoundDataRsp.parseFrom(packet.getData()).getCompoundQueDataListList();
    }

    private Map<Integer, Long> queryQueue(int id) throws Exception {
        return uintFields(
                query().stream()
                        .filter(q -> q.getCompoundId() == id)
                        .findFirst()
                        .orElseThrow()
                        .toByteArray());
    }

    @Test
    void descriptorMatchesNativeFieldLabelsAndBusinessUses() {
        assertEquals(1, fieldNumber("output_time"));
        assertEquals(7, fieldNumber("output_count"));
        assertEquals(2, fieldNumber("wait_count"));
        assertEquals(10, fieldNumber("compound_id"));
    }

    @Test
    void noPlaceholderFieldsRemainInQueue() {
        assertEquals(
                Set.of(1, 2, 7, 10),
                new HashSet<>(
                        CompoundQueueData.getDescriptor().getFields().stream()
                                .map(f -> f.getNumber())
                                .toList()));
    }

    @Test
    void independentNativeFixtureDecodesTimeAndBothCounts() throws Exception {
        var queue = nativeQueue();
        assertEquals(1001, queue.getCompoundId());
        assertEquals(100, fieldValue(queue, "output_time"));
        assertEquals(2, fieldValue(queue, "output_count"));
        assertEquals(3, queue.getWaitCount());
        assertTrue(queue.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void getResponseUsesNativeNestedQueueAndUnlockTags() throws Exception {
        var packet = new PacketGetCompoundDataRsp(Set.of(1001), List.of(nativeQueue()));
        var input = CodedInputStream.newInstance(packet.getData());
        assertEquals(34, input.readTag());
        assertArrayEquals(NATIVE_QUEUE, input.readByteArray());
        assertEquals(50, input.readTag());
        var unlocked = CodedInputStream.newInstance(input.readByteArray());
        assertEquals(1001, unlocked.readUInt32());
        assertEquals(0, input.readTag());
    }

    @Test
    void notifyUsesNativeNestedQueueAndUnlockTags() throws Exception {
        var input =
                CodedInputStream.newInstance(
                        new PacketCompoundDataNotify(Set.of(1001), List.of(nativeQueue()))
                                .getData());
        assertEquals(58, input.readTag());
        assertEquals(1001, CodedInputStream.newInstance(input.readByteArray()).readUInt32());
        assertEquals(122, input.readTag());
        assertArrayEquals(NATIVE_QUEUE, input.readByteArray());
        assertEquals(0, input.readTag());
    }

    @Test
    void enqueueResponseUsesNativeNestedQueueTag() throws Exception {
        var input =
                CodedInputStream.newInstance(
                        new PacketPlayerCompoundMaterialRsp(nativeQueue()).getData());
        assertEquals(34, input.readTag());
        assertArrayEquals(NATIVE_QUEUE, input.readByteArray());
        assertEquals(0, input.readTag());
    }

    @Test
    void unfinishedQueueSendsFutureOutputTimeAndWaitingCount() throws Exception {
        int start = Utils.getCurrentSeconds();
        player.getActiveCookCompounds().put(1001, new ActiveCookCompoundData(1001, 600, 3, start));
        assertEquals(Map.of(1, (long) start + 600, 2, 3L, 10, 1001L), queryQueue(1001));
    }

    @Test
    void partialQueueSendsReadyWaitingAndNextTimestamp() throws Exception {
        int start = Utils.getCurrentSeconds() - 1201;
        player.getActiveCookCompounds().put(1001, new ActiveCookCompoundData(1001, 600, 5, start));
        assertEquals(Map.of(1, (long) start + 1800, 2, 3L, 7, 2L, 10, 1001L), queryQueue(1001));
    }

    @Test
    void finishedQueueSendsReadyCountWithoutCountdown() throws Exception {
        player.getActiveCookCompounds()
                .put(
                        1001,
                        new ActiveCookCompoundData(1001, 600, 3, Utils.getCurrentSeconds() - 3001));
        assertEquals(Map.of(7, 3L, 10, 1001L), queryQueue(1001));
    }

    @Test
    void loginNotificationUsesSameCompleteQueueRepresentation() throws Exception {
        int start = Utils.getCurrentSeconds() - 1201;
        player.getActiveCookCompounds().put(1001, new ActiveCookCompoundData(1001, 600, 5, start));
        player.packets.clear();
        player.getCookingCompoundManager().onPlayerLogin();
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.CompoundDataNotify)
                        .findFirst()
                        .orElseThrow();
        var queue = CompoundDataNotify.parseFrom(packet.getData()).getCompoundQueDataList(0);
        assertEquals(
                Map.of(1, (long) start + 1800, 2, 3L, 7, 2L, 10, 1001L),
                uintFields(queue.toByteArray()));
    }

    @Test
    void independentQueuesKeepTheirOwnCountsAndTimes() throws Exception {
        int start = Utils.getCurrentSeconds();
        player.getActiveCookCompounds().put(1001, new ActiveCookCompoundData(1001, 600, 2, start));
        player.getActiveCookCompounds()
                .put(1002, new ActiveCookCompoundData(1002, 600, 3, start - 3001));
        assertEquals(Map.of(1, (long) start + 600, 2, 2L, 10, 1001L), queryQueue(1001));
        assertEquals(Map.of(7, 3L, 10, 1002L), queryQueue(1002));
    }

    @Test
    void legacyZeroTimeQueueSendsReadyCount() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 0, 3, Utils.getCurrentSeconds()));
        assertEquals(Map.of(7, 3L, 10, 1001L), queryQueue(1001));
    }

    @Test
    void futureTimestampRetainsUnsigned32Bits() {
        var queue = new ActiveCookCompoundData(1001, 60, 1, Integer.MAX_VALUE - 10);
        assertEquals(
                (long) Integer.MAX_VALUE + 50,
                Integer.toUnsignedLong(queue.getOutputTime(Integer.MAX_VALUE - 10)));
    }

    @Test
    void largeFutureTimestampRetainsUnsignedRange() {
        var queue = new ActiveCookCompoundData(1001, Integer.MAX_VALUE, 3, Integer.MAX_VALUE);
        assertEquals(4294967294L, Integer.toUnsignedLong(queue.getOutputTime(Integer.MAX_VALUE)));
    }

    @Test
    void actualEnqueueResponsePublishesNextTimeAndWaitingCount() throws Exception {
        assertTrue(player.getInventory().addItem(100017, 10));
        player.packets.clear();
        int before = Utils.getCurrentSeconds();
        player.getCookingCompoundManager()
                .handlePlayerCompoundMaterialReq(
                        PlayerCompoundMaterialReq.newBuilder()
                                .setCompoundId(1001)
                                .setCount(2)
                                .build());
        int after = Utils.getCurrentSeconds();
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerCompoundMaterialRsp)
                        .findFirst()
                        .orElseThrow();
        var message = PlayerCompoundMaterialRsp.parseFrom(packet.getData());
        assertEquals(0, message.getRetcode());
        var fields = uintFields(message.getCompoundQueData().toByteArray());
        assertEquals(2L, fields.get(2));
        assertEquals(1001L, fields.get(10));
        assertFalse(fields.containsKey(7));
        assertNotNull(fields.get(1));
        assertTrue(fields.get(1) >= before + 60L && fields.get(1) <= after + 60L);
    }

    @Test
    void actualPartialClaimNotificationRetainsRemainingCountdown() throws Exception {
        int start = Utils.getCurrentSeconds() - 1201;
        player.getActiveCookCompounds().put(1001, new ActiveCookCompoundData(1001, 600, 3, start));
        player.packets.clear();
        player.getCookingCompoundManager()
                .handleTakeCompoundOutputReq(
                        TakeCompoundOutputReq.newBuilder().setCompoundGroupId(1).build());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.CompoundDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        var queue = CompoundDataNotify.parseFrom(packet.getData()).getCompoundQueDataList(0);
        assertEquals(
                Map.of(1, (long) start + 1800, 2, 1L, 10, 1001L), uintFields(queue.toByteArray()));
    }
}

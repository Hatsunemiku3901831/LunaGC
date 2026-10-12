package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Native wire labels and all current boost rows, through the production resource loader. */
class CompoundBoostDefinitionTest {
    private static final Map<Integer, Integer> RATES =
            Map.of(
                    101001, 20, 101002, 40, 101003, 60, 101004, 60, 101006, 40, 101008, 60, 101009,
                    60, 101010, 60);
    private static final byte[] REQUEST = {24, (byte) 0x89, (byte) 0x95, 6, 40, 1, 64, 2, 96, 3};
    private AbilityTestFixture environment;
    @TempDir Path temporary;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
    }

    @AfterEach
    void cleanup() {
        environment.close();
    }

    private static Class<?> resourceType() {
        return assertDoesNotThrow(
                () -> Class.forName("emu.grasscutter.data.excels.CompoundBoostData"));
    }

    private static Descriptors.FileDescriptor file(String outer) {
        return assertDoesNotThrow(
                () ->
                        (Descriptors.FileDescriptor)
                                Class.forName("emu.grasscutter.net.proto." + outer + "OuterClass")
                                        .getMethod("getDescriptor")
                                        .invoke(null));
    }

    private static Descriptors.Descriptor message(String name) {
        var result = file(name).findMessageTypeByName(name);
        assertNotNull(result);
        return result;
    }

    private static Descriptors.FieldDescriptor field(Descriptors.Descriptor type, String name) {
        var result = type.findFieldByName(name);
        assertNotNull(result, "Missing field " + name);
        return result;
    }

    private static int value(DynamicMessage message, String name) {
        return (int) message.getField(field(message.getDescriptorForType(), name));
    }

    private static Int2ObjectMap<Object> load(Path path) throws Exception {
        var result = new Int2ObjectOpenHashMap<Object>();
        Class<?> type = resourceType();
        Method loader =
                ResourceLoader.class.getDeclaredMethod(
                        "loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        loader.setAccessible(true);
        loader.invoke(null, type, path, result);
        return result;
    }

    private static int rate(Object row) {
        return assertDoesNotThrow(() -> (int) row.getClass().getMethod("getBoostTime").invoke(row));
    }

    private Path ratesPath() {
        return Path.of("resources/ExcelBinOutput/CompoundBoostExcelConfigData.json");
    }

    @Test
    void requestFieldsMatchNativeWriter() {
        var type = message("PlayerCompoundMaterialBoostReq");
        assertEquals(12, field(type, "target_compound_group_id").getNumber());
        assertEquals(3, field(type, "consume_material_id").getNumber());
        assertEquals(8, field(type, "consume_material_count").getNumber());
        assertEquals(5, field(type, "is_boost_all").getNumber());
    }

    @Test
    void independentRequestBytesDecodeAllInputs() throws Exception {
        var request = DynamicMessage.parseFrom(message("PlayerCompoundMaterialBoostReq"), REQUEST);
        assertEquals(3, value(request, "target_compound_group_id"));
        assertEquals(101001, value(request, "consume_material_id"));
        assertEquals(2, value(request, "consume_material_count"));
        assertEquals(true, request.getField(field(request.getDescriptorForType(), "is_boost_all")));
        assertTrue(request.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void requestBuilderEmitsVerifiedWireBytes() {
        var type = message("PlayerCompoundMaterialBoostReq");
        var request =
                DynamicMessage.newBuilder(type)
                        .setField(field(type, "target_compound_group_id"), 3)
                        .setField(field(type, "consume_material_id"), 101001)
                        .setField(field(type, "consume_material_count"), 2)
                        .setField(field(type, "is_boost_all"), true)
                        .build();
        assertArrayEquals(REQUEST, request.toByteArray());
    }

    @Test
    void absentBoostAllDefaultsToFalse() throws Exception {
        var type = message("PlayerCompoundMaterialBoostReq");
        var request = DynamicMessage.parseFrom(type, new byte[] {24, 35, 64, 2, 96, 3});
        assertEquals(false, request.getField(field(type, "is_boost_all")));
    }

    @Test
    void responseFieldsMatchNativeParser() {
        var type = message("PlayerCompoundMaterialBoostRsp");
        assertEquals(5, field(type, "compound_que_data_list").getNumber());
        assertEquals(11, field(type, "take_item_list").getNumber());
        assertEquals(10, field(type, "take_status").getNumber());
        assertEquals(2, field(type, "retcode").getNumber());
        assertEquals(
                "CompoundQueueData",
                field(type, "compound_que_data_list").getMessageType().getName());
        assertEquals("ItemParam", field(type, "take_item_list").getMessageType().getName());
    }

    @Test
    void responseDecodesIndependentQueueAndTakenItemFixture() throws Exception {
        var response =
                DynamicMessage.parseFrom(
                        message("PlayerCompoundMaterialBoostRsp"),
                        new byte[] {
                            42, 8, 8, 100, 16, 1, 56, 2, 80, 7, 80, 2, 90, 4, 8, 35, 16, 2
                        });
        assertEquals(0, value(response, "retcode"));
        var status =
                (Descriptors.EnumValueDescriptor)
                        response.getField(field(response.getDescriptorForType(), "take_status"));
        assertEquals(2, status.getNumber());
        var queues =
                (List<?>)
                        response.getField(
                                field(response.getDescriptorForType(), "compound_que_data_list"));
        assertEquals(1, queues.size());
        assertEquals(7, value((DynamicMessage) queues.get(0), "compound_id"));
        assertEquals(100, value((DynamicMessage) queues.get(0), "output_time"));
        var items =
                (List<?>)
                        response.getField(field(response.getDescriptorForType(), "take_item_list"));
        assertEquals(1, items.size());
        assertEquals(35, value((DynamicMessage) items.get(0), "item_id"));
        assertEquals(2, value((DynamicMessage) items.get(0), "count"));
    }

    @Test
    void resultStatesPreserveReferenceNamesAndValues() {
        var type =
                file("CompoundBoostTakeStatusType")
                        .findEnumTypeByName("CompoundBoostTakeStatusType");
        assertNotNull(type);
        assertEquals(0, type.findValueByName("COMPOUND_BOOST_TAKE_STATUS_TYPE_NONE").getNumber());
        assertEquals(
                1, type.findValueByName("COMPOUND_BOOST_TAKE_STATUS_TYPE_BOOST_ONLY").getNumber());
        assertEquals(
                2,
                type.findValueByName("COMPOUND_BOOST_TAKE_STATUS_TYPE_BOOST_AND_TAKE").getNumber());
        assertEquals(
                3, type.findValueByName("COMPOUND_BOOST_TAKE_STATUS_TYPE_BAG_FULL").getNumber());
    }

    @Test
    void resourceIsRegisteredForProductionLoading() throws Exception {
        var type = resourceType();
        assertEquals(
                "CompoundBoostExcelConfigData.json",
                type.getAnnotation(ResourceType.class).name()[0]);
        assertTrue(ResourceLoader.getResourceDefClasses().contains(type));
        assertNotNull(GameData.class.getMethod("getCompoundBoostDataMap").invoke(null));
    }

    @Test
    void productionLoaderKeepsAllEightRates() throws Exception {
        var rows = load(ratesPath());
        assertEquals(8, rows.size());
        var actual = new HashMap<Integer, Integer>();
        rows.forEach((id, row) -> actual.put(id, rate(row)));
        assertEquals(RATES, actual);
    }

    @Test
    void productionGameDataMapAcceptsActualRows() throws Exception {
        @SuppressWarnings("unchecked")
        var map =
                (Int2ObjectMap<Object>)
                        assertDoesNotThrow(
                                () ->
                                        GameData.class
                                                .getMethod("getCompoundBoostDataMap")
                                                .invoke(null));
        var previous = new HashMap<>(map);
        try {
            map.clear();
            Method loader =
                    ResourceLoader.class.getDeclaredMethod(
                            "loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
            loader.setAccessible(true);
            loader.invoke(null, resourceType(), ratesPath(), map);
            assertEquals(RATES.keySet(), map.keySet());
            assertEquals(20, rate(map.get(101001)));
        } finally {
            map.clear();
            map.putAll(previous);
        }
    }

    @Test
    void invalidRatesAreRejectedWithoutDiscardingValidRows() throws Exception {
        var path = temporary.resolve("boost.json");
        Files.writeString(
                path,
                "[{id:101001,boostTime:20},{id:101002,boostTime:0},{id:101003,boostTime:-1},{id:0,boostTime:20}]");
        var rows = load(path);
        assertEquals(Set.of(101001), rows.keySet());
        assertEquals(20, rate(rows.get(101001)));
    }

    @Test
    void allBoostInputsResolveToStackableMaterials() throws Exception {
        var items =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                        ItemData.class);
        var definitions = new HashMap<Integer, ItemData>();
        items.forEach(item -> definitions.put(item.getId(), item));
        for (int id : RATES.keySet()) {
            var item = definitions.get(id);
            assertNotNull(item);
            assertEquals(ItemType.ITEM_MATERIAL, item.getItemType());
            assertTrue(item.getStackLimit() > 0);
        }
    }

    @Test
    void burnerLampExistsSeparatelyFromConsumedMinerals() throws Exception {
        var lamp =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(item -> item.getId() == 220060)
                        .findFirst()
                        .orElseThrow();
        assertEquals(3102898484L, lamp.getNameTextMapHash());
        assertFalse(RATES.containsKey(220060));
    }
}

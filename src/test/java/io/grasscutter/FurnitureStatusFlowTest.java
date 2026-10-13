package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.Descriptors.FieldDescriptor;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.HomeWorldLevelData;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeRsp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class FurnitureStatusFlowTest {
    private AbilityTestFixture environment;
    private FurnitureTakeFlowTest.CraftPlayer player;
    private Map<Integer, HomeWorldLevelData> oldLevels;
    private final Gson gson = new Gson();

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new FurnitureTakeFlowTest.CraftPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.home =
                GameHome.of()
                        .ownerUid(player.getUid())
                        .player(player)
                        .level(1)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        oldLevels = new HashMap<>(GameData.getHomeWorldLevelDataMap());
        GameData.getHomeWorldLevelDataMap().clear();
        level(3);
    }

    @AfterEach
    void cleanup() {
        GameData.getHomeWorldLevelDataMap().clear();
        GameData.getHomeWorldLevelDataMap().putAll(oldLevels);
        environment.close();
    }

    void level(int slots) {
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:1,exp:300,furnitureMakeSlotCount:" + slots + "}",
                                HomeWorldLevelData.class));
    }

    FurnitureMakeSlotItem job(int index) {
        return FurnitureMakeSlotItem.of()
                .index(index)
                .makeId(38)
                .avatarId(0)
                .beginTime(1700000000)
                .durTime(3600)
                .build();
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

    int capacity(FurnitureMakeRsp response) {
        return (Integer) response.getField(response.getDescriptorForType().findFieldByNumber(5));
    }

    @Test
    void capacityHasNativeVarintFieldFive() throws Exception {
        var descriptor = FurnitureMakeRsp.getDescriptor();
        var field = descriptor.findFieldByNumber(5);
        assertNotNull(field);
        assertEquals(FieldDescriptor.Type.UINT32, field.getType());
        assertEquals(3, capacity(FurnitureMakeRsp.parseFrom(HexFormat.of().parseHex("2803"))));
        assertEquals(15, descriptor.findFieldByName("furniture_make_slot").getNumber());
        assertEquals(3, descriptor.findFieldByName("retcode").getNumber());
        assertEquals(2989, PacketOpcodes.FurnitureMakeRsp);
    }

    @Test
    void explicitCapacityReachesTheClient() throws Exception {
        var response = query();
        assertEquals(0, response.getRetcode());
        assertEquals(3, capacity(response));
    }

    @Test
    void capacityDoesNotMeanFreeSlots() throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(job(0));
        var response = query();
        assertEquals(3, capacity(response));
        assertEquals(1, response.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
    }

    @Test
    void emptyLegacyListHasAValidSnapshotWithoutChangingStoredState() throws Exception {
        player.home.setFurnitureMakeSlotItemList(null);
        assertEquals(0, query().getRetcode());
        assertNull(player.home.getFurnitureMakeSlotItemList());
    }

    @Test
    void snapshotPreservesQueuedIdsAndTimes() throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(job(4));
        var data = query().getFurnitureMakeSlot().getFurnitureMakeDataList(0);
        assertEquals(4, data.getIndex());
        assertEquals(38, data.getMakeId());
        assertEquals(1700000000, data.getBeginTime());
        assertEquals(3600, data.getDurTime());
    }

    @Test
    void missingHomeReturnsOneTypedFailure() throws Exception {
        player.home = null;
        assertNotEquals(0, query().getRetcode());
    }

    @Test
    void foreignHomeIsNotDisclosed() throws Exception {
        player.home.setOwnerUid(9);
        player.home.getFurnitureMakeSlotItemList().add(job(0));
        var response = query();
        assertNotEquals(0, response.getRetcode());
        assertFalse(response.hasFurnitureMakeSlot());
        assertEquals(0, capacity(response));
    }

    @Test
    void missingLevelCannotMasqueradeAsAnEmptySuccessfulQueue() throws Exception {
        GameData.getHomeWorldLevelDataMap().clear();
        var response = query();
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, response.getRetcode());
        assertFalse(response.hasFurnitureMakeSlot());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void undefinedCapacityDoesNotLeakUnsignedBits(int slots) throws Exception {
        level(slots);
        var response = query();
        assertEquals(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE, response.getRetcode());
        assertEquals(0, capacity(response));
    }

    @Test
    void wrongLevelMapKeyIsRejected() throws Exception {
        GameData.getHomeWorldLevelDataMap()
                .put(
                        1,
                        gson.fromJson(
                                "{level:2,exp:300,furnitureMakeSlotCount:3}",
                                HomeWorldLevelData.class));
        assertNotEquals(0, query().getRetcode());
    }

    @Test
    void nullQueueEntryReturnsConfigFailure() throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(null);
        assertNotEquals(0, query().getRetcode());
    }

    @Test
    void duplicateQueueIndexesReturnConfigFailure() throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(job(0));
        player.home.getFurnitureMakeSlotItemList().add(job(0));
        assertNotEquals(0, query().getRetcode());
        assertEquals(2, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void negativeQueueIndexIsNotEncodedAsHugeUnsignedId() throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(job(-1));
        assertNotEquals(0, query().getRetcode());
    }

    @Test
    void existingJobsOverAChangedLimitRemainVisible() throws Exception {
        level(1);
        player.home.getFurnitureMakeSlotItemList().add(job(0));
        player.home.getFurnitureMakeSlotItemList().add(job(1));
        var response = query();
        assertEquals(0, response.getRetcode());
        assertEquals(1, capacity(response));
        assertEquals(2, response.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
    }

    @Test
    void aCompletedJobDoesNotCreateFakeFreeCapacity() throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(job(0));
        assertEquals(3, capacity(query()));
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void packetConstructorAlsoUsesTheConfirmedCapacity() throws Exception {
        var packet = new PacketFurnitureMakeRsp(player.home);
        assertEquals(3, capacity(FurnitureMakeRsp.parseFrom(packet.getData())));
    }
}

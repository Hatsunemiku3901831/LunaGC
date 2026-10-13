package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.HomeWorldLevelData;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeReqOuterClass.FurnitureMakeReq;
import emu.grasscutter.net.proto.FurnitureMakeRspOuterClass.FurnitureMakeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.packet.recv.HandlerFurnitureMakeReq;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class FurnitureQueryProtocol71Test {
    private AbilityTestFixture environment;
    private FurnitureTakeFlowTest.CraftPlayer player;
    private Map<Integer, HomeWorldLevelData> previous;
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
                        .level(10)
                        .exp(0)
                        .furnitureMakeSlotItemList(new ArrayList<>())
                        .build();
        previous = new HashMap<>(GameData.getHomeWorldLevelDataMap());
        GameData.getHomeWorldLevelDataMap().clear();
        GameData.getHomeWorldLevelDataMap()
                .put(
                        10,
                        gson.fromJson(
                                "{level:10,HALLBIKHGGJ:15,MBABBBPOFFM:14400}",
                                HomeWorldLevelData.class));
    }

    @AfterEach
    void cleanup() {
        GameData.getHomeWorldLevelDataMap().clear();
        GameData.getHomeWorldLevelDataMap().putAll(previous);
        environment.close();
    }

    FurnitureMakeSlotItem job() {
        return FurnitureMakeSlotItem.of()
                .index(4)
                .makeId(38)
                .beginTime(1700000000)
                .durTime(43200)
                .build();
    }

    FurnitureMakeRsp response() throws Exception {
        var session = (InvestigationRewardTest.ClaimSession) player.getSession();
        var replies = new ArrayList<BasePacket>(player.packets);
        replies.addAll(session.responses);
        var packets = replies.stream().filter(p -> p.getOpcode() == 2989).toList();
        assertEquals(1, packets.size());
        return FurnitureMakeRsp.parseFrom(packets.get(0).getData());
    }

    FurnitureMakeRsp request(byte[] payload) throws Exception {
        player.packets.clear();
        ((InvestigationRewardTest.ClaimSession) player.getSession()).responses.clear();
        assertDoesNotThrow(
                () ->
                        new HandlerFurnitureMakeReq()
                                .handle(player.getSession(), new byte[0], payload));
        return response();
    }

    @Test
    void queryOpcodeMatchesTheNativeEmptyRequestGetter() throws Exception {
        assertEquals(20116, PacketOpcodes.class.getField("FurnitureMakeReq").getInt(null));
    }

    @Test
    void handlerAnnotationUsesTheNativeQueryOpcode() {
        assertEquals(20116, HandlerFurnitureMakeReq.class.getAnnotation(Opcodes.class).value());
    }

    @Test
    void nativeOpcodeActuallyRegistersAHandler() throws Exception {
        var dispatcher = new GameServerPacketHandler(PacketHandler.class);
        var field = GameServerPacketHandler.class.getDeclaredField("handlers");
        field.setAccessible(true);
        var handlers = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>) field.get(dispatcher);
        assertInstanceOf(HandlerFurnitureMakeReq.class, handlers.get(20116));
    }

    @Test
    void otherFurnitureRequestsDoNotCollideWithTheQuery() throws Exception {
        for (String name :
                List.of(
                        "FurnitureMakeStartReq",
                        "TakeFurnitureMakeReq",
                        "FurnitureMakeCancelReq",
                        "TakeFurnitureMakeAllReq"))
            assertNotEquals(20116, PacketOpcodes.class.getField(name).getInt(null));
    }

    @Test
    void nativeQueryHasNoBusinessFields() {
        assertTrue(FurnitureMakeReq.getDescriptor().getFields().isEmpty());
        assertArrayEquals(new byte[0], FurnitureMakeReq.getDefaultInstance().toByteArray());
    }

    @Test
    void validEmptyQueryReachesTheFifteenSlotResponse() throws Exception {
        var result = request(new byte[0]);
        assertEquals(0, result.getRetcode());
        assertEquals(15, result.getFurnitureMakeSlotNum());
        assertEquals(0, result.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
    }

    @Test
    void queuedJobRemainsVisibleWithoutBeingConsumed() throws Exception {
        var slot = job();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        var result = request(new byte[0]);
        assertEquals(slot.toProto(), result.getFurnitureMakeSlot().getFurnitureMakeDataList(0));
        assertSame(slot, player.home.getFurnitureMakeSlotItemList().get(0));
        assertEquals(0, player.inventory.getItemCountById(360101));
    }

    @ParameterizedTest
    @ValueSource(strings = {"08", "1280", "0a03ff", "00", "0e", "0b", "0c"})
    void malformedQueryGetsOneErrorWithoutAStateSnapshot(String hex) throws Exception {
        player.home.getFurnitureMakeSlotItemList().add(job());
        var result = request(HexFormat.of().parseHex(hex));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, result.getRetcode());
        assertEquals(0, result.getFurnitureMakeSlotNum());
        assertEquals(0, result.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
        assertEquals(1, player.home.getFurnitureMakeSlotItemList().size());
    }

    @Test
    void missingPayloadIsNotAcceptedAsAValidEmptyQuery() throws Exception {
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, request(null).getRetcode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0801", "1202abcd", "1d78563412"})
    void validUnknownFieldsRemainForwardCompatible(String hex) throws Exception {
        assertEquals(0, request(HexFormat.of().parseHex(hex)).getRetcode());
    }

    @Test
    void missingPlayerGetsOneTypedHomeError() throws Exception {
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), null));
        assertEquals(
                Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE,
                request(new byte[0]).getRetcode());
    }

    @Test
    void foreignHomeCannotExposeItsQueue() throws Exception {
        player.home.setOwnerUid(player.getUid() + 1);
        player.home.getFurnitureMakeSlotItemList().add(job());
        var result = request(new byte[0]);
        assertEquals(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE, result.getRetcode());
        assertEquals(0, result.getFurnitureMakeSlot().getFurnitureMakeDataListCount());
    }

    @Test
    void repeatQueriesAreReadOnly() throws Exception {
        var slot = job();
        player.home.getFurnitureMakeSlotItemList().add(slot);
        assertEquals(request(new byte[0]), request(new byte[0]));
        assertSame(slot, player.home.getFurnitureMakeSlotItemList().get(0));
        assertEquals(0, player.home.getExp());
        assertNull(player.home.getPendingFurnitureTake());
        assertNull(player.home.getPendingFurnitureStart());
        assertNull(player.home.getPendingFurnitureBulk());
    }
}

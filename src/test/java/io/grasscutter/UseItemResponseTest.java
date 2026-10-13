package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;

import org.junit.jupiter.api.*;

import java.util.*;

/**
 * Request failures must finish with a typed response; success preserves the native scalar choice.
 */
class UseItemResponseTest {
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private InvestigationRewardTest.ClaimSession session;
    private GameServerPacketHandler registry;
    private GameItem source;
    private ItemData previous;
    private int calls, count, option;
    private long guid, target;
    private boolean multiplayer, reject, fail;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        var server = environment.scene.getWorld().getServer();
        session = new InvestigationRewardTest.ClaimSession(server, player);
        player.setSession(session);
        var data =
                new Gson()
                        .fromJson(
                                "{id:116002,itemType:'ITEM_MATERIAL',stackLimit:999}",
                                ItemData.class);
        previous = GameData.getItemDataMap().put(116002, data);
        source = new GameItem(data, 1);
        source.setOwner(player);
        var inventory =
                new InventorySystem(server) {
                    @Override
                    public GameItem useItem(
                            Player user,
                            long targetGuid,
                            long itemGuid,
                            int amount,
                            int choice,
                            boolean isMultiplayer) {
                        assertSame(player, user);
                        calls++;
                        count = amount;
                        option = choice;
                        guid = itemGuid;
                        target = targetGuid;
                        multiplayer = isMultiplayer;
                        if (fail) throw new IllegalStateException("injected inventory failure");
                        return reject ? null : source;
                    }
                };
        var field = server.getClass().getDeclaredField("inventorySystem");
        field.setAccessible(true);
        field.set(server, inventory);
        registry = new GameServerPacketHandler(PacketHandler.class);
    }

    @AfterEach
    void cleanup() {
        if (previous == null) GameData.getItemDataMap().remove(116002);
        else GameData.getItemDataMap().put(116002, previous);
        environment.close();
    }

    private UseItemReq.Builder request() {
        return UseItemReq.newBuilder()
                .setGuid(source.getGuid())
                .setTargetGuid(987654321L)
                .setCount(2)
                .setOptionIdx(3);
    }

    private UseItemRsp execute(byte[] payload) throws Exception {
        registry.handle(session, PacketOpcodes.UseItemReq, new byte[0], payload);
        assertEquals(1, session.responses.size(), "request must send exactly one response");
        var response = session.responses.get(0);
        assertEquals(PacketOpcodes.UseItemRsp, response.getOpcode());
        return UseItemRsp.parseFrom(response.getData());
    }

    @Test
    void malformedRequestReturnsTypedFailureWithoutUsingInventory() throws Exception {
        assertNotEquals(0, execute(new byte[] {0x1a, 0x02, 0x01}).getRetcode());
        assertEquals(0, calls);
    }

    @Test
    void nullPayloadReturnsTypedFailureWithoutUsingInventory() throws Exception {
        assertNotEquals(0, execute(null).getRetcode());
        assertEquals(0, calls);
    }

    @Test
    void inventoryExceptionReturnsFailureOnceWithoutRetry() throws Exception {
        fail = true;
        assertNotEquals(0, execute(request().build().toByteArray()).getRetcode());
        assertEquals(1, calls);
    }

    @Test
    void rejectedUseReturnsFailureOnce() throws Exception {
        reject = true;
        assertNotEquals(0, execute(request().build().toByteArray()).getRetcode());
        assertEquals(1, calls);
    }

    @Test
    void successfulResponseKeepsTheSelectedOption() throws Exception {
        assertEquals(3, execute(request().build().toByteArray()).getOptionIdx());
    }

    @Test
    void successfulResponseKeepsTheSourceItemAndGuid() throws Exception {
        var reply = execute(request().build().toByteArray());
        assertEquals(0, reply.getRetcode());
        assertEquals(116002, reply.getItemId());
        assertEquals(source.getGuid(), reply.getGuid());
        assertEquals(987654321L, reply.getTargetGuid());
    }

    @Test
    void requestPassesAllScalarUseArgumentsOnce() throws Exception {
        execute(request().setIsEnterMpDungeonTeam(true).build().toByteArray());
        assertEquals(1, calls);
        assertEquals(2, count);
        assertEquals(3, option);
        assertEquals(source.getGuid(), guid);
        assertEquals(987654321L, target);
        assertTrue(multiplayer);
    }

    @Test
    void zeroChoiceStaysZeroForAnOrdinaryItem() throws Exception {
        assertEquals(0, execute(request().setOptionIdx(0).build().toByteArray()).getOptionIdx());
        assertEquals(0, option);
    }

    @Test
    void unsignedChoiceBitsSurviveTheResponse() throws Exception {
        assertEquals(-1, execute(request().setOptionIdx(-1).build().toByteArray()).getOptionIdx());
    }

    @Test
    void responseTransportFailureDoesNotRetryTheInventoryOrSendTwice() throws Exception {
        var failingSession =
                new InvestigationRewardTest.ClaimSession(session.getServer(), player) {
                    @Override
                    public void send(emu.grasscutter.net.packet.BasePacket packet) {
                        super.send(packet);
                        throw new IllegalStateException("injected transport failure");
                    }
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        new emu.grasscutter.server.packet.recv.HandlerUseItemReq()
                                .handle(
                                        failingSession,
                                        new byte[0],
                                        request().build().toByteArray()));
        assertEquals(1, calls);
        assertEquals(1, failingSession.responses.size());
    }
}

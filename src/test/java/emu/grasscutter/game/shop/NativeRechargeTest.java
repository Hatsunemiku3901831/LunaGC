package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopRspOuterClass.GetShopRsp;
import emu.grasscutter.net.proto.RechargeReqOuterClass.RechargeReq;
import emu.grasscutter.net.proto.RechargeRspOuterClass.RechargeRsp;
import emu.grasscutter.net.proto.ShopCardProductOuterClass.ShopCardProduct;
import emu.grasscutter.net.proto.ShopMcoinProductOuterClass.ShopMcoinProduct;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerRechargeReq;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class NativeRechargeTest {
    @TempDir Path dir;
    ItemData oldCrystals;
    ServerRunMode oldMode;
    GameSession session;
    FreeStore store;
    ShopCatalog catalog;
    final List<BasePacket> packets = new ArrayList<>();
    final Player player = new Player() {
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
        @Override public void save() {}
    };
    int saves;
    boolean failSave;

    @BeforeEach void setup() throws Exception {
        oldCrystals = GameData.getItemDataMap().put(203,
                new Gson().fromJson("{\"id\":203,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        oldMode = Grasscutter.getRunMode();
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        var server = new GameServer();
        catalog = new ShopCatalog(dir.resolve("shop.json"));
        store = new FreeStore(catalog) {
            @Override protected void persist(Player player) {
                if (failSave) throw new IllegalStateException("simulated storage failure");
                saves++;
            }
        };
        store.load();
        var shops = new ShopSystem(server) {
            @Override public FreeStore getFreeStore() { return store; }
        };
        var field = GameServer.class.getDeclaredField("shopSystem");
        field.setAccessible(true);
        field.set(server, shops);
        session = new GameSession(server) {
            @Override public void send(BasePacket packet) { packets.add(packet); }
        };
        session.setPlayer(player);
        player.setSession(session);
        player.setPrimogems(12345);
        player.setMora(67890);
        packets.clear();
    }

    @AfterEach void restore() {
        if (oldCrystals == null) GameData.getItemDataMap().remove(203);
        else GameData.getItemDataMap().put(203, oldCrystals);
        Grasscutter.setRunModeOverride(oldMode);
    }

    RechargeRsp send(RechargeReq request) throws Exception {
        packets.clear();
        new HandlerRechargeReq().handle(session, new byte[0], request.toByteArray());
        return RechargeRsp.parseFrom(packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.RechargeRsp).findFirst().orElseThrow().getData());
    }

    RechargeReq request(String id) {
        // Client-supplied counts and bonuses must never influence the actual grant.
        return RechargeReq.newBuilder().setMcoinProduct(ShopMcoinProduct.newBuilder()
                .setProductId(id).setMcoinBase(999999).setMcoinFirst(999999).setBoughtNum(999)).build();
    }

    @Test void allSixNativePacksGrantFirstAndRepeatBonusesWithoutSpendingCurrency() throws Exception {
        var products = store.products().stream().filter(p -> p.kind().equals("crystals")).toList();
        assertEquals(6, products.size());
        int[] first = {120, 600, 1960, 3960, 6560, 12960};
        int[] repeat = {60, 330, 1090, 2240, 3880, 8080};
        int total = 0;
        for (int i = 0; i < 6; i++) {
            var product = products.get(i);
            for (int purchase = 1; purchase <= 2; purchase++) {
                var response = send(request(product.productId()));
                total += purchase == 1 ? first[i] : repeat[i];
                assertEquals(0, response.getRetcode());
                assertEquals("LunaGC.FreeRecharge:" + product.productId(), response.getProductId());
                assertEquals(total, player.getCrystals());
                assertEquals(12345, player.getPrimogems());
                assertEquals(67890, player.getMora());
                assertEquals(i * 2 + purchase, saves);
                var refreshed = GetShopRsp.parseFrom(packets.get(packets.size() - 1).getData()).getShop();
                assertEquals(903, refreshed.getShopType());
                assertEquals(purchase, refreshed.getMcoinProductList(i).getBoughtNum());
                assertTrue(packets.stream().anyMatch(p -> p.getOpcode() == PacketOpcodes.ItemAddHintNotify));
            }
        }
        assertEquals(12, player.getFreePurchaseReceipts().size());
    }

    @Test void invalidDisabledAndOverflowRequestsNeverAcknowledgeOrGrant() throws Exception {
        var id = "ys_chn_primogem1ststall_tier1";
        for (var request : List.of(RechargeReq.getDefaultInstance(), request("forged"),
                request(id).toBuilder().setCardProduct(ShopCardProduct.newBuilder().setProductId(id)).build(),
                RechargeReq.newBuilder().setCardProduct(ShopCardProduct.newBuilder().setProductId(id)).build())) {
            assertEquals(1, send(request).getRetcode());
        }
        catalog.setProductEnabled("crystals:1", false);
        assertEquals(1, send(request(id)).getRetcode());
        assertEquals(0, player.getCrystals());
        catalog.setProductEnabled("crystals:1", true);
        player.setCrystals(Integer.MAX_VALUE - 119);
        assertEquals(1, send(request(id)).getRetcode());
        assertEquals(Integer.MAX_VALUE - 119, player.getCrystals());
        assertEquals(0, saves);
        assertTrue(player.getFreeProductPurchases().isEmpty());
    }

    @Test void persistenceFailureReturnsFailureWithoutLocalSuccessMarkerOrCatalogRefresh() throws Exception {
        failSave = true;
        var response = send(request("ys_chn_primogem1ststall_tier1"));
        assertEquals(1, response.getRetcode());
        assertFalse(response.getProductId().startsWith(HandlerRechargeReq.LOCAL_CRYSTAL_ACK));
        assertTrue(packets.stream().noneMatch(p -> p.getOpcode() == PacketOpcodes.GetShopRsp));
        // The existing store mutates memory before persistence; this is not a DB transaction.
        assertEquals(120, player.getCrystals());
        assertEquals(0, saves);
    }
}

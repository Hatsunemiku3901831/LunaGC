package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopBatchReqOuterClass.GetShopBatchReq;
import emu.grasscutter.net.proto.GetShopBatchRspOuterClass.GetShopBatchRsp;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerGetShopBatchReq;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.*;
import org.junit.jupiter.api.Test;

class ShopBatchSaveTest {
    private static class CountingPlayer extends Player {
        int saves;
        boolean savedWhileHoldingInventory;

        @Override
        public void save() {
            saves++;
            savedWhileHoldingInventory |= Thread.holdsLock(getInventory());
        }
    }

    @Test
    void firstBatchInitializesAllLimitsThenSavesOnceOutsideInventory() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            var manager = new ShopSystem(server);
            var catalog = new Int2ObjectOpenHashMap<List<ShopInfo>>();
            for (int type : List.of(1001, 1002)) {
                var goods = new ArrayList<ShopInfo>();
                for (int i = 0; i < 128; i++) {
                    var good = new ShopInfo();
                    good.setGoodsId(type * 1000 + i);
                    good.setGoodsItem(new ItemParamData(990071, 1));
                    good.setCostItemList(List.of());
                    goods.add(good);
                }
                catalog.put(type, goods);
            }
            manager.getCatalog().replaceBase(catalog);
            var field = GameServer.class.getDeclaredField("shopSystem");
            field.setAccessible(true);
            field.set(server, manager);
            var player = new CountingPlayer();
            var packets = new ArrayList<BasePacket>();
            var session =
                    new GameSession(server) {
                        @Override
                        public Player getPlayer() {
                            return player;
                        }

                        @Override
                        public void send(BasePacket packet) {
                            packets.add(packet);
                        }
                    };
            var request =
                    GetShopBatchReq.newBuilder()
                            .addAllShopTypeList(List.of(1001, 1002, 1001))
                            .build()
                            .toByteArray();
            new HandlerGetShopBatchReq().handle(session, new byte[0], request);
            assertEquals(1, player.saves, "Catalog reads must not queue one whole-player write per good");
            assertFalse(player.savedWhileHoldingInventory);
            assertEquals(256, player.getShopLimit().size());
            assertTrue(player.getShopLimit().stream().allMatch(limit -> limit.getHasBought() == 0));
            var reply =
                    packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.GetShopBatchRsp)
                            .findFirst()
                            .orElseThrow();
            var response = GetShopBatchRsp.parseFrom(reply.getData());
            assertEquals(2, response.getShopListCount());
            assertEquals(
                    256, response.getShopListList().stream().mapToInt(s -> s.getGoodsListCount()).sum());
            new HandlerGetShopBatchReq().handle(session, new byte[0], request);
            assertEquals(2, player.saves);
            assertEquals(256, player.getShopLimit().size());
            player.addShopLimit(1001000, 1, 0);
            assertEquals(3, player.saves, "Purchase-side limit changes still request persistence");
            assertEquals(1, player.getGoodsLimit(1001000).getHasBought());
        }
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class ItemUseCooldownTest {
    static List<ItemData> revivalFoods;
    FoodBuffEffectsTest env;
    InventorySystem inventory;
    final AtomicLong now = new AtomicLong(1_800_000_000_000L);

    @BeforeAll
    static void resources() throws Exception {
        FoodBuffEffectsTest.resources();
        revivalFoods =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(d -> Set.of(108005, 108008).contains(d.getId()))
                        .toList();
        revivalFoods.forEach(ItemData::onLoad);
        assertEquals(2, revivalFoods.size());
    }

    @BeforeEach
    void setup() throws Exception {
        env = new FoodBuffEffectsTest();
        env.setup();
        inventory = new InventorySystem(env.env.fixture.scene.getWorld().getServer(), now::get);
    }

    @AfterEach
    void close() {
        env.close();
    }

    void fallen() throws Exception {
        env.first.setFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP, 0);
        var method =
                emu.grasscutter.game.entity.GameEntity.class.getDeclaredMethod(
                        "setDead", boolean.class);
        method.setAccessible(true);
        method.invoke(env.first, true);
    }

    UseItemParams params(ItemData data) {
        return new UseItemParams(
                env.env.fixture.player, data.getUseTarget(), env.first.getAvatar(), 1, 0, false);
    }

    ItemData food(int id, int group, String seconds, ItemUseAction action) throws Exception {
        var data =
                JsonUtils.decode(
                        "{id:"
                                + id
                                + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_FOOD',cdGroup:"
                                + group
                                + ",cdTime:"
                                + seconds
                                + ",useTarget:'ITEM_USE_TARGET_NONE'}",
                        ItemData.class);
        var field = ItemData.class.getDeclaredField("itemUseActions");
        field.setAccessible(true);
        field.set(data, List.of(action));
        return data;
    }

    ItemUseAction success() {
        return new ItemUseAction() {
            @Override
            public boolean useItem(UseItemParams p) {
                return true;
            }
        };
    }

    @Test
    void realRevivalRecipesShareTheir120SecondCooldown() throws Exception {
        fallen();
        var first = revivalFoods.get(0);
        var second = revivalFoods.get(1);
        assertTrue(inventory.useItemDirect(first, params(first)));
        fallen();
        assertFalse(inventory.useItemDirect(second, params(second)));
        assertFalse(env.first.isAlive());
        now.addAndGet(119999);
        assertFalse(inventory.useItemDirect(second, params(second)));
        now.incrementAndGet();
        assertTrue(inventory.useItemDirect(second, params(second)));
        assertTrue(env.first.isAlive());
    }

    @Test
    void groupIsSharedAcrossDifferentTargetCharacters() throws Exception {
        var data = food(1, 99, "120", success());
        assertTrue(inventory.useItemDirect(data, params(data)));
        var other = env.second();
        var request = params(data);
        request.targetAvatar = other.getAvatar();
        assertFalse(inventory.useItemDirect(data, request));
    }

    @Test
    void differentGroupsDoNotBlockEachOther() throws Exception {
        var first = food(1, 99, "120", success());
        var second = food(2, 98, "300", success());
        assertTrue(inventory.useItemDirect(first, params(first)));
        assertTrue(inventory.useItemDirect(second, params(second)));
        assertFalse(inventory.useItemDirect(first, params(first)));
    }

    @Test
    void failedUseDoesNotStartCooldown() throws Exception {
        var reject = food(1, 99, "120", new ItemUseAction());
        assertFalse(inventory.useItemDirect(reject, params(reject)));
        var valid = food(2, 99, "120", success());
        assertTrue(inventory.useItemDirect(valid, params(valid)));
    }

    @Test
    void throwingUseDoesNotStartCooldown() throws Exception {
        var reject =
                food(
                        1,
                        99,
                        "120",
                        new ItemUseAction() {
                            @Override
                            public boolean useItem(UseItemParams p) {
                                throw new IllegalStateException("food failure");
                            }
                        });
        assertThrows(
                IllegalStateException.class, () -> inventory.useItemDirect(reject, params(reject)));
        var valid = food(2, 99, "120", success());
        assertTrue(inventory.useItemDirect(valid, params(valid)));
    }

    @Test
    void invalidDurationCannotExecuteTheAction() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var data =
                food(
                        1,
                        99,
                        "-1",
                        new ItemUseAction() {
                            @Override
                            public boolean useItem(UseItemParams p) {
                                calls.incrementAndGet();
                                return true;
                            }
                        });
        assertFalse(inventory.useItemDirect(data, params(data)));
        assertEquals(0, calls.get());
    }

    @Test
    void twoConcurrentRequestsCannotUseTheSameGroupTwice() throws Exception {
        var data = food(1, 99, "120", success());
        var gate = new java.util.concurrent.CyclicBarrier(2);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first =
                    executor.submit(
                            () -> {
                                gate.await();
                                return inventory.useItemDirect(data, params(data));
                            });
            var second =
                    executor.submit(
                            () -> {
                                gate.await();
                                return inventory.useItemDirect(data, params(data));
                            });
            int successes =
                    (first.get(5, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                            + (second.get(5, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void realRequestReturnsCooldownAndPreservesTheSecondFood() throws Exception {
        var server = env.env.fixture.scene.getWorld().getServer();
        var field = server.getClass().getDeclaredField("inventorySystem");
        field.setAccessible(true);
        var old = field.get(server);
        field.set(server, inventory);
        var oldSession=env.env.fixture.player.getSession();
        var session=new emu.grasscutter.server.game.GameSession(server) {
            @Override public void send(emu.grasscutter.net.packet.BasePacket packet) {
                env.env.fixture.player.packets.add(packet);
            }
        };
        session.setPlayer(env.env.fixture.player);
        env.env.fixture.player.setSession(session);
        try {
            for (var data : revivalFoods)
                env.env.environment.put(
                        emu.grasscutter.data.GameData.getItemDataMap(), data.getId(), data);
            var first = env.stock(108005);
            fallen();
            var handler = new emu.grasscutter.server.packet.recv.HandlerUseItemReq();
            var request =
                    emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq.newBuilder()
                            .setGuid(first.getGuid())
                            .setTargetGuid(50001)
                            .setCount(1)
                            .build();
            handler.handle(env.env.fixture.player.getSession(), new byte[0], request.toByteArray());
            assertEquals(1, first.getCount());
            fallen();
            var second = env.stock(108008);
            handler.handle(
                    env.env.fixture.player.getSession(),
                    new byte[0],
                    request.toBuilder().setGuid(second.getGuid()).build().toByteArray());
            assertEquals(2, second.getCount());
            assertFalse(env.first.isAlive());
            var replies =
                    env.env.fixture.player.packets.stream()
                            .filter(
                                    p ->
                                            p.getOpcode()
                                                    == emu.grasscutter.net.packet.PacketOpcodes
                                                            .UseItemRsp)
                            .toList();
            assertEquals(2, replies.size());
            assertEquals(
                    0,
                    emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp.parseFrom(
                                    replies.get(0).getData())
                            .getRetcode());
            assertEquals(
                    608,
                    emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp.parseFrom(
                                    replies.get(1).getData())
                            .getRetcode());
        } finally {
            field.set(server, old);
            env.env.fixture.player.setSession(oldSession);
        }
    }

    @Test
    void fractionalDurationIsNotTruncatedToZero() throws Exception {
        var data = food(1, 5, "0.125", success());
        assertTrue(inventory.useItemDirect(data, params(data)));
        now.addAndGet(124);
        assertFalse(inventory.useItemDirect(data, params(data)));
        now.incrementAndGet();
        assertTrue(inventory.useItemDirect(data, params(data)));
    }

    @Test
    void cooldownBelongsToPlayerRatherThanInventorySystemInstance() throws Exception {
        var data = food(1, 99, "120", success());
        assertTrue(inventory.useItemDirect(data, params(data)));
        var another = new InventorySystem(env.env.fixture.scene.getWorld().getServer(), now::get);
        assertFalse(another.useItemDirect(data, params(data)));
    }

    @Test
    void zeroDurationDoesNotBlockSubsequentUse() throws Exception {
        var data = food(1, 99, "0", success());
        assertTrue(inventory.useItemDirect(data, params(data)));
        assertTrue(inventory.useItemDirect(data, params(data)));
    }

    @Test
    void backwardsClockCannotBypassAnUnexpiredCooldown() throws Exception {
        var data = food(1, 99, "120", success());
        assertTrue(inventory.useItemDirect(data, params(data)));
        now.addAndGet(-10000);
        assertFalse(inventory.useItemDirect(data, params(data)));
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/** Inventory effects must serialize with that player's payments, without a server-wide monitor. */
class InventoryUseConcurrencyTest {
    private static final int ITEM = 990101;
    private ItemData previous;
    private ItemData data;
    private InventorySystem system;
    private TestPlayer player;

    private static final class TestPlayer extends Player {
        @Override public void sendPacket(BasePacket packet) {}
        @Override public void save() {}
    }

    @BeforeEach
    void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        data = JsonUtils.decode("{id:990101,itemType:'ITEM_MATERIAL',"
                + "materialType:'MATERIAL_EXCHANGE',stackLimit:99,"
                + "useTarget:'ITEM_USE_TARGET_NONE'}", ItemData.class);
        previous = GameData.getItemDataMap().put(ITEM, data);
        player = new TestPlayer();
        system = new InventorySystem(null, () -> 100000, ignored -> {});
    }

    @AfterEach
    void restore() {
        if (previous == null) GameData.getItemDataMap().remove(ITEM);
        else GameData.getItemDataMap().put(ITEM, previous);
    }

    private void actions(ItemUseAction... actions) throws Exception {
        var field = ItemData.class.getDeclaredField("itemUseActions");
        field.setAccessible(true);
        field.set(data, List.of(actions));
    }

    private UseItemParams params(Player owner) {
        return new UseItemParams(owner, ItemUseTarget.ITEM_USE_TARGET_NONE);
    }

    private GameItem stock(int count) {
        var item = new GameItem(data, count);
        item.setOwner(player);
        player.getInventory().getItems().put(item.getGuid(), item);
        player.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        return item;
    }

    private static <T> Thread start(CompletableFuture<T> result, Callable<T> action) {
        var worker = new Thread(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }, "inventory-use-concurrency-test");
        worker.setDaemon(true);
        worker.start();
        return worker;
    }

    private static void awaitBlocked(Thread worker, CompletableFuture<?> result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!result.isDone() && worker.getState() != Thread.State.BLOCKED
                && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.BLOCKED, worker.getState(), "First use must reach the held inventory");
    }

    @Test
    void blockedInventoryDoesNotStopAnotherPlayersRealItemGrant() throws Exception {
        actions(new ItemUseAddItem(new String[] {Integer.toString(ITEM), "1"}));
        var other = new TestPlayer();
        var first = new CompletableFuture<Boolean>();
        var second = new CompletableFuture<Boolean>();
        Thread firstWorker = null;
        Thread secondWorker = null;
        try {
            synchronized (player.getInventory()) {
                firstWorker = start(first, () -> system.useItemDirect(data, params(player)));
                awaitBlocked(firstWorker, first);
                secondWorker = start(second, () -> system.useItemDirect(data, params(other)));
                // The first operation owns the old global system lock while waiting for this bag.
                // Timeout unwinds this synchronized block, so both workers always get released.
                assertTrue(second.get(2, TimeUnit.SECONDS), "An unrelated player's grant must finish");
                assertEquals(1, other.getInventory().getItemCountById(ITEM));
            }
        } finally {
            if (firstWorker != null) firstWorker.join(3000);
            if (secondWorker != null) secondWorker.join(3000);
        }
        assertTrue(first.get(1, TimeUnit.SECONDS));
        assertEquals(1, player.getInventory().getItemCountById(ITEM));
    }

    @Test
    void directEffectDoesNotAcquireGlobalSystemWhileInventoryIsHeld() throws Exception {
        actions(new ItemUseAction() {
            @Override public boolean useItem(UseItemParams params) {
                assertFalse(Thread.holdsLock(system), "Use-on-gain must not enter the global system monitor");
                assertTrue(Thread.holdsLock(player.getInventory()));
                return true;
            }
        });
        synchronized (player.getInventory()) {
            assertTrue(system.useItemDirect(data, params(player)));
        }
    }

    @Test
    void ordinaryItemEffectAndDebitShareTheInventoryTransaction() throws Exception {
        actions(new ItemUseAction() {
            @Override public boolean useItem(UseItemParams params) {
                assertTrue(Thread.holdsLock(player.getInventory()), "Ownership, effects and debit must be atomic");
                assertFalse(Thread.holdsLock(system));
                return true;
            }
        });
        var item = stock(1);
        assertSame(item, system.useItem(player, 0, item.getGuid(), 1, 0, false));
        assertEquals(0, item.getCount());
        assertNull(player.getInventory().getItemByGuid(item.getGuid()));
    }

    @Test
    void twoConcurrentUsesOfTheLastItemApplyOnlyOneEffect() throws Exception {
        var effects = new AtomicInteger();
        actions(new ItemUseAction() {
            @Override public boolean useItem(UseItemParams params) {
                effects.incrementAndGet();
                return true;
            }
        });
        var item = stock(1);
        var gate = new CountDownLatch(1);
        var first = new CompletableFuture<GameItem>();
        var second = new CompletableFuture<GameItem>();
        Callable<GameItem> use = () -> {
            assertTrue(gate.await(3, TimeUnit.SECONDS));
            return system.useItem(player, 0, item.getGuid(), 1, 0, false);
        };
        var a = start(first, use);
        var b = start(second, use);
        try {
            gate.countDown();
            var results = Arrays.asList(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Objects::nonNull).count());
            assertEquals(1, effects.get());
            assertEquals(0, item.getCount());
        } finally {
            gate.countDown();
            a.join(3000);
            b.join(3000);
        }
    }

    @Test
    void gadgetUseWaitsForSceneBeforeHoldingInventory() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            player.setScene(fixture.scene);
            player.setWorld(fixture.scene.getWorld());
            player.setMora(10000);
            actions(new ItemUseMakeGadget(new String[] {"70300080"}));
            var item = stock(1);
            var used = new CompletableFuture<GameItem>();
            var paid = new CompletableFuture<Boolean>();
            Thread useWorker = null;
            Thread payWorker = null;
            try {
                synchronized (fixture.scene) {
                    useWorker = start(used, () -> system.useItem(player, 0, item.getGuid(), 1, 0, false));
                    awaitBlocked(useWorker, used);
                    payWorker = start(paid, () -> player.getInventory().payItem(202, 1));
                    assertTrue(paid.get(2, TimeUnit.SECONDS), "Scene entry must not wait on a bag held by gadget placement");
                }
            } finally {
                if (useWorker != null) useWorker.join(3000);
                if (payWorker != null) payWorker.join(3000);
            }
            assertSame(item, used.get(1, TimeUnit.SECONDS));
            assertEquals(9999, player.getMora());
            assertEquals(0, item.getCount());
            assertEquals(1, fixture.scene.getEntities().values().stream()
                    .filter(emu.grasscutter.game.entity.EntityVehicle.class::isInstance).count());
        }
    }
}

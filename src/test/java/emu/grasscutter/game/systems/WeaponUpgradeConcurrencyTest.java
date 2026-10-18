package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.weapon.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class WeaponUpgradeConcurrencyTest {
    private final Map<Integer, ItemData> oldItems = new HashMap<>();
    private final Map<Integer, WeaponPromoteData> oldPromotes = new HashMap<>();
    private final Map<Integer, WeaponLevelData> oldLevels = new HashMap<>();
    private TestPlayer player;
    private InventorySystem system;

    private static final class TestPlayer extends Player {
        @Override public void sendPacket(BasePacket packet) {}
        @Override public void save() {}
    }

    private <T> T[] resource(String name, Class<T[]> type) throws Exception {
        return new Gson().fromJson(Files.readString(Path.of("resources", "ExcelBinOutput", name)), type);
    }

    @BeforeEach
    void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        for (var row : resource("WeaponExcelConfigData.json", ItemData[].class)) {
            if (row.getId() != 11101 && row.getId() != 11301) continue;
            row.onLoad();
            oldItems.put(row.getId(), GameData.getItemDataMap().put(row.getId(), row));
        }
        for (var row : resource("WeaponPromoteExcelConfigData.json", WeaponPromoteData[].class)) {
            if (row.getWeaponPromoteId() != 11101) continue;
            row.onLoad();
            oldPromotes.put(row.getId(), GameData.getWeaponPromoteDataMap().put(row.getId(), row));
        }
        for (var row : resource("WeaponLevelExcelConfigData.json", WeaponLevelData[].class)) {
            if (row.getLevel() > 20) continue;
            oldLevels.put(row.getId(), GameData.getWeaponLevelDataMap().put(row.getId(), row));
        }
        player = new TestPlayer();
        player.setMora(10000);
        system = new InventorySystem(null);
    }

    @AfterEach
    void restore() {
        oldItems.forEach((id, before) -> {
            if (before == null) GameData.getItemDataMap().remove(id);
            else GameData.getItemDataMap().put(id, before);
        });
        oldPromotes.forEach((id, before) -> {
            if (before == null) GameData.getWeaponPromoteDataMap().remove(id);
            else GameData.getWeaponPromoteDataMap().put(id, before);
        });
        oldLevels.forEach((id, before) -> {
            if (before == null) GameData.getWeaponLevelDataMap().remove(id);
            else GameData.getWeaponLevelDataMap().put(id, before);
        });
    }

    private GameItem stock() {
        return stock(11101);
    }

    private GameItem stock(int id) {
        var item = new GameItem(id, 1);
        item.setOwner(player);
        player.getInventory().getItems().put(item.getGuid(), item);
        player.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        return item;
    }

    private Thread upgrade(GameItem target, GameItem food, CompletableFuture<Void> result) {
        return operate(() -> system.upgradeWeapon(player, target.getGuid(), List.of(food.getGuid()), List.of()), result);
    }

    private Thread operate(Runnable operation, CompletableFuture<Void> result) {
        var thread = new Thread(() -> {
            try {
                operation.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, "weapon-upgrade-concurrency-test");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void awaitBlocked(Thread worker, CompletableFuture<?> result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!result.isDone() && worker.getState() != Thread.State.BLOCKED
                && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.BLOCKED, worker.getState(), "Upgrade must wait for inventory transaction");
    }

    @Test
    void concurrentUpgradesCannotSpendTheSameFoodWeaponTwice() throws Exception {
        var first = stock();
        var second = stock();
        var food = stock();
        var firstDone = new CompletableFuture<Void>();
        var secondDone = new CompletableFuture<Void>();
        Thread a = null;
        Thread b = null;
        try {
            synchronized (player.getInventory()) {
                a = upgrade(first, food, firstDone);
                awaitBlocked(a, firstDone);
                b = upgrade(second, food, secondDone);
                awaitBlocked(b, secondDone);
                // On the old code both requests have read the same feed GUID before payItems.
                // With transaction locking they wait before either ownership check instead.
            }
            firstDone.get(3, TimeUnit.SECONDS);
            secondDone.get(3, TimeUnit.SECONDS);
        } finally {
            if (a != null) a.join(3000);
            if (b != null) b.join(3000);
        }
        int feedExp = food.getItemData().getWeaponBaseExp();
        assertEquals(feedExp, first.getTotalExp() + second.getTotalExp(), "One feed weapon may fund one upgrade");
        assertEquals(10000 - feedExp / 10, player.getMora());
        assertEquals(1, List.of(first, second).stream().filter(item -> item.getTotalExp() > 0).count());
        assertNull(player.getInventory().getItemByGuid(food.getGuid()));
    }

    @Test
    void repeatedFeedGuidCannotCreditTheSameWeaponTwice() {
        var target = stock();
        var food = stock();
        system.upgradeWeapon(player, target.getGuid(), List.of(food.getGuid(), food.getGuid()), List.of());
        assertEquals(0, target.getTotalExp());
        assertEquals(10000, player.getMora());
        assertSame(food, player.getInventory().getItemByGuid(food.getGuid()));
        assertEquals(1, food.getCount());
    }

    @Test
    void targetWeaponCannotAlsoBeItsOwnFeed() {
        var target = stock();
        system.upgradeWeapon(player, target.getGuid(), List.of(target.getGuid()), List.of());
        assertEquals(0, target.getTotalExp());
        assertEquals(10000, player.getMora());
        assertSame(target, player.getInventory().getItemByGuid(target.getGuid()));
        assertEquals(1, target.getCount());
    }

    @Test
    void concurrentRefinementsCannotSpendTheSameFoodWeaponTwice() throws Exception {
        var first = stock(11301);
        var second = stock(11301);
        var food = stock(11301);
        assertFalse(first.getAffixes().isEmpty());
        var firstDone = new CompletableFuture<Void>();
        var secondDone = new CompletableFuture<Void>();
        Thread a = null;
        Thread b = null;
        try {
            synchronized (player.getInventory()) {
                a = operate(() -> system.refineWeapon(player, first.getGuid(), food.getGuid()), firstDone);
                awaitBlocked(a, firstDone);
                b = operate(() -> system.refineWeapon(player, second.getGuid(), food.getGuid()), secondDone);
                awaitBlocked(b, secondDone);
            }
            firstDone.get(3, TimeUnit.SECONDS);
            secondDone.get(3, TimeUnit.SECONDS);
        } finally {
            if (a != null) a.join(3000);
            if (b != null) b.join(3000);
        }
        assertEquals(1, first.getRefinement() + second.getRefinement());
        assertEquals(10000 - food.getItemData().getAwakenCosts()[0], player.getMora());
        assertNull(player.getInventory().getItemByGuid(food.getGuid()));
    }

    @Test
    void refinementCannotConsumeItsOwnTargetWeapon() {
        var target = stock(11301);
        system.refineWeapon(player, target.getGuid(), target.getGuid());
        assertEquals(0, target.getRefinement());
        assertEquals(10000, player.getMora());
        assertSame(target, player.getInventory().getItemByGuid(target.getGuid()));
        assertEquals(1, target.getCount());
    }
}

package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.BattlePassRewardTagOuterClass.BattlePassRewardTag;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattlePassInventoryLockTest {
    private Map<Integer, BattlePassScheduleData> schedules;
    private BattlePassRewardData level;
    private RewardData reward;
    private ItemData item;

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var gson = new Gson();
        schedules = new HashMap<>(GameData.getBattlePassScheduleDataMap());
        GameData.getBattlePassScheduleDataMap().clear();
        GameData.getBattlePassScheduleDataMap().put(7100, gson.fromJson(
                "{\"id\":7100,\"levelRewardIndexId\":1,\"extraPaidRewardId\":990001,\"extraPaidAddPoint\":0}",
                BattlePassScheduleData.class));
        level = GameData.getBattlePassRewardDataMap().put(101, gson.fromJson(
                "{\"id\":101,\"freeRewardIdList\":[990001],\"paidRewardIdList\":[]}", BattlePassRewardData.class));
        reward = GameData.getRewardDataMap().put(990001, gson.fromJson(
                "{\"id\":990001,\"rewardItemList\":[{\"itemId\":201,\"itemCount\":60}]}", RewardData.class));
        item = GameData.getItemDataMap().put(201, gson.fromJson(
                "{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
    }

    @AfterEach
    void restore() {
        GameData.getBattlePassScheduleDataMap().clear();
        GameData.getBattlePassScheduleDataMap().putAll(schedules);
        if (level == null) GameData.getBattlePassRewardDataMap().remove(101);
        else GameData.getBattlePassRewardDataMap().put(101, level);
        if (reward == null) GameData.getRewardDataMap().remove(990001);
        else GameData.getRewardDataMap().put(990001, reward);
        if (item == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, item);
    }

    static class LockedInventoryPlayer extends FreeStoreTest.TestPlayer {
        final CountDownLatch inventoryRequested = new CountDownLatch(1);
        volatile boolean observe;
        final Inventory lockedInventory = new Inventory(this) {
            @Override public synchronized void addItems(Collection<GameItem> items, ActionReason reason) {
                for (var item : items) granted.merge(item.getItemId(), item.getCount(), Integer::sum);
            }
        };
        @Override public Inventory getInventory() {
            if (observe) inventoryRequested.countDown();
            return lockedInventory == null ? super.getInventory() : lockedInventory;
        }
    }

    @Test
    void concurrentLevelPurchasesCannotCrossTheLevelCap() throws Exception {
        var firstRead = new CountDownLatch(1);
        var releaseRead = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var balance = new AtomicInteger(1000);
        var player = new FreeStoreTest.TestPlayer() {
            @Override public int getPrimogems() {
                if (reads.incrementAndGet() == 1) {
                    firstRead.countDown();
                    try { assertTrue(releaseRead.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                }
                return balance.get();
            }
            @Override public boolean setPrimogems(int amount) { balance.set(amount); return true; }
        };
        player.pass.setLevel(emu.grasscutter.GameConstants.BATTLE_PASS_MAX_LEVEL - 1);
        var purchased = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();
        Runnable buy = () -> {
            try { purchased.addAndGet(player.pass.buyLevels(1)); }
            catch (Throwable e) { failure.compareAndSet(null, e); }
        };
        var first = new Thread(buy, "first-pass-level-purchase");
        var second = new Thread(buy, "second-pass-level-purchase");
        first.setDaemon(true);
        second.setDaemon(true);
        try {
            first.start();
            assertTrue(firstRead.await(5, TimeUnit.SECONDS));
            second.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (second.isAlive() && second.getState() != Thread.State.BLOCKED
                    && System.nanoTime() < deadline) Thread.yield();
        } finally {
            releaseRead.countDown();
            first.join(5000);
            second.join(5000);
        }
        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertEquals(1, purchased.get());
        assertEquals(emu.grasscutter.GameConstants.BATTLE_PASS_MAX_LEVEL, player.pass.getLevel());
        assertEquals(1000 - emu.grasscutter.GameConstants.BATTLE_PASS_LEVEL_PRICE, balance.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rewardDeliveryWaitingForInventoryDoesNotBlockItsMissionCallback(boolean premium) throws Exception {
        var player = new LockedInventoryPlayer();
        player.pass.setLevel(1);
        var option = BattlePassRewardTakeOption.newBuilder().setTag(
                BattlePassRewardTag.newBuilder().setLevel(1).setRewardId(990001)
                        .setUnlockStatus(BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_FREE)).build();
        var pool = Executors.newFixedThreadPool(2, runnable -> {
            var thread = new Thread(runnable, "battle-pass-inventory-regression");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> delivery = null;
        Future<?> mission = null;
        boolean callbackFinished = false;
        try {
            synchronized (player.lockedInventory) {
                player.observe = true;
                delivery = pool.submit(() -> {
                    if (premium) assertTrue(player.pass.unlockPaid(true));
                    else assertEquals(List.of(option), player.pass.takeReward(List.of(option)));
                });
                assertTrue(player.inventoryRequested.await(5, TimeUnit.SECONDS));
                // Inventory removal invokes BattlePassSystem while owning the inventory monitor.
                // Keep the same ordering, but put a finite deadline on the pass-side operation.
                mission = pool.submit(() -> player.pass.refreshMissions());
                try {
                    mission.get(2, TimeUnit.SECONDS);
                    callbackFinished = true;
                } catch (TimeoutException expectedOnOldLockOrder) {
                    // Release inventory below so both old-code workers can still exit cleanly.
                }
            }
            delivery.get(5, TimeUnit.SECONDS);
            mission.get(5, TimeUnit.SECONDS);
            assertTrue(callbackFinished, "A reward must obtain inventory before the battle-pass monitor");
            assertEquals(60, player.granted.get(201));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

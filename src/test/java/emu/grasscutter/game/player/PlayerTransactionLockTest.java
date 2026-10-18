package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.JsonUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerTransactionLockTest {
    @BeforeAll
    static void initializeServerConfiguration() {
        emu.grasscutter.Grasscutter.getConfig();
    }

    private static final class ReachedManager extends RuntimeException {}

    private static class TickPlayer extends Player {
        Runnable callback = () -> {};
        final SotSManager recovery = new SotSManager(this) {
            @Override public void onTick(long now) {
                callback.run();
                throw new ReachedManager();
            }
        };
        @Override public SotSManager getSotsManager() { return recovery; }
        @Override public long getLastPingTime() { return System.currentTimeMillis(); }
        @Override public void sendPacket(BasePacket packet) {}
        @Override public void save() {}
    }

    @Test
    void playerTickDoesNotHoldPlayerAcrossManagerCallbacks() {
        var player = new TickPlayer();
        player.callback = () -> assertFalse(Thread.holdsLock(player),
                "Inventory and ability callbacks acquire Player in the opposite direction");
        assertThrows(ReachedManager.class, player::onTick);
    }

    @Test
    void inventoryOwnerCanReadCooldownWhileTickRunsItsManagerCallback() throws Exception {
        var player = new TickPlayer();
        var entered = new CountDownLatch(1);
        var read = new CountDownLatch(1);
        player.callback = () -> {
            entered.countDown();
            await(read, "Inventory owner cannot read Player while a manager callback runs");
        };
        var workers = Executors.newFixedThreadPool(2);
        try {
            var reader = workers.submit(() -> {
                synchronized (player.getInventory()) {
                    await(entered, "Tick did not reach its manager");
                    assertEquals(0, player.getItemUseCooldownEnd(1));
                    read.countDown();
                }
            });
            var tick = workers.submit(() -> assertThrows(ReachedManager.class, player::onTick));
            tick.get(5, TimeUnit.SECONDS);
            reader.get(5, TimeUnit.SECONDS);
        } finally {
            read.countDown();
            entered.countDown();
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentTicksForOnePlayerStillRunSerially() throws Exception {
        var player = new TickPlayer();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var visits = new AtomicInteger();
        player.callback = () -> {
            assertEquals(1, active.incrementAndGet(), "Tick callbacks overlapped");
            try {
                visits.incrementAndGet();
                entered.countDown();
                await(release, "Tick was not released");
            } finally { active.decrementAndGet(); }
        };
        var completed = new CompletableFuture<Void>();
        var second = new Thread(() -> {
            try {
                assertThrows(ReachedManager.class, player::onTick);
                completed.complete(null);
            } catch (Throwable failure) { completed.completeExceptionally(failure); }
        }, "second-player-tick");
        var worker = Executors.newSingleThreadExecutor();
        try {
            var first = worker.submit(() -> assertThrows(ReachedManager.class, player::onTick));
            await(entered, "First tick did not start");
            second.start();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (second.getState() != Thread.State.BLOCKED && !completed.isDone()
                    && System.nanoTime() < until) Thread.sleep(1);
            assertEquals(Thread.State.BLOCKED, second.getState());
            assertEquals(1, visits.get());
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            completed.get(5, TimeUnit.SECONDS);
            assertEquals(2, visits.get());
        } finally {
            release.countDown();
            second.join(5000);
            worker.shutdown();
            assertFalse(second.isAlive());
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void dailyResetDoesNotHoldPlayerWhenEnteringBattlePass() {
        var player = new TickPlayer() {
            final BattlePassManager pass = new BattlePassManager(this) {
                @Override public boolean refreshMissions(Instant now) {
                    assertFalse(Thread.holdsLock(getPlayer()),
                            "Daily refresh must not hold Player while waiting for inventory/pass");
                    throw new ReachedManager();
                }
            };
            @Override public BattlePassManager getBattlePassManager() { return pass; }
        };
        assertThrows(ReachedManager.class, () -> player.doDailyReset(Instant.now()));
    }

    @Test
    void expeditionTickUsesTheSameTransactionAsClaims() {
        var player = new TickPlayer() {
            final SotSManager idle = new SotSManager(this) {
                @Override public void onTick(long now) {}
            };
            @Override public SotSManager getSotsManager() { return idle; }
            @Override protected void doDailyReset(Instant now) {}
            @Override public Map<Long, ExpeditionInfo> getExpeditionInfo() {
                assertTrue(Thread.holdsLock(getInventory()),
                        "Refreshing trips must not race inventory-locked claim/start/remove");
                throw new ReachedManager();
            }
        };
        assertThrows(ReachedManager.class, player::onTick);
    }

    @ParameterizedTest
    @ValueSource(strings = {"purchase", "claim"})
    void moonCardRewardsUseInventoryWithoutHoldingPlayer(String operation) {
        var originals = new HashMap<Integer, ItemData>();
        for (int id : new int[] {201, 203})
            originals.put(id, GameData.getItemDataMap().put(id, JsonUtils.decode(
                    "{id:" + id + ",itemType:'ITEM_VIRTUAL'}", ItemData.class)));
        try {
            var rewards = new HashMap<Integer, Integer>();
            var today = LocalDate.of(2026, 10, 18);
            var player = new TickPlayer() {
                final Inventory inventory = new Inventory(this) {
                    private boolean grant(GameItem item) {
                        assertFalse(Thread.holdsLock(getPlayer()),
                                "A card reward must not invoke Inventory under Player");
                        assertTrue(Thread.holdsLock(this), "Reward and claim marker need one transaction");
                        rewards.merge(item.getItemId(), item.getCount(), Integer::sum);
                        return true;
                    }
                    @Override public boolean addItem(GameItem item) { return grant(item); }
                    @Override public boolean addItem(GameItem item, ActionReason reason) { return grant(item); }
                };
                @Override public Inventory getInventory() {
                    return inventory == null ? super.getInventory() : inventory;
                }
                @Override protected LocalDate moonCardToday() { return today; }
            };
            if (operation.equals("purchase")) assertTrue(player.rechargeMoonCard());
            else {
                player.setMoonCard(true);
                player.setMoonCardDuration(30);
                player.setMoonCardStartTime(Date.from(today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
                player.getTodayMoonCard();
            }
            player.getTodayMoonCard();
            assertEquals(90, rewards.get(201));
            assertEquals(operation.equals("purchase") ? 300 : 0, rewards.getOrDefault(203, 0));
            assertEquals(1, player.getMoonCardGetTimes().size());
        } finally {
            originals.forEach((id, old) -> {
                if (old == null) GameData.getItemDataMap().remove(id);
                else GameData.getItemDataMap().put(id, old);
            });
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try { assertTrue(latch.await(2, TimeUnit.SECONDS), message); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message, interrupted);
        }
    }
}

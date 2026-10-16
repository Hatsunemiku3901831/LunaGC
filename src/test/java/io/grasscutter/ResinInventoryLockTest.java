package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.battlepass.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.concurrent.*;

/** Exercise the same monitor used by inventory payment and a paid dungeon's capacity check. */
class ResinInventoryLockTest extends QuestTestFixture {
    ItemData oldCondensed;
    boolean resinUsage;

    @BeforeEach
    void resinPlayer() throws Exception {
        replaceField(
                GameServer.class,
                player.server,
                "battlePassSystem",
                new BattlePassSystem(player.server));
        replaceField(Player.class, player, "battlePassManager", new BattlePassManager(player));
        resinUsage = Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        var data =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(row -> row.getId() == 220007)
                        .findFirst()
                        .orElseThrow();
        data.onLoad();
        oldCondensed = GameData.getItemDataMap().put(data.getId(), data);
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 200);
        assertTrue(player.getInventory().addItem(new GameItem(data, 1)));
    }

    @AfterEach
    void restoreResin() {
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = resinUsage;
        if (oldCondensed == null) GameData.getItemDataMap().remove(220007);
        else GameData.getItemDataMap().put(220007, oldCondensed);
    }

    void awaitBlockedOrDone(Thread thread, CompletableFuture<?> result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!result.isDone()
                && thread.getState() != Thread.State.BLOCKED
                && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(
                result.isDone() || thread.getState() == Thread.State.BLOCKED,
                "Resin operation did not reach a monitor or finish");
    }

    Thread startDaemon(Runnable action) {
        var thread = new Thread(action, "resin-inventory-lock-test");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @ParameterizedTest
    @ValueSource(strings = {"spend", "add", "recharge", "login"})
    void resinMutationWaitsForAnInventoryPayment(String operation) throws Exception {
        if (operation.equals("recharge") || operation.equals("login"))
            player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 100);
        if (operation.equals("recharge"))
            player.setNextResinRefresh(
                    Utils.getCurrentSeconds()
                            - Configuration.GAME_OPTIONS.resinOptions.rechargeTime);
        var completed = new CompletableFuture<Void>();
        int before = player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        int refreshBefore = player.getNextResinRefresh();
        Thread worker = null;
        try {
            synchronized (player.getInventory()) {
                worker =
                        startDaemon(
                                () -> {
                                    try {
                                        var resin = player.getResinManager();
                                        switch (operation) {
                                            case "spend" -> assertTrue(resin.useResin(20));
                                            case "add" -> resin.addResin(5);
                                            case "recharge" -> resin.rechargeResin();
                                            case "login" -> resin.onPlayerLogin();
                                            default -> throw new AssertionError(operation);
                                        }
                                        completed.complete(null);
                                    } catch (Throwable failure) {
                                        completed.completeExceptionally(failure);
                                    }
                                });
                awaitBlockedOrDone(worker, completed);
                assertEquals(
                        before,
                        player.getProperty(PlayerProperty.PROP_PLAYER_RESIN),
                        "Resin changed before the inventory payment released its monitor");
                assertEquals(refreshBefore, player.getNextResinRefresh());
                assertFalse(completed.isDone(), "A resin mutation bypassed the inventory payment");
            }
        } finally {
            assertDoesNotThrow(() -> completed.get(3, TimeUnit.SECONDS));
            if (worker != null) worker.join(3000);
        }
    }

    @Test
    void condensedPaymentCannotInvertTheInventoryAndResinLockOrder() throws Exception {
        var holdsInventory = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        var ordinary = new CompletableFuture<Boolean>();
        var condensed = new CompletableFuture<Boolean>();
        // Daemon threads let the deliberately deadlocked old implementation terminate with the test
        // JVM.
        var holder =
                startDaemon(
                        () -> {
                            try {
                                synchronized (player.getInventory()) {
                                    holdsInventory.countDown();
                                    if (!proceed.await(3, TimeUnit.SECONDS))
                                        throw new IllegalStateException("No contender");
                                    ordinary.complete(player.getResinManager().useResin(20));
                                }
                            } catch (Throwable failure) {
                                ordinary.completeExceptionally(failure);
                            }
                        });
        assertTrue(holdsInventory.await(3, TimeUnit.SECONDS));
        var contender =
                startDaemon(
                        () -> {
                            try {
                                condensed.complete(player.getResinManager().useCondensedResin(1));
                            } catch (Throwable failure) {
                                condensed.completeExceptionally(failure);
                            }
                        });
        try {
            awaitBlockedOrDone(contender, condensed);
            proceed.countDown();
            assertTrue(
                    assertDoesNotThrow(
                            () -> ordinary.get(3, TimeUnit.SECONDS),
                            "Ordinary and condensed resin payments deadlocked"));
            assertTrue(assertDoesNotThrow(() -> condensed.get(3, TimeUnit.SECONDS)));
            assertEquals(180, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
            assertEquals(0, player.getInventory().getItemCountById(220007));
        } finally {
            proceed.countDown();
            holder.interrupt();
            contender.interrupt();
        }
    }
}

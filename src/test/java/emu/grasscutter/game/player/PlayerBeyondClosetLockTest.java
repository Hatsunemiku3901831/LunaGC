package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.beyond.BeyondCloset;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerBeyondClosetLockTest {
    @BeforeAll
    static void initialize() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @Test
    void ownershipReadsDoNotWaitForAPlayerSaveAfterClosetInitialization() throws Exception {
        var player = new Player();
        var closet = player.getBeyondCloset();
        closet.grant(List.of(9));
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var jobs = Executors.newFixedThreadPool(2);
        var save =
                jobs.submit(
                        () -> {
                            synchronized (player) {
                                acquired.countDown();
                                try {
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException error) {
                                    throw new AssertionError(error);
                                }
                            }
                        });
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            var read =
                    jobs.submit(
                            () -> {
                                synchronized (player.getInventory()) {
                                    assertSame(closet, player.getBeyondCloset());
                                    return player.getBeyondCloset().canGrant(List.of(9));
                                }
                            });
            assertFalse(read.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            save.get(5, TimeUnit.SECONDS);
            jobs.shutdown();
            assertTrue(jobs.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void simultaneousFirstAccessKeepsOneClosetAndAllOwnership() throws Exception {
        var player = new Player();
        var start = new CountDownLatch(1);
        var jobs = Executors.newFixedThreadPool(8);
        var reads = new ArrayList<Future<BeyondCloset>>();
        try {
            for (int i = 1; i <= 8; i++) {
                int costume = i;
                reads.add(
                        jobs.submit(
                                () -> {
                                    start.await();
                                    var closet = player.getBeyondCloset();
                                    closet.grant(List.of(costume));
                                    return closet;
                                }));
            }
            start.countDown();
            var first = reads.get(0).get(5, TimeUnit.SECONDS);
            for (var result : reads) assertSame(first, result.get(5, TimeUnit.SECONDS));
            assertEquals(8, first.toProto().size());
        } finally {
            start.countDown();
            jobs.shutdownNow();
            assertTrue(jobs.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

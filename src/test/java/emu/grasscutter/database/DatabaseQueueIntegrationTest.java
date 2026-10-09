package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.inventory.GameItem;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DatabaseQueueIntegrationTest {
    @Test
    void savesHaveABoundedBacklog() {
        Grasscutter.getConfig();
        var executor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        assertTrue(executor.getQueue().remainingCapacity() <= 16384,
                "Database outages must not accumulate an unbounded queue");
    }

    @Test
    void itemTrafficCannotStarveAnAccountSave() throws Exception {
        Grasscutter.getConfig();
        Field field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        Field dispatch = DatabaseManager.class.getDeclaredField("dispatchDatastore");
        dispatch.setAccessible(true);
        var original = field.get(null);
        var originalDispatch = dispatch.get(null);
        assertTrue(DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var accountSaved = new CountDownLatch(1);
        var executor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        var datastore = Proxy.newProxyInstance(Datastore.class.getClassLoader(),
                new Class<?>[] {Datastore.class}, (proxy, method, args) -> {
                    if (args[0] instanceof GameItem) {
                        entered.countDown();
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } else if (args[0] instanceof Account) accountSaved.countDown();
                    return args[0];
                });
        field.set(null, datastore);
        dispatch.set(null, datastore);
        try {
            for (int i = 0; i < executor.getCorePoolSize() + 8; i++) {
                var item = new GameItem();
                item.setCount(1);
                DatabaseHelper.saveGameAsync(item);
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            DatabaseHelper.saveAccountAsync(new Account());
            assertTrue(accountSaved.await(1, TimeUnit.SECONDS),
                    "Login account writes must be isolated from a blocked item backlog");
        } finally {
            release.countDown();
            try { assertTrue(DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5))); }
            finally { field.set(null, original); dispatch.set(null, originalDispatch); }
        }
    }
}

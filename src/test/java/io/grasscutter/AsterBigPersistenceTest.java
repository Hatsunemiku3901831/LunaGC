package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.aster.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real isolated Mongo writes, including accepted writes whose acknowledgements were lost. */
class AsterBigPersistenceTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void coldReloadRetainsFrozenDropsAndNeverRepeatsEffectsAfterAnAmbiguousWrite(int failure)
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_aster_big_" + UUID.randomUUID().toString().replace("-", "");
        try (var resources = new AsterBigTestFixture();
                var client = MongoClients.create(uri)) {
            var field = DatabaseManager.class.getDeclaredField("gameDatastore");
            field.setAccessible(true);
            var original = field.get(null);
            try {
                var store =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder()
                                        .storeEmpties(true)
                                        .storeNulls(false)
                                        .build());
                store.getMapper().map(PlayerActivityData.class);
                String token = resources.offer();
                store.save(resources.host);
                store.save(resources.guest);
                var hostWrites = new AtomicInteger();
                var guestWrites = new AtomicInteger();
                field.set(
                        null,
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    final Object result;
                                    try {
                                        result = method.invoke(store, args);
                                    } catch (InvocationTargetException error) {
                                        throw error.getCause();
                                    }
                                    if (method.getName().equals("save")
                                            && args[0] instanceof PlayerActivityData data) {
                                        int n =
                                                (data.getUid() == 10001 ? hostWrites : guestWrites)
                                                        .incrementAndGet();
                                        if (failure == 1 && data.getUid() == 10002 && n == 2
                                                || failure == 2 && data.getUid() == 10001 && n == 2
                                                || failure == 3 && data.getUid() == 10001 && n == 1
                                                || failure == 4 && data.getUid() == 10002 && n == 1)
                                            throw new IllegalStateException(
                                                    "Mongo accepted the write; acknowledgement"
                                                        + " lost");
                                    }
                                    return result;
                                }));
                var debits = new AtomicInteger();
                var grants = new AtomicInteger();
                var call =
                        (java.util.concurrent.Callable<Integer>)
                                () ->
                                        AsterBigRewards.takeShared(
                                                resources.host,
                                                resources.guest,
                                                resources.config,
                                                20,
                                                resources.now,
                                                token,
                                                () -> {
                                                    debits.incrementAndGet();
                                                    return true;
                                                },
                                                r -> grants.incrementAndGet(),
                                                resources.host::saveSync,
                                                resources.guest::saveSync);
                if (failure == 0) assertEquals(0, call.call());
                else assertThrows(IllegalStateException.class, call::call);
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                assertEquals(failure < 3 ? 1 : 0, debits.get());
                assertEquals(failure < 3 ? 1 : 0, grants.get());
                var host =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", 10001))
                                .first();
                var guest =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", 10002))
                                .first();
                assertNotNull(host);
                assertNotNull(guest);
                var point = AsterSchedule.progress(host).bigReward();
                var receipt = AsterSchedule.progress(guest);
                assertEquals(8, point.rewards().get(10002).worldLevel());
                assertEquals(210020080, point.rewards().get(10002).dropId());
                assertEquals(50, point.rewards().get(10002).essence());
                assertEquals(Set.of(10001, 10002), point.rewards().keySet());
                assertEquals(failure < 3 ? 50 : 0, receipt.bigCredit());
                assertEquals(failure < 3 ? 1 : 0, receipt.bigCollectCount());
                assertEquals(failure < 3, receipt.hasBigClaim(token));
                assertEquals(0, receipt.credit());
                assertEquals(0, receipt.midCollectCount());
                assertEquals(
                        failure == 1 || failure >= 3 ? Set.of(10001, 10002) : Set.of(10001),
                        point.remaining());
                assertEquals(
                        failure == 1 || failure >= 3 ? Set.of(10002) : Set.of(), point.reserved());
                assertNotEquals(
                        0,
                        AsterBigRewards.takeShared(
                                host,
                                guest,
                                resources.config,
                                20,
                                resources.now + 1000,
                                token,
                                () -> fail("repeated debit"),
                                r -> fail("repeated grant"),
                                () -> fail("repeated save"),
                                () -> fail("repeated save")));
                assertEquals(2, store.find(PlayerActivityData.class).count());
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                field.set(null, original);
                client.getDatabase(database).drop();
            }
        }
    }
}

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

/**
 * Real acknowledged activity documents, including a write accepted before its acknowledgement
 * fails.
 */
class AsterMidCoopPersistenceTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void sharedPointsAndPersonalReceiptsSurviveMongoReloadAndUncertainAcknowledgements(int failure)
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_aster_mid_coop_" + UUID.randomUUID().toString().replace("-", "");
        try (var fixture = new AsterMidTestFixture();
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
                var watchers = new HashMap<Integer, PlayerActivityData.WatcherInfo>();
                fixture.data
                        .getWatcherInfoMap()
                        .forEach(
                                (id, w) ->
                                        watchers.put(
                                                id,
                                                PlayerActivityData.WatcherInfo.of()
                                                        .watcherId(id)
                                                        .totalProgress(w.getTotalProgress())
                                                        .build()));
                var guest =
                        PlayerActivityData.of()
                                .uid(10002)
                                .activityId(2001)
                                .scheduleId(fixture.config.getScheduleId())
                                .watcherInfoMap(watchers)
                                .detail("{}")
                                .build();
                var progress = AsterSchedule.progress(fixture.data);
                int camp =
                        emu.grasscutter.data.GameData.getAsterMidDataMap()
                                .get(1)
                                .getCampVec()
                                .get(0);
                assertTrue(progress.offerMidReward(camp, 1, Set.of(10001, 10002)));
                String token = progress.midReward(camp).token();
                fixture.data.setDetail(progress);
                store.save(fixture.data);
                store.save(guest);
                var hostSaves = new AtomicInteger();
                var guestSaves = new AtomicInteger();
                field.set(
                        null,
                        Proxy.newProxyInstance(
                                Datastore.class.getClassLoader(),
                                new Class<?>[] {Datastore.class},
                                (proxy, method, args) -> {
                                    Object result;
                                    try {
                                        result = method.invoke(store, args);
                                    } catch (InvocationTargetException error) {
                                        throw error.getCause();
                                    }
                                    if (method.getName().equals("save")
                                            && args[0] instanceof PlayerActivityData data) {
                                        int n =
                                                (data.getUid() == 10001 ? hostSaves : guestSaves)
                                                        .incrementAndGet();
                                        if (n == 2
                                                && (failure == 1 && data.getUid() == 10002
                                                        || failure == 2 && data.getUid() == 10001))
                                            throw new IllegalStateException(
                                                    "Mongo accepted the write; acknowledgement"
                                                        + " lost");
                                    }
                                    return result;
                                }));
                var grants = new AtomicInteger();
                var debits = new AtomicInteger();
                var call =
                        (java.util.concurrent.Callable<Integer>)
                                () ->
                                        AsterMidRewards.takeShared(
                                                fixture.data,
                                                guest,
                                                fixture.config,
                                                20,
                                                fixture.now,
                                                camp,
                                                token,
                                                () -> {
                                                    debits.incrementAndGet();
                                                    return true;
                                                },
                                                r -> grants.incrementAndGet(),
                                                fixture.data::saveSync,
                                                guest::saveSync);
                if (failure == 0) assertEquals(0, call.call());
                else assertThrows(IllegalStateException.class, call::call);
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                assertEquals(1, grants.get());
                assertEquals(1, debits.get());
                var hostReload =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", 10001))
                                .first();
                var guestReload =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", 10002))
                                .first();
                assertNotNull(hostReload);
                assertNotNull(guestReload);
                var persistedGuest = AsterSchedule.progress(guestReload);
                var persistedPoint = AsterSchedule.progress(hostReload).midReward(camp);
                assertEquals(1, persistedGuest.midCollectCount());
                assertEquals(15, persistedGuest.credit());
                assertTrue(persistedGuest.hasMidClaim(token));
                assertEquals(1, guestReload.getWatcherInfoMap().get(1200110).getCurProgress());
                assertEquals(Set.of(10001, 10002), persistedPoint.qualified());
                assertEquals(
                        failure == 1 ? Set.of(10001, 10002) : Set.of(10001),
                        persistedPoint.remaining());
                assertEquals(failure == 1 ? Set.of(10002) : Set.of(), persistedPoint.reserved());
                assertNotEquals(
                        0,
                        AsterMidRewards.takeShared(
                                hostReload,
                                guestReload,
                                fixture.config,
                                20,
                                fixture.now + 1000,
                                camp,
                                token,
                                () -> fail("replayed debit"),
                                r -> fail("replayed grant"),
                                () -> fail(),
                                () -> fail()));
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                field.set(null, original);
                client.getDatabase(database).drop();
            }
        }
    }
}

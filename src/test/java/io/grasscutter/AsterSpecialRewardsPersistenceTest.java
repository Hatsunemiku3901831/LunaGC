package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.*;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.command.commands.AsterCommand;
import emu.grasscutter.database.*;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ActivityInfoNotifyOuterClass.ActivityInfoNotify;
import emu.grasscutter.net.proto.AddNoGachaAvatarCardNotifyOuterClass.AddNoGachaAvatarCardNotify;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Actual command, avatar/equipment conversion and acknowledged writes in a disposable Mongo DB. */
class AsterSpecialRewardsPersistenceTest {
    @ParameterizedTest
    @CsvSource({"-1,0", "0,0", "5,0", "6,0", "-1,1", "-1,2", "-1,3", "-1,4", "-1,5", "0,6", "6,6"})
    void actualGrantAndAmbiguousWritesDoNotReplayAfterColdReload(int constellation, int failure)
            throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_aster_special_" + UUID.randomUUID().toString().replace("-", "");
        try (var fixture = new AsterSpecialTestFixture();
                var client = MongoClients.create(uri)) {
            var field = DatabaseManager.class.getDeclaredField("gameDatastore");
            field.setAccessible(true);
            Object previous = field.get(null);
            try {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                var store =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder()
                                        .storeEmpties(true)
                                        .storeNulls(false)
                                        .build());
                store.getMapper()
                        .map(
                                PlayerActivityData.class,
                                AsterShopTestFixture.ShopPlayer.class,
                                Avatar.class,
                                GameItem.class);
                field.set(null, store);
                var player = fixture.base.player;
                var data = fixture.base.event.data;
                if (constellation >= 0) {
                    player.addAvatar(new Avatar(10000031), false);
                    if (constellation > 0)
                        assertTrue(player.getInventory().addItem(1131, constellation));
                    assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                }
                store.save(player);
                store.save(data);
                player.packets.clear();
                var activitySaves = new AtomicInteger();
                var fault = new AtomicBoolean();
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
                                            && args.length == 2
                                            && args[1] instanceof InsertOneOptions options) {
                                        assertEquals(
                                                "majority", options.writeConcern().getWString());
                                        assertEquals(
                                                Boolean.TRUE, options.writeConcern().getJournal());
                                        boolean lose = false;
                                        if (args[0] instanceof GameItem item)
                                            lose =
                                                    failure == 2 && item.getItemId() == 15101
                                                            || failure == 6
                                                                    && (item.getItemId() == 1131
                                                                            || item.getItemId()
                                                                                    == 221);
                                        if (args[0] instanceof Avatar) lose = failure == 3;
                                        if (lose) {
                                            fault.set(true);
                                            throw new IllegalStateException(
                                                    "Mongo accepted write; acknowledgement lost");
                                        }
                                    }
                                    return result;
                                }));
                GuardedActivityTestStore.bind(data, store, DatabaseManager.getGameDatastore(), true,
                        ignored -> {}, document -> {
                            int n = activitySaves.incrementAndGet();
                            if (failure == 1 && n == 1 || failure == 5 && n == 2) {
                                fault.set(true);
                                throw new IllegalStateException("Activity write accepted, acknowledgement lost");
                            }
                        });
                emu.grasscutter.game.player.GuardedPlayerTestStore.loadSeededProfile(player, store,
                        () -> {}, document -> {
                            if (failure == 4) {
                                fault.set(true);
                                throw new IllegalStateException("Player write accepted, acknowledgement lost");
                            }
                        });
                assertEquals(0, AsterSpecialRewardDelivery.validate(player, 3000000));
                if (failure == 0) new AsterCommand().execute(null, player, List.of("take"));
                else
                    assertThrows(IllegalStateException.class, () -> AsterSpecialRewards.take(data));
                assertEquals(failure != 0, fault.get());
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                var reloaded =
                        store.find(PlayerActivityData.class)
                                .filter(Filters.eq("uid", player.getUid()))
                                .first();
                assertNotNull(reloaded);
                var receipt = AsterSchedule.progress(reloaded);
                assertEquals(failure == 0 || failure == 5, receipt.specialRewardTaken());
                assertEquals(failure != 0 && failure != 5, receipt.pendingSpecialReward());
                assertEquals(14, receipt.midCollectCount());
                assertEquals(7, receipt.bigCollectCount());
                var avatars =
                        store.find(Avatar.class)
                                .filter(Filters.eq("ownerId", player.getUid()))
                                .iterator()
                                .toList();
                var weapons =
                        store.find(GameItem.class)
                                .filter(
                                        Filters.eq("ownerId", player.getUid()),
                                        Filters.eq("itemId", 15101))
                                .iterator()
                                .toList();
                assertEquals(failure == 1 ? 0 : 1, avatars.size());
                assertEquals(failure == 1 ? 0 : 1, weapons.size());
                if (!avatars.isEmpty()) {
                    assertEquals(10000031, avatars.get(0).getAvatarId());
                    assertEquals(1, avatars.get(0).getLevel());
                    assertEquals(10000031, weapons.get(0).getEquipCharacter());
                    assertEquals(1, weapons.get(0).getCount());
                }
                int talent = constellation < 0 ? 0 : constellation + (constellation < 6 ? 1 : 0);
                int glitter = constellation < 0 ? 0 : constellation < 6 ? 2 : 5;
                if (failure == 6 && constellation < 6) glitter = 0;
                assertEquals(talent, count(store, player.getUid(), 1131));
                assertEquals(glitter, count(store, player.getUid(), 221));
                if (failure == 0) {
                    var cards =
                            player.packets.stream()
                                    .filter(
                                            p ->
                                                    p.getOpcode()
                                                            == PacketOpcodes
                                                                    .AddNoGachaAvatarCardNotify)
                                    .toList();
                    assertEquals(1, cards.size());
                    var card = AddNoGachaAvatarCardNotify.parseFrom(cards.get(0).getData());
                    assertEquals(1031, card.getItemId());
                    assertEquals(10000031, card.getAvatarId());
                    assertEquals(1062, card.getReason());
                    assertEquals(constellation >= 0, card.getIsTransferToItem());
                    var transfers = new HashMap<Integer, Integer>();
                    card.getTransferItemListList()
                            .forEach(item -> transfers.put(item.getItemId(), item.getCount()));
                    assertEquals(
                            constellation < 0
                                    ? Map.of()
                                    : constellation < 6 ? Map.of(1131, 1, 221, 2) : Map.of(221, 5),
                            transfers);
                    var info =
                            player.packets.stream()
                                    .filter(p -> p.getOpcode() == PacketOpcodes.ActivityInfoNotify)
                                    .findFirst()
                                    .orElseThrow();
                    assertTrue(
                            ActivityInfoNotify.parseFrom(info.getData())
                                    .getActivityInfo()
                                    .getAsterInfo()
                                    .getIsSpecialRewardTaken());
                }
                if (failure != 1)
                    assertEquals(
                            failure == 0
                                    ? Retcode.RET_ASTER_SPECIAL_REWARD_HAS_TAKEN_VALUE
                                    : Retcode.RET_SVR_ERROR_VALUE,
                            AsterSpecialRewards.take(data));
                // A fresh activity object exercises persistent receipts without relying on memory.
                reloaded.setPlayer(player);
                player.activity.getPlayerActivityDataMap().put(2001, reloaded);
                assertEquals(
                        failure == 0 || failure == 5
                                ? Retcode.RET_ASTER_SPECIAL_REWARD_HAS_TAKEN_VALUE
                                : Retcode.RET_SVR_ERROR_VALUE,
                        AsterSpecialRewards.take(reloaded));
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                assertEquals(failure == 1 ? 0 : 1, store.find(Avatar.class).count());
                assertEquals(talent, count(store, player.getUid(), 1131));
                assertEquals(glitter, count(store, player.getUid(), 221));
            } finally {
                assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
                field.set(null, previous);
                client.getDatabase(database).drop();
            }
        }
    }

    private static int count(Datastore store, int uid, int id) {
        var items =
                store.find(GameItem.class)
                        .filter(Filters.eq("ownerId", uid), Filters.eq("itemId", id))
                        .iterator()
                        .toList();
        assertTrue(items.size() <= 1);
        return items.isEmpty() ? 0 : items.get(0).getCount();
    }
}

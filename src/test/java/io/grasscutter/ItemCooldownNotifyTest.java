package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.packet.send.PacketItemCdGroupTimeNotify;

import org.junit.jupiter.api.*;

import java.util.*;

/** Native map field 9 contains uint32 group keys and uint64 absolute end times. */
class ItemCooldownNotifyTest {
    ItemUseCooldownTest env;

    @BeforeAll
    static void resources() throws Exception {
        ItemUseCooldownTest.resources();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new ItemUseCooldownTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    Player player() {
        return env.env.env.fixture.player;
    }

    List<BasePacket> notifications() {
        return env.env.env.fixture.player.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.ItemCdGroupTimeNotify)
                .toList();
    }

    static Map<Integer, Long> nativeMap(BasePacket packet) throws Exception {
        assertEquals(25967, packet.getOpcode());
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertTrue(wire.asMap().keySet().stream().allMatch(k -> k == 9));
        var result = new TreeMap<Integer, Long>();
        for (var bytes : wire.getField(9).getLengthDelimitedList()) {
            var entry = UnknownFieldSet.parseFrom(bytes);
            assertEquals(Set.of(1, 2), entry.asMap().keySet());
            result.put(
                    entry.getField(1).getVarintList().get(0).intValue(),
                    entry.getField(2).getVarintList().get(0));
        }
        return result;
    }

    @Test
    void successfulUsePublishesItsExactCooldown() throws Exception {
        var food = env.food(1, 99, "120", env.success());
        assertTrue(env.inventory.useItemDirect(food, env.params(food)));
        assertEquals(1, notifications().size());
        assertEquals(Map.of(99, env.now.get() + 120000), nativeMap(notifications().get(0)));
    }

    @Test
    void retryDuringCooldownDoesNotPublishANewExpiry() throws Exception {
        var food = env.food(1, 99, "120", env.success());
        assertTrue(env.inventory.useItemDirect(food, env.params(food)));
        env.now.addAndGet(1000);
        assertFalse(env.inventory.useItemDirect(food, env.params(food)));
        assertEquals(1, notifications().size());
        assertEquals(Map.of(99, env.now.get() + 119000), nativeMap(notifications().get(0)));
    }

    @Test
    void fractionalCooldownRetainsMilliseconds() throws Exception {
        var food = env.food(1, 5, "0.125", env.success());
        assertTrue(env.inventory.useItemDirect(food, env.params(food)));
        assertEquals(1, notifications().size());
        assertEquals(Map.of(5, env.now.get() + 125), nativeMap(notifications().get(0)));
    }

    @Test
    void successfulSecondGroupIncludesTheOtherActiveGroup() throws Exception {
        var first = env.food(1, 99, "120", env.success());
        var second = env.food(2, 98, "300", env.success());
        assertTrue(env.inventory.useItemDirect(first, env.params(first)));
        assertTrue(env.inventory.useItemDirect(second, env.params(second)));
        assertEquals(2, notifications().size());
        assertEquals(
                Map.of(99, env.now.get() + 120000, 98, env.now.get() + 300000),
                nativeMap(notifications().get(1)));
    }

    @Test
    void expiredGroupIsOmittedFromTheNextSnapshot() throws Exception {
        var first = env.food(1, 99, "120", env.success());
        var second = env.food(2, 98, "300", env.success());
        assertTrue(env.inventory.useItemDirect(first, env.params(first)));
        env.now.addAndGet(120000);
        assertTrue(env.inventory.useItemDirect(second, env.params(second)));
        assertEquals(2, notifications().size());
        assertEquals(Map.of(98, env.now.get() + 300000), nativeMap(notifications().get(1)));
    }

    @Test
    void aZeroDurationDoesNotEmitACooldown() throws Exception {
        var food = env.food(1, 99, "0", env.success());
        assertTrue(env.inventory.useItemDirect(food, env.params(food)));
        assertTrue(notifications().isEmpty());
    }

    @Test
    void rejectedEffectDoesNotEmitACooldown() throws Exception {
        var action =
                new emu.grasscutter.game.props.ItemUseAction.ItemUseAction() {
                    @Override
                    public boolean useItem(
                            emu.grasscutter.game.props.ItemUseAction.UseItemParams p) {
                        return false;
                    }
                };
        var food = env.food(1, 99, "120", action);
        assertFalse(env.inventory.useItemDirect(food, env.params(food)));
        assertTrue(notifications().isEmpty());
    }

    @Test
    void actualUseItemRequestPublishesTheRevivalCooldown() throws Exception {
        env.realRequestReturnsCooldownAndPreservesTheSecondFood();
        assertEquals(1, notifications().size());
        assertEquals(Map.of(99, env.now.get() + 120000), nativeMap(notifications().get(0)));
    }

    @Test
    void snapshotIsImmutableAndDoesNotTrackLaterChanges() {
        player().rememberItemUseCooldown(99, env.now.get() + 120000);
        var snapshot = player().itemUseCooldownSnapshot(env.now.get());
        player().rememberItemUseCooldown(98, env.now.get() + 300000);
        assertEquals(Map.of(99, env.now.get() + 120000), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(1, 2L));
    }

    @Test
    void snapshotIgnoresInvalidEntriesWithoutChangingTheStoredMap() throws Exception {
        var original = new HashMap<Integer, Long>();
        original.put(99, env.now.get() + 120000);
        original.put(98, env.now.get());
        original.put(1, -10L);
        original.put(0, env.now.get() + 120000);
        original.put(-1, env.now.get() + 120000);
        original.put(2, null);
        original.put(null, env.now.get() + 120000);
        var field = Player.class.getDeclaredField("itemUseCooldowns");
        field.setAccessible(true);
        field.set(player(), original);
        assertEquals(
                Map.of(99, env.now.get() + 120000),
                nativeMap(new PacketItemCdGroupTimeNotify(player(), env.now.get())));
        assertSame(original, field.get(player()));
        assertEquals(7, original.size());
        assertEquals(0, player().getItemUseCooldownEnd(2));
        assertEquals(0, player().getItemUseCooldownEnd(-1));
    }

    @Test
    void legacyNullMapProducesAnEmptyNotification() throws Exception {
        var field = Player.class.getDeclaredField("itemUseCooldowns");
        field.setAccessible(true);
        field.set(player(), null);
        assertTrue(nativeMap(new PacketItemCdGroupTimeNotify(player(), env.now.get())).isEmpty());
        assertNull(field.get(player()));
    }

    @Test
    void readingAndSerializingASnapshotNeverSavesThePlayer() throws Exception {
        var saves = new java.util.concurrent.atomic.AtomicInteger();
        var actual =
                new AbilityTestFixture.TestPlayer() {
                    @Override
                    public void save() {
                        saves.incrementAndGet();
                    }
                };
        actual.rememberItemUseCooldown(99, env.now.get() + 120000);
        int before = saves.get();
        assertEquals(
                Map.of(99, env.now.get() + 120000),
                nativeMap(new PacketItemCdGroupTimeNotify(actual, env.now.get())));
        actual.itemUseCooldownSnapshot(env.now.get() + 120000);
        assertEquals(before, saves.get());
        assertEquals(env.now.get() + 120000, actual.getItemUseCooldownEnd(99));
    }

    @Test
    void nativeUint64ExpiryIsNotTruncatedTo32Bits() throws Exception {
        long expiry = 0x0000_ffff_ffff_0000L;
        player().rememberItemUseCooldown(99, expiry);
        assertEquals(
                Map.of(99, expiry),
                nativeMap(new PacketItemCdGroupTimeNotify(player(), env.now.get())));
    }

    @Test
    void notificationFailureDoesNotRejectAnAppliedUse() throws Exception {
        var actual =
                new AbilityTestFixture.TestPlayer() {
                    @Override
                    public void sendPacket(BasePacket packet) {
                        if (packet.getOpcode() == PacketOpcodes.ItemCdGroupTimeNotify)
                            throw new IllegalStateException("injected cooldown transport failure");
                        super.sendPacket(packet);
                    }
                };
        var food = env.food(1, 99, "120", env.success());
        var params =
                new emu.grasscutter.game.props.ItemUseAction.UseItemParams(
                        actual, food.getUseTarget());
        assertTrue(env.inventory.useItemDirect(food, params));
        assertEquals(env.now.get() + 120000, actual.getItemUseCooldownEnd(99));
        assertFalse(env.inventory.useItemDirect(food, params));
    }
}

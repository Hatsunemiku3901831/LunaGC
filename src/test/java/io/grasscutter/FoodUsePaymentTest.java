package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.systems.InventorySystem;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

class FoodUsePaymentTest {
    FoodUseFailureTest env;

    @BeforeAll
    static void resources() throws Exception {
        FoodUseFailureTest.resources();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new FoodUseFailureTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    Player player() {
        return env.env.env.fixture.player;
    }

    InventorySystem engine(Consumer<Object> save) throws Exception {
        var server = env.env.env.fixture.scene.getWorld().getServer();
        return new InventorySystem(server, () -> 100000, save);
    }

    GameItem food(ItemUseAction... actions) throws Exception {
        var data = env.food(actions);
        env.env.env.environment.put(
                emu.grasscutter.data.GameData.getItemDataMap(), data.getId(), data);
        return env.env.stock(data.getId());
    }

    GameItem use(InventorySystem engine, GameItem item) {
        return engine.useItem(player(), 50001, item.getGuid(), 1, 0, false);
    }

    ItemUseAction heal() {
        return new ItemUseAction() {
            @Override
            public boolean useItem(UseItemParams params) {
                env.env.first.heal(500);
                return true;
            }
        };
    }

    ItemUseAction throwing() {
        return new ItemUseAction() {
            @Override
            public boolean useItem(UseItemParams params) {
                throw new IllegalStateException("Injected unknown food outcome");
            }
        };
    }

    @Test
    void rejectedCostCannotHealOrLeaveAPeriodicBuff() throws Exception {
        var field = Player.class.getDeclaredField("inventory");
        field.setAccessible(true);
        field.set(
                player(),
                new Inventory(player()) {
                    @Override
                    public synchronized boolean removeItem(GameItem item, int count) {
                        return false;
                    }

                    public synchronized boolean reserveForUse(GameItem item, int count) {
                        return false;
                    }
                });
        var item = env.env.stock(108511);
        assertNull(use(engine(ignored -> {}), item));
        assertEquals(2, item.getCount());
        env.unchanged();
        env.env.tick(5);
        assertEquals(1000, env.env.hp(env.env.first));
        assertNull(player().getPendingItemUse());
    }

    @Test
    void unacknowledgedReservationCannotApplyAnyFoodEffect() throws Exception {
        var item = food(heal());
        var engine =
                engine(
                        ignored -> {
                            throw new IllegalStateException("Reservation unavailable");
                        });
        assertNull(use(engine, item));
        env.unchanged();
        assertEquals(2, item.getCount());
        assertNotNull(player().getPendingItemUse());
    }

    @Test
    void unacknowledgedCostCannotApplyEffectsOrCooldown() throws Exception {
        var item = food(heal());
        var group = item.getItemData().getClass().getDeclaredField("cdGroup");
        group.setAccessible(true);
        group.setInt(item.getItemData(), 77);
        var duration = item.getItemData().getClass().getDeclaredField("cdTime");
        duration.setAccessible(true);
        duration.setFloat(item.getItemData(), 120);
        var engine =
                engine(
                        value -> {
                            if (value instanceof GameItem)
                                throw new IllegalStateException("Cost write unavailable");
                        });
        assertNull(use(engine, item));
        env.unchanged();
        assertEquals(1, item.getCount());
        assertNotNull(player().getPendingItemUse());
        assertEquals(0, player().getItemUseCooldownEnd(item.getItemData().getCdGroup()));
    }

    @Test
    void effectStartsOnlyAfterCostAndIntentAreAcknowledged() throws Exception {
        var writes = new ArrayList<Object>();
        var observed = new AtomicInteger();
        var item =
                food(
                        new ItemUseAction() {
                            @Override
                            public boolean useItem(UseItemParams params) {
                                assertEquals(1, player().getInventory().getItemCountById(108033));
                                assertNotNull(player().getPendingItemUse());
                                assertEquals(4, player().getPendingItemUse().getState());
                                assertTrue(
                                        writes.stream()
                                                .anyMatch(value -> value instanceof GameItem));
                                observed.incrementAndGet();
                                return true;
                            }
                        });
        assertSame(item, use(engine(writes::add), item));
        assertEquals(1, observed.get());
        assertEquals(1, item.getCount());
        assertNull(player().getPendingItemUse());
        assertEquals(4234, env.env.first.getAvatar().getSatiation());
    }

    @Test
    void allActionsRejectedRefundsTheSameStackWithoutAnObtainOrCostNotification() throws Exception {
        var item = food(new ItemUseAction());
        long guid = item.getGuid();
        assertNull(use(engine(ignored -> {}), item));
        assertEquals(2, item.getCount());
        assertSame(item, player().getInventory().getItemByGuid(guid));
        assertNull(player().getPendingItemUse());
        env.unchanged();
        assertFalse(
                env.env.env.fixture.player.packets.stream()
                        .anyMatch(
                                p ->
                                        p.getOpcode()
                                                        == emu.grasscutter.net.packet.PacketOpcodes
                                                                .StoreItemChangeNotify
                                                || p.getOpcode()
                                                        == emu.grasscutter.net.packet.PacketOpcodes
                                                                .StoreItemDelNotify));
    }

    @Test
    void aLaterExceptionRetainsThePaidIntentAndAppliedSatiation() throws Exception {
        var item = food(heal(), throwing());
        var engine = engine(ignored -> {});
        assertNull(use(engine, item));
        assertEquals(1500, env.env.hp(env.env.first));
        assertEquals(1, item.getCount());
        assertEquals(4234, env.env.first.getAvatar().getSatiation());
        assertNotNull(player().getPendingItemUse());
        assertNull(use(engine, item));
        assertEquals(1500, env.env.hp(env.env.first));
        assertEquals(1, item.getCount());
    }

    @Test
    void unknownFirstActionOutcomeCannotBeRetriedForFree() throws Exception {
        var item = food(throwing());
        var engine = engine(ignored -> {});
        assertNull(use(engine, item));
        assertEquals(1, item.getCount());
        assertNotNull(player().getPendingItemUse());
        assertNull(use(engine, item));
        env.unchanged();
    }

    @Test
    void invalidTargetNeverReservesOrChargesTheFood() throws Exception {
        var writes = new ArrayList<Object>();
        var item = food(heal());
        assertNull(engine(writes::add).useItem(player(), 987654321, item.getGuid(), 1, 0, false));
        assertEquals(2, item.getCount());
        assertTrue(writes.isEmpty());
        assertNull(player().getPendingItemUse());
        env.unchanged();
    }

    @Test
    void rejectedRevivalFoodCannotReviveOrStartItsRealResourceCooldown() throws Exception {
        var field = Player.class.getDeclaredField("inventory");
        field.setAccessible(true);
        field.set(
                player(),
                new Inventory(player()) {
                    @Override
                    public synchronized boolean reserveForUse(GameItem item, int count) {
                        return false;
                    }
                });
        env.env.first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        env.env.first.getAvatar().setCurrentHp(0);
        var dead =
                emu.grasscutter.game.entity.GameEntity.class.getDeclaredMethod(
                        "setDead", boolean.class);
        dead.setAccessible(true);
        dead.invoke(env.env.first, true);
        var item = env.env.stock(108005);
        assertTrue(item.getItemData().getCdGroup() > 0);
        assertTrue(item.getItemData().getCdTime() > 0);
        assertNull(use(engine(ignored -> {}), item));
        assertFalse(env.env.first.isAlive());
        assertEquals(0, env.env.hp(env.env.first));
        assertEquals(2, item.getCount());
        assertEquals(0, player().getItemUseCooldownEnd(item.getItemData().getCdGroup()));
        assertNull(player().getPendingItemUse());
    }

    @Test
    void unknownFinalClearKeepsCompletedFoodFromBeingUsedAgain() throws Exception {
        var item = food(heal());
        var engine =
                engine(
                        value -> {
                            if (value == player() && player().getPendingItemUse() == null)
                                throw new IllegalStateException("Final clear unavailable");
                        });
        assertNull(use(engine, item));
        assertEquals(1500, env.env.hp(env.env.first));
        assertEquals(1, item.getCount());
        assertEquals(3, player().getPendingItemUse().getState());
        assertNull(use(engine, item));
        assertEquals(1500, env.env.hp(env.env.first));
        assertEquals(1, item.getCount());
    }

    @Test
    void unacknowledgedRefundKeepsTheExplicitRefundDecisionAndBlocksRetry() throws Exception {
        var item = food(new ItemUseAction());
        var itemWrites = new AtomicInteger();
        var engine =
                engine(
                        value -> {
                            if (value instanceof GameItem && itemWrites.incrementAndGet() == 2)
                                throw new IllegalStateException(
                                        "Refund acknowledgement unavailable");
                        });
        assertNull(use(engine, item));
        assertEquals(2, item.getCount());
        assertEquals(5, player().getPendingItemUse().getState());
        assertNull(use(engine, item));
        assertEquals(2, itemWrites.get());
        env.unchanged();
    }
}

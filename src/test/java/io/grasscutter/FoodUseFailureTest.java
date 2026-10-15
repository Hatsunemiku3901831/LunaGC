package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class FoodUseFailureTest {
    static ItemData special;
    FoodBuffEffectsTest env;
    InventorySystem inventory;

    @BeforeAll
    static void resources() throws Exception {
        FoodBuffEffectsTest.resources();
        special =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(d -> d.getId() == 108566)
                        .findFirst()
                        .orElseThrow();
        special.onLoad();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new FoodBuffEffectsTest();
        env.setup();
        var satiation=AbilityTestFixture.TestPlayer.class.getDeclaredField("satiation");
        satiation.setAccessible(true);
        satiation.set(env.env.fixture.player,
                new emu.grasscutter.game.managers.SatiationManager(env.env.fixture.player));
        inventory = new InventorySystem(env.env.fixture.scene.getWorld().getServer());
        env.first.getAvatar().setSatiation(1234);
        env.first.getAvatar().setSatiationPenalty(678);
    }

    @AfterEach
    void close() {
        env.close();
    }

    ItemData food(ItemUseAction... actions) throws Exception {
        var data =
                JsonUtils.decode(
                        "{id:108033,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_FOOD',stackLimit:99,"
                            + "satiationParams:[22,80000],useTarget:'ITEM_USE_TARGET_SPECIFY_ALIVE_AVATAR'}",
                        ItemData.class);
        var field = ItemData.class.getDeclaredField("itemUseActions");
        field.setAccessible(true);
        field.set(data, List.of(actions));
        return data;
    }

    UseItemParams params(Avatar target) {
        return new UseItemParams(
                env.env.fixture.player,
                ItemUseTarget.ITEM_USE_TARGET_SPECIFY_ALIVE_AVATAR,
                target,
                1,
                0,
                false);
    }

    void unchanged() {
        assertEquals(1234, env.first.getAvatar().getSatiation());
        assertEquals(678, env.first.getAvatar().getSatiationPenalty());
        assertEquals(1000, env.hp(env.first));
    }

    @Test
    void unsupportedRealRecipeDoesNotSpendSatiationOrInventory() throws Exception {
        env.env.environment.put(GameData.getItemDataMap(), special.getId(), special);
        var item = env.stock(special.getId());
        assertNull(inventory.useItem(env.env.fixture.player, 50001, item.getGuid(), 1, 0, false));
        assertEquals(2, item.getCount());
        unchanged();
    }

    @Test
    void rejectedActionRefundsSatiation() throws Exception {
        assertFalse(
                inventory.useItemDirect(food(new ItemUseAction()), params(env.first.getAvatar())));
        unchanged();
    }

    @Test
    void refundPreservesElapsedReductionAndPenaltyChanges() throws Exception {
        var data =
                food(
                        new ItemUseAction() {
                            @Override
                            public boolean useItem(UseItemParams p) {
                                p.targetAvatar.reduceSatiation(30);
                                p.targetAvatar.reduceSatiationPenalty(100);
                                return false;
                            }
                        });
        assertFalse(inventory.useItemDirect(data, params(env.first.getAvatar())));
        assertEquals(1204, env.first.getAvatar().getSatiation());
        assertEquals(578, env.first.getAvatar().getSatiationPenalty());
    }

    @Test
    void invalidMaximumHpDoesNotCreateAnOverflowingSatiationCharge() throws Exception {
        env.first.setFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP, 0);
        assertFalse(
                inventory.useItemDirect(food(new ItemUseAction()), params(env.first.getAvatar())));
        unchanged();
    }

    @Test
    void malformedSatiationRecipeIsRejectedBeforeExecutingActions() throws Exception {
        var data = food(new ItemUseAction());
        var field = ItemData.class.getDeclaredField("satiationParams");
        field.setAccessible(true);
        field.set(data, new int[] {22});
        assertFalse(inventory.useItemDirect(data, params(env.first.getAvatar())));
        unchanged();
    }

    @Test
    void rollbackPublishesTheRestoredSatiation() throws Exception {
        assertFalse(
                inventory.useItemDirect(food(new ItemUseAction()), params(env.first.getAvatar())));
        var packets = env.env.fixture.player.packets;
        var last =
                packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .AvatarPropNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        var notify =
                emu.grasscutter.net.proto.AvatarPropNotifyOuterClass.AvatarPropNotify.parseFrom(
                        last.getData());
        assertEquals(50001, notify.getAvatarGuid());
        assertEquals(
                1234,
                notify.getPropMapOrThrow(
                                emu.grasscutter.game.props.PlayerProperty.PROP_SATIATION_VAL
                                        .getId()));
    }

    @Test
    void throwingActionRefundsSatiationBeforeTheFailureEscapes() throws Exception {
        var data =
                food(
                        new ItemUseAction() {
                            @Override
                            public boolean useItem(UseItemParams p) {
                                throw new IllegalStateException("injected food failure");
                            }
                        });
        assertThrows(
                IllegalStateException.class,
                () -> inventory.useItemDirect(data, params(env.first.getAvatar())));
        unchanged();
    }

    @Test
    void foodWithoutActionsIsNotAccepted() throws Exception {
        assertFalse(inventory.useItemDirect(food(), params(env.first.getAvatar())));
        unchanged();
    }

    @Test
    void nonPartyTargetIsRejectedWithoutThrowing() throws Exception {
        var absent =
                new Avatar() {
                    @Override
                    public emu.grasscutter.game.player.Player getPlayer() {
                        return env.env.fixture.player;
                    }
                };
        assertFalse(inventory.useItemDirect(food(new ItemUseAction()), params(absent)));
        unchanged();
    }

    @Test
    void fullAvatarDoesNotExecuteTheFoodAction() throws Exception {
        var calls = new AtomicInteger();
        var data =
                food(
                        new ItemUseAction() {
                            @Override
                            public boolean useItem(UseItemParams p) {
                                calls.incrementAndGet();
                                return true;
                            }
                        });
        env.first.getAvatar().setSatiation(10000);
        assertFalse(inventory.useItemDirect(data, params(env.first.getAvatar())));
        assertEquals(0, calls.get());
        assertEquals(10000, env.first.getAvatar().getSatiation());
    }

    @Test
    void successfulFoodKeepsItsSatiationIncrease() {
        var data = GameData.getItemDataMap().get(108033);
        assertTrue(inventory.useItemDirect(data, params(env.first.getAvatar())));
        assertEquals(4234, env.first.getAvatar().getSatiation());
        assertEquals(4900, env.hp(env.first));
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TPS equipment and ammunition share the bag transaction, never the broad Player monitor. */
class TpsInventoryLockTest {
    private TpsEquipmentTest environment;
    private CheckingPlayer player;

    private static class CheckingPlayer extends TpsEquipmentTest.RecordingPlayer {
        boolean checkSave;

        @Override public void save() {
            if (checkSave) {
                assertFalse(Thread.holdsLock(this), "TPS saves must not retain Player while callbacks run");
                assertTrue(Thread.holdsLock(getInventory()), "Ammunition and equipment must use one transaction monitor");
            }
            super.save();
        }
    }

    private static class CheckingAvatar extends TpsEquipmentTest.RecordingAvatar {
        boolean checkRecalc;

        CheckingAvatar() { super(10000005); }

        @Override public void recalcStats(boolean force) {
            if (checkRecalc) {
                assertFalse(Thread.holdsLock(getPlayer()), "Recalculation enters modifier locks and must not retain Player");
                assertTrue(Thread.holdsLock(getPlayer().getInventory()));
            }
            super.recalcStats(force);
        }
    }

    @BeforeAll
    static void init() { TpsEquipmentTest.init(); }

    @BeforeEach
    void setup() {
        environment = new TpsEquipmentTest();
        environment.resources();
        player = new CheckingPlayer();
    }

    @AfterEach
    void close() { environment.restore(); }

    @ParameterizedTest
    @ValueSource(strings = {"wear", "remove"})
    void equipmentCallbacksDoNotHoldPlayerMonitor(String operation) {
        var avatar = new CheckingAvatar();
        player.getAvatars().addAvatar(avatar);
        var gun = new TpsEquipmentTest.RecordingItem(224001);
        assertTrue(player.getInventory().addItem(gun));
        if (operation.equals("remove")) avatar.getTpsWeaponIds().add(gun.getItemId());
        avatar.checkRecalc = true;
        if (operation.equals("wear")) {
            assertEquals(0, TpsWeaponSystem.wear(player, avatar.getGuid(), List.of(gun.getGuid())));
            assertEquals(List.of(224001), avatar.getTpsWeaponIds());
        } else {
            assertTrue(player.getInventory().removeItem(gun));
            assertTrue(avatar.getTpsWeaponIds().isEmpty());
        }
        assertEquals(1, avatar.recalcs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"change", "refill"})
    void ammunitionUpdatesUseTheSameInventoryTransaction(String operation) {
        player.getTpsAmmunition().put(1001, 100);
        player.checkSave = true;
        if (operation.equals("change")) {
            assertEquals(105, TpsWeaponSystem.changeReserve(player, 1001, 5));
        } else {
            TpsWeaponSystem.refillAmmunition(player);
            assertEquals(2000, TpsWeaponSystem.getReserve(player, 1001));
        }
    }
}

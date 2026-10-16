package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.DelayedPlayerSave;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsSupplyTransaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

class TpsSupplySaveFenceTest {
    @ParameterizedTest
    @CsvSource({"true,resume", "false,resume", "true,cancel", "false,cancel"})
    void aDelayedSupplyReservationIsResolvedBeforeDecidingItsOutcome(
            boolean originalWins, String action) throws Exception {
        try (var f = new TpsReportMongoFixture(0)) {
            var fault = new DelayedPlayerSave(f.player, f.store);
            fault.delayNextReplacement();
            var transaction = f.player.getTpsSupplyTransaction();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            transaction.grant(
                                    f.entity, List.of(new TpsSupplyTransaction.Amount(1003, 1))));
            String id = f.player.getPendingTpsSupply().getOperationId();
            var beforeAudit = f.row();
            transaction.audit(id);
            assertEquals(beforeAudit, f.row());
            assertEquals(0, fault.reads());
            assertThrows(IllegalStateException.class, () -> transaction.resume("wrong"));
            assertThrows(IllegalStateException.class, () -> transaction.cancel("wrong"));
            assertEquals(0, fault.reads());
            if (originalWins) fault.commitBeforeNextCheck();
            if (action.equals("resume")) transaction.resume(id);
            else transaction.cancel(id);
            var committed = f.row();
            assertFalse(fault.release());
            assertEquals(committed, f.row());
            var cold = f.store.find(Player.class).first();
            assertEquals(action.equals("resume") ? 4 : 3, cold.getTpsAmmunition().get(1003));
            assertNull(cold.getPendingTpsSupply());
            assertNull(f.player.getPendingTpsSupply());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"resume", "cancel"})
    void unchangedSupplyDataFromAForeignVersionIsRejectedBeforeAnyWrite(String action)
            throws Exception {
        try (var f = new TpsReportMongoFixture(0)) {
            int[] writes = {0};
            var transaction =
                    new TpsSupplyTransaction(
                            f.player,
                            () -> {
                                writes[0]++;
                                f.player.saveTpsAmmunition();
                                if (writes[0] == 1)
                                    throw new IllegalStateException("lost outer acknowledgement");
                            });
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            transaction.grant(
                                    f.entity, List.of(new TpsSupplyTransaction.Amount(1003, 1))));
            String id = f.player.getPendingTpsSupply().getOperationId();
            var foreign = f.store.find(Player.class).first();
            foreign.getArtifactTransmuterData().setProgressUnits(5600);
            DatabaseHelper.saveGameSync(foreign);
            var committed = f.row();
            transaction.audit(id);
            writes[0] = 0;
            var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> {
                                if (action.equals("resume")) transaction.resume(id);
                                else transaction.cancel(id);
                            });
            assertTrue(failure.getMessage().contains("版本"));
            assertEquals(0, writes[0]);
            assertNotNull(f.player.getPendingTpsSupply());
            assertEquals(committed, f.row());
        }
    }
}

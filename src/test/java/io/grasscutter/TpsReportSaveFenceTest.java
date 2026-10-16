package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.DelayedPlayerSave;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsReportTransaction;
import emu.grasscutter.game.tps.TpsWeaponSystem;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TpsReportSaveFenceTest {
    static TpsReportTransaction transaction(TpsReportMongoFixture f, Runnable save)
            throws Exception {
        var transaction = new TpsReportTransaction(f.player, save);
        var field = Player.class.getDeclaredField("tpsReportTransaction");
        field.setAccessible(true);
        field.set(f.player, transaction);
        return transaction;
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,true", "0,false", "1,false"})
    void cancellationMustDecideAfterTheUncertainWriteHasBeenRetiredOrRecognized(
            int type, boolean originalWins) throws Exception {
        try (var f = new TpsReportMongoFixture(type)) {
            var fault = new DelayedPlayerSave(f.player, f.store);
            fault.delayNextReplacement();
            var report =
                    TpsReportRecoveryTest.reportFor(
                            f.entity.getId(), f.gun.getWeaponEntity().getId(), type);
            assertThrows(
                    IllegalStateException.class,
                    () -> TpsWeaponSystem.onAmmunitionInvoke(f.player, report));
            var transaction = f.player.getTpsReportTransaction();
            String id = f.player.getLastTpsReport().getOperationId();
            var beforeAudit = f.row();
            assertTrue(transaction.audit(id).contains("尚未写入"));
            assertEquals(beforeAudit, f.row());
            assertEquals(0, fault.reads(), "Audit must not reconcile or retire a write");
            if (originalWins) {
                fault.commitBeforeNextCheck();
                IllegalStateException denied = null;
                try {
                    transaction.cancel(id);
                } catch (IllegalStateException expected) {
                    denied = expected;
                }
                var decision = f.store.find(Player.class).first();
                assertEquals(
                        type == 0 ? 2 : 3,
                        decision.getTpsAmmunition().get(1003),
                        "Cancellation must not roll back a committed ammunition result");
                assertEquals(
                        type == 0 ? 2 : 3,
                        decision.getTpsMagazineAmmunition().get(301),
                        "Cancellation must not roll back a committed magazine result");
                assertNotNull(denied, "An already committed report cannot be cancelled");
                assertTrue(denied.getMessage().contains("不能取消"));
                assertTrue(transaction.isPending());
                transaction.resume(id);
            } else {
                transaction.cancel(id);
                var committed = f.row();
                assertFalse(
                        fault.release(), "The cancelled original cannot arrive after cancellation");
                assertEquals(committed, f.row());
            }
            assertFalse(transaction.isPending());
            var cold = f.store.find(emu.grasscutter.game.player.Player.class).first();
            assertEquals(originalWins && type == 0 ? 2 : 3, cold.getTpsAmmunition().get(1003));
            assertEquals(
                    originalWins ? (type == 0 ? 2 : 3) : (type == 0 ? 3 : 1),
                    cold.getTpsMagazineAmmunition().get(301));
            if (originalWins) assertEquals(id, cold.getLastTpsReport().getOperationId());
            else assertNull(cold.getLastTpsReport());
            assertEquals(originalWins ? 1 : 2, fault.replacements());
            assertEquals(originalWins ? 0 : 1, fault.advances());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,true", "0,false", "1,false"})
    void resumingEitherRetiresTheOriginalOrRecognizesItWithoutRepeatingTheReport(
            int type, boolean originalWins) throws Exception {
        try (var f = new TpsReportMongoFixture(type)) {
            var fault = new DelayedPlayerSave(f.player, f.store);
            fault.delayNextReplacement();
            var report =
                    TpsReportRecoveryTest.reportFor(
                            f.entity.getId(), f.gun.getWeaponEntity().getId(), type);
            assertThrows(
                    IllegalStateException.class,
                    () -> TpsWeaponSystem.onAmmunitionInvoke(f.player, report));
            var transaction = f.player.getTpsReportTransaction();
            String id = f.player.getLastTpsReport().getOperationId();
            if (originalWins) fault.commitBeforeNextCheck();
            transaction.resume(id);
            assertFalse(transaction.isPending());
            var result = f.row();
            assertFalse(fault.release());
            assertEquals(result, f.row());
            var cold = f.store.find(Player.class).first();
            assertEquals(type == 0 ? 2 : 3, cold.getTpsAmmunition().get(1003));
            assertEquals(type == 0 ? 2 : 3, cold.getTpsMagazineAmmunition().get(301));
            assertEquals(id, cold.getLastTpsReport().getOperationId());
            assertEquals(originalWins ? 1 : 2, fault.replacements());
            assertEquals(originalWins ? 0 : 1, fault.advances());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,resume", "1,resume", "0,cancel", "1,cancel"})
    void aForeignVersionWithTheSameReportDataCannotBeAcceptedAsOurStoredVersion(
            int type, String action) throws Exception {
        try (var f = new TpsReportMongoFixture(type)) {
            int[] writes = {0};
            var transaction =
                    transaction(
                            f,
                            () -> {
                                writes[0]++;
                                f.player.saveTpsAmmunition();
                                if (writes[0] == 1)
                                    throw new IllegalStateException("lost outer acknowledgement");
                            });
            var report =
                    TpsReportRecoveryTest.reportFor(
                            f.entity.getId(), f.gun.getWeaponEntity().getId(), type);
            assertThrows(
                    IllegalStateException.class,
                    () -> TpsWeaponSystem.onAmmunitionInvoke(f.player, report));
            String id = f.player.getLastTpsReport().getOperationId();
            var foreign = f.store.find(Player.class).first();
            foreign.getArtifactTransmuterData().setProgressUnits(5500);
            DatabaseHelper.saveGameSync(foreign);
            var committed = f.row();
            assertTrue(transaction.audit(id).contains("已完整写入"));
            writes[0] = 0;
            var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> {
                                if (action.equals("resume")) transaction.resume(id);
                                else transaction.cancel(id);
                            });
            assertTrue(failure.getMessage().contains("版本"));
            assertTrue(transaction.isPending());
            assertEquals(0, writes[0]);
            assertEquals(committed, f.row());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void anInvalidOperationIdDoesNotRetireOrAcknowledgeAnyPendingWrite(int type) throws Exception {
        try (var f = new TpsReportMongoFixture(type)) {
            var fault = new DelayedPlayerSave(f.player, f.store);
            fault.delayNextReplacement();
            var report =
                    TpsReportRecoveryTest.reportFor(
                            f.entity.getId(), f.gun.getWeaponEntity().getId(), type);
            assertThrows(
                    IllegalStateException.class,
                    () -> TpsWeaponSystem.onAmmunitionInvoke(f.player, report));
            var transaction = f.player.getTpsReportTransaction();
            var committed = f.row();
            assertThrows(IllegalStateException.class, () -> transaction.resume("wrong"));
            assertThrows(IllegalStateException.class, () -> transaction.cancel("wrong"));
            assertEquals(0, fault.reads());
            assertEquals(0, fault.advances());
            assertEquals(1, fault.replacements());
            assertEquals(committed, f.row());
        }
    }
}

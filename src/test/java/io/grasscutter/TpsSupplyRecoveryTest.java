package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.concurrent.*;

class TpsSupplyRecoveryTest {
    TpsAmmunitionSupplyTest state;
    TpsSupplyTransaction transaction;
    TpsSupplyTransaction.Stored stored;
    int writes, failAt;
    boolean commitBeforeFailure;
    final List<TpsSupplyTransaction.Amount> plan =
            List.of(
                    new TpsSupplyTransaction.Amount(1001, 75),
                    new TpsSupplyTransaction.Amount(1003, 6),
                    new TpsSupplyTransaction.Amount(1001, 75));

    static TpsSupplyReceipt copy(TpsSupplyReceipt receipt) {
        return receipt == null
                ? null
                : JsonUtils.decode(JsonUtils.toJson(receipt), TpsSupplyReceipt.class);
    }

    TpsSupplyTransaction.Stored snapshot(Player player) {
        return new TpsSupplyTransaction.Stored(
                player.getUid(),
                new LinkedHashMap<>(player.getTpsAmmunition()),
                copy(player.getPendingTpsSupply()));
    }

    void save(Player player) {
        assertEquals(
                0,
                packets(PacketOpcodes.TpsAmmunitionChangeNotify),
                "no increment may be emitted before acknowledged completion");
        writes++;
        if (writes == failAt && !commitBeforeFailure)
            throw new IllegalStateException("before write");
        stored = snapshot(player);
        if (writes == failAt) throw new IllegalStateException("after write");
    }

    @BeforeEach
    void setup() throws Exception {
        state = new TpsAmmunitionSupplyTest();
        state.setup();
        state.player.packets.clear();
        stored = snapshot(state.player);
        transaction =
                new TpsSupplyTransaction(
                        state.player, () -> save(state.player), () -> stored, () -> 1000L);
    }

    @AfterEach
    void close() {
        state.close();
    }

    long packets(int opcode) {
        return state.player.packets.stream().filter(p -> p.getOpcode() == opcode).count();
    }

    @Test
    void theCompleteSupplyAndItsIntentAreAcknowledgedBeforeItsOneIncrementNotification() {
        assertTrue(transaction.grant(state.recipient, plan));
        assertEquals(150, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        assertEquals(stored.ammunition(), state.player.getTpsAmmunition());
        assertNull(stored.receipt());
        assertNull(state.player.getPendingTpsSupply());
        assertEquals(3, writes);
        assertEquals(1, packets(PacketOpcodes.TpsAmmunitionChangeNotify));
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true", "3,false", "3,true"})
    void eachUncertainCheckpointCanResumeWithoutRepeatingAnyAllocation(
            int checkpoint, boolean committed) {
        failAt = checkpoint;
        commitBeforeFailure = committed;
        assertThrows(IllegalStateException.class, () -> transaction.grant(state.recipient, plan));
        var pending = state.player.getPendingTpsSupply();
        assertNotNull(pending);
        String id = pending.getOperationId();
        assertEquals(checkpoint == 1 ? 0 : 150, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(0, packets(PacketOpcodes.TpsAmmunitionChangeNotify));
        int beforeAudit = writes;
        transaction.audit(id);
        assertEquals(beforeAudit, writes);
        failAt = 0;
        transaction.resume(id);
        assertEquals(150, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        assertNull(stored.receipt());
        assertNull(state.player.getPendingTpsSupply());
        assertEquals(0, packets(PacketOpcodes.TpsAmmunitionChangeNotify));
        assertEquals(1, packets(PacketOpcodes.TpsRegionalPlaySupplyInfoNotify));
        int completed = writes;
        assertThrows(IllegalStateException.class, () -> transaction.resume(id));
        assertEquals(completed, writes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUnappliedIntentCanBeCancelledEvenWhenItsInitialWriteWasUncertain(boolean committed) {
        failAt = 1;
        commitBeforeFailure = committed;
        assertThrows(IllegalStateException.class, () -> transaction.grant(state.recipient, plan));
        String id = state.player.getPendingTpsSupply().getOperationId();
        failAt = 0;
        transaction.cancel(id);
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1001));
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1003));
        assertNull(state.player.getPendingTpsSupply());
        assertNull(stored.receipt());
    }

    @Test
    void anAppliedIntentCannotBeCancelledOrSpentBeforeReconciliation() throws Exception {
        failAt = 2;
        assertThrows(IllegalStateException.class, () -> transaction.grant(state.recipient, plan));
        String id = state.player.getPendingTpsSupply().getOperationId();
        var frozen = new HashMap<>(state.player.getTpsAmmunition());
        int count = writes;
        assertThrows(IllegalStateException.class, () -> transaction.cancel(id));
        assertFalse(transaction.grant(state.recipient, plan));
        assertFalse(TpsWeaponSystem.supplyAmmunition(state.recipient, 1001, 1));
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.changeReserve(state.player, 1001, -1));
        assertThrows(
                IllegalStateException.class, () -> TpsWeaponSystem.refillAmmunition(state.player));
        assertFalse(TpsWeaponSystem.hasAmmunitionSpace(state.recipient, 1001));
        assertFalse(
                TpsWeaponSystem.onAmmunitionInvoke(
                        state.player, AbilityInvokeEntry.getDefaultInstance()));
        assertEquals(frozen, state.player.getTpsAmmunition());
        assertEquals(count, writes);
    }

    @Test
    void anUncertainCancellationCanBeRetriedWithoutGranting() {
        failAt = 1;
        commitBeforeFailure = true;
        assertThrows(IllegalStateException.class, () -> transaction.grant(state.recipient, plan));
        String id = state.player.getPendingTpsSupply().getOperationId();
        failAt = 2;
        assertThrows(IllegalStateException.class, () -> transaction.cancel(id));
        assertNotNull(state.player.getPendingTpsSupply());
        assertNull(stored.receipt());
        failAt = 0;
        transaction.cancel(id);
        assertEquals(0, TpsWeaponSystem.getReserve(state.player, 1001));
        assertNull(state.player.getPendingTpsSupply());
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-amount", "stored-amount", "stored-plan", "wrong-id"})
    void disagreementCannotBeRepairedByGuessing(String mismatch) {
        failAt = 1;
        commitBeforeFailure = true;
        assertThrows(IllegalStateException.class, () -> transaction.grant(state.recipient, plan));
        String id = state.player.getPendingTpsSupply().getOperationId();
        switch (mismatch) {
            case "live-amount" -> state.player.getTpsAmmunition().put(1001, 1);
            case "stored-amount" -> stored.ammunition().put(1001, 1);
            case "stored-plan" -> stored =
                    new TpsSupplyTransaction.Stored(
                            state.player.getUid(),
                            stored.ammunition(),
                            JsonUtils.decode(
                                    JsonUtils.toJson(stored.receipt())
                                            .toString()
                                            .replace(id, UUID.randomUUID().toString()),
                                    TpsSupplyReceipt.class));
            default -> id = UUID.randomUUID().toString();
        }
        String requested = id;
        int count = writes;
        assertThrows(IllegalStateException.class, () -> transaction.resume(requested));
        assertThrows(IllegalStateException.class, () -> transaction.cancel(requested));
        assertEquals(count, writes);
    }

    @Test
    void concurrentResumesConfirmOneFrozenResult() throws Exception {
        failAt = 2;
        commitBeforeFailure = true;
        assertThrows(IllegalStateException.class, () -> transaction.grant(state.recipient, plan));
        String id = state.player.getPendingTpsSupply().getOperationId();
        failAt = 0;
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> resume(id));
            var b = pool.submit(() -> resume(id));
            assertEquals(
                    1, (a.get(5, TimeUnit.SECONDS) ? 1 : 0) + (b.get(5, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(150, TpsWeaponSystem.getReserve(state.player, 1001));
            assertEquals(6, TpsWeaponSystem.getReserve(state.player, 1003));
        } finally {
            pool.shutdownNow();
        }
    }

    boolean resume(String id) {
        try {
            transaction.resume(id);
            return true;
        } catch (IllegalStateException alreadyConfirmed) {
            return false;
        }
    }
}

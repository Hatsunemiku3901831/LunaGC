package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.commands.TpsCommand;
import emu.grasscutter.game.tps.*;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.TpsAmmunitionChangeOuterClass.TpsAmmunitionChange;
import emu.grasscutter.net.proto.TpsWeaponAccessoryInfoOuterClass.TpsWeaponAccessoryInfo;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

class TpsReportRecoveryTest {
    TpsAmmunitionSupplyTest state;
    ReportPlayer player;
    TpsReportTransaction transaction;
    TpsReportTransaction.Stored stored;
    boolean failBefore, failAfter;

    static class ReportPlayer extends TpsEquipmentTest.RecordingPlayer {
        boolean failAcknowledgement;
        int acknowledgedWrites;
        Runnable persistence;
        TpsReportTransaction transaction;

        @Override
        public synchronized TpsReportTransaction getTpsReportTransaction() {
            return transaction == null ? super.getTpsReportTransaction() : transaction;
        }

        @Override
        public void saveTpsAmmunition() {
            acknowledgedWrites++;
            if (failAcknowledgement) throw new IllegalStateException("Unconfirmed report write");
            if (persistence != null) persistence.run();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        state =
                new TpsAmmunitionSupplyTest() {
                    @Override
                    TpsEquipmentTest.RecordingPlayer createPlayer() {
                        var reportPlayer = new ReportPlayer();
                        reportPlayer.setUid(74101);
                        TpsReportRecoveryTest.this.player = reportPlayer;
                        return reportPlayer;
                    }
                };
        state.setup();
        player.getTpsAmmunition().put(1003, 3);
        player.getTpsMagazineAmmunition().put(301, 3);
        player.acknowledgedWrites = 0;
        stored = snapshot();
        player.persistence =
                () -> {
                    if (failBefore) throw new IllegalStateException("before report write");
                    stored = snapshot();
                    if (failAfter) throw new IllegalStateException("after report write");
                };
        transaction =
                new TpsReportTransaction(
                        player, player::saveTpsAmmunition, () -> stored, () -> 1000L);
        player.transaction = transaction;
    }

    @AfterEach
    void close() {
        state.close();
    }

    TpsReportTransaction.Stored snapshot() {
        return new TpsReportTransaction.Stored(
                player.getUid(),
                new LinkedHashMap<>(player.getTpsAmmunition()),
                new LinkedHashMap<>(player.getTpsMagazineAmmunition()),
                player.getLastTpsReport() == null
                        ? null
                        : JsonUtils.decode(
                                JsonUtils.toJson(player.getLastTpsReport()),
                                TpsReportReceipt.class));
    }

    void resetFailure() {
        failBefore = false;
        failAfter = false;
        player.failAcknowledgement = false;
    }

    AbilityInvokeEntry report(int type) {
        return reportFor(state.recipient.getId(), state.weapon.getId(), type);
    }

    static AbilityInvokeEntry reportFor(int avatar, int weapon, int type) {
        return AbilityInvokeEntry.newBuilder()
                .setEntityId(avatar)
                .setArgumentType(AbilityInvokeArgument.ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION)
                .setTotalTickTime(12.5)
                .setAbilityData(
                        AbilityMetaUpdateTpsWeaponAmmunition.newBuilder()
                                .setUpdateTypeValue(type)
                                .addAmmunitionList(
                                        TpsAmmunitionChange.newBuilder()
                                                .setAmmunitionConfigId(1003)
                                                .setChangeCount(type == 0 ? -1 : 0))
                                .addAccessoryList(
                                        TpsWeaponAccessoryInfo.newBuilder()
                                                .setEntityId(weapon)
                                                .setAmmunitionType(301)
                                                .setAmmunitionConfigId(1003)
                                                .setCurrentAmmunition(type == 0 ? 2 : 3))
                                .build()
                                .toByteString())
                .build();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void changedReportsCannotBeAcceptedBeforeAcknowledgedPersistence(int type) {
        if (type == 1) player.getTpsMagazineAmmunition().put(301, 1);
        player.failAcknowledgement = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
        assertEquals(1, player.acknowledgedWrites);
        assertEquals(
                0,
                player.saves,
                "The fire/reload path must not fall back to an asynchronous player save");
        assertTrue(transaction.isPending());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void anAcceptedReportAtomicallyStoresBothMapsAndItsReceiptWithOneWrite(int type)
            throws Exception {
        if (type == 1) player.getTpsMagazineAmmunition().put(301, 1);
        assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
        assertEquals(type == 0 ? 2 : 3, stored.ammunition().get(1003));
        assertEquals(type == 0 ? 2 : 3, stored.magazines().get(301));
        assertNotNull(stored.receipt());
        stored.receipt().validate();
        assertEquals(player.getLastTpsReport().getOperationId(), stored.receipt().getOperationId());
        assertEquals(1, player.acknowledgedWrites);
        assertFalse(transaction.isPending());
        assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
        assertEquals(1, player.acknowledgedWrites);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "1,false", "1,true"})
    void ambiguousWritesResumeTheFrozenResultWithoutRepeatingTheFee(int type, boolean committed)
            throws Exception {
        if (type == 1) player.getTpsMagazineAmmunition().put(301, 1);
        stored = snapshot();
        failBefore = !committed;
        failAfter = committed;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
        String id = player.getLastTpsReport().getOperationId();
        assertTrue(transaction.pending().contains(id));
        int writes = player.acknowledgedWrites;
        transaction.audit(id);
        assertEquals(writes, player.acknowledgedWrites);
        assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
        assertFalse(TpsWeaponSystem.hasAmmunitionSpace(state.recipient, 1003));
        assertThrows(IllegalStateException.class, () -> TpsWeaponSystem.refillAmmunition(player));
        assertThrows(
                IllegalStateException.class, () -> TpsWeaponSystem.changeReserve(player, 1003, 1));
        assertFalse(TpsWeaponSystem.supplyAmmunition(state.recipient, 1003, 1));
        resetFailure();
        transaction.resume(id);
        assertEquals(writes + (committed ? 0 : 1), player.acknowledgedWrites);
        assertEquals(type == 0 ? 2 : 3, player.getTpsAmmunition().get(1003));
        assertEquals(type == 0 ? 2 : 3, player.getTpsMagazineAmmunition().get(301));
        assertFalse(transaction.isPending());
        assertThrows(IllegalStateException.class, () -> transaction.resume(id));
        assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void cancelRestoresTheExactBeforeMapsOnlyWhenStorageStillContainsThem(int type)
            throws Exception {
        if (type == 1) player.getTpsMagazineAmmunition().put(301, 1);
        stored = snapshot();
        var before = stored;
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(type)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        new TpsCommand().execute(null, player, List.of("cancel", id));
        assertFalse(transaction.isPending());
        assertEquals(before.ammunition(), player.getTpsAmmunition());
        assertEquals(before.magazines(), player.getTpsMagazineAmmunition());
        assertNull(player.getLastTpsReport());
        assertNull(stored.receipt());
    }

    @Test
    void aStoredResultCannotBeCancelledAndResumeDoesNotIssueAnotherWrite() throws Exception {
        failAfter = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        assertThrows(IllegalStateException.class, () -> transaction.cancel(id));
        int writes = player.acknowledgedWrites;
        transaction.resume(id);
        assertEquals(writes, player.acknowledgedWrites);
        assertEquals(2, stored.ammunition().get(1003));
    }

    @ParameterizedTest
    @ValueSource(strings = {"uid", "total", "magazine", "receipt", "live", "liveIdentity"})
    void conflictingStorageOrLiveStateCannotBeOverwrittenByRecovery(String change)
            throws Exception {
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        switch (change) {
            case "uid" -> stored =
                    new TpsReportTransaction.Stored(
                            12, stored.ammunition(), stored.magazines(), stored.receipt());
            case "total" -> stored.ammunition().put(1003, 99);
            case "magazine" -> stored.magazines().put(301, 0);
            case "receipt" -> stored =
                    new TpsReportTransaction.Stored(
                            player.getUid(),
                            stored.ammunition(),
                            stored.magazines(),
                            snapshot().receipt());
            case "live" -> player.getTpsMagazineAmmunition().put(301, 99);
            case "liveIdentity" -> {
                player.setUid(99);
                stored =
                        new TpsReportTransaction.Stored(
                                99, stored.ammunition(), stored.magazines(), stored.receipt());
            }
        }
        int writes = player.acknowledgedWrites;
        assertThrows(IllegalStateException.class, () -> transaction.audit(id));
        assertThrows(IllegalStateException.class, () -> transaction.resume(id));
        assertThrows(IllegalStateException.class, () -> transaction.cancel(id));
        assertTrue(transaction.isPending());
        assertEquals(writes, player.acknowledgedWrites);
    }

    @Test
    void aPreviousCommittedReportIsPreservedWhenTheNextUnwrittenReportIsCancelled()
            throws Exception {
        assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String previous = player.getLastTpsReport().getOperationId();
        var next =
                report(0).toBuilder()
                        .setTotalTickTime(13)
                        .setAbilityData(
                                AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(
                                                report(0).getAbilityData())
                                        .toBuilder()
                                        .setAccessoryList(
                                                0,
                                                TpsWeaponAccessoryInfo.newBuilder()
                                                        .setEntityId(state.weapon.getId())
                                                        .setAmmunitionType(301)
                                                        .setAmmunitionConfigId(1003)
                                                        .setCurrentAmmunition(1))
                                        .build()
                                        .toByteString())
                        .build();
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, next));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        transaction.audit(id);
        transaction.cancel(id);
        assertEquals(previous, player.getLastTpsReport().getOperationId());
        assertEquals(previous, stored.receipt().getOperationId());
        assertEquals(2, stored.ammunition().get(1003));
        assertEquals(2, stored.magazines().get(301));
    }

    @Test
    void concurrentResumeCommitsTheOriginalResultOnce() throws Exception {
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> action =
                    () -> {
                        try {
                            transaction.resume(id);
                            return true;
                        } catch (IllegalStateException done) {
                            return false;
                        }
                    };
            var a = pool.submit(action);
            var b = pool.submit(action);
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, player.acknowledgedWrites);
        assertEquals(2, stored.ammunition().get(1003));
        assertEquals(2, stored.magazines().get(301));
    }

    @Test
    void unchangedReloadDoesNotWriteOrCreateAReceipt() throws Exception {
        assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, report(1)));
        assertEquals(0, player.acknowledgedWrites);
        assertNull(player.getLastTpsReport());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUnconfirmedReportPreventsWeaponRemovalBeforeAnyInventoryMutation(boolean byGuid)
            throws Exception {
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        var gun = player.getInventory().getItemById(224003);
        assertFalse(
                byGuid
                        ? player.getInventory().removeItem(gun.getGuid(), 1)
                        : player.getInventory().removeItem(gun));
        assertSame(gun, player.getInventory().getItemById(224003));
        assertEquals(1, gun.getCount());
        assertSame(state.weapon, state.fixture.scene.getWeaponEntities().get(state.weapon.getId()));
        transaction.cancel(id);
        assertTrue(player.getInventory().removeItem(gun));
        assertNull(player.getInventory().getItemById(224003));
    }

    @ParameterizedTest
    @CsvSource({"resume,false", "resume,true", "cancel,false", "cancel,true"})
    void ambiguousRecoveryWritesCanBeRetriedWithoutRepeatingTheFee(
            String operation, boolean committed) throws Exception {
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        failBefore = !committed;
        failAfter = committed;
        assertThrows(
                IllegalStateException.class,
                () -> {
                    if (operation.equals("resume")) transaction.resume(id);
                    else transaction.cancel(id);
                });
        assertTrue(transaction.isPending());
        resetFailure();
        transaction.audit(id);
        if (operation.equals("resume")) transaction.resume(id);
        else transaction.cancel(id);
        assertFalse(transaction.isPending());
        assertEquals(operation.equals("resume") ? 2 : 3, stored.ammunition().get(1003));
        assertEquals(operation.equals("resume") ? 2 : 3, stored.magazines().get(301));
    }

    @Test
    void anotherSaveOfTheSameLivePlayerCanConfirmTheWholePinnedResult() throws Exception {
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        stored = snapshot();
        int writes = player.acknowledgedWrites;
        transaction.resume(id);
        assertEquals(writes, player.acknowledgedWrites);
        assertEquals(2, stored.ammunition().get(1003));
        assertEquals(2, stored.magazines().get(301));
    }

    @Test
    void recoverySendsAbsoluteTotalsAndExistingWeaponMagazineSnapshots() throws Exception {
        player.loggedIn = true;
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        player.packets.clear();
        state.fixture.scene.packets.clear();
        new TpsCommand().execute(null, player, List.of("resume", id));
        assertFalse(transaction.isPending());
        assertEquals(
                1,
                player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .TpsRegionalPlaySupplyInfoNotify)
                        .count());
        assertEquals(
                0,
                player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .TpsAmmunitionChangeNotify)
                        .count());
        var packet =
                state.fixture.scene.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .TpsEquipChangeNotify)
                        .findFirst()
                        .orElseThrow();
        var result =
                emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify
                        .parseFrom(packet.getData());
        assertEquals(state.weapon.getId(), result.getTpsWeaponList(0).getEntityId());
        assertEquals(2, result.getTpsWeaponList(0).getAmmunitionList(0).getCurrentAmmunition());
        assertSame(state.weapon, state.player.getInventory().getItemById(224003).getWeaponEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalidated", "replaced", "world"})
    void recoveryDoesNotRepublishAnExpiredOrReplacedAvatar(String kind) throws Exception {
        player.loggedIn = true;
        failBefore = true;
        assertThrows(
                IllegalStateException.class,
                () -> TpsWeaponSystem.onAmmunitionInvoke(player, report(0)));
        String id = player.getLastTpsReport().getOperationId();
        resetFailure();
        if (kind.equals("invalidated")) state.recipient.invalidateAbilityLifecycle();
        else if (kind.equals("world"))
            player.setWorld(
                    new emu.grasscutter.game.world.World(
                            state.fixture.scene.getWorld().getServer(), player));
        else {
            var replacement = new AbilityTestFixture.TestEntity(state.fixture.scene);
            replacement.setId(state.recipient.getId());
            state.fixture.scene.getEntities().put(replacement.getId(), replacement);
        }
        state.fixture.scene.packets.clear();
        transaction.resume(id);
        assertFalse(transaction.isPending());
        assertEquals(2, stored.ammunition().get(1003));
        assertEquals(
                0,
                state.fixture.scene.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .TpsEquipChangeNotify)
                        .count());
    }
}

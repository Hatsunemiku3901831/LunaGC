package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.reliquary.ArtifactTransmuter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryDustReqOuterClass.ReliquaryDustReq;
import emu.grasscutter.net.proto.ReliquaryDustRspOuterClass.ReliquaryDustRsp;
import emu.grasscutter.net.proto.ReliquaryDustSelectReqOuterClass.ReliquaryDustSelectReq;
import emu.grasscutter.net.proto.ReliquaryDustSelectRspOuterClass.ReliquaryDustSelectRsp;
import emu.grasscutter.net.proto.ReliquaryOfferCandidateOuterClass.ReliquaryOfferCandidate;
import emu.grasscutter.net.proto.ReliquaryOfferCompanionNotifyOuterClass.ReliquaryOfferCompanionNotify;
import emu.grasscutter.net.proto.ReliquaryOfferExtractReqOuterClass.ReliquaryOfferExtractReq;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

/**
 * Actual current dust requests and persisted items; inherited definition checks share the fixture.
 */
class ArtifactReshapeTest extends ArtifactTransmuterDefineTest {
    static ItemData dust;

    @BeforeAll
    static void dustResource() throws Exception {
        dust =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 105006)
                        .findFirst()
                        .orElseThrow();
        dust.onLoad();
    }

    @BeforeEach
    void dustInventory() {
        put(GameData.getItemDataMap(), dust.getId(), dust);
        stock(dust.getId(), 100);
    }

    GameItem relic(int slot) {
        return relic(slot, 4);
    }

    GameItem relic(int slot, int initialProps) {
        var data =
                items.stream()
                        .filter(
                                x ->
                                        x.getItemType() == ItemType.ITEM_RELIQUARY
                                                && x.getEquipType().getValue() == slot
                                                && x.getAppendPropNum() == initialProps)
                        .findFirst()
                        .orElseThrow();
        var item = stock(data.getId(), 1);
        item.setLevel(21);
        var props = List.of(6, 9, 20, 22);
        var main =
                mains.stream()
                        .filter(
                                x ->
                                        x.getPropDepotId() == data.getMainPropDepotId()
                                                && x.getWeight() > 0
                                                && !props.contains(x.getFightProp().getId()))
                        .findFirst()
                        .orElseThrow();
        item.setMainPropId(main.getId());
        item.getAppendPropIdList().clear();
        for (int prop : props) {
            var affix =
                    affixes.stream()
                            .filter(
                                    x ->
                                            x.getDepotId() == data.getAppendPropDepotId()
                                                    && x.getFightProp().getId() == prop
                                                    && x.getWeight() > 0
                                                    && x.getUpgradeWeight() > 0)
                            .findFirst()
                            .orElseThrow();
            item.getAppendPropIdList().add(affix.getId());
        }
        for (int i = 0; i < initialProps + 1; i++)
            item.getAppendPropIdList().add(item.getAppendPropIdList().get(0));
        store.save(item);
        return item;
    }

    GameItem definedRelic() {
        var item = relic(1);
        var original = List.copyOf(item.getAppendPropIdList());
        item.setLevel(1);
        item.getAppendPropIdList().subList(4, item.getAppendPropIdList().size()).clear();
        item.markAsDefinedReliquary(List.of(original.get(2), original.get(3)), 2);
        item.setLevel(21);
        item.getAppendPropIdList().clear();
        item.getAppendPropIdList().addAll(original);
        store.save(item);
        return item;
    }

    ReliquaryDustRsp roll(long guid, List<Integer> props) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryDustReq,
                PacketHead.newBuilder().setClientSequenceId(91).build().toByteArray(),
                ReliquaryDustReq.newBuilder()
                        .setReliquaryGuid(guid)
                        .addAllAppendPropTypeList(props)
                        .build()
                        .toByteArray());
        var replies =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryDustRsp)
                        .toList();
        assertEquals(1, replies.size(), "Reshape must respond to the actual request");
        assertEquals(91, PacketHead.parseFrom(replies.get(0).getHeader()).getClientSequenceId());
        return ReliquaryDustRsp.parseFrom(replies.get(0).getData());
    }

    ReliquaryDustRsp roll(GameItem item) throws Exception {
        return roll(item.getGuid(), List.of(20, 22));
    }

    ReliquaryDustSelectRsp choose(GameItem item, boolean adopt) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryDustSelectReq,
                PacketHead.newBuilder().setClientSequenceId(92).build().toByteArray(),
                ReliquaryDustSelectReq.newBuilder()
                        .setReliquaryGuid(item.getGuid())
                        .setIsNew(adopt)
                        .build()
                        .toByteArray());
        var replies =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryDustSelectRsp)
                        .toList();
        assertEquals(1, replies.size());
        assertEquals(92, PacketHead.parseFrom(replies.get(0).getHeader()).getClientSequenceId());
        return ReliquaryDustSelectRsp.parseFrom(replies.get(0).getData());
    }

    ReliquaryOfferCompanionNotify dustState() throws Exception {
        return ReliquaryOfferCompanionNotify.parseFrom(
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferCompanionNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    ReliquaryOfferCandidate candidate(GameItem item) throws Exception {
        return dustState().getCandidateListList().stream()
                .filter(x -> x.getReliquaryGuid() == item.getGuid())
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void createsPaidCandidateAtTheResourceSlotCostWithoutReplacingTheArtifact(int slot)
            throws Exception {
        var item = relic(slot);
        var old = List.copyOf(item.getAppendPropIdList());
        assertEquals(0, roll(item).getRetcode());
        assertEquals(old, item.getAppendPropIdList());
        assertEquals(100 - (slot <= 2 ? 1 : 2), actual.getInventory().getItemCountById(105006));
        var result = candidate(item);
        assertEquals(List.of(20, 22), result.getAppendPropTypeListList());
        assertEquals(old.size(), result.getAppendPropIdListCount());
        assertEquals(
                Set.of(6, 9, 20, 22),
                result.getAppendPropIdListList().stream()
                        .map(x -> GameData.getReliquaryAffixDataMap().get(x).getFightProp().getId())
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(
                result.getAppendPropIdListList().subList(4, old.size()).stream()
                                .filter(
                                        x ->
                                                List.of(20, 22)
                                                        .contains(
                                                                GameData.getReliquaryAffixDataMap()
                                                                        .get(x)
                                                                        .getFightProp()
                                                                        .getId()))
                                .count()
                        >= 2);
        int notice = -1, reply = -1;
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i).getOpcode() == PacketOpcodes.ReliquaryOfferCompanionNotify)
                notice = i;
            if (packets.get(i).getOpcode() == PacketOpcodes.ReliquaryDustRsp) reply = i;
        }
        assertTrue(
                notice >= 0 && notice < reply,
                "Equipment page reads the candidate when handling success");
    }

    @Test
    void repeatRequestRepublishesExactlyTheSameCandidateWithoutAnotherPayment() throws Exception {
        var item = relic(1);
        assertEquals(0, roll(item).getRetcode());
        var first = candidate(item);
        assertEquals(0, roll(item).getRetcode());
        assertEquals(first, candidate(item));
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        assertEquals(1, dustState().getProgress());
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "duplicate", "unknown", "absent", "affix"})
    void invalidChoicesDoNotInventSelectionsOrPay(String kind) throws Exception {
        var item = relic(1);
        var selected =
                switch (kind) {
                    case "empty" -> List.<Integer>of();
                    case "duplicate" -> List.of(20, 20);
                    case "unknown" -> List.of(20, -1);
                    case "absent" -> List.of(20, 23);
                    default -> List.of(
                            item.getAppendPropIdList().get(0), item.getAppendPropIdList().get(1));
                };
        assertNotEquals(0, roll(item.getGuid(), selected).getRetcode());
        assertEquals(100, actual.getInventory().getItemCountById(105006));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void explicitChoiceAppliesOnlyTheSelectedResultAndNeverRefundsDust(boolean adopt)
            throws Exception {
        var item = relic(1);
        var old = List.copyOf(item.getAppendPropIdList());
        assertEquals(0, roll(item).getRetcode());
        var rolled = candidate(item).getAppendPropIdListList();
        assertEquals(0, choose(item, adopt).getRetcode());
        assertEquals(adopt ? rolled : old, item.getAppendPropIdList());
        assertTrue(dustState().getCandidateListList().isEmpty());
        assertEquals(1, dustState().getProgress());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        var id = item.getObjectId();
        reload();
        assertEquals(
                adopt ? rolled : old,
                artifacts().stream()
                        .filter(x -> id.equals(x.getObjectId()))
                        .findFirst()
                        .orElseThrow()
                        .getAppendPropIdList());
    }

    @Test
    void multipleArtifactsKeepIndependentCandidates() throws Exception {
        var first = relic(1);
        var second = relic(2);
        assertEquals(0, roll(first).getRetcode());
        var one = candidate(first);
        assertEquals(0, roll(second).getRetcode());
        assertEquals(2, dustState().getCandidateListCount());
        assertEquals(one, candidate(first));
        assertEquals(0, choose(second, false).getRetcode());
        assertEquals(List.of(one), dustState().getCandidateListList());
        assertEquals(98, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void loginRebindsPersistedCandidatesToCurrentInventoryGuidsWithoutRequiringTheCraftingGadget()
            throws Exception {
        actual.getInventory().removeItem(actual.getInventory().getItemById(220109), 1);
        var item = relic(1);
        var id = item.getObjectId();
        long oldGuid = item.getGuid();
        assertEquals(0, roll(item).getRetcode());
        var planned = candidate(item).getAppendPropIdListList();
        reload();
        // Loading a smaller inventory may reuse an old GUID for a different item. Move the
        // allocator forward before this fixture deliberately assigns entirely new GUIDs.
        for (int i = 0; i < 10; i++) actual.getNextGameGuid();
        for (var entry : new ArrayList<>(actual.getInventory().getItems().values())) {
            actual.getInventory().getItems().remove(entry.getGuid());
            var tab = actual.getInventory().getInventoryTab(entry.getItemType());
            tab.onRemoveItem(entry);
            entry.setOwner(actual);
            actual.getInventory().getItems().put(entry.getGuid(), entry);
            tab.onAddItem(entry);
        }
        item =
                artifacts().stream()
                        .filter(x -> id.equals(x.getObjectId()))
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(oldGuid, item.getGuid());
        assertNull(actual.getInventory().getItemByGuid(oldGuid));
        assertFalse(item.isDestroyable());
        packets.clear();
        actual.getArtifactTransmuter().onLogin();
        assertEquals(planned, candidate(item).getAppendPropIdListList());
        assertEquals(1, dustState().getProgress());
        assertEquals(0, choose(item, true).getRetcode());
        assertEquals(planned, item.getAppendPropIdList());
    }

    @Test
    void anUnrelatedGuidCannotAdoptOrDiscardAnotherArtifactsCandidate() throws Exception {
        var item = relic(1);
        var other = relic(2);
        assertEquals(0, roll(item).getRetcode());
        var first = candidate(item);
        assertNotEquals(0, choose(other, true).getRetcode());
        actual.getArtifactTransmuter().onLogin();
        assertEquals(first, candidate(item));
        assertEquals(0, choose(item, true).getRetcode());
    }

    @Test
    void pendingArtifactsCannotBeConsumedByOtherInventoryOperations() throws Exception {
        var item = relic(1);
        assertEquals(0, roll(item).getRetcode());
        assertFalse(
                item.isDestroyable(),
                "Enhancement, strongbox and destroy checks must see the held candidate");
        assertFalse(actual.getInventory().removeItem(item, 1));
        assertSame(item, actual.getInventory().getItemByGuid(item.getGuid()));
        var req =
                ReliquaryOfferExtractReq.newBuilder()
                        .setScheduleId(700)
                        .addReliquaryGuidList(item.getGuid())
                        .build();
        assertNotEquals(0, actual.getArtifactTransmuter().extract(req).getRetcode());
        assertNull(
                actual.getArtifactTransmuterData().getPending(),
                "Reject held artifacts before reserving an extraction");
        assertEquals(0, choose(item, false).getRetcode());
        assertTrue(item.isDestroyable());
        assertTrue(actual.getInventory().removeItem(item, 1));
    }

    @Test
    void failedPaymentConfirmationBlocksReplayAfterReload() throws Exception {
        var item = relic(1);
        var id = item.getObjectId();
        replaceField(
                Player.class,
                actual,
                "artifactTransmuter",
                new ArtifactTransmuter(
                        actual,
                        () -> System.currentTimeMillis() / 1000L,
                        () -> DatabaseHelper.saveGameSync(actual),
                        x -> {
                            DatabaseHelper.saveGameSync(x);
                            throw new IllegalStateException(
                                    "injected dust payment confirmation failure");
                        }));
        assertNotEquals(0, roll(item).getRetcode());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        reload();
        item =
                artifacts().stream()
                        .filter(x -> id.equals(x.getObjectId()))
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, roll(item).getRetcode());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
        actual.getArtifactTransmuter().onLogin();
        assertTrue(dustState().getCandidateListList().isEmpty());
    }

    @Test
    void sixthAndEighteenthDustCrossingsUseTheResourceGuarantees() throws Exception {
        for (int i = 1; i <= 18; i++) {
            var item = relic(1);
            assertEquals(0, roll(item).getRetcode());
            var rolls = candidate(item).getAppendPropIdListList();
            long selected =
                    rolls.subList(4, rolls.size()).stream()
                            .filter(
                                    x ->
                                            List.of(20, 22)
                                                    .contains(
                                                            GameData.getReliquaryAffixDataMap()
                                                                    .get(x)
                                                                    .getFightProp()
                                                                    .getId()))
                            .count();
            assertTrue(selected >= (i == 18 ? 4 : i % 6 == 0 ? 3 : 2));
            assertEquals(i, dustState().getProgress());
        }
    }

    @Test
    void threeInitialPropsKeepEightTotalRollsWithoutInventingAnExtraUpgrade() throws Exception {
        var item = relic(3, 3);
        assertEquals(8, item.getAppendPropIdList().size());
        assertEquals(0, roll(item).getRetcode());
        assertEquals(8, candidate(item).getAppendPropIdListCount());
        assertEquals(98, actual.getInventory().getItemCountById(105006));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "lowLevel",
                "foreignOwner",
                "unknownAffix",
                "badHistory",
                "invalidMain",
                "material",
                "missing"
            })
    void ineligibleOrMissingArtifactsNeverConsumeDust(String kind) throws Exception {
        var item = relic(1);
        switch (kind) {
            case "lowLevel" -> item.setLevel(20);
            case "foreignOwner" -> replaceField(GameItem.class, item, "ownerId", 20002);
            case "unknownAffix" -> item.getAppendPropIdList().set(0, -1);
            case "badHistory" -> item.getAppendPropIdList().clear();
            case "invalidMain" -> item.setMainPropId(-1);
        }
        long guid =
                kind.equals("material")
                        ? actual.getInventory().getItemById(105006).getGuid()
                        : kind.equals("missing") ? 0 : item.getGuid();
        assertNotEquals(0, roll(guid, List.of(20, 22)).getRetcode());
        assertEquals(100, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void insufficientDustDoesNotReserveOrConsumeTheArtifact() throws Exception {
        var item = relic(3);
        var material = actual.getInventory().getItemById(105006);
        material.setCount(1);
        store.save(material);
        assertNotEquals(0, roll(item).getRetcode());
        assertEquals(1, material.getCount());
        assertTrue(item.isDestroyable());
        actual.getArtifactTransmuter().onLogin();
        assertTrue(dustState().getCandidateListList().isEmpty());
        assertEquals(0, dustState().getProgress());
    }

    @Test
    void failedAdoptionConfirmationSurvivesReloadAndCannotChangeToTheOppositeChoice()
            throws Exception {
        var item = relic(1);
        var id = item.getObjectId();
        assertEquals(0, roll(item).getRetcode());
        var planned = candidate(item).getAppendPropIdListList();
        replaceField(
                Player.class,
                actual,
                "artifactTransmuter",
                new ArtifactTransmuter(
                        actual,
                        () -> System.currentTimeMillis() / 1000L,
                        () -> DatabaseHelper.saveGameSync(actual),
                        x -> {
                            DatabaseHelper.saveGameSync(x);
                            throw new IllegalStateException(
                                    "injected adoption confirmation failure");
                        }));
        assertNotEquals(0, choose(item, true).getRetcode());
        assertEquals(planned, item.getAppendPropIdList());
        reload();
        item =
                artifacts().stream()
                        .filter(x -> id.equals(x.getObjectId()))
                        .findFirst()
                        .orElseThrow();
        assertFalse(item.isDestroyable());
        assertNotEquals(0, choose(item, false).getRetcode());
        assertEquals(0, choose(item, true).getRetcode());
        assertEquals(planned, item.getAppendPropIdList());
        assertTrue(item.isDestroyable());
        assertEquals(99, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void concurrentRequestsShareTheLastDustWithoutASecondCandidate() throws Exception {
        var one = relic(1);
        var two = relic(2);
        var material = actual.getInventory().getItemById(105006);
        material.setCount(1);
        store.save(material);
        packets.clear();
        var gate = new java.util.concurrent.CyclicBarrier(2);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (var item : List.of(one, two))
                tasks.add(
                        executor.submit(
                                () -> {
                                    gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                    dispatcher.handle(
                                            actual.getSession(),
                                            PacketOpcodes.ReliquaryDustReq,
                                            PacketHead.newBuilder()
                                                    .setClientSequenceId(item == one ? 101 : 102)
                                                    .build()
                                                    .toByteArray(),
                                            ReliquaryDustReq.newBuilder()
                                                    .setReliquaryGuid(item.getGuid())
                                                    .addAllAppendPropTypeList(List.of(20, 22))
                                                    .build()
                                                    .toByteArray());
                                    return null;
                                }));
            for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        var replies = new ArrayList<Integer>();
        for (var packet : packets)
            if (packet.getOpcode() == PacketOpcodes.ReliquaryDustRsp)
                replies.add(ReliquaryDustRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(2, replies.size());
        assertEquals(1, replies.stream().filter(x -> x == 0).count());
        assertEquals(0, actual.getInventory().getItemCountById(105006));
        assertEquals(1, dustState().getCandidateListCount());
        assertEquals(1, dustState().getProgress());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lockedEquippedArtifactsRetainTheirFlagsAndRefreshTheWearingAvatar(boolean adopt)
            throws Exception {
        var item = relic(1);
        var refreshes = new java.util.concurrent.atomic.AtomicInteger();
        var avatar =
                new emu.grasscutter.game.avatar.Avatar() {
                    @Override
                    public void recalcStats() {
                        refreshes.incrementAndGet();
                    }
                };
        actual.getAvatars().getAvatars().put(10000002, avatar);
        item.setEquipCharacter(10000002);
        item.setLocked(true);
        assertEquals(0, roll(item).getRetcode());
        assertEquals(0, choose(item, adopt).getRetcode());
        assertEquals(10000002, item.getEquipCharacter());
        assertTrue(item.isLocked());
        assertEquals(1, refreshes.get());
    }

    @Test
    void malformedRequestStillAcknowledgesItsSequenceWithoutCreatingACandidate() throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryDustReq,
                PacketHead.newBuilder().setClientSequenceId(57).build().toByteArray(),
                new byte[] {0x30, (byte) 0x80});
        var response =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryDustRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(57, PacketHead.parseFrom(response.getHeader()).getClientSequenceId());
        assertNotEquals(0, ReliquaryDustRsp.parseFrom(response.getData()).getRetcode());
        assertEquals(100, actual.getInventory().getItemCountById(105006));
        actual.getArtifactTransmuter().onLogin();
        assertTrue(dustState().getCandidateListList().isEmpty());
    }

    @Test
    void
            definedArtifactUsesItsOriginalChosenPropertiesWithoutInventingDefaultsForOrdinaryArtifacts()
                    throws Exception {
        var item = definedRelic();
        assertEquals(0, roll(item.getGuid(), List.of()).getRetcode());
        assertEquals(List.of(20, 22), candidate(item).getAppendPropTypeListList());
        var candidate = candidate(item);
        assertEquals(0, roll(item.getGuid(), List.of()).getRetcode());
        assertEquals(candidate, candidate(item));
        assertEquals(99, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void definedPropertiesCannotBeReplacedByAnotherPairFromTheSameArtifact() throws Exception {
        var item = definedRelic();
        assertNotEquals(0, roll(item.getGuid(), List.of(6, 9)).getRetcode());
        assertEquals(100, actual.getInventory().getItemCountById(105006));
    }

    @Test
    void definedPropertySelectionSurvivesAdoptionAndANewReshape() throws Exception {
        var item = definedRelic();
        assertEquals(0, roll(item).getRetcode());
        assertEquals(0, choose(item, true).getRetcode());
        assertEquals(0, roll(item.getGuid(), List.of()).getRetcode());
        assertEquals(List.of(20, 22), candidate(item).getAppendPropTypeListList());
        assertEquals(98, actual.getInventory().getItemCountById(105006));
    }
}

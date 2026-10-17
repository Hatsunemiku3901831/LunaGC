package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.reliquary.ArtifactTransmuter;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryOfferDataNotifyOuterClass.ReliquaryOfferDataNotify;
import emu.grasscutter.net.proto.ReliquaryOfferExtractReqOuterClass.ReliquaryOfferExtractReq;
import emu.grasscutter.net.proto.ReliquaryOfferExtractRspOuterClass.ReliquaryOfferExtractRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Actual request routing, resource costs, inventory and isolated Mongo writes. */
class ArtifactTransmuterExtractTest extends QuestTestFixture {
    static GameServerPacketHandler dispatcher;
    static List<ItemData> materials;
    final List<Runnable> restores = new ArrayList<>();
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();
    Player actual;

    @BeforeAll
    static void resources() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        materials =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(
                                x ->
                                        Set.of(220109, 105002, 105003, 105004, 105005)
                                                .contains(x.getId()))
                        .toList();
        materials.forEach(ItemData::onLoad);
        assertEquals(5, materials.size());
    }

    @BeforeEach
    void transmuter() throws Exception {
        for (var data : materials) put(data);
        put(
                JsonUtils.decode(
                        "{id:75543,itemType:'ITEM_RELIQUARY',rankLevel:5,maxLevel:21}",
                        ItemData.class));
        put(
                JsonUtils.decode(
                        "{id:75443,itemType:'ITEM_RELIQUARY',rankLevel:4,maxLevel:17}",
                        ItemData.class));
        store.getMapper().map(new Class<?>[] {Player.class, GameItem.class});
        actual = new Player();
        actual.setUid(10002);
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        packets.add(packet);
                    }
                };
        session.setPlayer(actual);
        session.setState(GameSession.SessionState.ACTIVE);
        actual.setSession(session);
        actual.setWorld(player.getWorld());
        actual.setScene(player.getScene());
        replaceField(Player.class, actual, "teamManager", new TeamManager(actual));
        actual.getTeamManager().setEntity(new EntityTeam(actual));
        stock(220109, 1);
    }

    @AfterEach
    void restoreResources() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
    }

    void put(ItemData data) {
        data.onLoad();
        var old = GameData.getItemDataMap().put(data.getId(), data);
        restores.add(
                () -> {
                    if (old == null) GameData.getItemDataMap().remove(data.getId());
                    else GameData.getItemDataMap().put(data.getId(), old);
                });
    }

    GameItem stock(int id, int count) {
        var item = new GameItem();
        item.setItemData(GameData.getItemDataMap().get(id));
        item.setItemId(id);
        item.setCount(count);
        item.setLevel(1);
        item.setOwner(actual);
        actual.getInventory().getItems().put(item.getGuid(), item);
        actual.getInventory().getInventoryTab(item.getItemData().getItemType()).onAddItem(item);
        DatabaseHelper.saveGameSync(item);
        return item;
    }

    ReliquaryOfferExtractReq.Builder req() {
        return ReliquaryOfferExtractReq.newBuilder().setScheduleId(700);
    }

    ItemParam material(int id, int count) {
        return ItemParam.newBuilder().setItemId(id).setCount(count).build();
    }

    ReliquaryOfferExtractRsp extract(ReliquaryOfferExtractReq.Builder req) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryOfferExtractReq,
                PacketHead.newBuilder().setClientSequenceId(31).build().toByteArray(),
                req.build().toByteArray());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var replies =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferExtractRsp)
                        .toList();
        assertEquals(1, replies.size(), "Extraction must receive a real response");
        assertEquals(31, PacketHead.parseFrom(replies.get(0).getHeader()).getClientSequenceId());
        return ReliquaryOfferExtractRsp.parseFrom(replies.get(0).getData());
    }

    ReliquaryOfferDataNotify state() throws Exception {
        var packet =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ReliquaryOfferDataNotify.parseFrom(packet.getData());
    }

    Player readPlayer() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var value = store.find(Player.class).field("id").equal(10002).first();
        assertNotNull(value);
        return value;
    }

    @Test
    void halfPointMaterialIsConsumedExactlyAndSurvivesMongoReload() throws Exception {
        stock(105002, 3);
        var rsp = extract(req().addItemList(material(105002, 3)));
        assertEquals(0, rsp.getRetcode());
        assertEquals(700, rsp.getScheduleId());
        assertEquals(List.of(material(105002, 3)), rsp.getItemListList());
        assertEquals(0, actual.getInventory().getItemCountById(105002));
        assertEquals(1, state().getProgress());
        assertEquals(0.5f, state().getFractionalProgress());
        assertEquals(0, state().getExtractedCount());
        var saved = readPlayer();
        var data = Player.class.getMethod("getArtifactTransmuterData").invoke(saved);
        assertEquals(1500L, data.getClass().getMethod("getProgressUnits").invoke(data));
        assertNull(data.getClass().getMethod("getPending").invoke(data));
    }

    @Test
    void fullProgressGrantsOneElixirAndBlocksAnotherExtractionThisCycle() throws Exception {
        stock(105003, 100);
        assertEquals(0, extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(1, actual.getInventory().getItemCountById(105005));
        assertEquals(0, state().getProgress());
        assertEquals(1, state().getExtractedCount());
        assertEquals(
                Retcode.RET_RELIQUARY_OFFER_VERSION_TIMES_LIMIT_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(1, actual.getInventory().getItemCountById(105005));
        assertEquals(
                1,
                store.find(GameItem.class)
                        .field("ownerId")
                        .equal(10002)
                        .field("itemId")
                        .equal(105005)
                        .first()
                        .getCount());
        readPlayer();
    }

    @Test
    void enhancedArtifactsUseTheResourceCurveAndEchoTheirGuids() throws Exception {
        var low = stock(75543, 1);
        low.setLevel(5); // Internal level 5 is displayed +4: four progress.
        var high = stock(75543, 1);
        high.setLevel(21); // Displayed +20: fifty progress.
        var rsp =
                extract(
                        req().addReliquaryGuidList(low.getGuid())
                                .addReliquaryGuidList(high.getGuid()));
        assertEquals(0, rsp.getRetcode());
        assertEquals(List.of(low.getGuid(), high.getGuid()), rsp.getReliquaryGuidListList());
        assertNull(actual.getInventory().getItemByGuid(low.getGuid()));
        assertNull(actual.getInventory().getItemByGuid(high.getGuid()));
        assertEquals(54, state().getProgress());
        assertEquals(
                0,
                store.find(GameItem.class)
                        .field("ownerId")
                        .equal(10002)
                        .field("itemId")
                        .equal(75543)
                        .filter(dev.morphia.query.experimental.filters.Filters.ne("deleted", true))
                        .count());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "locked",
                "equipped",
                "lowLevel",
                "wrongRank",
                "foreignOwner",
                "duplicate",
                "missing"
            })
    void invalidArtifactRejectsTheWholeBatchBeforeAnyMaterialIsSpent(String failure)
            throws Exception {
        stock(105003, 2);
        var artifact = stock(failure.equals("wrongRank") ? 75443 : 75543, 1);
        artifact.setLevel(failure.equals("lowLevel") ? 4 : 5);
        if (failure.equals("locked")) artifact.setLocked(true);
        if (failure.equals("equipped")) artifact.setEquipCharacter(10000001);
        if (failure.equals("foreignOwner"))
            replaceField(GameItem.class, artifact, "ownerId", player.getUid());
        var request =
                req().addItemList(material(105003, 2))
                        .addReliquaryGuidList(
                                failure.equals("missing") ? 999L : artifact.getGuid());
        if (failure.equals("duplicate")) request.addReliquaryGuidList(artifact.getGuid());
        assertNotEquals(0, extract(request).getRetcode());
        assertEquals(2, actual.getInventory().getItemCountById(105003));
        assertEquals(1, artifact.getCount());
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 3})
    void invalidOrInsufficientMaterialQuantityDoesNotPartiallyConsume(int count) throws Exception {
        stock(105003, 2);
        assertNotEquals(0, extract(req().addItemList(material(105003, count))).getRetcode());
        assertEquals(2, actual.getInventory().getItemCountById(105003));
    }

    @Test
    void unknownMaterialAndDuplicateMaterialRowsAreRejected() throws Exception {
        stock(105003, 10);
        stock(105004, 10);
        assertNotEquals(0, extract(req().addItemList(material(105004, 1))).getRetcode());
        assertNotEquals(
                0,
                extract(req().addItemList(material(105003, 1)).addItemList(material(105003, 1)))
                        .getRetcode());
        assertEquals(10, actual.getInventory().getItemCountById(105003));
        assertEquals(10, actual.getInventory().getItemCountById(105004));
    }

    @Test
    void overflowIsRejectedInsteadOfDestroyingExtraMaterials() throws Exception {
        stock(105003, 51);
        assertEquals(
                Retcode.RET_RELIQUARY_OFFER_PROGRESS_OVER_LIMIT_VALUE,
                extract(req().addItemList(material(105003, 51))).getRetcode());
        assertEquals(51, actual.getInventory().getItemCountById(105003));
    }

    @Test
    void staleScheduleAndMissingGadgetCannotExtract() throws Exception {
        stock(105003, 50);
        assertEquals(
                Retcode.RET_RELIQUARY_OFFER_PURCHASE_VERSION_IDX_ERROR_VALUE,
                extract(req().setScheduleId(1).addItemList(material(105003, 50))).getRetcode());
        var gadget = actual.getInventory().getItemById(220109);
        actual.getInventory().removeItem(gadget, 1);
        assertNotEquals(0, extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(0, actual.getInventory().getItemCountById(220109));
    }

    @Test
    void emptySubmissionCannotGainFallbackProgress() throws Exception {
        assertNotEquals(0, extract(req()).getRetcode());
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void openingOwnedGadgetFromBagIsReusableAndSendsState() {
        var gadget = actual.getInventory().getItemById(220109);
        var result =
                new InventorySystem(player.server)
                        .useItem(actual, 0, gadget.getGuid(), 1, 0, false);
        assertSame(gadget, result);
        assertEquals(1, gadget.getCount());
        assertTrue(
                packets.stream()
                        .anyMatch(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferDataNotify));
    }

    void restoreActual() throws Exception {
        var session = actual.getSession();
        actual = readPlayer();
        actual.setSession(session);
        session.setPlayer(actual);
        actual.setWorld(player.getWorld());
        actual.setScene(player.getScene());
        replaceField(Player.class, actual, "teamManager", new TeamManager(actual));
        actual.getTeamManager().setEntity(new EntityTeam(actual));
        for (var item : DatabaseHelper.getInventoryItems(actual)) {
            item.setOwner(actual);
            actual.getInventory().getItems().put(item.getGuid(), item);
            actual.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        }
    }

    @Test
    void fractionalProgressContinuesAfterReloadWithoutResettingTheCycle() throws Exception {
        stock(105002, 3);
        assertEquals(0, extract(req().addItemList(material(105002, 1))).getRetcode());
        int end = state().getCycleEndTime();
        restoreActual();
        assertEquals(0, extract(req().addItemList(material(105002, 2))).getRetcode());
        assertEquals(1, state().getProgress());
        assertEquals(0.5f, state().getFractionalProgress());
        assertEquals(end, state().getCycleEndTime());
        assertEquals(0, actual.getInventory().getItemCountById(105002));
    }

    @Test
    void extractionLimitSurvivesReloadAndLoginResendsIt() throws Exception {
        stock(105003, 100);
        assertEquals(0, extract(req().addItemList(material(105003, 50))).getRetcode());
        restoreActual();
        packets.clear();
        actual.getArtifactTransmuter().onLogin();
        assertEquals(1, state().getExtractedCount());
        assertEquals(
                Retcode.RET_RELIQUARY_OFFER_VERSION_TIMES_LIMIT_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(1, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void cycleResetsOnlyAtTheExactBoundaryAndPreservesPartialProgress() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong(1000);
        var manager =
                new ArtifactTransmuter(
                        actual,
                        clock::get,
                        () -> DatabaseHelper.saveGameSync(actual),
                        DatabaseHelper::saveGameSync);
        replaceField(Player.class, actual, "artifactTransmuter", manager);
        stock(105003, 100);
        assertEquals(0, extract(req().addItemList(material(105003, 50))).getRetcode());
        long end = actual.getArtifactTransmuterData().getCycleEndTime();
        assertEquals(1000 + 3 * 86400, end);
        clock.set(end - 1);
        assertEquals(
                Retcode.RET_RELIQUARY_OFFER_VERSION_TIMES_LIMIT_VALUE,
                extract(req().addItemList(material(105003, 1))).getRetcode());
        clock.set(end);
        stock(105002, 1);
        assertEquals(0, extract(req().addItemList(material(105002, 1))).getRetcode());
        assertEquals(0, state().getExtractedCount());
        assertEquals(0.5f, state().getFractionalProgress());
        long second = actual.getArtifactTransmuterData().getCycleEndTime();
        clock.set(second);
        assertEquals(0, manager.open());
        assertEquals(0.5f, state().getFractionalProgress());
        assertEquals(second + 3 * 86400, actual.getArtifactTransmuterData().getCycleEndTime());
        assertEquals(500, readPlayer().getArtifactTransmuterData().getProgressUnits());
    }

    @Test
    void reservationAcknowledgementFailureLeavesMaterialsAndADurableBlockedReceipt()
            throws Exception {
        stock(105003, 100);
        var owner = actual;
        var manager =
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> {
                            DatabaseHelper.saveGameSync(owner);
                            var pending = owner.getArtifactTransmuterData().getPending();
                            if (pending != null && pending.getState() == 1)
                                throw new IllegalStateException(
                                        "Injected reservation acknowledgement failure");
                        },
                        DatabaseHelper::saveGameSync);
        replaceField(Player.class, owner, "artifactTransmuter", manager);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        var receipt = readPlayer().getArtifactTransmuterData().getPending();
        assertNotNull(receipt);
        assertEquals(1, receipt.getState());
        assertEquals(50, receipt.getCosts().get(0).getConsumed());
        assertNotNull(receipt.getCosts().get(0).getObjectId());
        restoreActual();
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(100, actual.getInventory().getItemCountById(105003));
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void paymentAcknowledgementFailureCannotBeRetriedAfterReload() throws Exception {
        stock(105003, 100);
        var owner = actual;
        var manager =
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> DatabaseHelper.saveGameSync(owner),
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (item.getItemId() == 105003)
                                throw new IllegalStateException(
                                        "Injected payment acknowledgement failure");
                        });
        replaceField(Player.class, owner, "artifactTransmuter", manager);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertNotNull(readPlayer().getArtifactTransmuterData().getPending());
        restoreActual();
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void rewardAcknowledgementFailureCannotDuplicateTheAlreadyPersistedElixir() throws Exception {
        stock(105003, 100);
        var owner = actual;
        var manager =
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> DatabaseHelper.saveGameSync(owner),
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (item.getItemId() == 105005)
                                throw new IllegalStateException(
                                        "Injected reward acknowledgement failure");
                        });
        replaceField(Player.class, owner, "artifactTransmuter", manager);
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        var receipt = readPlayer().getArtifactTransmuterData().getPending();
        assertNotNull(receipt);
        assertEquals(2, receipt.getState());
        assertEquals(1, receipt.getRewardCount());
        restoreActual();
        assertEquals(1, actual.getInventory().getItemCountById(105005));
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(1, actual.getInventory().getItemCountById(105005));
        assertEquals(50, actual.getInventory().getItemCountById(105003));
    }

    @Test
    void competingFullExtractionsOnlySpendAndAwardOnce() throws Exception {
        stock(105003, 100);
        var manager = actual.getArtifactTransmuter();
        var request = req().addItemList(material(105003, 50)).build();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return manager.extract(request).getRetcode();
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return manager.extract(request).getRetcode();
                            });
            start.countDown();
            var codes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(codes, 0));
            assertEquals(
                    1,
                    Collections.frequency(
                            codes, Retcode.RET_RELIQUARY_OFFER_VERSION_TIMES_LIMIT_VALUE));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(1, actual.getInventory().getItemCountById(105005));
        assertEquals(1, readPlayer().getArtifactTransmuterData().getExtractedCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedHeaderOrPayloadIsRejectedBeforePayment(boolean header) throws Exception {
        stock(105003, 50);
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryOfferExtractReq,
                header ? new byte[] {(byte) 0x80} : new byte[0],
                header
                        ? req().addItemList(material(105003, 50)).build().toByteArray()
                        : new byte[] {0x0a, (byte) 0x80});
        var packet =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferExtractRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, ReliquaryOfferExtractRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(50, actual.getInventory().getItemCountById(105003));
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void aSlotFreedByTheExactPaymentCanHoldTheElixir() throws Exception {
        stock(105003, 50);
        var tab = actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL);
        replaceField(MaterialInventoryTab.class, tab, "maxCapacity", tab.getSize());
        assertEquals(0, extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(1, actual.getInventory().getItemCountById(105005));
        assertEquals(0, actual.getInventory().getItemCountById(105003));
    }

    @Test
    void fullBagWithoutAFreedSlotRejectsBeforeAnyCost() throws Exception {
        stock(105003, 100);
        var tab = actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL);
        replaceField(MaterialInventoryTab.class, tab, "maxCapacity", tab.getSize());
        assertEquals(
                Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                extract(req().addItemList(material(105003, 50))).getRetcode());
        assertEquals(100, actual.getInventory().getItemCountById(105003));
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void loginSynchronizesAnEmptyReshapeSnapshotAndKeepsExtractionProgressSeparate()
            throws Exception {
        actual.getArtifactTransmuterData().setProgressUnits(1500);
        packets.clear();
        actual.getArtifactTransmuter().onLogin();
        var snapshots =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferCompanionNotify)
                        .toList();
        assertEquals(1, snapshots.size());
        assertArrayEquals(new byte[0], snapshots.get(0).getData());
        assertEquals(1, state().getProgress());
        assertEquals(0.5f, state().getFractionalProgress());
        assertEquals(1, actual.getInventory().getItemCountById(220109));
        assertEquals(0, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void loginWithoutTheGadgetDoesNotSendReshapeStateOrGrantTheGadget() {
        actual.getInventory().removeItem(actual.getInventory().getItemById(220109), 1);
        packets.clear();
        actual.getArtifactTransmuter().onLogin();
        assertTrue(
                packets.stream()
                        .noneMatch(
                                x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferCompanionNotify));
        assertEquals(0, actual.getInventory().getItemCountById(220109));
    }
}

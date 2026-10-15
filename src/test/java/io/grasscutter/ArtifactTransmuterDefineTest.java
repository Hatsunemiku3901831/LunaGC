package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.reliquary.ArtifactTransmuter;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.ReliquaryOfferDataNotifyOuterClass.ReliquaryOfferDataNotify;
import emu.grasscutter.net.proto.ReliquaryOfferDefineReqOuterClass.ReliquaryOfferDefineReq;
import emu.grasscutter.net.proto.ReliquaryOfferDefineRspOuterClass.ReliquaryOfferDefineRsp;
import emu.grasscutter.net.proto.ReliquaryOfferExtractReqOuterClass.ReliquaryOfferExtractReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Actual definition requests and artifacts from the current purchase table, with isolated Mongo.
 */
class ArtifactTransmuterDefineTest extends QuestTestFixture {
    static GameServerPacketHandler dispatcher;
    static List<ItemData> items;
    static List<ReliquaryMainPropData> mains;
    static List<ReliquaryAffixData> affixes;
    static JsonArray offers;
    final List<Runnable> restores = new ArrayList<>();
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();
    Player actual;

    @BeforeAll
    static void resources() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        var root = Path.of("resources/ExcelBinOutput");
        offers =
                JsonParser.parseString(
                                Files.readString(
                                        root.resolve("ReliquaryOfferPurchaseExcelConfigData.json")))
                        .getAsJsonArray();
        var ids = new HashSet<Integer>();
        for (var row : offers)
            for (var field :
                    List.of("flowerIds", "featherIds", "timepieceIds", "gobletIds", "circletIds"))
                row.getAsJsonObject().getAsJsonArray(field).forEach(x -> ids.add(x.getAsInt()));
        items =
                new ArrayList<>(
                        JsonUtils.loadToList(
                                        root.resolve("ReliquaryExcelConfigData.json"),
                                        ItemData.class)
                                .stream()
                                .filter(x -> ids.contains(x.getId()))
                                .toList());
        items.addAll(
                JsonUtils.loadToList(root.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(
                                x ->
                                        x.getId() == 220109
                                                || x.getId() == 105005
                                                || x.getId() == 105003)
                        .toList());
        items.forEach(ItemData::onLoad);
        mains =
                JsonUtils.loadToList(
                        root.resolve("ReliquaryMainPropExcelConfigData.json"),
                        ReliquaryMainPropData.class);
        affixes =
                JsonUtils.loadToList(
                        root.resolve("ReliquaryAffixExcelConfigData.json"),
                        ReliquaryAffixData.class);
        assertEquals(46, offers.size());
        assertEquals(463, items.size());
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void definePlayer() throws Exception {
        items.forEach(x -> put(GameData.getItemDataMap(), x.getId(), x));
        mains.forEach(x -> put(GameData.getReliquaryMainPropDataMap(), x.getId(), x));
        affixes.forEach(x -> put(GameData.getReliquaryAffixDataMap(), x.getId(), x));
        for (var field :
                List.of("relicRandomMainPropDepot", "relicMainPropDepot", "relicAffixDepot"))
            replaceField(GameDepot.class, null, field, new Int2ObjectOpenHashMap<>());
        replaceField(
                GameDepot.class,
                null,
                "relicAffixValueTier",
                new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap());
        replaceField(
                GameDepot.class,
                null,
                "relicAffixValueTierCount",
                new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap());
        GameDepot.load();
        assertSame(
                GameData.getReliquaryOfferPurchaseDataMap(),
                GameData.getMapByResourceDef(ReliquaryOfferPurchaseData.class));
        for (var row : offers) {
            var value = gson.fromJson(row, ReliquaryOfferPurchaseData.class);
            value.onLoad();
            put(GameData.getReliquaryOfferPurchaseDataMap(), value.getId(), value);
        }
        store.getMapper().map(new Class<?>[] {Player.class, GameItem.class});
        actual = new Player();
        actual.setUid(10002);
        attach();
        stock(220109, 1);
        stock(105005, 20);
    }

    void attach() throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket p) {
                        packets.add(p);
                    }
                };
        session.setPlayer(actual);
        session.setState(GameSession.SessionState.ACTIVE);
        actual.setSession(session);
        actual.setWorld(player.getWorld());
        actual.setScene(player.getScene());
        replaceField(Player.class, actual, "teamManager", new TeamManager(actual));
        actual.getTeamManager().setEntity(new EntityTeam(actual));
    }

    GameItem stock(int id, int count) {
        var item = new GameItem(GameData.getItemDataMap().get(id), count);
        item.setOwner(actual);
        actual.getInventory().getItems().put(item.getGuid(), item);
        actual.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        store.save(item);
        return item;
    }

    @AfterEach
    void restoreDefinitions() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
    }

    ReliquaryOfferDefineReq.Builder req(int slot) {
        int main =
                switch (slot) {
                    case 1 -> 2;
                    case 2 -> 5;
                    case 3 -> 6;
                    case 4 -> 30;
                    default -> 20;
                };
        return ReliquaryOfferDefineReq.newBuilder()
                .setScheduleId(700)
                .setPurchaseId(1)
                .setEquipType(slot)
                .setMainPropType(main)
                .addAllAppendPropTypeList(slot == 5 ? List.of(6, 28) : List.of(20, 22));
    }

    ReliquaryOfferDefineRsp define(ReliquaryOfferDefineReq.Builder request) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryOfferDefineReq,
                PacketHead.newBuilder().setClientSequenceId(46).build().toByteArray(),
                request.build().toByteArray());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var replies =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferDefineRsp)
                        .toList();
        assertEquals(1, replies.size(), "Definition needs a real response");
        assertEquals(46, PacketHead.parseFrom(replies.get(0).getHeader()).getClientSequenceId());
        return ReliquaryOfferDefineRsp.parseFrom(replies.get(0).getData());
    }

    List<GameItem> artifacts() {
        return actual.getInventory().getItems().values().stream()
                .filter(x -> x.getItemType() == ItemType.ITEM_RELIQUARY)
                .toList();
    }

    ReliquaryOfferDataNotify state() throws Exception {
        return ReliquaryOfferDataNotify.parseFrom(
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    void reload() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        actual = store.find(Player.class).field("id").equal(10002).first();
        assertNotNull(actual);
        attach();
        for (var item : store.find(GameItem.class).field("ownerId").equal(10002)) {
            item.setOwner(actual);
            actual.getInventory().getItems().put(item.getGuid(), item);
            actual.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void createsTheSelectedSetSlotMainAndTwoSubstatsAtTheResourceCost(int slot) throws Exception {
        var request = req(slot);
        var rsp = define(request);
        assertEquals(0, rsp.getRetcode());
        assertEquals(1, rsp.getPurchaseId());
        assertEquals(700, rsp.getScheduleId());
        int cost = new int[] {0, 1, 1, 2, 4, 3}[slot];
        assertEquals(20 - cost, actual.getInventory().getItemCountById(105005));
        assertEquals(1, artifacts().size());
        var item = artifacts().get(0);
        assertEquals(15001, item.getItemData().getSetId());
        assertEquals(slot, item.getEquipSlot());
        assertEquals(1, item.getLevel());
        assertTrue(item.isLocked());
        assertEquals(
                request.getMainPropType(),
                GameData.getReliquaryMainPropDataMap()
                        .get(item.getMainPropId())
                        .getFightProp()
                        .getId());
        assertTrue(
                item.getAppendPropIdList().size() == 3 || item.getAppendPropIdList().size() == 4);
        var props =
                item.getAppendPropIdList().stream()
                        .map(x -> GameData.getReliquaryAffixDataMap().get(x).getFightProp().getId())
                        .toList();
        assertEquals(props.size(), new HashSet<>(props).size());
        assertFalse(props.contains(request.getMainPropType()));
        assertTrue(props.containsAll(request.getAppendPropTypeListList()));
        var proto = item.toReliquaryProto();
        assertEquals(
                false,
                proto.getField(proto.getDescriptorForType().findFieldByName("_is_relic_starred")));
        var selected =
                (List<?>)
                        proto.getField(
                                proto.getDescriptorForType()
                                        .findFieldByName("_purchased_append_prop_id_list"));
        var definite =
                (List<?>)
                        proto.getField(
                                proto.getDescriptorForType()
                                        .findFieldByName("_definite_append_prop_id_list"));
        assertEquals(2, selected.size());
        assertEquals(selected, definite);
        assertEquals(15001, state().getDefineCountList(0).getDefineId());
        assertEquals(1, state().getDefineCountList(0).getCount());
        reload();
        assertEquals(1, artifacts().size());
        assertEquals(
                selected,
                artifacts()
                        .get(0)
                        .toReliquaryProto()
                        .getField(
                                proto.getDescriptorForType()
                                        .findFieldByName("_purchased_append_prop_id_list")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missingPurchase",
                "badSlot",
                "badMain",
                "mainIsSub",
                "duplicateSub",
                "missingSub",
                "extraSub",
                "unknownSub",
                "badSchedule"
            })
    void malformedSelectionsRejectBeforePaymentAndDoNotProduceAnArtifact(String bad)
            throws Exception {
        var request = req(3);
        switch (bad) {
            case "missingPurchase" -> request.setPurchaseId(99999);
            case "badSlot" -> request.setEquipType(6);
            case "badMain" -> request.setMainPropType(20);
            case "mainIsSub" -> request.clearAppendPropTypeList()
                    .addAllAppendPropTypeList(List.of(6, 22));
            case "duplicateSub" -> request.clearAppendPropTypeList()
                    .addAllAppendPropTypeList(List.of(20, 20));
            case "missingSub" -> request.clearAppendPropTypeList().addAppendPropTypeList(20);
            case "extraSub" -> request.addAppendPropTypeList(28);
            case "unknownSub" -> request.clearAppendPropTypeList()
                    .addAllAppendPropTypeList(List.of(20, 99999));
            case "badSchedule" -> request.setScheduleId(1);
        }
        assertNotEquals(0, define(request).getRetcode());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void insufficientElixirDoesNotAutomaticallyTopUpTheCurrency() throws Exception {
        var elixir = actual.getInventory().getItemById(105005);
        elixir.setCount(1);
        store.save(elixir);
        assertEquals(Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE, define(req(4)).getRetcode());
        assertEquals(1, elixir.getCount());
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void twoDefinitionsShareTheSetLimitAcrossSlotsAndReload() throws Exception {
        assertEquals(0, define(req(1)).getRetcode());
        reload();
        assertEquals(0, define(req(2)).getRetcode());
        reload();
        assertEquals(
                Retcode.RET_RELIQUARY_PURCHASE_SUITE_TIMES_OVER_LIMIT_VALUE,
                define(req(3)).getRetcode());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertEquals(2, artifacts().size());
    }

    @Test
    void fullArtifactBagRejectsBeforeConsumingTheElixir() throws Exception {
        var tab = actual.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY);
        replaceField(EquipInventoryTab.class, tab, "maxCapacity", 0);
        assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, define(req(3)).getRetcode());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    Set<Integer> selectedProperties(GameItem item) {
        var props = new HashSet<Integer>();
        for (int id : item.getPurchasedAppendPropIdList())
            props.add(GameData.getReliquaryAffixDataMap().get(id).getFightProp().getId());
        return props;
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void theLastNecessaryUpgradesHonorTheSharedGuaranteeAfterReload(int initial) throws Exception {
        var weights = GameData.getReliquaryOfferPurchaseDataMap().get(1).getWeights();
        weights[0] = initial == 3 ? 1 : 0;
        weights[1] = initial == 4 ? 1 : 0;
        assertEquals(0, define(req(3)).getRetcode());
        assertEquals(initial, artifacts().get(0).getAppendPropIdList().size());
        reload();
        var item = artifacts().get(0);
        assertEquals(initial, item.getDefinedInitialAppendCount());
        assertEquals(2, item.getDefinedUpgradeGuarantee());
        var selected = selectedProperties(item);
        if (initial == 3) item.addAppendProp();
        assertEquals(4, item.getAppendPropIdList().size());
        int other =
                item.getAppendPropIdList().stream()
                        .filter(
                                x ->
                                        !selected.contains(
                                                GameData.getReliquaryAffixDataMap()
                                                        .get(x)
                                                        .getFightProp()
                                                        .getId()))
                        .findFirst()
                        .orElseThrow();
        int maxGrowth = 5 - (4 - initial);
        for (int i = 0; i < maxGrowth - 2; i++) item.getAppendPropIdList().add(other);
        int before = item.getAppendPropIdList().size();
        item.addAppendProp(affix -> 0);
        item.addAppendProp(affix -> 0);
        assertEquals(before + 2, item.getAppendPropIdList().size());
        for (int id : item.getAppendPropIdList().subList(before, before + 2))
            assertTrue(
                    selected.contains(
                            GameData.getReliquaryAffixDataMap().get(id).getFightProp().getId()));
        item.setLevel(21);
        item.save();
        reload();
        assertEquals(21, artifacts().get(0).getLevel());
        assertEquals(4 + maxGrowth, artifacts().get(0).getAppendPropIdList().size());
    }

    @Test
    void actualInventoryEnhancementToTwentyPreservesTheSelectedStatsAndGuarantee()
            throws Exception {
        replaceField(
                Player.class,
                actual,
                "battlePassManager",
                new emu.grasscutter.game.battlepass.BattlePassManager(actual));
        replaceField(
                GameServer.class,
                player.server,
                "battlePassSystem",
                new emu.grasscutter.game.battlepass.BattlePassSystem(player.server));
        assertEquals(0, define(req(3)).getRetcode());
        var item = artifacts().get(0);
        int initial = item.getAppendPropIdList().size();
        var selected = selectedProperties(item);
        var root = Path.of("resources/ExcelBinOutput");
        for (var row :
                JsonUtils.loadToList(
                        root.resolve("ReliquaryLevelExcelConfigData.json"),
                        ReliquaryLevelData.class)) {
            row.onLoad();
            @SuppressWarnings("unchecked")
            var levelMap =
                    (Map<Integer, ReliquaryLevelData>)
                            (Map<?, ?>) GameData.getMapByResourceDef(ReliquaryLevelData.class);
            put(levelMap, row.getId(), row);
        }
        actual.setProperty(PlayerProperty.PROP_PLAYER_SCOIN, 2_000_000);
        stock(105003, 100);
        new InventorySystem(player.server)
                .upgradeRelic(
                        actual,
                        item.getGuid(),
                        List.of(),
                        List.of(ItemParam.newBuilder().setItemId(105003).setCount(100).build()));
        assertEquals(21, item.getLevel());
        assertEquals(initial + 5, item.getAppendPropIdList().size());
        long hits =
                item.getAppendPropIdList().stream()
                        .filter(
                                x ->
                                        selected.contains(
                                                GameData.getReliquaryAffixDataMap()
                                                        .get(x)
                                                        .getFightProp()
                                                        .getId()))
                        .count();
        assertTrue(hits - 2 >= 2, "At least two growth rolls must belong to the chosen pair");
        reload();
        assertEquals(21, artifacts().get(0).getLevel());
        assertEquals(2, artifacts().get(0).getPurchasedAppendPropIdList().size());
    }

    @Test
    void ordinaryArtifactsKeepTheirExistingUpgradeBiasAndHaveNoDefinitionFlags() {
        var item = new GameItem(GameData.getItemDataMap().get(75554));
        var first =
                GameData.getReliquaryAffixDataMap()
                        .get(item.getAppendPropIdList().get(0))
                        .getFightProp();
        item.addAppendProp(x -> x.getFightProp() == first ? 1 : 0);
        assertEquals(
                first,
                GameData.getReliquaryAffixDataMap()
                        .get(item.getAppendPropIdList().get(4))
                        .getFightProp());
        assertTrue(item.getPurchasedAppendPropIdList().isEmpty());
        assertTrue(item.getDefiniteAppendPropIdList().isEmpty());
        assertEquals(0, item.getDefinedUpgradeGuarantee());
    }

    @Test
    void differentPurchaseIdsForTheSameSetShareOneCounter() throws Exception {
        var row =
                java.util.stream.StreamSupport.stream(offers.spliterator(), false)
                        .filter(x -> x.getAsJsonObject().get("id").getAsInt() == 1)
                        .findFirst()
                        .orElseThrow()
                        .deepCopy()
                        .getAsJsonObject();
        row.addProperty("id", 999);
        var duplicate = gson.fromJson(row, ReliquaryOfferPurchaseData.class);
        duplicate.onLoad();
        put(GameData.getReliquaryOfferPurchaseDataMap(), 999, duplicate);
        assertEquals(0, define(req(1)).getRetcode());
        assertEquals(0, define(req(2).setPurchaseId(999)).getRetcode());
        assertEquals(1, state().getDefineCountListCount());
        assertEquals(15001, state().getDefineCountList(0).getDefineId());
        assertEquals(2, state().getDefineCountList(0).getCount());
        assertEquals(
                Retcode.RET_RELIQUARY_PURCHASE_SUITE_TIMES_OVER_LIMIT_VALUE,
                define(req(3)).getRetcode());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void exactCycleBoundaryResetsSetCountsAndRetainsExtractionProgress() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong(1000);
        var owner = actual;
        var manager =
                new ArtifactTransmuter(
                        owner,
                        now::get,
                        () -> DatabaseHelper.saveGameSync(owner),
                        DatabaseHelper::saveGameSync);
        replaceField(Player.class, owner, "artifactTransmuter", manager);
        owner.getArtifactTransmuterData().setProgressUnits(500);
        assertEquals(0, define(req(1)).getRetcode());
        assertEquals(0, define(req(2)).getRetcode());
        long end = owner.getArtifactTransmuterData().getCycleEndTime();
        now.set(end - 1);
        assertEquals(
                Retcode.RET_RELIQUARY_PURCHASE_SUITE_TIMES_OVER_LIMIT_VALUE,
                define(req(3)).getRetcode());
        now.set(end);
        assertEquals(0, define(req(1)).getRetcode());
        assertEquals(1, state().getDefineCountList(0).getCount());
        assertEquals(0.5f, state().getFractionalProgress());
        assertEquals(17, owner.getInventory().getItemCountById(105005));
    }

    @Test
    void anUnconfirmedArtifactDeliveryBlocksBothDefinitionAndExtractionAfterReload()
            throws Exception {
        var owner = actual;
        var manager =
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> DatabaseHelper.saveGameSync(owner),
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            if (item.getItemType() == ItemType.ITEM_RELIQUARY)
                                throw new IllegalStateException(
                                        "Injected defined artifact acknowledgement failure");
                        });
        replaceField(Player.class, owner, "artifactTransmuter", manager);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, define(req(3)).getRetcode());
        assertEquals(1, artifacts().size());
        var output = artifacts().get(0);
        reload();
        var receipt = actual.getArtifactTransmuterData().getPending();
        assertNotNull(receipt);
        assertEquals(2, receipt.getState());
        assertEquals(1, receipt.getPurchaseId());
        assertEquals(15001, receipt.getSetId());
        assertEquals(output.getObjectId().toHexString(), receipt.getOutputObjectId());
        assertEquals(output.getPurchasedAppendPropIdList(), receipt.getSelectedPropIds());
        assertEquals(output.getAppendPropIdList(), receipt.getAppendPropIds());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, define(req(3)).getRetcode());
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                actual.getArtifactTransmuter()
                        .extract(ReliquaryOfferExtractReq.newBuilder().setScheduleId(700).build())
                        .getRetcode());
        assertEquals(1, artifacts().size());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
    }

    @Test
    void interruptedDefinitionPaymentDoesNotGrantOrRepeatTheArtifact() throws Exception {
        var owner = actual;
        var manager =
                new ArtifactTransmuter(
                        owner,
                        () -> 1000,
                        () -> DatabaseHelper.saveGameSync(owner),
                        item -> {
                            DatabaseHelper.saveGameSync(item);
                            throw new IllegalStateException(
                                    "Injected definition payment acknowledgement failure");
                        });
        replaceField(Player.class, owner, "artifactTransmuter", manager);
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, define(req(3)).getRetcode());
        reload();
        assertEquals(1, actual.getArtifactTransmuterData().getPending().getState());
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, define(req(3)).getRetcode());
        assertEquals(18, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedHeaderOrPayloadDoesNotConsumeElixir(boolean badHeader) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.ReliquaryOfferDefineReq,
                badHeader ? new byte[] {(byte) 0x80} : new byte[0],
                badHeader ? req(3).build().toByteArray() : new byte[] {(byte) 0x80});
        var packet =
                packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.ReliquaryOfferDefineRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(0, ReliquaryOfferDefineRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void missingGadgetAndBrokenPurchaseTemplateAreRejectedBeforePayment() throws Exception {
        GameData.getReliquaryOfferPurchaseDataMap().get(1).getTimepieceIds()[1] = 99999999;
        assertEquals(Retcode.RET_NOT_FOUND_CONFIG_VALUE, define(req(3)).getRetcode());
        actual.getInventory().removeItem(actual.getInventory().getItemById(220109), 1);
        assertEquals(Retcode.RET_ITEM_NOT_EXIST_VALUE, define(req(1)).getRetcode());
        assertEquals(20, actual.getInventory().getItemCountById(105005));
        assertTrue(artifacts().isEmpty());
    }

    @Test
    void competingRequestsCannotBothUseTheLastSetAllowance() throws Exception {
        assertEquals(0, define(req(1)).getRetcode());
        var request = req(3).build();
        var manager = actual.getArtifactTransmuter();
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return manager.define(request).getRetcode();
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return manager.define(request).getRetcode();
                            });
            start.countDown();
            var codes =
                    List.of(
                            first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                            second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(codes, 0));
            assertEquals(
                    1,
                    Collections.frequency(
                            codes, Retcode.RET_RELIQUARY_PURCHASE_SUITE_TIMES_OVER_LIMIT_VALUE));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, artifacts().size());
        assertEquals(17, actual.getInventory().getItemCountById(105005));
        reload();
        assertEquals(2, actual.getArtifactTransmuterData().getDefinedCounts().get(15001));
    }
}

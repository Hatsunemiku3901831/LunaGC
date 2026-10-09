package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.gacha.GachaBanner;
import emu.grasscutter.game.gacha.GachaSystem;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarUpgradeRspOuterClass.AvatarUpgradeRsp;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto._AvatarExtraLevelNotifyOuterClass._AvatarExtraLevelNotify;
import emu.grasscutter.net.proto._AvatarExtraLevelUpgradeReqOuterClass._AvatarExtraLevelUpgradeReq;
import emu.grasscutter.net.proto._AvatarExtraLevelUpgradeRspOuterClass._AvatarExtraLevelUpgradeRsp;
import emu.grasscutter.server.game.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class AvatarExtraLevelTest {
    private final Gson gson = new Gson();
    private final Map<Integer, ItemData> oldItems = new HashMap<>();
    private final Map<Integer, AvatarPromoteData> oldPromotes = new HashMap<>();
    private final Map<Integer, AvatarData> oldAvatars = new HashMap<>();
    private final Map<Integer, AvatarTalentData> oldTalents = new HashMap<>();
    private final Map<Integer, AvatarSkillDepotData> oldGachaDepots = new HashMap<>();

    @SuppressWarnings("unchecked")
    private final Map<Integer, AvatarPromoteData> promotes =
            (Map<Integer, AvatarPromoteData>) GameData.getMapByResourceDef(AvatarPromoteData.class);

    private Map<Integer, AvatarCurveData> oldCurves;
    private Map<Integer, AvatarExtraLevelData> oldExtra;
    private int depotId;
    private AvatarSkillDepotData oldDepot;
    private TestPlayer player;
    private TestAvatar avatar;
    private InventorySystem system;

    private static class TestPlayer extends Player {
        private final TeamManager team = new TeamManager(this);
        private final BattlePassManager pass =
                new BattlePassManager(this) {
                    @Override
                    public void triggerMission(WatcherTriggerType type, int param, int progress) {}
                };
        GameServer server;
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return pass;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            if (packets != null) packets.add(packet);
        }

        @Override
        public void save() {}
    }

    private static class TestAvatar extends Avatar {
        int saves;
        int constellation = -1;

        TestAvatar(AvatarData data) {
            super(data);
        }

        @Override
        public int getCoreProudSkillLevel() {
            return constellation < 0 ? super.getCoreProudSkillLevel() : constellation;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private <T> T[] rows(String name, Class<T[]> type) throws Exception {
        return gson.fromJson(Files.readString(Path.of("resources", "ExcelBinOutput", name)), type);
    }

    @BeforeEach
    void prepare() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        oldCurves = new HashMap<>(GameData.getAvatarCurveDataMap());
        oldExtra = new HashMap<>(GameData.getAvatarExtraLevelDataMap());
        for (var curve : rows("AvatarCurveExcelConfigData.json", AvatarCurveData[].class)) {
            curve.onLoad();
            GameData.getAvatarCurveDataMap().put(curve.getId(), curve);
        }
        GameData.getAvatarExtraLevelDataMap().clear();
        for (var row : rows("AvatarExtraLevelExcelConfigData.json", AvatarExtraLevelData[].class)) {
            row.onLoad();
            GameData.getAvatarExtraLevelDataMap().put(row.getId(), row);
        }
        AvatarData data =
                Arrays.stream(rows("AvatarExcelConfigData.json", AvatarData[].class))
                        .filter(row -> row.getId() == 10000042)
                        .findFirst()
                        .orElseThrow();
        depotId = data.getSkillDepotId();
        var depot =
                Arrays.stream(rows("AvatarSkillDepotExcelConfigData.json", AvatarSkillDepotData[].class))
                        .filter(row -> row.getId() == depotId)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        oldDepot = GameData.getAvatarSkillDepotDataMap().put(depotId, depot);
        data.onLoad();
        for (var promote : rows("AvatarPromoteExcelConfigData.json", AvatarPromoteData[].class)) {
            if (promote.getAvatarPromoteId() != data.getAvatarPromoteId()) continue;
            promote.onLoad();
            oldPromotes.put(promote.getId(), promotes.put(promote.getId(), promote));
        }
        for (var item : rows("MaterialExcelConfigData.json", ItemData[].class)) {
            if (item.getId() != 104300 && item.getId() != 104003) continue;
            item.onLoad();
            oldItems.put(item.getId(), GameData.getItemDataMap().put(item.getId(), item));
        }
        player = new TestPlayer();
        player.setMora(10000);
        avatar = new TestAvatar(data);
        avatar.setLevel(90);
        avatar.setPromoteLevel(6);
        assertTrue(player.getAvatars().addAvatar(avatar));
        avatar.recalcStats();
        avatar.saves = 0;
        stock(104300, 3);
        stock(104003, 5);
        system = new InventorySystem(null);
        player.packets.clear();
    }

    private void stock(int id, int count) {
        var item = new GameItem(id, count);
        item.setOwner(player);
        player.getInventory().getItems().put(item.getGuid(), item);
        player.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
    }

    @AfterEach
    void restore() {
        if (oldCurves != null) {
            GameData.getAvatarCurveDataMap().clear();
            GameData.getAvatarCurveDataMap().putAll(oldCurves);
        }
        if (oldExtra != null) {
            GameData.getAvatarExtraLevelDataMap().clear();
            GameData.getAvatarExtraLevelDataMap().putAll(oldExtra);
        }
        oldItems.forEach(
                (id, data) -> {
                    if (data == null) GameData.getItemDataMap().remove(id);
                    else GameData.getItemDataMap().put(id, data);
                });
        oldPromotes.forEach(
                (id, data) -> {
                    if (data == null) promotes.remove(id);
                    else promotes.put(id, data);
                });
        oldAvatars.forEach(
                (id, data) -> {
                    if (data == null) GameData.getAvatarDataMap().remove(id);
                    else GameData.getAvatarDataMap().put(id, data);
                });
        oldTalents.forEach(
                (id, data) -> {
                    if (data == null) GameData.getAvatarTalentDataMap().remove(id);
                    else GameData.getAvatarTalentDataMap().put(id, data);
                });
        oldGachaDepots.forEach(
                (id, data) -> {
                    if (data == null) GameData.getAvatarSkillDepotDataMap().remove(id);
                    else GameData.getAvatarSkillDepotDataMap().put(id, data);
                });
        if (depotId != 0) {
            if (oldDepot == null) GameData.getAvatarSkillDepotDataMap().remove(depotId);
            else GameData.getAvatarSkillDepotDataMap().put(depotId, oldDepot);
        }
    }

    private int amount(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private void upgrade(int expectedLevel) {
        system.upgradeAvatarExtraLevel(player, avatar.getGuid(), expectedLevel);
    }

    private _AvatarExtraLevelUpgradeRsp lastResponse() throws Exception {
        var packets =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes._AvatarExtraLevelUpgradeRsp)
                        .toList();
        return _AvatarExtraLevelUpgradeRsp.parseFrom(packets.get(packets.size() - 1).getData());
    }

    private void assertUnchanged(Retcode result, int oldLevel, int remaining) throws Exception {
        assertEquals(oldLevel, avatar.getLevel());
        assertEquals(remaining, amount(104300));
        assertEquals(0, avatar.saves);
        assertEquals(result.getNumber(), lastResponse().getRetcode());
    }

    @Test
    void bothResourceStepsConsumeTheirExactMaterialsAndRecalculateStats() throws Exception {
        assertTrue(ResourceLoader.getResourceDefClasses().contains(AvatarExtraLevelData.class));
        var hp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        var attack = avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK);
        var defense = avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_DEFENSE);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp / 2);
        avatar.setExp(123);
        upgrade(90);
        assertEquals(95, avatar.getLevel());
        assertEquals(2, amount(104300));
        assertEquals(0, avatar.getExp());
        assertEquals(6, avatar.getPromoteLevel());
        assertEquals(90, lastResponse().getOldLevel());
        assertEquals(95, lastResponse().getCurLevel());
        assertEquals(0, lastResponse().getRetcode());
        assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) > hp);
        assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK) > attack);
        assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_DEFENSE) > defense);
        assertEquals(
                0.5f,
                avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                        / avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP),
                0.00001f);
        assertEquals(95, avatar.toProto().getExtraLevel());
        var notifyPacket =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes._AvatarExtraLevelNotify)
                        .findFirst()
                        .orElseThrow();
        var notify = _AvatarExtraLevelNotify.parseFrom(notifyPacket.getData());
        assertEquals(95, notify.getExtraLevel());
        assertEquals(avatar.getGuid(), notify.getAvatarGuid());
        upgrade(95);
        assertEquals(100, avatar.getLevel());
        assertEquals(0, amount(104300));
        assertEquals(100, avatar.toProto().getExtraLevel());
        assertEquals(2, avatar.saves);
        assertEquals(95, lastResponse().getOldLevel());
        assertEquals(100, lastResponse().getCurLevel());
        assertEquals(10000, player.getMora());
    }

    @Test
    void secondStepCannotPartiallySpendInsufficientMaterials() throws Exception {
        avatar.setLevel(95);
        player.getInventory().removeItem(104300, 2);
        player.packets.clear();
        upgrade(95);
        assertUnchanged(Retcode.RET_ITEM_COUNT_NOT_ENOUGH, 95, 1);
    }

    @Test
    void ordinaryAscensionMustBeCompletedFirst() throws Exception {
        avatar.setPromoteLevel(5);
        upgrade(90);
        assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 90, 3);
        assertEquals(0, avatar.toProto().getExtraLevel());
    }

    @Test
    void trialAvatarsCannotSpendPermanentMaterials() throws Exception {
        avatar.setTrialAvatarId(1);
        upgrade(90);
        assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 90, 3);
        assertEquals(0, avatar.getExtraLevel());
    }

    @Test
    void anotherPlayersAvatarGuidIsRejected() throws Exception {
        system.upgradeAvatarExtraLevel(player, avatar.getGuid() + 1000, 90);
        assertUnchanged(Retcode.RET_AVATAR_ID_ERROR, 90, 3);
    }

    @Test
    void zeroAndWrongStartingLevelsDoNotSpendMaterials() throws Exception {
        for (int level : new int[] {0, 89, 95, 100, -1}) {
            upgrade(level);
            assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 90, 3);
        }
    }

    @Test
    void repeatingTheFirstRequestCannotBuyTheSecondStep() throws Exception {
        upgrade(90);
        avatar.saves = 0;
        upgrade(90);
        assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 95, 2);
    }

    @Test
    void concurrentIdenticalRequestsSpendOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first =
                    executor.submit(
                            () -> {
                                start.await();
                                upgrade(90);
                                return null;
                            });
            var second =
                    executor.submit(
                            () -> {
                                start.await();
                                upgrade(90);
                                return null;
                            });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(95, avatar.getLevel());
            assertEquals(2, amount(104300));
            assertEquals(1, avatar.saves);
            var results = new ArrayList<Integer>();
            for (var packet : player.packets)
                if (packet.getOpcode() == PacketOpcodes._AvatarExtraLevelUpgradeRsp)
                    results.add(_AvatarExtraLevelUpgradeRsp.parseFrom(packet.getData()).getRetcode());
            assertEquals(List.of(0, Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR_VALUE), results);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void finalLevelAndMissingResourcesReturnFailure() throws Exception {
        avatar.setLevel(100);
        upgrade(100);
        assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 100, 3);
        avatar.setLevel(90);
        GameData.getAvatarExtraLevelDataMap().clear();
        upgrade(90);
        assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 90, 3);
    }

    @Test
    void malformedAndUnknownMaterialCostsCannotMakeTheUpgradeFree() throws Exception {
        for (String costs :
                List.of("[]", "[{\"id\":104300,\"count\":-1}]", "[{\"id\":2147483647,\"count\":1}]")) {
            var row =
                    gson.fromJson(
                            "{\"requireLevel\":90,\"maxLevel\":95,\"costItems\":" + costs + "}",
                            AvatarExtraLevelData.class);
            row.onLoad();
            GameData.getAvatarExtraLevelDataMap().put(90, row);
            upgrade(90);
            assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 90, 3);
        }
    }

    @Test
    void duplicatedCostItemsAreSummedBeforeCheckingTheInventory() throws Exception {
        var row =
                gson.fromJson(
                        "{\"requireLevel\":90,\"maxLevel\":95,\"costItems\":[{\"id\":104300,\"count\":2},{\"id\":104300,\"count\":2}]}",
                        AvatarExtraLevelData.class);
        row.onLoad();
        GameData.getAvatarExtraLevelDataMap().put(90, row);
        upgrade(90);
        assertUnchanged(Retcode.RET_ITEM_COUNT_NOT_ENOUGH, 90, 3);
        assertEquals(1, row.getCostItems().length);
        assertEquals(4, row.getCostItems()[0].getCount());
    }

    @Test
    void missingTargetStatsRejectTheUpgradeBeforePayment() throws Exception {
        GameData.getAvatarCurveDataMap().remove(95);
        upgrade(90);
        assertUnchanged(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR, 90, 3);
    }

    @Test
    @SuppressWarnings("removal")
    void expBooksCannotBeChargedAtTheCapOrBypassExtraLevelCosts() throws Exception {
        for (int level : new int[] {90, 95, 100}) {
            avatar.setLevel(level);
            player.packets.clear();
            system.upgradeAvatar(
                    player,
                    avatar.getGuid(),
                    List.of(ItemParam.newBuilder().setItemId(104003).setCount(1).build()));
            system.upgradeAvatar(player, avatar.getGuid(), 104003, 1);
            assertEquals(level, avatar.getLevel());
            assertEquals(5, amount(104003));
            assertEquals(10000, player.getMora());
            assertEquals(0, avatar.saves);
            var replies =
                    player.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.AvatarUpgradeRsp)
                            .toList();
            assertEquals(2, replies.size());
            for (var reply : replies)
                assertEquals(
                        Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR_VALUE,
                        AvatarUpgradeRsp.parseFrom(reply.getData()).getRetcode());
        }
    }

    @Test
    void nativeRoutingPassesTheRequiredLevelToSettlement() throws Exception {
        var server = new GameServer();
        // The isolated HTTP configuration is dispatch-only; install the real inventory service
        // without starting a second UDP server or any unrelated systems.
        var serviceField = GameServer.class.getDeclaredField("inventorySystem");
        serviceField.setAccessible(true);
        serviceField.set(server, system);
        var session = new GameSession(server);
        session.setPlayer(player);
        session.setState(GameSession.SessionState.ACTIVE);
        var registry = new GameServerPacketHandler(PacketHandler.class);
        var request =
                _AvatarExtraLevelUpgradeReq.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .setRequireLevel(90)
                        .build();
        registry.handle(session, 7915, new byte[0], request.toByteArray());
        assertEquals(95, avatar.getLevel());
        assertEquals(2, amount(104300));
        assertEquals(0, lastResponse().getRetcode());
        registry.handle(session, 7915, new byte[0], request.toByteArray());
        assertEquals(95, avatar.getLevel());
        assertEquals(2, amount(104300));
        assertEquals(Retcode.RET_AVATAR_LIMIT_LEVEL_ERROR_VALUE, lastResponse().getRetcode());
    }

    @Test
    void resourceAliasesRetainTheCurrentCostsAndRejectOverflow() {
        for (String names :
                List.of(
                        "\"fromLevel\":90,\"toLevel\":95",
                        "\"GGCNFKENPPB\":90,\"LCBMNGNFOCE\":95",
                        "\"LFKJOBPEIKM\":90,\"DKLMGPLCDGG\":95")) {
            var row =
                    gson.fromJson(
                            "{" + names + ",\"costItems\":[{\"id\":104300,\"count\":1},{\"id\":0,\"count\":0}]}",
                            AvatarExtraLevelData.class);
            row.onLoad();
            assertTrue(row.isValid());
            assertEquals(90, row.getRequireLevel());
            assertEquals(95, row.getMaxLevel());
            assertEquals(1, row.getCostItems().length);
        }
        var overflow =
                gson.fromJson(
                        "{\"requireLevel\":90,\"maxLevel\":95,\"costItems\":[{\"id\":104300,\"count\":2147483647},{\"id\":104300,\"count\":1}]}",
                        AvatarExtraLevelData.class);
        overflow.onLoad();
        assertFalse(overflow.isValid());
    }

    private GachaSystem prepareWishes() throws Exception {
        Assumptions.assumeTrue(System.getenv("LUNAGC_TEST_MONGO_URI") != null);
        if (DatabaseManager.getGameDatastore() == null) DatabaseManager.initialize();
        player.server = new GameServer();
        var field = GameServer.class.getDeclaredField("inventorySystem");
        field.setAccessible(true);
        field.set(player.server, system);
        oldAvatars.put(
                avatar.getAvatarId(),
                GameData.getAvatarDataMap().put(avatar.getAvatarId(), avatar.getAvatarData()));
        int talentId = avatar.getSkillDepot().getTalents().get(0);
        var talent =
                Arrays.stream(rows("AvatarTalentExcelConfigData.json", AvatarTalentData[].class))
                        .filter(row -> row.getId() == talentId)
                        .findFirst()
                        .orElseThrow();
        oldTalents.put(talentId, GameData.getAvatarTalentDataMap().put(talentId, talent));
        avatar.getSkillDepot().onLoad();
        for (var item : rows("MaterialExcelConfigData.json", ItemData[].class)) {
            if (!Set.of(1042, 1142, 1034, 221, 222, 223).contains(item.getId())) continue;
            item.onLoad();
            oldItems.put(item.getId(), GameData.getItemDataMap().put(item.getId(), item));
        }
        for (var item : rows("WeaponExcelConfigData.json", ItemData[].class)) {
            if (item.getId() != 11501) continue;
            item.onLoad();
            oldItems.put(item.getId(), GameData.getItemDataMap().put(item.getId(), item));
        }
        stock(223, 20);
        return new GachaSystem(player.server) {
            @Override
            public synchronized boolean load() {
                return true;
            }

            @Override
            public int randomRange(int min, int max) {
                return min;
            }
        };
    }

    private DoGachaRsp wish(GachaSystem wishes, int card, int rarity, int count) throws Exception {
        var banner =
                gson.fromJson(
                        "{\"scheduleId\":1,\"bannerType\":\"CHARACTER\",\"prefabPath\":\"test\","
                                + "\"costItemId\":223,\"rateUpItems"
                                + rarity
                                + "\":["
                                + card
                                + "],\"eventChance"
                                + rarity
                                + "\":100,"
                                + "\"weights5\":[[1,"
                                + (rarity == 5 ? 10000 : 0)
                                + "]],\"weights4\":[[1,10000]]}",
                        GachaBanner.class);
        banner.onLoad();
        wishes.getGachaBanners().put(1, banner);
        player.packets.clear();
        wishes.doPulls(player, 1, count);
        var reply =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.DoGachaRsp)
                        .findFirst()
                        .orElseThrow();
        return DoGachaRsp.parseFrom(reply.getData());
    }

    @Test
    void fullConstellationFiveStarTenPullsGrantTenMasterlessStellaAnd250Starglitter()
            throws Exception {
        var wishes = prepareWishes();
        avatar.constellation = 6;
        player.getInventory().removeItem(104300, 3);
        var reply = wish(wishes, 1042, 5, 10);
        assertEquals(0, reply.getRetcode());
        assertEquals(10, reply.getGachaItemListCount());
        assertEquals(10, amount(104300));
        assertEquals(250, amount(221));
        assertEquals(10, amount(223));
        int newRewards = 0;
        for (var card : reply.getGachaItemListList()) {
            var reward =
                    card.getTransferItemsList().stream()
                            .filter(i -> i.getItem().getItemId() == 104300)
                            .findFirst()
                            .orElseThrow();
            assertEquals(1, reward.getItem().getCount());
            if (reward.getIsTransferItemNew()) newRewards++;
            assertEquals(
                    25,
                    card.getTokenItemListList().stream()
                            .filter(i -> i.getItemId() == 221)
                            .findFirst()
                            .orElseThrow()
                            .getCount());
        }
        assertEquals(1, newRewards);
    }

    @Test
    void tenPullsCrossingC6SettleTheFirstConstellationAndRemainingExtraLevelMaterials()
            throws Exception {
        var wishes = prepareWishes();
        avatar.constellation = 5;
        var reply = wish(wishes, 1042, 5, 10);
        assertEquals(1, amount(1142));
        assertEquals(12, amount(104300));
        assertEquals(235, amount(221));
        assertTrue(
                reply.getGachaItemList(0).getTransferItemsList().stream()
                        .anyMatch(i -> i.getItem().getItemId() == 1142));
        assertFalse(
                reply.getGachaItemList(0).getTransferItemsList().stream()
                        .anyMatch(i -> i.getItem().getItemId() == 104300));
        for (int i = 1; i < 10; i++)
            assertTrue(
                    reply.getGachaItemList(i).getTransferItemsList().stream()
                            .anyMatch(t -> t.getItem().getItemId() == 104300));
    }

    @Test
    void fourStarDuplicatesAndFiveStarWeaponsNeverGrantExtraLevelMaterials() throws Exception {
        var wishes = prepareWishes();
        var noelleData =
                Arrays.stream(rows("AvatarExcelConfigData.json", AvatarData[].class))
                        .filter(row -> row.getId() == 10000034)
                        .findFirst()
                        .orElseThrow();
        var depot =
                Arrays.stream(rows("AvatarSkillDepotExcelConfigData.json", AvatarSkillDepotData[].class))
                        .filter(row -> row.getId() == noelleData.getSkillDepotId())
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        oldGachaDepots.put(
                depot.getId(), GameData.getAvatarSkillDepotDataMap().put(depot.getId(), depot));
        noelleData.onLoad();
        oldAvatars.put(
                noelleData.getId(), GameData.getAvatarDataMap().put(noelleData.getId(), noelleData));
        var noelle = new TestAvatar(noelleData);
        noelle.constellation = 6;
        player.getAvatars().addAvatar(noelle);
        var fourStars = wish(wishes, 1034, 4, 10);
        assertEquals(3, amount(104300));
        assertEquals(50, amount(221));
        assertTrue(
                fourStars.getGachaItemListList().stream()
                        .flatMap(c -> c.getTransferItemsList().stream())
                        .noneMatch(i -> i.getItem().getItemId() == 104300));
        var weapon = wish(wishes, 11501, 5, 1);
        assertEquals(3, amount(104300));
        assertEquals(60, amount(221));
        assertTrue(
                weapon.getGachaItemList(0).getTransferItemsList().stream()
                        .noneMatch(i -> i.getItem().getItemId() == 104300));
    }

    @Test
    void missingExtraLevelResourcesDoNotConvertDuplicateRewardsIntoAnotherMaterial()
            throws Exception {
        var wishes = prepareWishes();
        avatar.constellation = 6;
        GameData.getAvatarExtraLevelDataMap().clear();
        var reply = wish(wishes, 1042, 5, 1);
        assertEquals(3, amount(104300));
        assertEquals(25, amount(221));
        assertTrue(
                reply.getGachaItemList(0).getTransferItemsList().stream()
                        .noneMatch(i -> i.getItem().getItemId() == 104300));
    }

    @Entity("snapshots")
    public static class Snapshot {
        @Id private String id = "avatar";
        private Avatar value;

        public Snapshot() {}

        Snapshot(Avatar value) {
            this.value = value;
        }
    }

    @Test
    void mongoRoundTripPreservesBothExtraLevelStepsWithoutASecondProgressField() {
        var uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = "lunagc_extra_level_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore =
                        Morphia.createDatastore(
                                client, database, MapperOptions.builder().storeEmpties(true).build());
                datastore.getMapper().map(Snapshot.class, Avatar.class);
                for (int level : new int[] {95, 100}) {
                    var saved = new Avatar();
                    saved.setLevel(level);
                    saved.setPromoteLevel(6);
                    datastore.save(new Snapshot(saved));
                    var loaded = datastore.find(Snapshot.class).first().value;
                    assertEquals(level, loaded.getLevel());
                    assertEquals(6, loaded.getPromoteLevel());
                    assertEquals(level, loaded.getExtraLevel());
                }
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }
}

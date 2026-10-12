package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarSkillUpgradeReqOuterClass.AvatarSkillUpgradeReq;
import emu.grasscutter.net.proto.AvatarSkillUpgradeRspOuterClass.AvatarSkillUpgradeRsp;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Actual talent resources, payment and registered requests; persistence is a controlled double. */
class TalentUpgradeHandbookTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private static final int SKILL = 10732;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture environment;
    private TalentPlayer player;
    private SkillTalentLevelTest.RecordingAvatar avatar;
    private Object previousDatastore;
    private java.lang.reflect.Field datastore;

    static class TalentPlayer extends InvestigationRewardTest.ClaimPlayer {
        final Inventory talentInventory =
                new Inventory(this) {
                    @Override
                    public synchronized boolean payItems(
                            Iterable<ItemParamData> items, int quantity, ActionReason reason) {
                        boolean result = super.payItems(items, quantity, reason);
                        var callback = afterPayment;
                        afterPayment = null;
                        if (result && callback != null) callback.run();
                        return result;
                    }
                };
        Runnable afterPayment;

        @Override
        public Inventory getInventory() {
            return talentInventory;
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new TalentPlayer();
        player.setScene(environment.scene);
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        var skills =
                JsonUtils.loadToList(
                        EXCEL.resolve("AvatarSkillExcelConfigData.json"), AvatarSkillData.class);
        for (var skill : skills)
            if (Set.of(SKILL, 10731, 10735, 10962).contains(skill.getId()))
                put(GameData.getAvatarSkillDataMap(), skill.getId(), skill);
        var depot =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(d -> d.getId() == 7301)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        var proudSkills =
                JsonUtils.loadToList(
                                EXCEL.resolve("ProudSkillExcelConfigData.json"),
                                ProudSkillData.class)
                        .stream()
                        .filter(d -> Set.of(7332, 9632).contains(d.getProudSkillGroupId()))
                        .toList();
        for (var row : proudSkills) put(GameData.getProudSkillDataMap(), row.getId(), row);
        for (int skill : List.of(SKILL, 10962)) {
            int group = GameData.getAvatarSkillDataMap().get(skill).getProudSkillGroupId();
            var levels = new IntOpenHashSet();
            proudSkills.stream()
                    .filter(p -> p.getProudSkillGroupId() == group)
                    .forEach(p -> levels.add(p.getLevel()));
            put(GameData.getAvatarSkillLevels(), skill, levels);
        }
        var materials = new HashSet<Integer>();
        proudSkills.forEach(
                p ->
                        p.getTotalCostItems()
                                .forEach(
                                        i -> {
                                            if (i.getCount() > 0) materials.add(i.getId());
                                        }));
        for (var item :
                JsonUtils.loadToList(EXCEL.resolve("MaterialExcelConfigData.json"), ItemData.class))
            if (materials.contains(item.getId()))
                put(GameData.getItemDataMap(), item.getId(), item);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        for (int id : materials) if (id != 202) assertTrue(player.getInventory().addItem(id, 100));
        player.setMora(1000000);
        var chapter =
                JsonUtils.loadToList(
                                EXCEL.resolve("InvestigationConfigData.json"),
                                InvestigationData.class)
                        .stream()
                        .filter(d -> d.getId() == 10007)
                        .findFirst()
                        .orElseThrow();
        var target =
                JsonUtils.loadToList(
                                EXCEL.resolve("InvestigationTargetConfigData.json"),
                                InvestigationTargetData.class)
                        .stream()
                        .filter(d -> d.getId() == 60053)
                        .findFirst()
                        .orElseThrow();
        assertEquals(10007, target.getInvestigationId());
        assertEquals(3, target.getProgress());
        assertEquals("TRIGGER_UPGRADE_TALENT", target.getTriggerConfig().getTriggerType());
        put(GameData.getInvestigationDataMap(), chapter.getId(), chapter);
        put(GameData.getInvestigationTargetDataMap(), target.getId(), target);
        avatar = new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot);
        avatar.setPromoteLevel(6);
        assertTrue(player.getAvatars().addAvatar(avatar));
        avatar.saves = 0;
        player.packets.clear();
    }

    @AfterEach
    void close() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, previousDatastore);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (environment != null) environment.close();
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private int progress() {
        return player.getPlayerProgress().getInvestigation().get(60053);
    }

    private Map<Integer, Integer> stock() {
        var result = new TreeMap<Integer, Integer>();
        for (var item : player.getInventory())
            result.merge(item.getItemId(), item.getCount(), Integer::sum);
        result.put(202, player.getMora());
        return result;
    }

    private void failsWithoutPayment(int skill) {
        var before = stock();
        assertFalse(avatar.upgradeSkill(skill));
        assertEquals(before, stock());
        assertEquals(0, progress());
        assertEquals(0, avatar.saves);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> storedLevels() throws Exception {
        var field = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField("skillLevelMap");
        field.setAccessible(true);
        return (Map<Integer, Integer>) field.get(avatar);
    }

    @Test
    void actualUpgradePaysResourceCostsAndAdvancesCanonicalTargetOnce() throws Exception {
        var before = stock();
        assertTrue(avatar.upgradeSkill(SKILL));
        var after = stock();
        var costs = GameData.getProudSkillDataMap().get(733202).getTotalCostItems();
        for (var cost : costs)
            if (cost.getCount() > 0)
                assertEquals(before.get(cost.getId()) - cost.getCount(), after.get(cost.getId()));
        assertEquals(2, avatar.getSkillLevelMap().get(SKILL));
        assertEquals(1, progress());
        assertEquals(1, avatar.saves);
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerInvestigationTargetNotify)
                        .findFirst()
                        .orElseThrow();
        var target =
                PlayerInvestigationTargetNotify.parseFrom(packet.getData())
                        .getInvestigationTargetListList()
                        .stream()
                        .filter(t -> t.getQuestId() == 60053)
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, target.getProgress());
    }

    @Test
    void independentPaidUpgradesCountEachTimeAndStopAtTargetTotal() {
        for (int i = 0; i < 4; i++) assertTrue(avatar.upgradeSkill(SKILL));
        assertEquals(5, avatar.getSkillLevelMap().get(SKILL));
        assertEquals(3, progress());
        assertEquals(4, avatar.saves);
    }

    @Test
    void insufficientMoraLeavesTalentAndProgressUntouched() {
        player.setMora(0);
        failsWithoutPayment(SKILL);
        assertEquals(1, avatar.getSkillLevelMap().get(SKILL));
    }

    @Test
    void insufficientMaterialsLeavesTalentAndProgressUntouched() {
        var cost =
                GameData.getProudSkillDataMap().get(733202).getCostItems().stream()
                        .filter(i -> i.getCount() > 0)
                        .findFirst()
                        .orElseThrow();
        var item = player.getInventory().getItemById(cost.getId());
        player.getInventory().removeItem(item.getGuid(), item.getCount());
        failsWithoutPayment(SKILL);
    }

    @Test
    void promotionGateLeavesPaymentAndProgressUntouched() {
        avatar.setPromoteLevel(0);
        failsWithoutPayment(SKILL);
    }

    @Test
    void anotherAvatarsSkillCannotBeLearnedOrPaidFor() {
        failsWithoutPayment(10962);
    }

    @Test
    void missingResourceCannotBeUpgraded() {
        failsWithoutPayment(999999);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10, 15, Integer.MAX_VALUE})
    void invalidOrMaxedCurrentLevelCannotConsumeCosts(int level) throws Exception {
        storedLevels().put(SKILL, level);
        failsWithoutPayment(SKILL);
    }

    @Test
    void absentOwnedSkillCannotStartAtLevelOne() throws Exception {
        storedLevels().remove(SKILL);
        failsWithoutPayment(SKILL);
    }

    @Test
    void resourceLevelMismatchCannotConsumeMaterials() {
        put(
                GameData.getProudSkillDataMap(),
                733202,
                gson.fromJson(
                        "{proudSkillId:733202,proudSkillGroupId:7332,level:3,coinCost:100,costItems:[]}",
                        ProudSkillData.class));
        failsWithoutPayment(SKILL);
    }

    @Test
    void unavailableNextLevelCannotPayThenReportSuccess() {
        put(GameData.getAvatarSkillLevels(), SKILL, new IntOpenHashSet(new int[] {1}));
        failsWithoutPayment(SKILL);
        assertEquals(1, avatar.getSkillLevelMap().get(SKILL));
    }

    @Test
    void directGmLevelAssignmentDoesNotCountAsPaidUpgrade() {
        var before = stock();
        assertTrue(avatar.setSkillLevel(SKILL, 2));
        assertEquals(before, stock());
        assertEquals(0, progress());
    }

    @Test
    void closedChapterStillAllowsOrdinaryTalentUpgrade() {
        player.getOpenStates().put(1100, 0);
        assertTrue(avatar.upgradeSkill(SKILL));
        assertEquals(2, avatar.getSkillLevelMap().get(SKILL));
        assertEquals(0, progress());
    }

    @Test
    void actualRegisteredRequestUpdatesTalentPaymentAndHandbook() throws Exception {
        var server = environment.scene.getWorld().getServer();
        var session = new InvestigationRewardTest.ClaimSession(server, player);
        new GameServerPacketHandler(PacketHandler.class)
                .handle(
                        session,
                        PacketOpcodes.AvatarSkillUpgradeReq,
                        new byte[0],
                        AvatarSkillUpgradeReq.newBuilder()
                                .setAvatarGuid(avatar.getGuid())
                                .setAvatarSkillId(SKILL)
                                .build()
                                .toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AvatarSkillUpgradeRsp)
                        .findFirst()
                        .orElseThrow();
        var reply = AvatarSkillUpgradeRsp.parseFrom(packet.getData());
        assertEquals(1, reply.getOldLevel());
        assertEquals(2, reply.getCurLevel());
        assertEquals(1, progress());
    }

    @Test
    void paymentCallbackCannotReenterSameTalent() {
        player.afterPayment = () -> assertFalse(avatar.upgradeSkill(SKILL));
        assertTrue(avatar.upgradeSkill(SKILL));
        assertEquals(2, avatar.getSkillLevelMap().get(SKILL));
        assertEquals(1, progress());
        assertEquals(1, avatar.saves);
    }

    @Test
    void parallelPaidUpgradesKeepDistinctLevelsCostsAndCounts() throws Exception {
        var expected = stock();
        for (int level = 2; level <= 5; level++)
            GameData.getProudSkillDataMap()
                    .get(733200 + level)
                    .getTotalCostItems()
                    .forEach(
                            i -> {
                                if (i.getCount() > 0)
                                    expected.merge(i.getId(), -i.getCount(), Integer::sum);
                            });
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> avatar.upgradeSkill(SKILL)));
            for (var job : jobs) assertTrue(job.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, avatar.getSkillLevelMap().get(SKILL));
        assertEquals(expected, stock());
        assertEquals(3, progress());
        assertEquals(4, avatar.saves);
    }
}

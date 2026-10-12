package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.expedition.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardReqOuterClass.AvatarExpeditionGetRewardReq;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto.AvatarExpeditionStartReqOuterClass.AvatarExpeditionStartReq;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto._AvatarExpeditionBasicInfoOuterClass._AvatarExpeditionBasicInfo;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/** Actual passive/depot bindings and registered start/claim with controlled base rewards. */
class ExpeditionTalentTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private static List<ProudSkillData> talents;
    private static List<AvatarSkillDepotData> depots;
    private static final Map<Integer, String> effectTypes = new HashMap<>();
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private ExpeditionSystem system;
    private Avatar avatar;
    private Object previousDatastore;
    private java.lang.reflect.Field datastore;

    @BeforeAll
    static void resources() throws Exception {
        var raw =
                JsonUtils.loadToList(
                        EXCEL.resolve("ProudSkillExcelConfigData.json"),
                        com.google.gson.JsonObject.class);
        for (var row : raw)
            if (row.has("lifeEffectType")
                    && row.get("lifeEffectType")
                            .getAsString()
                            .startsWith("PROUD_EFFECT_EXPEDITION_"))
                effectTypes.put(
                        row.get("proudSkillId").getAsInt(),
                        row.get("lifeEffectType").getAsString());
        talents =
                JsonUtils.loadToList(
                                EXCEL.resolve("ProudSkillExcelConfigData.json"),
                                ProudSkillData.class)
                        .stream()
                        .filter(t -> effectTypes.containsKey(t.getId()))
                        .toList();
        depots =
                JsonUtils.loadToList(
                        EXCEL.resolve("AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class);
        assertEquals(14, talents.size());
    }

    static Stream<Integer> passiveIds() {
        return talents.stream().map(ProudSkillData::getId);
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        for (var talent : talents) {
            talent.onLoad();
            put(GameData.getProudSkillDataMap(), talent.getId(), talent);
        }
        put(
                GameData.getItemDataMap(),
                100001,
                gson.fromJson(
                        "{id:100001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                        ItemData.class));
        var preview =
                gson.fromJson(
                        "{id:10001,previewItems:[{id:100001,count:'8'}]}", RewardPreviewData.class);
        preview.onLoad();
        put(GameData.getRewardPreviewDataMap(), 10001, preview);
        location(1, 20);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        var server = environment.scene.getWorld().getServer();
        system = new ExpeditionSystem(server);
        var field = GameServer.class.getDeclaredField("expeditionSystem");
        field.setAccessible(true);
        field.set(server, system);
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

    private void location(int city, int hours) {
        put(
                GameData.getExpeditionDataMap(),
                101,
                gson.fromJson(
                        "{ID:101,city_id:"
                                + city
                                + ",openConditionVec:[],timeRewardVec:[{htime:"
                                + hours
                                + ",reward_drop_id:0,rewardPreview:10001}]}",
                        ExpeditionData.class));
    }

    private void avatar(int passive) {
        int group = passive / 100;
        var depot =
                depots.stream()
                        .filter(
                                d -> {
                                    d.onLoad();
                                    return d.getInherentProudSkillOpens().stream()
                                            .anyMatch(p -> p.getProudSkillGroupId() == group);
                                })
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        avatar =
                new SkillTalentLevelTest.RecordingAvatar(
                        player, 10000000 + depot.getId() / 100, depot);
        assertTrue(player.getAvatars().addAvatar(avatar));
        assertTrue(avatar.getProudSkillList().contains(passive));
    }

    private void ordinaryAvatar() {
        var depot =
                gson.fromJson(
                        "{id:7301,skills:[],energySkill:0,talents:[]}", AvatarSkillDepotData.class);
        depot.onLoad();
        avatar = new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot);
        assertTrue(player.getAvatars().addAvatar(avatar));
    }

    private InvestigationRewardTest.ClaimSession request(int opcode, byte[] body, byte[] header)
            throws Exception {
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new GameServerPacketHandler(PacketHandler.class).handle(session, opcode, header, body);
        return session;
    }

    private AvatarExpeditionStartRsp start(int hours, float clientRatio) throws Exception {
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionStartReq,
                        AvatarExpeditionStartReq.newBuilder()
                                .addBasicInfoList(
                                        _AvatarExpeditionBasicInfo.newBuilder()
                                                .setAvatarGuid(avatar.getGuid())
                                                .setExpId(101)
                                                .setHourTime(hours)
                                                .setShortenRatio(clientRatio))
                                .build()
                                .toByteArray(),
                        new byte[0]);
        return AvatarExpeditionStartRsp.parseFrom(session.responses.get(0).getData());
    }

    private AvatarExpeditionGetRewardRsp claim() throws Exception {
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionGetRewardReq,
                        AvatarExpeditionGetRewardReq.newBuilder()
                                .setAvatarGuid(avatar.getGuid())
                                .build()
                                .toByteArray(),
                        new byte[0]);
        return AvatarExpeditionGetRewardRsp.parseFrom(session.responses.get(0).getData());
    }

    private ExpeditionInfo trip() {
        return player.getExpeditionInfo(avatar.getGuid());
    }

    @ParameterizedTest
    @MethodSource("passiveIds")
    void everyActualPassiveBindingProducesItsDeclaredBenefit(int id) throws Exception {
        avatar(id);
        var talent = GameData.getProudSkillDataMap().get(id);
        int city = Integer.parseInt(talent.getLifeEffectParams().get(0));
        location(city, 20);
        var start = start(20, 0);
        assertEquals(0, start.getRetcode());
        assertEquals(1, start.getExpeditionBasicInfoListCount());
        boolean shortens = effectTypes.get(id).equals("PROUD_EFFECT_EXPEDITION_SHORTEN_TIME");
        assertEquals(shortens ? 0.25f : 0f, trip().toProto().getShortenRatio());
        assertEquals(shortens ? 0.25f : 0f, start.getExpeditionBasicInfoList(0).getShortenRatio());
        trip().setStartTime(Utils.getCurrentSeconds() - (shortens ? 54001 : 72001));
        var result = claim();
        assertEquals(0, result.getRetcode());
        assertEquals(8, result.getExpeditionRewardList(0).getItemList(0).getCount());
        assertEquals(shortens ? 0 : 1, result.getExpeditionRewardList(0).getExtraItemListCount());
        assertEquals(shortens ? 8 : 10, player.getInventory().getItemCountById(100001));
    }

    @Test
    void shortTripFinishesAtFifteenHoursWithoutChangingTwentyHourRewardKey() throws Exception {
        avatar(322301);
        assertEquals(0, start(20, 0).getRetcode());
        trip().setStartTime(Utils.getCurrentSeconds() - 53990);
        assertNotEquals(0, claim().getRetcode());
        assertEquals(20, trip().getHourTime());
        trip().setStartTime(Utils.getCurrentSeconds() - 54001);
        assertEquals(0, claim().getRetcode());
    }

    @Test
    void wrongCityCannotUseShorteningTalent() throws Exception {
        avatar(322301);
        location(2, 20);
        assertEquals(0, start(20, 0).getRetcode());
        assertEquals(0f, trip().toProto().getShortenRatio());
        trip().setStartTime(Utils.getCurrentSeconds() - 54001);
        assertNotEquals(0, claim().getRetcode());
    }

    @Test
    void clientRatioCannotCreateShorteningForOrdinaryAvatar() throws Exception {
        ordinaryAvatar();
        var reply = start(20, 0.99f);
        assertEquals(0, reply.getRetcode());
        assertEquals(0f, trip().toProto().getShortenRatio());
        assertEquals(0f, reply.getExpeditionBasicInfoList(0).getShortenRatio());
    }

    @Test
    void removingTalentAfterStartDoesNotChangeStoredShortening() throws Exception {
        avatar(322301);
        assertEquals(0, start(20, 0).getRetcode());
        avatar.getProudSkillList().clear();
        trip().setStartTime(Utils.getCurrentSeconds() - 54001);
        assertEquals(0, claim().getRetcode());
    }

    @Test
    void bonusOnlyAppliesToDeclaredTwentyHourTrip() throws Exception {
        avatar(602301);
        location(2, 4);
        assertEquals(0, start(4, 0).getRetcode());
        trip().setStartTime(Utils.getCurrentSeconds() - 14401);
        var result = claim();
        assertEquals(0, result.getRetcode());
        assertEquals(0, result.getExpeditionRewardList(0).getExtraItemListCount());
        assertEquals(8, player.getInventory().getItemCountById(100001));
    }

    @Test
    void wrongCityCannotUseExtraOutputTalent() throws Exception {
        avatar(602301);
        location(1, 20);
        assertEquals(0, start(20, 0).getRetcode());
        trip().setStartTime(Utils.getCurrentSeconds() - 72001);
        var result = claim();
        assertEquals(0, result.getRetcode());
        assertEquals(0, result.getExpeditionRewardList(0).getExtraItemListCount());
    }

    @Test
    void storedExtraOutputSurvivesTalentChange() throws Exception {
        avatar(602301);
        location(2, 20);
        assertEquals(0, start(20, 0).getRetcode());
        avatar.getProudSkillList().clear();
        trip().setStartTime(Utils.getCurrentSeconds() - 72001);
        assertEquals(0, claim().getRetcode());
        assertEquals(10, player.getInventory().getItemCountById(100001));
    }

    @Test
    void inactivePassiveDoesNotGrantBonus() throws Exception {
        avatar(322301);
        avatar.getProudSkillList().clear();
        assertEquals(0, start(20, 0).getRetcode());
        assertEquals(0f, trip().toProto().getShortenRatio());
    }

    @Test
    void bonusCountsParticipateInCapacityPreflight() throws Exception {
        avatar(602301);
        location(2, 20);
        assertEquals(0, start(20, 0).getRetcode());
        assertTrue(player.getInventory().addItem(100001, 992));
        trip().setStartTime(Utils.getCurrentSeconds() - 72001);
        assertNotEquals(0, claim().getRetcode());
        assertEquals(992, player.getInventory().getItemCountById(100001));
        assertNotNull(trip());
    }

    @Test
    void repeatedClaimCannotPayBonusAgain() throws Exception {
        avatar(602301);
        location(2, 20);
        assertEquals(0, start(20, 0).getRetcode());
        trip().setStartTime(Utils.getCurrentSeconds() - 72001);
        assertEquals(0, claim().getRetcode());
        assertNotEquals(0, claim().getRetcode());
        assertEquals(10, player.getInventory().getItemCountById(100001));
    }

    @Test
    void startReplyUsesSequenceAndPublishesAuthoritativeBasicInfo() throws Exception {
        avatar(322301);
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionStartReq,
                        AvatarExpeditionStartReq.newBuilder()
                                .addBasicInfoList(
                                        _AvatarExpeditionBasicInfo.newBuilder()
                                                .setAvatarGuid(avatar.getGuid())
                                                .setExpId(101)
                                                .setHourTime(20)
                                                .setShortenRatio(0.9f))
                                .build()
                                .toByteArray(),
                        PacketHead.newBuilder().setClientSequenceId(77).build().toByteArray());
        var response = session.responses.get(0);
        assertEquals(77, PacketHead.parseFrom(response.getHeader()).getClientSequenceId());
        var reply = AvatarExpeditionStartRsp.parseFrom(response.getData());
        assertEquals(0.25f, reply.getExpeditionBasicInfoList(0).getShortenRatio());
        assertTrue(
                session.responses.stream()
                        .anyMatch(p -> p.getOpcode() == PacketOpcodes.AvatarExpeditionDataNotify));
    }

    @Test
    void oldOwnedTripBindsResourceBonusOnceBeforeCheckingExpiry() throws Exception {
        avatar(322301);
        player.addExpeditionInfo(avatar.getGuid(), 101, 20, Utils.getCurrentSeconds() - 54001);
        assertEquals(0, claim().getRetcode());
        assertEquals(8, player.getInventory().getItemCountById(100001));
    }

    @Test
    void legacyRefreshAppliesSnapshotAndMarksShortTripFinishedWithoutGranting() throws Exception {
        avatar(322301);
        player.addExpeditionInfo(avatar.getGuid(), 101, 20, Utils.getCurrentSeconds() - 54001);
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new emu.grasscutter.server.packet.recv.HandlerAvatarExpeditionAllDataReq()
                .handle(session, new byte[0], new byte[0]);
        assertEquals(2, trip().getState());
        assertEquals(0.25f, trip().toProto().getShortenRatio());
        assertEquals(0, player.getInventory().getItemCountById(100001));
    }

    @Test
    void unrelatedDepotCannotUseInjectedPassiveId() throws Exception {
        ordinaryAvatar();
        avatar.getProudSkillList().add(322301);
        assertEquals(0, start(20, 0).getRetcode());
        assertEquals(0f, trip().toProto().getShortenRatio());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "1.0", "-0.1", "0.00001"})
    void invalidOrUnrepresentableShorteningDefinitionGrantsNoEffect(String rate) throws Exception {
        avatar(322301);
        put(
                GameData.getProudSkillDataMap(),
                322301,
                gson.fromJson(
                        "{proudSkillId:322301,proudSkillGroupId:3223,level:1,lifeEffectType:'PROUD_EFFECT_EXPEDITION_SHORTEN_TIME',lifeEffectParams:['1','"
                                + rate
                                + "']}",
                        ProudSkillData.class));
        assertEquals(0, start(20, 0).getRetcode());
        assertEquals(0f, trip().toProto().getShortenRatio());
    }

    @Test
    void zeroProbabilityCannotGenerateExtraRewards() throws Exception {
        avatar(602301);
        location(2, 20);
        put(
                GameData.getProudSkillDataMap(),
                602301,
                gson.fromJson(
                        "{proudSkillId:602301,proudSkillGroupId:6023,level:1,lifeEffectType:'PROUD_EFFECT_EXPEDITION_EXTRA_OUTPUT',lifeEffectParams:['2','20','0','0.25']}",
                        ProudSkillData.class));
        assertEquals(0, start(20, 0).getRetcode());
        trip().setStartTime(Utils.getCurrentSeconds() - 72001);
        var result = claim();
        assertEquals(0, result.getRetcode());
        assertEquals(0, result.getExpeditionRewardList(0).getExtraItemListCount());
    }

    @Test
    void basePlusBonusCannotOverflowCombinedCount() throws Exception {
        avatar(602301);
        location(2, 20);
        var preview =
                gson.fromJson(
                        "{id:10001,previewItems:[{id:100001,count:'2147483647'}]}",
                        RewardPreviewData.class);
        preview.onLoad();
        put(GameData.getRewardPreviewDataMap(), 10001, preview);
        put(
                GameData.getItemDataMap(),
                100001,
                gson.fromJson(
                        "{id:100001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:2147483647}",
                        ItemData.class));
        assertEquals(0, start(20, 0).getRetcode());
        trip().setStartTime(Utils.getCurrentSeconds() - 72001);
        assertNotEquals(0, claim().getRetcode());
        assertEquals(0, player.getInventory().getItemCountById(100001));
        assertNotNull(trip());
    }

    @dev.morphia.annotations.Entity(value = "expedition_talent_test", useDiscriminator = false)
    static class StoredTrip {
        @dev.morphia.annotations.Id String id;
        ExpeditionInfo trip;
    }

    @Test
    void bonusSnapshotSurvivesRealMongoCodecAndTalentChanges() throws Exception {
        avatar(322301);
        assertEquals(0, start(20, 0).getRetcode());
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String database =
                "lunagc_test_expedition140_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, database);
            store.getMapper().map(StoredTrip.class);
            try {
                var record = new StoredTrip();
                record.id = "player1";
                record.trip = trip();
                store.save(record);
                var loaded = store.find(StoredTrip.class).first();
                assertNotNull(loaded);
                assertEquals(0.25f, loaded.trip.toProto().getShortenRatio());
                player.getExpeditionInfo().put(avatar.getGuid(), loaded.trip);
                avatar.getProudSkillList().clear();
                trip().setStartTime(Utils.getCurrentSeconds() - 54001);
                assertEquals(0, claim().getRetcode());
            } finally {
                assertTrue(database.startsWith("lunagc_test_expedition140_"));
                client.getDatabase(database).drop();
            }
        }
    }
}

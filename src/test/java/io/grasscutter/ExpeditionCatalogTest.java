package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamStringData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.server.DropTableExcelConfigData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.expedition.ExpeditionSystem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionAllDataRspOuterClass.AvatarExpeditionAllDataRsp;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardReqOuterClass.AvatarExpeditionGetRewardReq;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto.AvatarExpeditionStartReqOuterClass.AvatarExpeditionStartReq;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;
import emu.grasscutter.net.proto._AvatarExpeditionBasicInfoOuterClass._AvatarExpeditionBasicInfo;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionAllDataRsp;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Current 48-place sheet, quantity ranges, ownership and registered start/claim paths. */
class ExpeditionCatalogTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private static List<JsonObject> locations;
    private static List<JsonObject> previewRows;
    private static List<DropTableExcelConfigData> dropRows;
    private static List<ItemData> itemRows;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private ExpeditionSystem system;
    private final List<Avatar> avatars = new ArrayList<>();
    private Object previousDatastore;
    private java.lang.reflect.Field datastore;

    @BeforeAll
    static void resources() throws Exception {
        locations =
                JsonUtils.loadToList(
                        EXCEL.resolve("ExpeditionDataExcelConfigData.json"), JsonObject.class);
        assertEquals(48, locations.size());
        var previewIds = new HashSet<Integer>();
        var dropIds = new HashSet<Integer>();
        locations.forEach(
                l ->
                        l.getAsJsonArray("timeRewardVec")
                                .forEach(
                                        e -> {
                                            var d = e.getAsJsonObject();
                                            previewIds.add(d.get("rewardPreview").getAsInt());
                                            dropIds.add(d.get("reward_drop_id").getAsInt());
                                        }));
        previewRows =
                JsonUtils.loadToList(
                                EXCEL.resolve("RewardPreviewExcelConfigData.json"),
                                JsonObject.class)
                        .stream()
                        .filter(p -> previewIds.contains(p.get("id").getAsInt()))
                        .toList();
        dropRows =
                JsonUtils.loadToList(
                                Path.of("resources/Server/DropTableExcelConfigData.json"),
                                DropTableExcelConfigData.class)
                        .stream()
                        .filter(d -> dropIds.contains(d.getId()))
                        .toList();
        var itemIds = new HashSet<Integer>();
        previewRows.forEach(
                p ->
                        p.getAsJsonArray("previewItems")
                                .forEach(
                                        e -> {
                                            var i = e.getAsJsonObject();
                                            if (i.has("id") && i.get("id").getAsInt() > 0)
                                                itemIds.add(i.get("id").getAsInt());
                                        }));
        itemRows =
                JsonUtils.loadToList(EXCEL.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(i -> itemIds.contains(i.getId()))
                        .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        var points = new HashSet<Integer>();
        for (var location : locations)
            for (var element : location.getAsJsonArray("openConditionVec")) {
                var condition = element.getAsJsonObject();
                if (condition.has("type")
                        && condition.get("type").getAsString().equals("EXP_OPEN_COND_POINT"))
                    points.add(condition.get("para").getAsInt());
            }
        player.getUnlockedScenePoints().put(3, points);
        try {
            var definition = Class.forName("emu.grasscutter.data.excels.ExpeditionData");
            var map = (Map) GameData.getMapByResourceDef(definition);
            var before = new HashMap(map);
            map.clear();
            restore.add(
                    () -> {
                        map.clear();
                        map.putAll(before);
                    });
            for (var row : locations) {
                var value = (GameResource) gson.fromJson(row, definition);
                value.onLoad();
                map.put(value.getId(), value);
            }
        } catch (ClassNotFoundException oldImplementation) {
            /* Baseline lacks this resource type. */
        }
        for (var row : previewRows) {
            var preview = gson.fromJson(row, RewardPreviewData.class);
            preview.onLoad();
            put(GameData.getRewardPreviewDataMap(), preview.getId(), preview);
        }
        for (var drop : dropRows)
            put(GameData.getDropTableExcelConfigDataMap(), drop.getId(), drop);
        for (var item : itemRows) put(GameData.getItemDataMap(), item.getId(), item);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        var depot =
                gson.fromJson(
                        "{id:7301,skills:[],energySkill:0,talents:[]}", AvatarSkillDepotData.class);
        depot.onLoad();
        for (int i = 0; i < 3; i++) {
            var avatar = new SkillTalentLevelTest.RecordingAvatar(player, 10000073 + i, depot);
            assertTrue(player.getAvatars().addAvatar(avatar));
            avatars.add(avatar);
        }
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

    private _AvatarExpeditionBasicInfo basic(int avatar, int location, int hours) {
        return _AvatarExpeditionBasicInfo.newBuilder()
                .setAvatarGuid(avatars.get(avatar).getGuid())
                .setExpId(location)
                .setHourTime(hours)
                .build();
    }

    private InvestigationRewardTest.ClaimSession request(int opcode, byte[] body) throws Exception {
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new GameServerPacketHandler(PacketHandler.class).handle(session, opcode, new byte[0], body);
        return session;
    }

    private AvatarExpeditionStartRsp start(_AvatarExpeditionBasicInfo... entries) throws Exception {
        var builder = AvatarExpeditionStartReq.newBuilder();
        for (var entry : entries) builder.addBasicInfoList(entry);
        var session =
                request(PacketOpcodes.AvatarExpeditionStartReq, builder.build().toByteArray());
        return AvatarExpeditionStartRsp.parseFrom(session.responses.get(0).getData());
    }

    private AvatarExpeditionGetRewardRsp claim(int avatar) throws Exception {
        var session =
                request(
                        PacketOpcodes.AvatarExpeditionGetRewardReq,
                        AvatarExpeditionGetRewardReq.newBuilder()
                                .setAvatarGuid(avatars.get(avatar).getGuid())
                                .build()
                                .toByteArray());
        return AvatarExpeditionGetRewardRsp.parseFrom(session.responses.get(0).getData());
    }

    @Test
    void rangeConversionDoesNotDestroySourceMinimum() {
        var row = gson.fromJson("{id:101001,count:'4;5'}", ItemParamStringData.class);
        assertEquals(5, row.toItemParamData().getCount());
        assertEquals("4;5", row.getCount());
        assertEquals(5, row.toItemParamData().getCount());
    }

    @Test
    void packetCatalogIncludesAllResourceLocationsInsteadOfFixedTwentyFour() throws Exception {
        var packet = new PacketAvatarExpeditionAllDataRsp(Map.of(), 5);
        var parsed = AvatarExpeditionAllDataRsp.parseFrom(packet.getData());
        assertEquals(48, parsed.getOpenExpeditionListCount());
        assertTrue(parsed.getOpenExpeditionListList().contains(806));
    }

    @Test
    void validOwnedAvatarStartsResourceTrip() throws Exception {
        assertEquals(0, start(basic(0, 101, 4)).getRetcode());
        var entry = player.getExpeditionInfo(avatars.get(0).getGuid());
        assertEquals(101, entry.getExpId());
        assertEquals(4, entry.getHourTime());
        assertEquals(1, entry.getState());
    }

    @Test
    void newRegionStartsAndClaimsOriginalQuantityRanges() throws Exception {
        assertEquals(0, start(basic(0, 806, 4)).getRetcode());
        player.getExpeditionInfo(avatars.get(0).getGuid())
                .setStartTime(Utils.getCurrentSeconds() - 14401);
        var result = claim(0);
        assertEquals(0, result.getRetcode());
        var items = result.getExpeditionRewardList(0).getItemListList();
        assertEquals(
                Set.of(101278, 100001),
                items.stream()
                        .map(i -> i.getItemId())
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(items.stream().allMatch(i -> i.getCount() == 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 501, 601, 701, 801, 806})
    void allNewCityAreasCanActuallyClaimTheirConfiguredRewards(int location) throws Exception {
        // These claims exercise already running trips; availability is checked separately.
        player.addExpeditionInfo(
                avatars.get(0).getGuid(), location, 4, Utils.getCurrentSeconds() - 14401);
        var result = claim(0);
        assertEquals(0, result.getRetcode());
        assertFalse(result.getExpeditionRewardList(0).getItemListList().isEmpty());
    }

    @Test
    void missingLocationCannotStart() throws Exception {
        assertNotEquals(0, start(basic(0, 999999, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1, 5, Integer.MAX_VALUE})
    void unsupportedDurationCannotStart(int hours) throws Exception {
        assertNotEquals(0, start(basic(0, 101, hours)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void unownedAvatarCannotStart() throws Exception {
        var row =
                _AvatarExpeditionBasicInfo.newBuilder()
                        .setAvatarGuid(999)
                        .setExpId(101)
                        .setHourTime(4)
                        .build();
        assertNotEquals(0, start(row).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void trialAvatarCannotStart() throws Exception {
        avatars.get(0).setTrialAvatarId(999);
        assertNotEquals(0, start(basic(0, 101, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void duplicateAvatarRejectsWholeBatch() throws Exception {
        assertNotEquals(0, start(basic(0, 101, 4), basic(0, 102, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void duplicatePlaceRejectsWholeBatch() throws Exception {
        assertNotEquals(0, start(basic(0, 101, 4), basic(1, 101, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void invalidSecondEntryDoesNotStartFirst() throws Exception {
        assertNotEquals(0, start(basic(0, 101, 4), basic(1, 999999, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void quotaCannotBeExceededByBatch() throws Exception {
        assertEquals(2, player.getExpeditionLimit());
        assertNotEquals(
                0, start(basic(0, 101, 4), basic(1, 102, 4), basic(2, 103, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void existingTripCannotBeOverwritten() throws Exception {
        assertEquals(0, start(basic(0, 101, 4)).getRetcode());
        var original = player.getExpeditionInfo(avatars.get(0).getGuid());
        assertNotEquals(0, start(basic(0, 102, 4)).getRetcode());
        assertSame(original, player.getExpeditionInfo(avatars.get(0).getGuid()));
    }

    @Test
    void occupiedPlaceCannotTakeAnotherAvatar() throws Exception {
        assertEquals(0, start(basic(0, 101, 4)).getRetcode());
        assertNotEquals(0, start(basic(1, 101, 4)).getRetcode());
        assertEquals(1, player.getExpeditionInfo().size());
    }

    @Test
    void rankGateIsChecked() throws Exception {
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 13);
        assertNotEquals(0, start(basic(0, 101, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void pointGateIsChecked() throws Exception {
        player.getUnlockedScenePoints().clear();
        assertNotEquals(0, start(basic(0, 101, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void malformedStartStillReceivesFailure() throws Exception {
        var session = request(PacketOpcodes.AvatarExpeditionStartReq, new byte[] {8, (byte) 0x80});
        assertEquals(1, session.responses.size());
        assertNotEquals(
                0,
                AvatarExpeditionStartRsp.parseFrom(session.responses.get(0).getData())
                        .getRetcode());
    }

    @Test
    void emptyStartIsNotSuccess() throws Exception {
        assertNotEquals(0, start().getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void parallelStartsRespectQuotaAndOccupancy() throws Exception {
        var pool = Executors.newFixedThreadPool(3);
        try {
            var jobs = new ArrayList<Future<AvatarExpeditionStartRsp>>();
            for (int i = 0; i < 3; i++) {
                int index = i;
                jobs.add(pool.submit(() -> start(basic(index, 101 + index, 4))));
            }
            int successes = 0;
            for (var job : jobs) if (job.get(15, TimeUnit.SECONDS).getRetcode() == 0) successes++;
            assertEquals(2, successes);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, player.getExpeditionInfo().size());
    }

    @Test
    void everyResourceDurationResolvesOriginalPreviewItemsAndRanges() throws Exception {
        var previews = new HashMap<Integer, JsonObject>();
        for (var row : previewRows) previews.put(row.get("id").getAsInt(), row);
        int checked = 0;
        for (var location : locations)
            for (var element : location.getAsJsonArray("timeRewardVec")) {
                var duration = element.getAsJsonObject();
                int hours = duration.get("htime").getAsInt();
                var definitions = system.getResolvedRewardDataList(location.get("ID").getAsInt());
                assertNotNull(definitions);
                var matching = definitions.stream().filter(d -> d.getHourTime() == hours).toList();
                assertEquals(1, matching.size());
                var expected = new TreeMap<Integer, List<Integer>>();
                for (var item :
                        previews.get(duration.get("rewardPreview").getAsInt())
                                .getAsJsonArray("previewItems")) {
                    var row = item.getAsJsonObject();
                    if (!row.has("id") || row.get("id").getAsInt() == 0) continue;
                    var pair = row.get("count").getAsString().split(";");
                    expected.put(
                            row.get("id").getAsInt(),
                            List.of(
                                    Integer.parseInt(pair[0]),
                                    Integer.parseInt(pair[pair.length - 1])));
                }
                var actual = new TreeMap<Integer, List<Integer>>();
                matching.get(0)
                        .getExpeditionRewardData()
                        .forEach(
                                row ->
                                        actual.put(
                                                row.getItemId(),
                                                List.of(row.getMinCount(), row.getMaxCount())));
                assertEquals(
                        expected, actual, "location " + location.get("ID") + " hours " + hours);
                checked++;
            }
        assertEquals(192, checked);
    }

    @Test
    void currentMiningPreviewAddsIronMissingFromOldDrop() throws Exception {
        assertEquals(0, start(basic(0, 101, 20)).getRetcode());
        player.getExpeditionInfo(avatars.get(0).getGuid())
                .setStartTime(Utils.getCurrentSeconds() - 72001);
        var result = claim(0);
        assertEquals(0, result.getRetcode());
        var iron =
                result.getExpeditionRewardList(0).getItemListList().stream()
                        .filter(i -> i.getItemId() == 101001)
                        .findFirst()
                        .orElseThrow();
        assertTrue(iron.getCount() >= 7 && iron.getCount() <= 8);
    }

    @Test
    void actualUserFileOverridesLocationWhileOtherRegionsRemainAvailable() throws Exception {
        var path = FileUtils.getDataUserPath("ExpeditionReward.json");
        byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
        Files.createDirectories(path.getParent());
        try {
            Files.writeString(
                    path,
                    "[{expId:806,expeditionRewardDataList:[{hourTime:4,expeditionRewardData:[{itemId:100001,minCount:7,maxCount:7}]}]}]");
            system.load();
            assertEquals(4, system.getResolvedRewardDataList(801).size());
            assertEquals(0, start(basic(0, 806, 4)).getRetcode());
            player.getExpeditionInfo(avatars.get(0).getGuid())
                    .setStartTime(Utils.getCurrentSeconds() - 14401);
            var result = claim(0);
            assertEquals(0, result.getRetcode());
            assertEquals(1, result.getExpeditionRewardList(0).getItemListCount());
            assertEquals(7, result.getExpeditionRewardList(0).getItemList(0).getCount());
        } finally {
            if (original == null) Files.delete(path);
            else Files.write(path, original);
            system.load();
        }
    }

    @Test
    void legacyHandlerReportsOnlyLocationsWhoseConditionsAreSatisfied() throws Exception {
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 13);
        var session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new emu.grasscutter.server.packet.recv.HandlerAvatarExpeditionAllDataReq()
                .handle(session, new byte[0], new byte[0]);
        assertEquals(
                0,
                AvatarExpeditionAllDataRsp.parseFrom(session.responses.get(0).getData())
                        .getOpenExpeditionListCount());
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        session =
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player);
        new emu.grasscutter.server.packet.recv.HandlerAvatarExpeditionAllDataReq()
                .handle(session, new byte[0], new byte[0]);
        assertEquals(
                47,
                AvatarExpeditionAllDataRsp.parseFrom(session.responses.get(0).getData())
                        .getOpenExpeditionListCount());
    }

    @Test
    void malformedServerProbabilityDoesNotSilentlyBecomePreviewReward() throws Exception {
        put(
                GameData.getDropTableExcelConfigDataMap(),
                205010100,
                gson.fromJson(
                        "{id:205010100,randomType:1,dropVec:[{itemId:101001,countRange:'4;5',weight:5000}]}",
                        DropTableExcelConfigData.class));
        assertNotEquals(0, start(basic(0, 101, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void malformedPreviewRangeForMissingDropCannotStart() throws Exception {
        var preview =
                gson.fromJson(
                        "{id:1207,previewItems:[{id:100001,count:'5;2'}]}",
                        RewardPreviewData.class);
        preview.onLoad();
        put(GameData.getRewardPreviewDataMap(), 1207, preview);
        assertNotEquals(0, start(basic(0, 806, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @Test
    void missingNextPreviewCannotStartEvenWhenPlaceExists() throws Exception {
        var old = GameData.getRewardPreviewDataMap().remove(1207);
        restore.add(() -> GameData.getRewardPreviewDataMap().put(1207, old));
        assertNotEquals(0, start(basic(0, 806, 4)).getRetcode());
        assertTrue(player.getExpeditionInfo().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEST_STATE_FINISHED", "FINISHED"})
    void completedQuestGateAcceptsPersistedEnumAliases(String state) throws Exception {
        var location =
                locations.stream()
                        .filter(
                                l ->
                                        java.util.stream.StreamSupport.stream(
                                                        l.getAsJsonArray("openConditionVec")
                                                                .spliterator(),
                                                        false)
                                                .anyMatch(
                                                        e ->
                                                                e.getAsJsonObject().has("type")
                                                                        && e.getAsJsonObject()
                                                                                .get("type")
                                                                                .getAsString()
                                                                                .equals(
                                                                                        "EXP_OPEN_COND_QUEST")))
                        .findFirst()
                        .orElseThrow();
        var condition =
                java.util.stream.StreamSupport.stream(
                                location.getAsJsonArray("openConditionVec").spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .filter(
                                c ->
                                        c.has("type")
                                                && c.get("type")
                                                        .getAsString()
                                                        .equals("EXP_OPEN_COND_QUEST"))
                        .findFirst()
                        .orElseThrow();
        int questId = condition.get("para").getAsInt();
        var quest = new emu.grasscutter.game.quest.GameQuest();
        quest.state = emu.grasscutter.game.quest.enums.QuestState.valueOf(state);
        var field = Player.class.getDeclaredField("questManager");
        field.setAccessible(true);
        var original = field.get(player);
        var manager = new emu.grasscutter.game.quest.QuestManager(player);
        var main = new emu.grasscutter.game.quest.GameMainQuest();
        var childField =
                emu.grasscutter.game.quest.GameMainQuest.class.getDeclaredField("childQuests");
        childField.setAccessible(true);
        childField.set(main, new HashMap<Integer, emu.grasscutter.game.quest.GameQuest>());
        main.getChildQuests().put(questId, quest);
        manager.getMainQuests().put(396, main);
        put(
                GameData.getQuestDataMap(),
                questId,
                gson.fromJson(
                        "{id:" + questId + ",mainId:396}",
                        emu.grasscutter.data.excels.quest.QuestData.class));
        field.set(player, manager);
        try {
            assertEquals(0, start(basic(0, location.get("ID").getAsInt(), 4)).getRetcode());
        } finally {
            field.set(player, original);
        }
    }
}

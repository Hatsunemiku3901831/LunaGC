package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.forging.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateReqOuterClass.ForgeQueueManipulateReq;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeStartReqOuterClass.ForgeStartReq;
import emu.grasscutter.net.proto.ForgeStartRspOuterClass.ForgeStartRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Actual passive/depot and forge rows, with joint weapon/refund delivery and frozen benefits. */
class ForgeTalentFlowTest {
    private static final Path EXCEL = Path.of("/resources/ExcelBinOutput");
    private AbilityTestFixture environment;
    private TalentPlayer player;
    private Avatar avatar;
    private GameServerPacketHandler dispatcher;
    private final Gson gson = new Gson();
    private final List<Runnable> restore = new ArrayList<>();
    private java.lang.reflect.Field datastore;
    private Object oldDatastore;
    private int saves, failSave;
    private static List<AvatarSkillDepotData> depots;
    private static List<ForgeData> recipes;
    private static List<ProudSkillData> skills;
    private static List<ItemData> materials, weapons;
    private static List<CompoundBoostData> minerals;

    @BeforeAll
    static void resources() throws Exception {
        skills =
                JsonUtils.loadToList(
                                EXCEL.resolve("ProudSkillExcelConfigData.json"),
                                ProudSkillData.class)
                        .stream()
                        .filter(
                                skill ->
                                        Set.of(162301, 302301, 372301, 22501, 32501)
                                                .contains(skill.getId()))
                        .toList();
        depots =
                JsonUtils.loadToList(
                        EXCEL.resolve("AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class);
        recipes = JsonUtils.loadToList(EXCEL.resolve("ForgeExcelConfigData.json"), ForgeData.class);
        materials =
                JsonUtils.loadToList(EXCEL.resolve("MaterialExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(
                                item ->
                                        Set.of(101002, 101003, 101102, 101103, 101105, 202)
                                                .contains(item.getId()))
                        .toList();
        weapons =
                JsonUtils.loadToList(EXCEL.resolve("WeaponExcelConfigData.json"), ItemData.class)
                        .stream()
                        .filter(item -> Set.of(12406, 15406, 13402).contains(item.getId()))
                        .toList();
        minerals =
                JsonUtils.loadToList(
                        EXCEL.resolve("CompoundBoostExcelConfigData.json"),
                        CompoundBoostData.class);
    }

    static class TalentPlayer extends ForgeStartFlowTest.ForgePlayer {
        boolean refuseOreReturn;
        final Inventory talentInventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item) {
                        if (refuseOreReturn && item.getItemId() == 101002) return false;
                        return super.addItem(item);
                    }
                };

        @Override
        public Inventory getInventory() {
            return talentInventory;
        }
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new TalentPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, 3);
        player.setMora(10000);
        player.setForgePoints(300000);
        for (var original : skills) {
            var serialized = gson.toJsonTree(original).getAsJsonObject();
            serialized.remove("paramListMap"); // Runtime fastutil cache is rebuilt by onLoad().
            var skill = gson.fromJson(serialized, ProudSkillData.class);
            skill.onLoad();
            put(GameData.getProudSkillDataMap(), skill.getId(), skill);
        }
        for (var recipe : recipes)
            if (Set.of(13003, 13005, 13009).contains(recipe.getId())) {
                put(GameData.getForgeDataMap(), recipe.getId(), recipe);
                player.getUnlockedForgingBlueprints().add(recipe.getId());
            }
        for (var item : materials) put(GameData.getItemDataMap(), item.getId(), item);
        for (var item : weapons) put(GameData.getItemDataMap(), item.getId(), item);
        for (var ore : minerals) put(GameData.getCompoundBoostDataMap(), ore.getId(), ore);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    || method.getName().equals("delete")) {
                                if (args.length == 2 && ++saves == failSave)
                                    throw new IllegalStateException("Confirmation fault");
                                return method.getName().equals("delete")
                                        ? com.mongodb.client.result.DeleteResult.acknowledged(1)
                                        : args[0];
                            }
                            return null;
                        }));
        for (int id : List.of(101002, 101003)) assertTrue(player.getInventory().addItem(id, 1000));
        for (int id : List.of(101102, 101103, 101105))
            assertTrue(player.getInventory().addItem(id, 10));
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        environment.close();
    }

    private void avatar(int passive) throws Exception {
        int group = passive / 100;
        var depot =
                depots.stream()
                        .filter(
                                d ->
                                        d.getInherentProudSkillOpens().stream()
                                                .anyMatch(p -> p.getProudSkillGroupId() == group))
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        avatar =
                new SkillTalentLevelTest.RecordingAvatar(
                        player, 10000000 + depot.getId() / 100, depot);
        assertTrue(player.getAvatars().addAvatar(avatar));
        assertTrue(avatar.getProudSkillList().contains(passive));
    }

    private int recipeFor(int passive) {
        return switch (passive) {
            case 162301 -> 13005;
            case 302301 -> 13009;
            case 372301 -> 13003;
            default -> throw new IllegalArgumentException();
        };
    }

    private void request(int opcode, byte[] body) {
        dispatcher.handle(player.getSession(), opcode, new byte[0], body);
    }

    private int startCode(int recipe, int actor) throws Exception {
        request(
                PacketOpcodes.ForgeStartReq,
                ForgeStartReq.newBuilder()
                        .setForgeId(recipe)
                        .setForgeCount(1)
                        .setAvatarId(actor)
                        .build()
                        .toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeStartRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ForgeStartRsp.parseFrom(packet.getData()).getRetcode();
    }

    private ActiveForgeData start(int passive) throws Exception {
        assertEquals(0, startCode(recipeFor(passive), avatar.getAvatarId()));
        saves = 0;
        return player.getActiveForges().get(0);
    }

    private ForgeQueueManipulateRsp take() throws Exception {
        request(
                PacketOpcodes.ForgeQueueManipulateReq,
                ForgeQueueManipulateReq.newBuilder().setForgeQueueId(1).build().toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ForgeQueueManipulateRsp.parseFrom(packet.getData());
    }

    private void ready(ActiveForgeData queue) {
        queue.setStartTime(Utils.getCurrentSeconds() - queue.getForgeTime() - 1);
    }

    private int stock(int id) {
        return player.getInventory().getItemCountById(id);
    }

    private int version(ActiveForgeData queue) {
        return assertDoesNotThrow(
                () -> (int) queue.getClass().getMethod("getBonusVersion").invoke(queue));
    }

    private ForgeQueueManipulateRsp collect(int passive) throws Exception {
        avatar(passive);
        var queue = start(passive);
        assertEquals(1, version(queue));
        ready(queue);
        return take();
    }

    @ParameterizedTest
    @ValueSource(ints = {162301, 302301, 372301})
    void eachActualPassiveReturnsOreForItsDeclaredWeaponType(int passive) throws Exception {
        var result = collect(passive);
        assertEquals(0, result.getRetcode());
        assertEquals(1, result.getOutputItemListCount());
        assertEquals(2, result.getReturnItemListCount());
        assertEquals(0, result.getExtraOutputItemListCount());
        assertEquals(957, stock(101002));
        assertEquals(957, stock(101003));
        assertTrue(result.getReturnItemListList().stream().allMatch(h -> h.getCount() == 7));
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void billetMoraAndForgePointsAreNotReturnedByPassive() throws Exception {
        collect(162301);
        assertEquals(9, stock(101103));
        assertEquals(9500, player.getMora());
        assertEquals(300000, player.getForgePoints());
        assertTrue(take().getReturnItemListList().isEmpty());
    }

    @Test
    void mismatchedWeaponTypeDoesNotActivatePassive() throws Exception {
        avatar(162301);
        assertEquals(0, startCode(13003, avatar.getAvatarId()));
        var queue = player.getActiveForges().get(0);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101002));
        assertTrue(take().getReturnItemListList().isEmpty());
    }

    @Test
    void noAssistantGivesNoBonus() throws Exception {
        assertEquals(0, startCode(13005, 0));
        var queue = player.getActiveForges().get(0);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101003));
    }

    @Test
    void unknownAssistantIsRejectedBeforeCosts() throws Exception {
        assertNotEquals(0, startCode(13005, 10000016));
        assertEquals(1000, stock(101002));
        assertEquals(10, stock(101103));
        assertTrue(player.getActiveForges().isEmpty());
    }

    @Test
    void foreignProudSkillInsertedIntoListCannotActivateBonus() throws Exception {
        avatar(162301);
        avatar.getProudSkillList().add(302301);
        assertEquals(0, startCode(13009, avatar.getAvatarId()));
        var queue = player.getActiveForges().get(0);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101003));
    }

    @Test
    void trialAssistantGetsNoBonus() throws Exception {
        avatar(162301);
        avatar.setTrialAvatarId(1);
        var queue = start(162301);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101003));
    }

    @Test
    void removedPassiveBeforeStartGivesNoBenefit() throws Exception {
        avatar(162301);
        avatar.getProudSkillList().clear();
        var queue = start(162301);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101002));
    }

    @Test
    void benefitIsFrozenBeforeLaterSkillAndResourceChanges() throws Exception {
        avatar(162301);
        var queue = start(162301);
        avatar.getProudSkillList().clear();
        GameData.getProudSkillDataMap().remove(162301);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(957, stock(101003));
    }

    @Test
    void malformedRateDoesNotCreateBonus() throws Exception {
        avatar(162301);
        var field = ProudSkillData.class.getDeclaredField("lifeEffectParams");
        field.setAccessible(true);
        var data = GameData.getProudSkillDataMap().get(162301);
        var old = field.get(data);
        restore.add(
                () -> {
                    try {
                        field.set(data, old);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        field.set(data, List.of("10000", "5", "NaN"));
        var queue = start(162301);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101003));
    }

    @Test
    void duplicatePassiveDoesNotStack() throws Exception {
        avatar(162301);
        avatar.getProudSkillList().add(162301);
        var queue = start(162301);
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(957, stock(101002));
    }

    @Test
    void blockedOreReturnPreventsAnyMainOutput() throws Exception {
        avatar(162301);
        var queue = start(162301);
        var data = GameData.getItemDataMap().get(101003);
        var field = ItemData.class.getDeclaredField("stackLimit");
        field.setAccessible(true);
        int old = field.getInt(data);
        restore.add(
                () -> {
                    try {
                        field.setInt(data, old);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        field.setInt(data, 956);
        ready(queue);
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, stock(12406));
        assertEquals(950, stock(101003));
        assertEquals(0, queue.getOutputState());
        assertTrue(player.getInventory().removeItem(101003, 1));
        assertEquals(0, take().getRetcode());
        assertEquals(1, stock(12406));
        assertEquals(956, stock(101003));
    }

    @Test
    void missingOreDefinitionPreventsMainWeaponDelivery() throws Exception {
        avatar(162301);
        var queue = start(162301);
        GameData.getItemDataMap().remove(101003);
        ready(queue);
        assertNotEquals(0, take().getRetcode());
        assertEquals(0, stock(12406));
        assertEquals(0, queue.getOutputState());
    }

    @Test
    void partiallyDeliveredWeaponAndOreCannotBeRepeated() throws Exception {
        avatar(162301);
        var queue = start(162301);
        ready(queue);
        player.refuseOreReturn = true;
        assertNotEquals(0, take().getRetcode());
        assertEquals(1, stock(12406));
        assertEquals(957, stock(101003));
        assertEquals(950, stock(101002));
        assertEquals(1, queue.getOutputState());
        player.refuseOreReturn = false;
        assertNotEquals(0, take().getRetcode());
        assertEquals(1, stock(12406));
        assertEquals(957, stock(101003));
    }

    @Test
    void cancellingDoesNotAddPassiveReturnOnTopOfPaidCosts() throws Exception {
        avatar(162301);
        start(162301);
        request(
                PacketOpcodes.ForgeQueueManipulateReq,
                ForgeQueueManipulateReq.newBuilder()
                        .setForgeQueueId(1)
                        .setManipulateTypeValue(1)
                        .build()
                        .toByteArray());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ForgeQueueManipulateRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertEquals(0, ForgeQueueManipulateRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(1000, stock(101003));
        assertEquals(1000, stock(101002));
        assertEquals(10, stock(101103));
        assertEquals(10000, player.getMora());
    }

    @Test
    void receiptFailureKeepsOneRecordedBenefit() throws Exception {
        avatar(162301);
        var queue = start(162301);
        ready(queue);
        failSave = 3;
        assertNotEquals(0, take().getRetcode());
        int ore = stock(101003), weapon = stock(12406);
        failSave = 0;
        assertNotEquals(0, take().getRetcode());
        assertEquals(ore, stock(101003));
        assertEquals(weapon, stock(12406));
        assertNotEquals(0, queue.getOutputState());
    }

    @dev.morphia.annotations.Entity(value = "forge_talent_test", useDiscriminator = false)
    static class StoredForge {
        @dev.morphia.annotations.Id String id;
        ActiveForgeData queue;
    }

    @Test
    void benefitSurvivesMongoAndMissingSkillData() throws Exception {
        avatar(162301);
        var queue = start(162301);
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        String name = "lunagc_test_forgetalent151_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(StoredForge.class);
            try {
                var row = new StoredForge();
                row.id = "queue";
                row.queue = queue;
                store.save(row);
                var loaded = store.find(StoredForge.class).first().queue;
                assertEquals(1, version(loaded));
                player.getActiveForges().set(0, loaded);
                GameData.getProudSkillDataMap().remove(162301);
                ready(loaded);
                assertEquals(0, take().getRetcode());
                assertEquals(957, stock(101003));
            } finally {
                assertTrue(name.startsWith("lunagc_test_forgetalent151_"));
                client.getDatabase(name).drop();
            }
        }
    }

    @Test
    void oldQueueWithoutBenefitFieldDoesNotInventRetroactiveBonus() throws Exception {
        avatar(162301);
        var queue = start(162301);
        try {
            var field = ActiveForgeData.class.getDeclaredField("bonusVersion");
            field.setAccessible(true);
            field.setInt(queue, 0);
        } catch (NoSuchFieldException oldImplementation) {
        }
        ready(queue);
        assertEquals(0, take().getRetcode());
        assertEquals(950, stock(101003));
    }
}

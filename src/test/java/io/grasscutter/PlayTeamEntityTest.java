package io.grasscutter;

import static emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.activity.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.DelScenePlayTeamEntityNotifyOuterClass.DelScenePlayTeamEntityNotify;
import emu.grasscutter.net.proto.SyncScenePlayTeamEntityNotifyOuterClass.SyncScenePlayTeamEntityNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.PacketSyncScenePlayTeamEntityNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

import javax.script.SimpleBindings;

class PlayTeamEntityTest {
    private static final int MAIN = 133001413, GADGET = 70370002;
    private static JsonArray gadgetRows, abilityRows;
    private static JsonObject gadgetConfigs;
    private AbilityTestFixture environment;
    private PlayScene scene;
    private AbilityTestFixture.TestPlayer host, guest;
    private ScenePlayBattleManager battles;
    private MpPlayGroupData entry;
    private MpPlayMatchData match;
    private ScriptLib library;
    private SimpleBindings bindings;
    private final List<Runnable> restore = new ArrayList<>();
    private Workers workers;

    @BeforeAll
    static void resources() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        gadgetRows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/GadgetExcelConfigData.json")))
                        .getAsJsonArray();
        gadgetConfigs =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Gadget/ConfigGadget_Scene_Meteorite.json")))
                        .getAsJsonObject();
        abilityRows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Meteorite.json")))
                        .getAsJsonArray();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        host = environment.player;
        scene =
                new PlayScene(
                        new AbilityTestFixture.TestWorld(
                                environment.scene.getWorld().getServer(), host));
        guest = new AbilityTestFixture.TestPlayer();
        ready(host, 10001, 1);
        ready(guest, 10002, 2);
        battles = scene.getScenePlayBattleManager();
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scene.scripts);
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        entry = row("MpPlayGroupExcelConfigData.json", "playId", MpPlayGroupData.class);
        match = row("MpPlayMatchExcelConfigData.json", "id", MpPlayMatchData.class);
        for (int id : List.of(MAIN, 133001426, 133001427, 133001498)) {
            var group = new SceneGroup();
            group.id = id;
            scene.scripts.loaded.put(id, group);
        }
        for (int id : List.of(70370001, 70370002, 70370003)) installResource(id);
        workers = new Workers();
        scene.packets.clear();
    }

    private <T> T row(String file, String key, Class<T> type) throws Exception {
        var rows =
                JsonParser.parseString(Files.readString(Path.of("resources/ExcelBinOutput", file)))
                        .getAsJsonArray();
        for (var raw : rows)
            if (raw.getAsJsonObject().get(key).getAsInt() == 2)
                return JsonUtils.decode(raw.toString(), type);
        throw new AssertionError(file);
    }

    private void installResource(int id) {
        var raw =
                java.util.stream.StreamSupport.stream(gadgetRows.spliterator(), false)
                        .filter(v -> v.getAsJsonObject().get("id").getAsInt() == id)
                        .findFirst()
                        .orElseThrow();
        var gadget = JsonUtils.decode(raw.toString(), GadgetData.class);
        replace(GameData.getGadgetDataMap(), id, gadget);
        String name = gadget.getJsonName();
        replace(
                GameData.getGadgetConfigData(),
                name,
                JsonUtils.decode(gadgetConfigs.get(name).toString(), ConfigEntityGadget.class));
        var rawAbility =
                java.util.stream.StreamSupport.stream(abilityRows.spliterator(), false)
                        .map(v -> v.getAsJsonObject().getAsJsonObject("Default"))
                        .filter(
                                v ->
                                        v != null
                                                && v.has("abilityName")
                                                && name.equals(v.get("abilityName").getAsString()))
                        .findFirst()
                        .orElseThrow();
        var data = JsonUtils.decode(rawAbility.toString(), AbilityData.class);
        data.initialize();
        replace(GameData.getAbilityDataMap(), name, data);
    }

    private <K, V> void replace(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private void ready(AbilityTestFixture.TestPlayer player, int uid, int peer) {
        player.setUid(uid);
        player.setPeerId(peer);
        player.setScene(scene);
        player.setWorld(scene.getWorld());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
    }

    @AfterEach
    void cleanup() throws Exception {
        battles.close();
        workers.close();
        library.removeCurrentEntity();
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        scene.scripts.onDestroy();
        scene.scripts.drain();
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        environment.close();
    }

    private double lua(String source) throws Exception {
        return ((Number) ScriptLoader.getEngine().eval(source, bindings)).doubleValue();
    }

    private void readyBattle(int type, Collection<Player> members) {
        assertTrue(battles.prepare(entry, match, 2, type, members));
        scene.now.set(1020);
        battles.tick();
        scene.scripts.drain();
        assertEquals(READY, battles.getInfo(MAIN).getState());
    }

    private void start() {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        scene.now.set(1030);
        battles.tick();
        scene.scripts.drain();
        assertEquals(START, battles.getInfo(MAIN).getState());
    }

    private EntityPlayTeam carrier(Player player) {
        return battles.getPlayTeamEntities().stream()
                .filter(e -> e.getPlayer() == player)
                .findFirst()
                .orElseThrow();
    }

    private List<BasePacket> packets(int opcode) {
        return scene.packets.stream().filter(p -> p.getOpcode() == opcode).toList();
    }

    private void assertRemoved(List<EntityPlayTeam> previous) throws Exception {
        assertTrue(battles.getPlayTeamEntities().isEmpty());
        for (var entity : previous) {
            assertNull(scene.getEntityById(entity.getId()));
            assertFalse(entity.isAbilityLifecycleCurrent(0));
            assertTrue(entity.getInstancedAbilities().isEmpty());
        }
        var deleted = DelScenePlayTeamEntityNotify.parseFrom(packets(26830).get(0).getData());
        assertEquals(3, deleted.getSceneId());
        assertEquals(
                previous.stream().map(GameEntity::getId).sorted().toList(),
                deleted.getDelEntityIdListList().stream().sorted().toList());
        assertEquals(1, packets(26830).size());
        assertTrue(packets(2050).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {70370001, 70370002, 70370003})
    void actualMeteoriteConfigCreatesSeparateParticipantOwnedNonspatialCarriers(int gadget)
            throws Exception {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, gadget));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertEquals(2, battles.getPlayTeamEntities().size());
        for (var player : List.of(host, guest)) {
            var entity = carrier(player);
            assertEquals(12, entity.getId() >> 21);
            assertEquals(EntityType.PlayTeam, entity.getEntityType());
            assertEquals(player.getUid(), entity.getPlayerUid());
            assertEquals(player.getPeerId(), entity.getAuthorityPeerId());
            assertSame(scene, entity.getScene());
            assertSame(entity, scene.getEntityById(entity.getId()));
            assertFalse(scene.getEntities().containsKey(entity.getId()));
            assertFalse(scene.getWeaponEntities().containsKey(entity.getId()));
            var ability = entity.getInstancedAbilities().get(0);
            assertSame(player, ability.getPlayerOwner());
            assertSame(player.getAbilityManager(), ability.getManager());
            assertEquals(
                    GameData.getGadgetDataMap().get(gadget).getJsonName(),
                    ability.getData().abilityName);
        }
        assertNotEquals(carrier(host).getId(), carrier(guest).getId());
        var packet = SyncScenePlayTeamEntityNotify.parseFrom(packets(25654).get(0).getData());
        assertEquals(3, packet.getSceneId());
        assertEquals(2, packet.getEntityInfoListCount());
        assertTrue(
                packet.getEntityInfoListList().stream()
                        .allMatch(
                                e ->
                                        e.hasAbilityInfo()
                                                && !e.getAbilityInfo().getIsInited()
                                                && e.getGadgetConfigId() == gadget));
        assertTrue(packets(PacketOpcodes.SceneEntityAppearNotify).isEmpty());
    }

    @Test
    void luaSetterWorksDuringPreparationAndZeroGroupResolvesCurrentScript() throws Exception {
        assertTrue(battles.prepare(entry, match, 2, MATCH, List.of(host, guest)));
        library.setCurrentGroup(scene.scripts.loaded.get(MAIN));
        assertEquals(
                0,
                lua(
                        "return"
                            + " ScriptLib.SetScenePlayBattlePlayTeamEntityGadgetId(ScriptLib,0,70370002)"));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
        scene.now.set(1020);
        battles.tick();
        assertEquals(
                0,
                lua(
                        "return"
                            + " ScriptLib.PrestartScenePlayBattle(ScriptLib,{group_id=0,duration=600,start_cd=10,progress_stage={0,6500},mode=203})"));
        assertEquals(2, battles.getPlayTeamEntities().size());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"0", "-1", "1.5", "'70370002'", "true", "nil", "0/0", "1/0", "4294967296"})
    void malformedLuaGadgetIdsFailBeforePublishingAnyCarrier(String value) throws Exception {
        readyBattle(MATCH, List.of(host, guest));
        assertEquals(
                -1,
                lua(
                        "return"
                            + " ScriptLib.SetScenePlayBattlePlayTeamEntityGadgetId(ScriptLib,133001413,"
                                + value
                                + ")"));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
    }

    @Test
    void ordinaryBattleWithoutGadgetSettingRetainsExistingBehavior() {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
        assertTrue(packets(25654).isEmpty());
    }

    @Test
    void notReadyCannotCreateCarriers() {
        assertTrue(battles.prepare(entry, match, 2, MATCH, List.of(host, guest)));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        assertFalse(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
    }

    @Test
    void latestValidSettingWinsAndCannotChangePublishedCarriers() {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, 70370001));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, 70370003));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertFalse(battles.setPlayTeamEntityGadgetId(MAIN, 70370002));
        assertEquals(70370003, carrier(host).getGadgetConfigId());
    }

    @Test
    void missingConfigAbilityOrNamedOverrideCannotPublishInertCarriers() {
        readyBattle(MATCH, List.of(host, guest));
        assertFalse(battles.setPlayTeamEntityGadgetId(MAIN, 99999999));
        String name = GameData.getGadgetDataMap().get(GADGET).getJsonName();
        var original = GameData.getAbilityDataMap().remove(name);
        assertFalse(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        GameData.getAbilityDataMap().put(name, original);
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        GameData.getGadgetConfigData().get(name).getAbilities().iterator().next().abilityOverride =
                "unimplemented";
        assertFalse(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
        assertTrue(packets(25654).isEmpty());
    }

    @Test
    void missingAuthorityPeerFailsWholeCreationBeforeAnyPacket() {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        guest.setPeerId(0);
        assertFalse(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
        assertTrue(packets(25654).isEmpty());
    }

    @Test
    void repeatedInitializationDoesNotDuplicateConfiguredAbilityIndices() {
        start();
        var entity = carrier(guest);
        var first = entity.getInstancedAbilities().get(0);
        entity.initAbilities();
        entity.initAbilities();
        assertEquals(1, entity.getInstancedAbilities().size());
        assertSame(first, entity.getInstancedAbilities().get(0));
    }

    @Test
    void lateSpectatorResyncContainsExistingCarriersAndNoSpectatorCarrier() throws Exception {
        start();
        var spectator = new AbilityTestFixture.TestPlayer();
        ready(spectator, 10003, 3);
        spectator.setSceneId(99); // Persistent location can lag behind the actual scene object.
        var packet = new PacketSyncScenePlayTeamEntityNotify(spectator);
        var sync = SyncScenePlayTeamEntityNotify.parseFrom(packet.getData());
        assertEquals(3, sync.getSceneId());
        assertEquals(
                List.of(10001, 10002),
                sync.getEntityInfoListList().stream().map(e -> e.getPlayerUid()).toList());
        assertEquals(2, battles.getPlayTeamEntities().size());
        assertTrue(battles.addProgress(MAIN, 6500));
        assertEquals(
                0,
                SyncScenePlayTeamEntityNotify.parseFrom(
                                new PacketSyncScenePlayTeamEntityNotify(spectator).getData())
                        .getEntityInfoListCount());
    }

    @Test
    void missingParticipantTeamFailsBeforePublishingCarriers() {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        guest.getTeamManager().setEntity(null);
        assertFalse(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        assertTrue(battles.getPlayTeamEntities().isEmpty());
        assertTrue(packets(25654).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"win", "timeout", "group", "close", "host", "guest"})
    void everyRoundTerminationRemovesAndInvalidatesCarriersExactlyOnce(String cause)
            throws Exception {
        start();
        var previous = battles.getPlayTeamEntities();
        switch (cause) {
            case "win" -> assertTrue(battles.addProgress(MAIN, 6500));
            case "timeout" -> {
                scene.now.set(1630);
                battles.tick();
            }
            case "group" -> battles.onGroupUnloaded(scene.scripts.loaded.get(MAIN));
            case "close" -> battles.close();
            case "host" -> battles.onPlayerLeaving(host);
            case "guest" -> battles.onPlayerLeaving(guest);
        }
        battles.tick();
        battles.close();
        assertRemoved(previous);
    }

    @Test
    void leavingGuestOnlyRemovesTheirCarrierWhenDirectRoundCanContinue() throws Exception {
        var third = new AbilityTestFixture.TestPlayer();
        ready(third, 10003, 3);
        readyBattle(DIRECT, List.of(host, guest, third));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 0, 203, List.of(0L, 6500L))));
        var leaving = carrier(guest);
        battles.onPlayerLeaving(guest);
        scene.getPlayers().remove(guest);
        guest.setScene(null);
        battles.tick();
        assertEquals(START, battles.getInfo(MAIN).getState());
        assertEquals(
                List.of(10001, 10003),
                battles.getPlayTeamEntities().stream().map(EntityPlayTeam::getPlayerUid).toList());
        assertNull(scene.getEntityById(leaving.getId()));
        assertEquals(
                List.of(leaving.getId()),
                DelScenePlayTeamEntityNotify.parseFrom(packets(26830).get(0).getData())
                        .getDelEntityIdListList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uid", "peer", "scene", "world", "roster"})
    void changedPlayerIdentityRejectsLookupBeforeNextTickAndTickCleansRemainingCarriers(
            String change) throws Exception {
        start();
        var previous = battles.getPlayTeamEntities();
        var entity = carrier(guest);
        switch (change) {
            case "uid" -> guest.setUid(10004);
            case "peer" -> guest.setPeerId(4);
            case "scene" -> guest.setScene(null);
            case "world" -> guest.setWorld(null);
            case "roster" -> scene.getPlayers().remove(guest);
        }
        assertNull(battles.getPlayTeamEntity(entity.getId()));
        battles.tick();
        assertRemoved(previous);
    }

    @Test
    void silentDepartureIsPrunedEvenWhenDirectRoundStillHasEnoughPlayers() {
        readyBattle(DIRECT, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 0, 203, List.of(0L, 6500L))));
        var leaving = carrier(guest);
        scene.getPlayers().remove(guest);
        battles.tick();
        assertEquals(START, battles.getInfo(MAIN).getState());
        assertEquals(
                List.of(host),
                battles.getPlayTeamEntities().stream().map(EntityPlayTeam::getPlayer).toList());
        assertFalse(leaving.isAbilityLifecycleCurrent(0));
    }

    @Test
    void freshRoundHasFreshEntityIdsAndOldQueuedActionsCannotWriteToNewRound() throws Exception {
        start();
        var old = carrier(guest);
        var increment =
                JsonUtils.decode(
                        "{\"$type\":\"AddGlobalValue\",\"key\":\"oldWork\",\"value\":1,\"target\":\"Team\"}",
                        AbilityModifierAction.class);
        old.getInstancedAbilities()
                .get(0)
                .getManager()
                .executeAction(
                        old.getInstancedAbilities().get(0), increment, ByteString.EMPTY, old);
        assertTrue(battles.addProgress(MAIN, 6500));
        scene.scripts.drain();
        scene.now.set(2000);
        assertTrue(battles.prepare(entry, match, 2, MATCH, List.of(host, guest)));
        scene.now.set(2020);
        battles.tick();
        scene.scripts.drain();
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        assertTrue(
                battles.prestart(
                        MAIN, new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L))));
        var fresh = carrier(guest);
        assertNotEquals(old.getId(), fresh.getId());
        workers.close();
        assertFalse(
                guest.getTeamManager().getEntity().getGlobalAbilityValues().containsKey("oldWork"));
        assertTrue(old.getGlobalAbilityValues().isEmpty());
        assertNull(scene.getEntityById(old.getId()));
        assertSame(fresh, scene.getEntityById(fresh.getId()));
    }

    @Test
    void serverOnAddedUsesEachRecordedParticipantTeam() throws Exception {
        String name = GameData.getGadgetDataMap().get(GADGET).getJsonName();
        replace(
                GameData.getAbilityDataMap(),
                name,
                JsonUtils.decode(
                        "{\"abilityName\":\""
                                + name
                                + "\",\"onAdded\":[{\"$type\":\"SetGlobalValue\",\"key\":\"participantMark\",\"value\":7,\"target\":\"Team\"}]}",
                        AbilityData.class));
        start();
        workers.close();
        assertEquals(
                7f,
                host.getTeamManager().getEntity().getGlobalAbilityValues().get("participantMark"));
        assertEquals(
                7f,
                guest.getTeamManager().getEntity().getGlobalAbilityValues().get("participantMark"));
    }

    @Test
    void stoppingRemovesOnlyCarrierOwnedEffectsFromParticipantTeam() {
        start();
        var entity = carrier(guest);
        var team = guest.getTeamManager().getEntity();
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"CarrierEffects\",\"modifiers\":{\"shared\":{}}}",
                        AbilityData.class);
        var owned = new Ability(data, entity, guest);
        entity.getInstancedAbilities().add(owned);
        var unrelated = new Ability(data, team, guest);
        owned.registerModifier(
                team, new AbilityModifierController(owned, data, data.modifiers.get("shared")));
        unrelated.registerModifier(
                team, new AbilityModifierController(unrelated, data, data.modifiers.get("shared")));
        assertEquals(2, team.getAppliedAbilityModifiers().size());
        assertTrue(battles.addProgress(MAIN, 6500));
        assertFalse(team.getAppliedAbilityModifiers().containsKey(owned));
        assertTrue(team.getAppliedAbilityModifiers().containsKey(unrelated));
        unrelated.removeInstalledModifiers();
    }

    @Test
    void carrierAbilityTimersAdvanceWithSceneBattleTicks() throws Exception {
        start();
        var entity = carrier(guest);
        var timer = new AtomicLong(100);
        var field = AbilityModifierDuration.class.getDeclaredField("clock");
        field.setAccessible(true);
        field.set(entity.getAbilityModifierDuration(), (LongSupplier) timer::get);
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"CarrierTimer\",\"modifiers\":{\"timed\":{\"duration\":1}}}",
                        AbilityData.class);
        var ability = new Ability(data, entity, guest);
        entity.getInstancedAbilities().add(ability);
        ability.registerModifier(
                entity, new AbilityModifierController(ability, data, data.modifiers.get("timed")));
        assertTrue(entity.getAppliedAbilityModifiers().containsKey(ability));
        timer.addAndGet(1_000_000_000);
        battles.tick();
        assertFalse(entity.getAppliedAbilityModifiers().containsKey(ability));
    }

    private AbilityInvokeEntry report(EntityPlayTeam entity) {
        return AbilityInvokeEntry.newBuilder()
                .setEntityId(entity.getId())
                .setArgumentType(
                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                .setAbilityData(
                        AbilityScalarValueEntry.newBuilder()
                                .setKey(AbilityString.newBuilder().setStr("clientMark"))
                                .setFloatValue(5)
                                .build()
                                .toByteString())
                .build();
    }

    @Test
    void onlyCarrierOwnerCanSendClientAbilityReports() throws Exception {
        start();
        var entity = carrier(guest);
        assertFalse(host.getAbilityManager().acceptAbilityInvoke(report(entity)));
        assertFalse(entity.getGlobalAbilityValues().containsKey("clientMark"));
        assertTrue(guest.getAbilityManager().acceptAbilityInvoke(report(entity)));
        assertEquals(5f, entity.getGlobalAbilityValues().get("clientMark"));
        assertTrue(battles.addProgress(MAIN, 6500));
        assertFalse(guest.getAbilityManager().acceptAbilityInvoke(report(entity)));
    }

    @Test
    void concurrentPrestartPublishesOneCarrierPerParticipantExactlyOnce() throws Exception {
        readyBattle(MATCH, List.of(host, guest));
        assertTrue(battles.setPlayTeamEntityGadgetId(MAIN, GADGET));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            Callable<Boolean> call =
                    () -> {
                        gate.await();
                        return battles.prestart(
                                MAIN,
                                new ScenePlayBattleParameters(600, 10, 203, List.of(0L, 6500L)));
                    };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            assertNotEquals(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertEquals(2, battles.getPlayTeamEntities().size());
            assertEquals(1, packets(25654).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void queuedCarrierActionsKeepSceneMonitorEvenWithoutScenePlayLua() throws Exception {
        String name = GameData.getGadgetDataMap().get(GADGET).getJsonName();
        replace(
                GameData.getAbilityDataMap(),
                name,
                JsonUtils.decode(
                        "{\"abilityName\":\""
                                + name
                                + "\",\"onAdded\":[{\"$type\":\"SetGlobalValue\",\"key\":\"locked\",\"value\":1}]}",
                        AbilityData.class));
        var field = AbilityManager.class.getDeclaredField("actionHandlers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var handlers = (Map<AbilityModifierAction.Type, AbilityActionHandler>) field.get(null);
        var original = handlers.get(AbilityModifierAction.Type.SetGlobalValue);
        var calls = new AtomicInteger();
        handlers.put(
                AbilityModifierAction.Type.SetGlobalValue,
                new AbilityActionHandler() {
                    @Override
                    public boolean execute(
                            Ability ability,
                            AbilityModifierAction action,
                            ByteString raw,
                            GameEntity target) {
                        assertTrue(Thread.holdsLock(scene));
                        calls.incrementAndGet();
                        return original.execute(ability, action, raw, target);
                    }
                });
        try {
            start();
            workers.close();
            assertEquals(2, calls.get());
        } finally {
            handlers.put(AbilityModifierAction.Type.SetGlobalValue, original);
        }
    }

    @Test
    void foreignAndRemovedCarrierTargetsAreRejectedBeforeMetadataDispatch() throws Exception {
        start();
        var entity = carrier(guest);
        var invoke =
                report(entity).toBuilder()
                        .setEntityId(host.getTeamManager().getEntity().getId())
                        .setHead(
                                emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass
                                        .AbilityInvokeEntryHead.newBuilder()
                                        .setTargetId(entity.getId()))
                        .build();
        assertFalse(host.getAbilityManager().acceptAbilityInvoke(invoke));
        assertFalse(
                host.getTeamManager()
                        .getEntity()
                        .getGlobalAbilityValues()
                        .containsKey("clientMark"));
        assertTrue(battles.addProgress(MAIN, 6500));
        var ownInvoke =
                invoke.toBuilder().setEntityId(guest.getTeamManager().getEntity().getId()).build();
        assertFalse(guest.getAbilityManager().acceptAbilityInvoke(ownInvoke));
    }

    @Test
    void carrierLuaCallsSelectTheirOwnRoundWhenAnotherRoundSharesParticipants() {
        start();
        var entity = carrier(guest);
        var group = new SceneGroup();
        group.id = 133001600;
        scene.scripts.loaded.put(group.id, group);
        var other =
                JsonUtils.decode(
                        "{\"playId\":3,\"prepareTime\":0,\"groupList\":[133001600],\"serverLuaCallGroupList\":[133001600]}",
                        MpPlayGroupData.class);
        var otherMatch =
                JsonUtils.decode(
                        "{\"id\":3,\"minPlayers\":1,\"maxPlayers\":4}", MpPlayMatchData.class);
        assertTrue(battles.prepare(other, otherMatch, 2, DIRECT, List.of(host, guest)));
        var called = new ArrayList<Integer>();
        assertTrue(
                battles.callServerLua(
                        entity,
                        entity,
                        guest,
                        selected -> {
                            called.add(selected.id);
                            return true;
                        }));
        assertEquals(List.of(MAIN), called);
    }

    private static final class Workers implements AutoCloseable {
        final CountDownLatch release = new CountDownLatch(1);
        final List<Future<?>> blockers = new ArrayList<>();
        boolean closed;

        Workers() throws Exception {
            var started = new CountDownLatch(4);
            for (int i = 0; i < 4; i++)
                blockers.add(
                        AbilityManager.eventExecutor.submit(
                                () -> {
                                    started.countDown();
                                    try {
                                        if (!release.await(15, TimeUnit.SECONDS))
                                            throw new AssertionError("worker hold expired");
                                    } catch (InterruptedException e) {
                                        throw new AssertionError(e);
                                    }
                                }));
            assertTrue(started.await(3, TimeUnit.SECONDS));
        }

        @Override
        public void close() throws Exception {
            if (closed) return;
            closed = true;
            var queued =
                    new ArrayList<>(((ThreadPoolExecutor) AbilityManager.eventExecutor).getQueue());
            release.countDown();
            for (var task : queued) ((Future<?>) task).get(5, TimeUnit.SECONDS);
            for (var blocker : blockers) blocker.get(5, TimeUnit.SECONDS);
        }
    }

    private static final class PlayScene extends AbilityTestFixture.TestScene {
        final AtomicLong now = new AtomicLong(1000);
        final QueuedScripts scripts;

        PlayScene(AbilityTestFixture.TestWorld world) throws Exception {
            super(
                    world,
                    new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_DUNGEON\"}", SceneData.class));
            scripts = new QueuedScripts(this);
            var field = Scene.class.getDeclaredField("scenePlayBattleManager");
            field.setAccessible(true);
            field.set(this, new ScenePlayBattleManager(this, now::get));
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }
    }

    private static final class QueuedScripts extends SceneScriptManager {
        final Map<Integer, SceneGroup> loaded = new LinkedHashMap<>();
        final List<FutureTask<Void>> queued = new ArrayList<>();

        QueuedScripts(Scene scene) {
            super(scene);
        }

        @Override
        public SceneGroup getLoadedGroupById(int id) {
            return loaded.get(id);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args, BooleanSupplier current) {
            var task = new FutureTask<Void>(() -> {}, null);
            queued.add(task);
            return task;
        }

        void drain() {
            for (var task : List.copyOf(queued)) task.run();
        }
    }
}

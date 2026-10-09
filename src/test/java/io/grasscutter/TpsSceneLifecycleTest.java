package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;
import emu.grasscutter.data.*;
import emu.grasscutter.data.custom.TrialAvatarCustomData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.tps.TpsWeaponData;
import emu.grasscutter.data.excels.trial.TrialAvatarData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.tps.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarDataNotifyOuterClass.AvatarDataNotify;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

/** Actual 7.1 resource rows, real Scene.addPlayer/removePlayer and login packets. */
class TpsSceneLifecycleTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture fixture;
    private RecordingPlayer player;
    private AbilityTestFixture.TestWorld world;
    private SceneData tpsData;
    private Object previousDatastore;

    static class RecordingPlayer extends AbilityTestFixture.TestPlayer {
        private static final java.util.concurrent.atomic.AtomicInteger NEXT_UID =
                new java.util.concurrent.atomic.AtomicInteger(14000);

        RecordingPlayer() {
            setUid(NEXT_UID.incrementAndGet());
        }

        int mainId = 10000005;
        int saves;
        boolean loggedIn;
        GameServer server;

        @Override
        public int getMainCharacterId() {
            return mainId;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public boolean hasSentLoginPackets() {
            return loggedIn;
        }

        @Override
        public void save() {
            saves++;
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

    private <T> List<T> load(String name, Class<T> type) throws Exception {
        return JsonUtils.loadToList(EXCEL.resolve(name + "ExcelConfigData.json"), type);
    }

    @BeforeEach
    void setup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        previousDatastore = field.get(null);
        field.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        fixture = new AbilityTestFixture();
        player = new RecordingPlayer();
        player.server = fixture.scene.getWorld().getServer();
        world = new AbilityTestFixture.TestWorld(player.server, player);
        player.setWorld(world);
        var curves = new HashMap<>(GameData.getAvatarCurveDataMap());
        GameData.getAvatarCurveDataMap().clear();
        restore.add(
                () -> {
                    GameData.getAvatarCurveDataMap().clear();
                    GameData.getAvatarCurveDataMap().putAll(curves);
                });
        for (var row : load("AvatarCurve", AvatarCurveData.class)) {
            row.onLoad();
            GameData.getAvatarCurveDataMap().put(row.getId(), row);
        }
        for (var row : load("AvatarSkillDepot", AvatarSkillDepotData.class)) {
            if (!List.of(501, 701, 1501, 50001).contains(row.getId())) continue;
            row.onLoad();
            put(GameData.getAvatarSkillDepotDataMap(), row.getId(), row);
        }
        for (var row : load("Avatar", AvatarData.class)) {
            if (!List.of(10000005, 10000007, 10000015, 10000134, 10000135).contains(row.getId()))
                continue;
            row.onLoad();
            put(GameData.getAvatarDataMap(), row.getId(), row);
        }
        for (var row : load("TrialAvatar", TrialAvatarData.class))
            if (row.getTrialAvatarParamList() != null
                    && row.getTrialAvatarParamList().size() >= 2
                    && TpsAvatarSystem.isTpsAvatar(row.getTrialAvatarParamList().get(0)))
                put(GameData.getTrialAvatarDataMap(), row.getId(), row);
        for (var row : load("TpsWeapon", ItemData.class))
            put(GameData.getItemDataMap(), row.getId(), row);
        for (var row : load("TpsWeapon", TpsWeaponData.class))
            put(GameData.getTpsWeaponDataMap(), row.getId(), row);
        for (var row : load("Weapon", ItemData.class)) {
            row.onLoad();
            put(GameData.getItemDataMap(), row.getId(), row);
        }
        tpsData =
                load("Scene", SceneData.class).stream()
                        .filter(d -> d.getId() == 51334)
                        .findFirst()
                        .orElseThrow();
        party(10000005, 10000015);
        player.getTeamManager().setCurrentCharacterIndex(1);
        player.packets.clear();
    }

    private void party(int... ids) {
        for (int id : ids) {
            var avatar = new Avatar(id);
            player.getAvatars().addAvatar(avatar);
            player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars().add(id);
        }
    }

    private Scene enter() {
        var scene = new AbilityTestFixture.TestScene(world, tpsData);
        scene.setDontDestroyWhenEmpty(true);
        scene.addPlayer(player);
        player.getTeamManager().applyAbilities(scene);
        return scene;
    }

    @AfterEach
    void teardown() throws Exception {
        if (player != null && player.getScene() != null) player.getScene().removePlayer(player);
        assertTrue(emu.grasscutter.database.DatabaseHelper.awaitIdle(java.time.Duration.ofSeconds(5)));
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        field.set(null, previousDatastore);
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (fixture != null) fixture.close();
    }

    @Test
    void enteringTheActualSceneUsesABorrowedTravelerWithoutChangingTheSavedParty() {
        var scene = enter();
        var manager = player.getTeamManager();
        assertEquals(List.of(10000134), manager.getCurrentTeamInfo().getAvatars());
        assertEquals(
                List.of(10000005, 10000015), manager.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(0, manager.getCurrentCharacterIndex());
        assertEquals(1, manager.getActiveTeam().size());
        var avatar = manager.getCurrentAvatarEntity().getAvatar();
        assertTrue(avatar.getTrialAvatarId() > 0);
        assertFalse(player.getAvatars().hasAvatar(10000134));
        assertEquals(20, avatar.getLevel());
        assertEquals(List.of(224001), avatar.getTpsWeaponIds());
        assertEquals(List.of(224001), player.getTpsLoadout());
        assertEquals(2, scene.getWeaponEntities().size(), "normal placeholder and TPS weapon");
        assertTrue(
                player.packets.isEmpty(),
                () ->
                        "premature packets: "
                                + player.packets.stream().map(p -> p.getClass().getSimpleName()).toList());
    }

    @Test
    void loginIncludesBorrowedAvatarAndChoosesItInsteadOfTheOwnedTraveler() throws Exception {
        enter();
        var chosen = player.getTeamManager().getCurrentAvatarEntity().getAvatar();
        var packet = AvatarDataNotify.parseFrom(new PacketAvatarDataNotify(player).getData());
        assertEquals(10000134, chosen.getAvatarId());
        assertEquals(chosen.getGuid(), packet.getChooseAvatarGuid());
        assertEquals(List.of(chosen.getGuid()), packet.getTempAvatarGuidListList());
        assertTrue(packet.getAvatarListList().stream().anyMatch(a -> a.getGuid() == chosen.getGuid()));
    }

    @Test
    void exitRestoresSelectedSlotAndReentryDoesNotGrantAnotherWeapon() {
        var scene = enter();
        scene.removePlayer(player);
        assertEquals(
                List.of(10000005, 10000015), player.getTeamManager().getCurrentTeamInfo().getAvatars());
        assertEquals(1, player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(scene.getWeaponEntities().isEmpty());
        assertFalse(player.getTeamManager().isUsingTrialTeam());
        var first = TpsWeaponSystem.findOwnedWeapon(player, 224001);
        assertNotNull(first);
        enter();
        assertSame(first, TpsWeaponSystem.findOwnedWeapon(player, 224001));
        assertEquals(1, player.getInventory().getItems().size());
    }

    @Test
    void unrelatedCustomTrialRowsDoNotDisableTheExcelTraveler() {
        var custom =
                JsonUtils.decode(
                        "{\"trialAvatarId\":99999,\"trialAvatarParamList\":[\"10000015;80\"]}",
                        TrialAvatarCustomData.class);
        put(GameData.getTrialAvatarCustomData(), 99999, custom);
        assertEquals(List.of(10000134, 20), player.getTeamManager().getTrialAvatarParam(10064));
        enter();
        assertEquals(
                10000134, player.getTeamManager().getCurrentAvatarEntity().getAvatar().getAvatarId());
    }

    @Test
    void femaleTravelerAndMaleTravelerHaveSeparateSceneParties() throws Exception {
        var multiplayer = World.class.getDeclaredField("isMultiplayer");
        multiplayer.setAccessible(true);
        multiplayer.setBoolean(world, true);
        player
                .getTeamManager()
                .getMpTeam()
                .copyFrom(player.getTeamManager().getCurrentSinglePlayerTeamInfo());
        var scene = enter();
        var female = new RecordingPlayer();
        female.server = player.server;
        female.mainId = 10000007;
        female.setWorld(world);
        female.getAvatars().addAvatar(new Avatar(10000007));
        female.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars().add(10000007);
        female.getTeamManager().getMpTeam().getAvatars().add(10000007);
        scene.addPlayer(female);
        assertEquals(
                10000135, female.getTeamManager().getCurrentAvatarEntity().getAvatar().getAvatarId());
        assertEquals(
                10000134, player.getTeamManager().getCurrentAvatarEntity().getAvatar().getAvatarId());
        assertNotSame(
                player.getTeamManager().getTpsSceneAvatar(), female.getTeamManager().getTpsSceneAvatar());
        scene.removePlayer(female);
        assertEquals(List.of(10000007), female.getTeamManager().getCurrentTeamInfo().getAvatars());
        assertEquals(1, player.getTeamManager().getActiveTeam().size());
        assertEquals(2, scene.getWeaponEntities().size());
    }

    @Test
    void emptyLoadoutIsAnIntentionalPersistentChoice() {
        var scene = enter();
        var avatar = player.getTeamManager().getTpsSceneAvatar();
        assertEquals(0, TpsWeaponSystem.wear(player, avatar.getGuid(), List.of()));
        assertTrue(player.isTpsLoadoutInitialized());
        scene.removePlayer(player);
        enter();
        assertTrue(player.getTeamManager().getTpsSceneAvatar().getTpsWeaponIds().isEmpty());
        assertTrue(player.getTpsLoadout().isEmpty());
        assertEquals(1, player.getInventory().getItems().size());
    }

    @Test
    void anAlreadyEmptyLoadoutIsStillMarkedInitialized() {
        player.setTpsLoadoutInitialized(true);
        enter();
        player.setTpsLoadoutInitialized(false);
        var avatar = player.getTeamManager().getTpsSceneAvatar();
        assertTrue(avatar.getTpsWeaponIds().isEmpty());
        assertEquals(0, TpsWeaponSystem.wear(player, avatar.getGuid(), List.of()));
        assertTrue(player.isTpsLoadoutInitialized());
    }

    @Test
    void legacyLoadoutUsesOwnedWeaponsAndRepairsDuplicatesMissingItemsAndSlotOverflow() {
        for (int id : List.of(224001, 224002, 224003, 224004, 224008))
            player.getInventory().addItem(new GameItem(id));
        player
                .getTpsLoadout()
                .addAll(Arrays.asList(null, 224001, 224001, 999999, 224002, 224003, 224004, 224008));
        enter();
        assertEquals(List.of(224001, 224002, 224004), player.getTpsLoadout());
        assertEquals(
                player.getTpsLoadout(), player.getTeamManager().getTpsSceneAvatar().getTpsWeaponIds());
        assertEquals(5, player.getInventory().getItems().size());
        assertTrue(player.isTpsLoadoutInitialized());
    }

    private Avatar questTrial() {
        var manager = player.getTeamManager();
        manager.setupTrialAvatars(true);
        var trial = new Avatar(10000015);
        trial.setTrialAvatarInfo(20, 91002, GrantReason.GRANT_REASON_BY_QUEST, 307);
        trial.setOwner(player);
        trial.equipTrialItems(false);
        trial.recalcStats(false, false);
        manager.addAvatarToTrialTeam(trial);
        manager.getQuestTrialAvatarIds().put(91002, 307);
        return trial;
    }

    @Test
    void exitRestoresTheQuestTrialEvenWhenTheSameCharacterIsOwned() {
        var trial = questTrial();
        var owned = player.getAvatars().getAvatarById(10000015);
        var scene = enter();
        assertSame(trial, player.getTeamManager().getTrialAvatars().get(10000015));
        scene.removePlayer(player);
        var normal =
                new AbilityTestFixture.TestScene(
                        world, JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        normal.setDontDestroyWhenEmpty(true);
        normal.addPlayer(player);
        assertSame(trial, player.getTeamManager().getCurrentAvatarEntity().getAvatar());
        assertSame(owned, player.getAvatars().getAvatarById(10000015));
        assertEquals(307, player.getTeamManager().getQuestTrialAvatarIds().get(91002));
    }

    @Test
    void questCompletionInsideTpsRestoresTheOriginalSelectedSlot() {
        questTrial();
        var scene = enter();
        var tps = player.getTeamManager().getCurrentAvatarEntity();
        player.getTeamManager().removeQuestTrialAvatars(307);
        assertSame(tps, player.getTeamManager().getCurrentAvatarEntity());
        assertTrue(player.getTeamManager().getTrialAvatars().isEmpty());
        scene.removePlayer(player);
        assertFalse(player.getTeamManager().isUsingTrialTeam());
        assertEquals(1, player.getTeamManager().getCurrentCharacterIndex());
    }

    @Test
    void temporaryPartyRemainsIntactAndCannotBeReplacedInsideTps() throws Exception {
        var manager = player.getTeamManager();
        var temporary = new TeamInfo(new ArrayList<>(List.of(10000015)));
        var field = TeamManager.class.getDeclaredField("temporaryTeam");
        field.setAccessible(true);
        field.set(manager, List.of(temporary));
        var index = TeamManager.class.getDeclaredField("useTemporarilyTeamIndex");
        index.setAccessible(true);
        index.setInt(manager, 0);
        manager.setCurrentCharacterIndex(0);
        var scene = enter();
        manager.setupTemporaryTeam(
                List.of(List.of(player.getAvatars().getAvatarById(10000005).getGuid())));
        assertFalse(manager.cleanTemporaryTeam());
        assertFalse(manager.canAddAvatarToCurrentTeam());
        scene.removePlayer(player);
        assertSame(temporary, manager.getCurrentTeamInfo());
        assertEquals(0, manager.getCurrentCharacterIndex());
    }

    @Test
    void aStaleRemovalDoesNotDestroyTheNextSceneParty() {
        var first = enter();
        var second = enter();
        var active = player.getTeamManager().getCurrentAvatarEntity();
        first.removePlayer(player);
        assertSame(second, player.getScene());
        assertSame(active, player.getTeamManager().getCurrentAvatarEntity());
        assertEquals(2, second.getWeaponEntities().size());
    }

    @Test
    void anOwnedTpsCharacterCannotReplaceTheBorrowedSceneCharacter() {
        var owned = new Avatar(10000134);
        player.getAvatars().addAvatar(owned);
        enter();
        assertNotSame(owned, player.getTeamManager().getCurrentAvatarEntity().getAvatar());
        assertSame(owned, player.getAvatars().getAvatarById(10000134));
    }

    @Test
    void missingResourcesRejectEntryBeforeRemovingTheOldScene() {
        var normal =
                new AbilityTestFixture.TestScene(
                        world, JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        normal.setDontDestroyWhenEmpty(true);
        normal.addPlayer(player);
        var active = player.getTeamManager().getCurrentAvatarEntity();
        var original = GameData.getAvatarDataMap().remove(10000134);
        try {
            assertFalse(TpsAvatarSystem.canEnter(player, tpsData));
            assertThrows(IllegalStateException.class, this::enter);
            put(GameData.getSceneDataMap(), tpsData.getId(), tpsData);
            assertFalse(
                    world.transferPlayerToScene(
                            player,
                            TeleportProperties.builder()
                                    .sceneId(tpsData.getId())
                                    .teleportType(TeleportType.INTERNAL)
                                    .build()));
            assertSame(normal, player.getScene());
            assertSame(active, player.getTeamManager().getCurrentAvatarEntity());
        } finally {
            GameData.getAvatarDataMap().put(10000134, original);
        }
    }

    @Test
    void allFourActualTpsDungeonTypesDecodeAndNullActivityPlayTypeIsHarmless() throws Exception {
        var rows =
                load("Dungeon", DungeonData.class).stream()
                        .filter(d -> List.of(10888, 10920, 10953, 10955).contains(d.getId()))
                        .toList();
        assertEquals(4, rows.size());
        rows.forEach(d -> assertNotNull(d.getType()));
        var scene =
                new AbilityTestFixture.TestScene(
                        world, JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        var manager =
                new DungeonManager(
                        scene,
                        JsonUtils.decode(
                                "{\"id\":1,\"type\":\"DUNGEON_ACTIVITY\",\"passCond\":0}", DungeonData.class));
        assertDoesNotThrow(() -> manager.applyTrialTeam(player));
    }

    @Test
    void dungeonExitRemovesOnlyItsOwnTrialsForEachPlayer() throws Exception {
        var quest = questTrial();
        put(
                GameData.getTrialAvatarDataMap(),
                91001,
                JsonUtils.decode(
                        "{\"trialAvatarId\":91001,\"trialAvatarParamList\":[10000007,20]}",
                        TrialAvatarData.class));
        player.loggedIn = true;
        var second = new RecordingPlayer();
        second.server = player.server;
        second.setWorld(world);
        second.loggedIn = true;
        second.getAvatars().addAvatar(new Avatar(10000005));
        second.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars().add(10000005);
        var scene =
                new AbilityTestFixture.TestScene(
                        world, JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        var dungeon =
                new DungeonManager(
                        scene,
                        JsonUtils.decode(
                                "{\"id\":1,\"type\":\"DUNGEON_ELEMENT_CHALLENGE\",\"passCond\":0}",
                                DungeonData.class));
        var apply =
                DungeonManager.class.getDeclaredMethod(
                        "applyTrialTeam", Player.class, DungeonTrialTeam.class);
        apply.setAccessible(true);
        var team =
                new DungeonTrialTeam(
                        List.of(91001, 91002), GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY);
        apply.invoke(dungeon, player, team);
        apply.invoke(dungeon, second, new DungeonTrialTeam(List.of(91001), team.getGrantReason()));
        long secondGuid = second.getTeamManager().getTrialAvatarGuid(91001);
        assertNotEquals(0, secondGuid);
        assertNotEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
        dungeon.unsetTrialTeam(player);
        assertEquals(0, player.getTeamManager().getTrialAvatarGuid(91001));
        assertSame(quest, player.getTeamManager().getTrialAvatars().get(10000015));
        assertEquals(secondGuid, second.getTeamManager().getTrialAvatarGuid(91001));
        dungeon.unsetTrialTeam(second);
        dungeon.unsetTrialTeam(player);
        assertEquals(0, second.getTeamManager().getTrialAvatarGuid(91001));
        assertEquals(List.of(10000005), second.getTeamManager().getCurrentTeamInfo().getAvatars());
        assertEquals(307, player.getTeamManager().getQuestTrialAvatarIds().get(91002));
    }

    @Test
    void replacingADungeonTrialWithAQuestGrantProtectsTheNewInstanceOnExit() throws Exception {
        put(
                GameData.getTrialAvatarDataMap(),
                91001,
                JsonUtils.decode(
                        "{\"trialAvatarId\":91001,\"trialAvatarParamList\":[10000007,20]}",
                        TrialAvatarData.class));
        player.loggedIn = true;
        var scene =
                new AbilityTestFixture.TestScene(
                        world, JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        var dungeon =
                new DungeonManager(
                        scene,
                        JsonUtils.decode(
                                "{\"id\":1,\"type\":\"DUNGEON_ELEMENT_CHALLENGE\",\"passCond\":0}",
                                DungeonData.class));
        var apply =
                DungeonManager.class.getDeclaredMethod(
                        "applyTrialTeam", Player.class, DungeonTrialTeam.class);
        apply.setAccessible(true);
        apply.invoke(
                dungeon,
                player,
                new DungeonTrialTeam(List.of(91001), GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY));
        long old = player.getTeamManager().getTrialAvatarGuid(91001);
        assertTrue(
                player.getTeamManager().addTrialAvatar(91001, 308, GrantReason.GRANT_REASON_BY_QUEST));
        long replacement = player.getTeamManager().getTrialAvatarGuid(91001);
        assertNotEquals(old, replacement);
        dungeon.unsetTrialTeam(player);
        assertEquals(replacement, player.getTeamManager().getTrialAvatarGuid(91001));
        assertEquals(308, player.getTeamManager().getQuestTrialAvatarIds().get(91001));
    }

    @Test
    void loggedInEntryAnnouncesTheBorrowedAvatarBeforeAnyStatOrEquipmentUpdates() {
        player.loggedIn = true;
        enter();
        var relevant =
                player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode() == PacketOpcodes.AvatarAddNotify
                                                || p.getOpcode() == PacketOpcodes.AvatarFightPropNotify
                                                || p.getOpcode() == PacketOpcodes.AvatarEquipChangeNotify
                                                || p.getOpcode() == PacketOpcodes.TpsEquipChangeNotify
                                                || p.getOpcode() == PacketOpcodes.AvatarSkillChangeNotify)
                        .toList();
        assertFalse(relevant.isEmpty());
        assertEquals(PacketOpcodes.AvatarAddNotify, relevant.get(0).getOpcode());
        assertEquals(1, relevant.size(), "AvatarAdd carries the initialized stats/equipment");
    }

    @Test
    void questGrantedInsideTpsBecomesAvailableWhenLeavingWithoutReplacingTheTpsParty() {
        put(
                GameData.getTrialAvatarDataMap(),
                91002,
                JsonUtils.decode(
                        "{\"trialAvatarId\":91002,\"trialAvatarParamList\":[10000015,20]}",
                        TrialAvatarData.class));
        player.loggedIn = true;
        var scene = enter();
        var active = player.getTeamManager().getCurrentAvatarEntity();
        player.getTeamManager().addTrialAvatar(91002, 307);
        assertSame(active, player.getTeamManager().getCurrentAvatarEntity());
        assertEquals(List.of(10000134), player.getTeamManager().getCurrentTeamInfo().getAvatars());
        scene.removePlayer(player);
        assertEquals(
                List.of(10000005, 10000015), player.getTeamManager().getCurrentTeamInfo().getAvatars());
        assertTrue(player.getTeamManager().isUsingTrialTeam());
        assertEquals(1, player.getTeamManager().getCurrentCharacterIndex());
        assertEquals(307, player.getTeamManager().getQuestTrialAvatarIds().get(91002));
    }

    @Test
    void allSevenActualForcedScenesCanEnterAndLeaveTheSameParty() throws Exception {
        var rows = load("Scene", SceneData.class).stream().filter(TpsAvatarSystem::isTpsScene).toList();
        assertEquals(7, rows.size());
        for (var row : rows) {
            tpsData = row;
            var scene = enter();
            assertEquals(List.of(10000134), player.getTeamManager().getCurrentTeamInfo().getAvatars());
            scene.removePlayer(player);
            assertEquals(
                    List.of(10000005, 10000015), player.getTeamManager().getCurrentTeamInfo().getAvatars());
            assertEquals(1, player.getTeamManager().getCurrentCharacterIndex());
            assertTrue(scene.getWeaponEntities().isEmpty());
        }
        assertEquals(1, player.getInventory().getItems().size());
    }

    @Test
    void malformedCustomEquipmentRejectsEntryBeforeTouchingTheOldParty() {
        for (int id : List.of(10064, 10066, 10068, 10070))
            put(
                    GameData.getTrialAvatarCustomData(),
                    id,
                    JsonUtils.decode(
                            "{\"trialAvatarId\":"
                                    + id
                                    + ",\"trialAvatarParamList\":[\"10000134;20\",\"invalid\"]}",
                            TrialAvatarCustomData.class));
        assertFalse(TpsAvatarSystem.canEnter(player, tpsData));
        assertEquals(
                List.of(10000005, 10000015), player.getTeamManager().getCurrentTeamInfo().getAvatars());
    }

    @Test
    void malformedOrWrongSexCustomTrialParametersAreSkipped() {
        var bad =
                JsonUtils.decode(
                        "{\"trialAvatarId\":10064,\"trialAvatarParamList\":[\"10000134\"]}",
                        TrialAvatarCustomData.class);
        put(GameData.getTrialAvatarCustomData(), 10064, bad);
        assertTrue(player.getTeamManager().getTrialAvatarParam(10064).isEmpty());
        assertEquals(10066, TpsAvatarSystem.getTrialAvatarId(player, tpsData));
        bad.setTrialAvatarParamList(List.of("10000135;20"));
        assertEquals(10066, TpsAvatarSystem.getTrialAvatarId(player, tpsData));
        bad.setTrialAvatarParamList(Collections.singletonList(null));
        assertTrue(player.getTeamManager().getTrialAvatarParam(10064).isEmpty());
        bad.setTrialAvatarParamList(List.of("not-a-number;20"));
        assertTrue(player.getTeamManager().getTrialAvatarParam(10064).isEmpty());
    }
}

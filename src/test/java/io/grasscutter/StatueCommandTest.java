package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.game.city.StatueOfferReceipt;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class StatueCommandTest {
    StatueOfferingTest env;
    final List<String> messages = new ArrayList<>();
    final List<String> rewards = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        StatueOfferingTest.resources();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new StatueOfferingTest();
        env.setup();
        env.statue();
        env.player
                .getSession()
                .setState(emu.grasscutter.server.game.GameSession.SessionState.ACTIVE);
        env.replaceField(
                Player.class,
                env.player,
                "inventory",
                new Inventory(env.player) {
                    @Override
                    public boolean addItem(int id, int count, ActionReason reason) {
                        rewards.add(id + ":" + count);
                        return true;
                    }
                });
        Grasscutter.getPluginManager()
                .registerListener(
                        new EventHandler<>(ReceiveCommandFeedbackEvent.class)
                                .listener(
                                        event -> {
                                            messages.add(event.getMessage());
                                            event.cancel();
                                        }));
        var root =
                com.google.gson.JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Scene/Point/scene3_point.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("points");
        for (int id : new int[] {7, 31, 1121}) {
            var data = JsonUtils.decode(root.get(Integer.toString(id)), PointData.class);
            data.setId(id);
            env.put(GameData.getScenePointEntryMap(), (3 << 16) + id, new ScenePointEntry(3, data));
        }
        env.player.getPosition().set(GameData.getScenePointEntryById(3, 7).getPointData().getPos());
    }

    @AfterEach
    void close() throws Exception {
        env.restoreStatueData();
        env.cleanup();
    }

    CommandHandler command() throws Exception {
        return (CommandHandler)
                Class.forName("emu.grasscutter.command.commands.StatueCommand")
                        .getConstructor()
                        .newInstance();
    }

    void run(String... args) throws Exception {
        command().execute(null, env.player, List.of(args));
    }

    @Test
    void cityListingUsesActualResourceCitiesWithoutCreatingProgress() throws Exception {
        env.player.setCityInfoData(new HashMap<>());
        run("city");
        assertTrue(messages.stream().anyMatch(x -> x.contains("city=7")));
        assertTrue(messages.stream().anyMatch(x -> x.contains("city=8")));
        assertTrue(env.player.getCityInfoData().isEmpty());
        assertEquals(0, env.saves.get());
    }

    @Test
    void explicitCityQueryReportsSavedPartialProgress() throws Exception {
        env.manager.getCityInfo(7).setLevel(4);
        env.manager.getCityInfo(7).setNumCrystal(17);
        run("city", "7");
        assertTrue(
                messages.stream()
                        .anyMatch(
                                x ->
                                        x.contains("city=7")
                                                && x.contains("level=4")
                                                && x.contains("crystal=17")));
        assertEquals(0, env.saves.get());
    }

    @Test
    void adminLevelChangePersistsAndSendsLaterRegionSnapshotWithoutRewards() throws Exception {
        env.manager.getCityInfo(7).setNumCrystal(17);
        var stock = env.stock(107030, 40);
        int stamina = env.player.getProperty(PlayerProperty.PROP_MAX_STAMINA);
        run("level", "7", "6");
        assertEquals(6, env.manager.getCityInfo(7).getLevel());
        assertEquals(0, env.manager.getCityInfo(7).getNumCrystal());
        assertEquals(1, env.saves.get());
        assertEquals(40, stock.getCount());
        assertEquals(stamina, env.player.getProperty(PlayerProperty.PROP_MAX_STAMINA));
        assertTrue(rewards.isEmpty());
        assertTrue(env.granted.isEmpty());
        assertEquals(0, env.events.get());
        var packet =
                env.player.packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.GetSceneAreaRsp)
                        .findFirst()
                        .orElseThrow();
        var city =
                GetSceneAreaRsp.parseFrom(packet.getData()).getCityInfoListList().stream()
                        .filter(x -> x.getCityId() == 7)
                        .findFirst()
                        .orElseThrow();
        assertEquals(6, city.getLevel());
    }

    @Test
    void resetSupportsExplicitCityAndReferenceDefaultCityEight() throws Exception {
        env.manager.getCityInfo(7).setLevel(6);
        env.manager.getCityInfo(8).setLevel(5);
        run("reset", "7");
        assertEquals(1, env.manager.getCityInfo(7).getLevel());
        assertEquals(5, env.manager.getCityInfo(8).getLevel());
        run("reset");
        assertEquals(1, env.manager.getCityInfo(8).getLevel());
        assertTrue(rewards.isEmpty());
    }

    @Test
    void unresolvedReceiptBlocksBothLevelChangeAndReset() throws Exception {
        var info = env.manager.getCityInfo(7);
        var receipt =
                new StatueOfferReceipt(107030, 40, 20, 1, 0, 2, 0, 7, 10000, 10700, List.of());
        info.setPendingOffer(receipt);
        run("level", "7", "6");
        run("reset", "7");
        assertEquals(1, info.getLevel());
        assertSame(receipt, info.getPendingOffer());
        assertEquals(0, env.saves.get());
        String operation = receipt.getOperationId();
        assertTrue(messages.stream().anyMatch(x -> x.contains(operation)));
    }

    @Test
    void invalidCityLevelsAndExtraArgumentsDoNotChangeProgress() throws Exception {
        for (String[] args :
                new String[][] {
                    {"level", "7", "11"}, {"level", "7", "0"}, {"level", "9", "2"},
                    {"level", "-1", "2"}, {"level", "x", "2"}, {"level", "7", "2", "extra"},
                    {"level", "7", "258"}, {"level", "7", "2147483647"}
                }) run(args);
        assertEquals(0, env.saves.get());
        assertTrue(env.player.packets.isEmpty());
        assertFalse(messages.isEmpty());
    }

    @Test
    void failedSaveRestoresPriorLevelAndPartialAmount() throws Exception {
        var info = env.manager.getCityInfo(7);
        info.setLevel(3);
        info.setNumCrystal(17);
        env.failSaveAt = 1;
        run("level", "7", "6");
        assertEquals(3, env.manager.getCityInfo(7).getLevel());
        assertEquals(17, env.manager.getCityInfo(7).getNumCrystal());
        assertTrue(env.player.packets.isEmpty());
        assertFalse(messages.isEmpty());
    }

    @Test
    void nearestStatusFindsActualStatuePointAndDoesNotMutateIt() throws Exception {
        run("status");
        assertTrue(
                messages.stream()
                        .anyMatch(x -> x.contains("point=7") && x.contains("spring=5000")));
        assertFalse(env.player.isScenePointForceLocked(3, 7));
        assertTrue(rewards.isEmpty());
    }

    @Test
    void rawSpringVolumeAliasCanIdentifyAStatue() throws Exception {
        var point = JsonUtils.decode("{PHBHHNDGFCF:5000}", PointData.class);
        point.setId(1234);
        env.put(GameData.getScenePointEntryMap(), (3 << 16) + 1234, new ScenePointEntry(3, point));
        run("status", "1234");
        assertTrue(
                messages.stream()
                        .anyMatch(x -> x.contains("point=1234") && x.contains("spring=5000")));
    }

    @Test
    void lockKeepsDiscoveryAndRefreshesHiddenPointSnapshot() throws Exception {
        env.player.getUnlockedScenePoints(3).add(7);
        run("lock", "7");
        assertTrue(env.player.isScenePointForceLocked(3, 7));
        assertTrue(env.player.getUnlockedScenePoints(3).contains(7));
        var packet =
                env.player.packets.stream()
                        .filter(x -> x.getOpcode() == PacketOpcodes.GetScenePointRsp)
                        .findFirst()
                        .orElseThrow();
        assertTrue(GetScenePointRsp.parseFrom(packet.getData()).getHidePointListList().contains(7));
        assertTrue(rewards.isEmpty());
    }

    @Test
    void firstUnlockGivesRewardOnceAcrossRepeatedLocksAndUnlocks() throws Exception {
        run("unlock", "7");
        run("lock", "7");
        run("unlock", "7");
        run("unlock", "7");
        assertFalse(env.player.isScenePointForceLocked(3, 7));
        assertTrue(env.player.getUnlockedScenePoints(3).contains(7));
        assertEquals(List.of("201:5", "102:50"), rewards);
    }

    @Test
    void unknownOrNonStatuePointIsRejected() throws Exception {
        run("lock", "31");
        run("unlock", "99999");
        assertFalse(env.player.isScenePointForceLocked(3, 31));
        assertFalse(env.player.getUnlockedScenePoints(3).contains(99999));
        assertTrue(rewards.isEmpty());
        assertTrue(env.player.packets.isEmpty());
    }

    @Test
    void aliasUsesTheExistingTargetAndPermissionPipeline() throws Exception {
        var old = Grasscutter.getPermissionHandler();
        var checks = new ArrayList<String>();
        try {
            Grasscutter.setPermissionHandler(
                    new PermissionHandler() {
                        public boolean EnablePermissionCommand() {
                            return true;
                        }

                        public boolean checkPermission(
                                Player sender, Player target, String own, String others) {
                            assertSame(env.player, target);
                            checks.add(own);
                            checks.add(others);
                            return false;
                        }
                    });
            new CommandMap(false)
                    .registerCommand("statue", command())
                    .invoke(null, env.player, "sots level 7 6");
            assertEquals(List.of("player.teleport", "player.teleport.others"), checks);
            assertEquals(0, env.saves.get());
        } finally {
            Grasscutter.setPermissionHandler(old);
        }
    }

    @Test
    void aliasCanChangeTheExplicitCommandMapTarget() throws Exception {
        var old = Grasscutter.getPermissionHandler();
        try {
            Grasscutter.setPermissionHandler(new DefaultPermissionHandler());
            var map = new CommandMap(false).registerCommand("statue", command());
            map.invoke(null, env.player, "sots level 7 6");
            assertEquals(6, env.manager.getCityInfo(7).getLevel());
            assertEquals(1, env.saves.get());
        } finally {
            Grasscutter.setPermissionHandler(old);
        }
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class StatueProximityTest {
    StatueHealingTest env;
    ScenePointEntry previous;
    List<Integer> previousIds;
    ConfigContainer.GameOptions previousOptions;
    PointData point;
    long start;

    @BeforeEach
    void setup() throws Exception {
        env = new StatueHealingTest();
        env.setup();
        env.env.fixture.player.getSession().setState(SessionState.ACTIVE);
        previous = GameData.getScenePointEntryMap().get((3 << 16) + 7);
        previousIds = GameData.getScenePointsPerScene().get(3);
        previousOptions = Grasscutter.getConfig().server.game.gameOptions;
        configure("{enabled:true,radius:15}");
        var row =
                com.google.gson.JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Scene/Point/scene3_point.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("points")
                        .get("7");
        point = JsonUtils.decode(row, PointData.class);
        point.setId(7);
        GameData.getScenePointEntryMap().put((3 << 16) + 7, new ScenePointEntry(3, point));
        GameData.getScenePointsPerScene().put(3, List.of(7));
        env.env.fixture.player.getUnlockedScenePoints(3).add(7);
        move(0);
        start = System.currentTimeMillis();
    }

    @AfterEach
    void close() {
        if (previous == null) GameData.getScenePointEntryMap().remove((3 << 16) + 7);
        else GameData.getScenePointEntryMap().put((3 << 16) + 7, previous);
        if (previousIds == null) GameData.getScenePointsPerScene().remove(3);
        else GameData.getScenePointsPerScene().put(3, previousIds);
        Grasscutter.getConfig().server.game.gameOptions = previousOptions;
        env.close();
    }

    void configure(String config) {
        Grasscutter.getConfig().server.game.gameOptions =
                JsonUtils.decode(
                        "{statueRecovery:" + config + "}", ConfigContainer.GameOptions.class);
    }

    void move(float offset) {
        env.env.fixture.player.getPosition().set(point.getTranPos());
        env.env.fixture.player.getPosition().setX(point.getTranPos().getX() + offset);
    }

    void tick(long offset) throws Exception {
        SotSManager.class.getMethod("onTick", long.class).invoke(env.manager, start + offset);
    }

    @Test
    void actualStatueHealsAfterEntryDelayWithoutClientRegionNotification() throws Exception {
        tick(0);
        tick(2499);
        assertEquals(10, env.hp());
        tick(2500);
        assertEquals(100, env.hp());
        assertEquals(1000, env.manager.getCurrentVolume());
        assertEquals(1, env.healCalls);
    }

    @Test
    void gadgetCategoryAlsoEnablesRecoveryWithoutSpringMetadata() throws Exception {
        var before = GameData.getGadgetDataMap().get(70130009);
        try {
            var json =
                    com.google.gson.JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    "resources/BinOutput/Scene/Point/scene3_point.json")))
                            .getAsJsonObject()
                            .getAsJsonObject("points")
                            .getAsJsonObject("7")
                            .deepCopy();
            json.remove("maxSpringVolume");
            json.remove("PHBHHNDGFCF");
            json.addProperty("gadgetId", 70130009);
            var resource = JsonUtils.decode(json, PointData.class);
            resource.setId(7);
            GameData.getScenePointEntryMap().put((3 << 16) + 7, new ScenePointEntry(3, resource));
            GameData.getGadgetDataMap()
                    .put(
                            70130009,
                            JsonUtils.decode(
                                    "{id:70130009,type:'TransPointFirst'}",
                                    emu.grasscutter.data.excels.GadgetData.class));
            tick(0);
            tick(2500);
            assertEquals(100, env.hp());
            assertEquals(1, env.healCalls);
        } finally {
            if (before == null) GameData.getGadgetDataMap().remove(70130009);
            else GameData.getGadgetDataMap().put(70130009, before);
        }
    }

    @Test
    void subsequentCyclesKeepTheFifteenSecondInterval() throws Exception {
        tick(0);
        tick(2500);
        env.health(100, 50);
        env.prop(PlayerProperty.PROP_CUR_SPRING_VOLUME, 10000);
        tick(17499);
        assertEquals(50, env.hp());
        tick(17500);
        assertEquals(100, env.hp());
        assertEquals(2, env.healCalls);
    }

    @Test
    void leavingBeforeTheDueCyclePreventsRemoteHealing() throws Exception {
        tick(0);
        move(100);
        tick(2500);
        assertEquals(10, env.hp());
        assertEquals(0, env.healCalls);
    }

    @Test
    void questLockStopsHealingEvenThoughDiscoveryIsRetained() throws Exception {
        tick(0);
        env.env.fixture.player.getForceLockedScenePoints(3).add(7);
        tick(2500);
        assertEquals(10, env.hp());
        assertTrue(env.env.fixture.player.getUnlockedScenePoints(3).contains(7));
    }

    @Test
    void undiscoveredStatueDoesNotProvideRecovery() throws Exception {
        env.env.fixture.player.getUnlockedScenePoints(3).clear();
        tick(0);
        tick(2500);
        assertEquals(10, env.hp());
    }

    @Test
    void absentSceneClearsTheOldCycleBeforeReentry() throws Exception {
        tick(0);
        env.env.fixture.player.setScene(null);
        tick(1000);
        env.env.fixture.player.setScene(env.env.fixture.scene);
        tick(2500);
        assertEquals(10, env.hp());
        tick(5000);
        assertEquals(100, env.hp());
    }

    @Test
    void inactiveSessionDoesNotContinueRecovery() throws Exception {
        tick(0);
        env.env.fixture.player.getSession().setState(SessionState.INACTIVE);
        tick(2500);
        assertEquals(10, env.hp());
    }

    @Test
    void disabledOrInvalidRadiusPreventsProximityHealing() throws Exception {
        configure("{enabled:false,radius:15}");
        tick(0);
        tick(2500);
        assertEquals(10, env.hp());
        configure("{enabled:true,radius:-1}");
        tick(5000);
        tick(7500);
        assertEquals(10, env.hp());
    }

    @Test
    void currentResourceReplacementIsNotHiddenByAPermanentPointCache() throws Exception {
        tick(0);
        var changed =
                JsonUtils.decode(
                        "{maxSpringVolume:5000,tranPos:{x:999999,y:999999,z:999999}}",
                        PointData.class);
        changed.setId(7);
        GameData.getScenePointEntryMap().put((3 << 16) + 7, new ScenePointEntry(3, changed));
        tick(2500);
        assertEquals(10, env.hp());
    }

    @Test
    void nonFinitePlayerCoordinatesCannotEnterTheRegion() throws Exception {
        env.env.fixture.player.getPosition().setX(Float.NaN);
        tick(0);
        tick(2500);
        assertEquals(10, env.hp());
    }

    @Test
    void legacyRegionMessagesCannotHealFromFarAway() throws Exception {
        tick(0);
        move(100);
        new emu.grasscutter.server.packet.recv.HandlerEnterTransPointRegionNotify()
                .handle(env.env.fixture.player.getSession(), new byte[0], new byte[0]);
        new emu.grasscutter.server.packet.recv.HandlerExitTransPointRegionNotify()
                .handle(env.env.fixture.player.getSession(), new byte[0], new byte[0]);
        tick(2500);
        assertEquals(10, env.hp());
    }

    @Test
    void enteringTheRegionUsesTheExistingDeadAvatarRevival() throws Exception {
        var dead = GameEntity.class.getDeclaredMethod("setDead", boolean.class);
        dead.setAccessible(true);
        dead.invoke(env.env.caster, true);
        env.health(100, 0);
        tick(0);
        assertTrue(env.env.caster.isAlive());
        assertTrue(env.hp() > 0);
    }

    @Test
    void explicitStopDiscardsThePendingCycle() throws Exception {
        tick(0);
        SotSManager.class.getMethod("stopRecovery").invoke(env.manager);
        tick(2500);
        assertEquals(10, env.hp());
        tick(5000);
        assertEquals(10, env.hp());
        SotSManager.class.getMethod("startRecovery").invoke(env.manager);
        tick(5001);
        tick(7501);
        assertEquals(100, env.hp());
    }

    @Test
    void disabledAutoRecoveryDoesNotReviveAgainInsideTheRegion() throws Exception {
        env.prop(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 0);
        tick(0);
        var dead = GameEntity.class.getDeclaredMethod("setDead", boolean.class);
        dead.setAccessible(true);
        dead.invoke(env.env.caster, true);
        env.health(100, 0);
        tick(2500);
        assertFalse(env.env.caster.isAlive());
    }
}

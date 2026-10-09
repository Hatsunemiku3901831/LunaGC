package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.server.game.GameServer;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;

class WorldAdmissionLifecycleTest {
    private AbilityTestFixture fixture;
    private GameServer server;

    class Member extends AbilityTestFixture.TestPlayer {
        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void save() {}
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        server = fixture.scene.getWorld().getServer();
        field("players", new ConcurrentHashMap<Integer, Player>());
        field("scheduler", new emu.grasscutter.server.scheduler.ServerTaskScheduler());
        field(
                "crucibleMatchSystem",
                new emu.grasscutter.game.activity.crucible.CrucibleMatchSystem(server));
        field(
                "homeWorlds",
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<
                        emu.grasscutter.game.home.HomeWorld>());
    }

    private void field(String name, Object value) throws Exception {
        var field = GameServer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(server, value);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void tickBetweenConstructionAndFirstAdmissionCannotRetireTheNewWorld() {
        var player = new Member();
        var world = new World(player);
        var scene = new Scene(world, fixture.scene.getSceneData());
        world.registerScene(scene);
        server.onTick();
        assertTrue(world.tryAddPlayer(player, scene.getId()));
        assertSame(world, player.getWorld());
        assertTrue(server.getWorlds().contains(world));
    }

    @Test
    void worldIsNotPublishedBeforeSubclassConstructionCompletes() {
        var player = new Member();
        var world =
                new AbilityTestFixture.TestWorld(server, player) {
                    @Override
                    public boolean onTick() {
                        fail("World tick escaped before first admission and subclass initialization");
                        return false;
                    }
                };
        server.onTick();
        assertFalse(server.getWorlds().contains(world));
    }

    @Test
    void failedAdmissionDoesNotPublishAnUnusableWorld() {
        var player = new Member();
        var world = new World(server, player);
        assertFalse(world.tryAddPlayer(player, -123));
        assertFalse(server.getWorlds().contains(world));
        assertNull(player.getWorld());
    }

    @Test
    void explicitRetirementStillRejectsAStaleReference() {
        var player = new Member();
        var world = new World(server, player);
        world.registerScene(new Scene(world, fixture.scene.getSceneData()));
        assertTrue(world.tryCloseIfEmpty());
        assertFalse(world.tryAddPlayer(player, 3));
        assertNull(player.getWorld());
        assertFalse(server.getWorlds().contains(world));
    }

    @Test
    void homeInitializationCannotExposeAPartiallyConstructedWorldToTick() {
        var home = emu.grasscutter.game.home.GameHome.create(20000);
        var player =
                new Member() {
                    @Override
                    public boolean isOnline() {
                        return true;
                    }

                    @Override
                    public emu.grasscutter.game.home.GameHome getHome() {
                        return home;
                    }
                };
        var world =
                new emu.grasscutter.game.home.HomeWorld(server, player) {
                    @Override
                    public void refreshModuleManager() {
                        assertFalse(server.getWorlds().contains(this));
                        server.onTick();
                    }
                };
        assertTrue(world.tryCloseIfEmpty());
        assertFalse(server.getWorlds().contains(world));
    }
}

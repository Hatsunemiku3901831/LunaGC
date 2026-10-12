package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;

import org.junit.jupiter.api.*;

import java.util.*;

/** Dungeon entry must report and commit the actual scene transfer outcome. */
class DungeonEntryOutcomeTest extends QuestTestFixture {
    private static final int DUNGEON = 9001;
    private Map<Integer, DungeonData> previous;
    private Scene source, destination;
    private TransferWorld world;
    private DungeonSystem system;

    private class TransferWorld extends World {
        boolean success, throwTransfer;
        int transfers;

        TransferWorld() {
            super(player.server, player);
        }

        @Override
        public boolean transferPlayerToScene(Player target, int scene, DungeonData data) {
            transfers++;
            if (throwTransfer) throw new IllegalStateException("Controlled transfer failure");
            if (!success) return false;
            target.setScene(destination);
            target.setSceneId(destination.getId());
            destination.getPlayers().add(target);
            return true;
        }
    }

    @BeforeEach
    void dungeon() {
        previous = new HashMap<>(GameData.getDungeonDataMap());
        var data =
                gson.fromJson(
                        "{id:9001,sceneId:9,type:'DUNGEON_PLOT',subType:'DUNGEON_SUB_NONE',passCond:0}",
                        DungeonData.class);
        GameData.getDungeonDataMap().put(DUNGEON, data);
        world = new TransferWorld();
        player.setWorld(world);
        boolean enabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        try {
            source = new Scene(world, gson.fromJson("{id:4,type:'SCENE_WORLD'}", SceneData.class));
            destination =
                    new Scene(world, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = enabled;
        }
        source.setPrevScene(77);
        source.setPrevScenePoint(88);
        player.setScene(source);
        player.setSceneId(4);
        source.getPlayers().add(player);
        system = new DungeonSystem(player.server);
    }

    @AfterEach
    void restore() {
        source.getScriptManager().onDestroy();
        destination.getScriptManager().onDestroy();
        GameData.getDungeonDataMap().clear();
        GameData.getDungeonDataMap().putAll(previous);
    }

    @Test
    void rejectedEntryReturnsFalseAndPreservesBothScenesExitState() {
        assertFalse(system.enterDungeon(player, 123, DUNGEON, true));
        assertEquals(1, world.transfers);
        assertSame(source, player.getScene());
        assertEquals(77, source.getPrevScene());
        assertEquals(88, source.getPrevScenePoint());
        assertNull(destination.getDungeonManager());
    }

    @Test
    void rejectedTowerHandoffReturnsFalseWithoutInstallingObservers() {
        assertFalse(
                system.handoffDungeon(player, DUNGEON, List.of(new TowerDungeonSettleListener())));
        assertSame(source, player.getScene());
        assertNull(destination.getDungeonManager());
        assertNull(destination.getDungeonSettleListeners());
    }

    @Test
    void successfulEntryRecordsTheSourceOnTheDestinationAndInstallsItsManager() {
        world.success = true;
        assertTrue(system.enterDungeon(player, 123, DUNGEON, true));
        assertEquals(4, destination.getPrevScene());
        assertEquals(123, destination.getPrevScenePoint());
        assertEquals(77, source.getPrevScene());
        assertEquals(88, source.getPrevScenePoint());
        assertNotNull(destination.getDungeonManager());
        assertEquals(DUNGEON, destination.getDungeonManager().getDungeonData().getId());
        assertFalse(destination.getDungeonSettleListeners().isEmpty());
    }

    @Test
    void noSavePreviousKeepsDestinationExitMetadata() {
        world.success = true;
        destination.setPrevScene(6);
        destination.setPrevScenePoint(7);
        assertTrue(system.enterDungeon(player, 123, DUNGEON, false));
        assertEquals(6, destination.getPrevScene());
        assertEquals(7, destination.getPrevScenePoint());
    }

    @Test
    void successfulTowerHandoffUsesProvidedObserversAndTowerManager() {
        world.success = true;
        var listener = new TowerDungeonSettleListener();
        assertTrue(system.handoffDungeon(player, DUNGEON, List.of(listener)));
        assertTrue(destination.getDungeonManager().isTowerDungeon());
        assertEquals(List.of(listener), destination.getDungeonSettleListeners());
    }

    @Test
    void missingResourceNeverAttemptsTransfer() {
        assertFalse(system.enterDungeon(player, 1, 999999, true));
        assertFalse(system.handoffDungeon(player, 999999, List.of()));
        assertEquals(0, world.transfers);
    }

    @Test
    void absentActorWorldOrSceneReturnsFailure() {
        assertFalse(system.enterDungeon(null, 1, DUNGEON, true));
        assertFalse(system.handoffDungeon(null, DUNGEON, List.of()));
        player.setWorld(null);
        assertFalse(system.enterDungeon(player, 1, DUNGEON, true));
        assertFalse(system.handoffDungeon(player, DUNGEON, List.of()));
        player.setWorld(world);
        player.setScene(null);
        assertFalse(system.enterDungeon(player, 1, DUNGEON, true));
    }

    @Test
    void thrownTransferLeavesOriginalExitMetadataUntouched() {
        world.throwTransfer = true;
        assertThrows(
                IllegalStateException.class, () -> system.enterDungeon(player, 123, DUNGEON, true));
        assertEquals(77, source.getPrevScene());
        assertEquals(88, source.getPrevScenePoint());
        assertNull(destination.getDungeonManager());
    }
}

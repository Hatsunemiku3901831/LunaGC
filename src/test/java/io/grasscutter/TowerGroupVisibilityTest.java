package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.world.GridPosition;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TowerGroupVisibilityTest {
    @Test
    void entityRangeProtectionCannotDropTheAbyssClockAndLowerHalfGroups() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var options = Grasscutter.getConfig().server.game.gameOptions;
            boolean previous = options.isPreventEntityError;
            var gson = new Gson();
            var scene = new Scene(environment.scene.getWorld(),
                    gson.fromJson("{id:33737,type:'SCENE_DUNGEON'}", SceneData.class), false);
            var library = ScriptLoader.getScriptLib();
            // Pin the live failure's grid layout without reading a machine's ignored cache file.
            var cacheField = SceneScriptManager.class.getDeclaredField("groupGridsCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var cache = (Map<Integer, List<Grid>>) cacheField.get(null);
            var grids = new ArrayList<Grid>();
            for (int i = 0; i < 6; i++) grids.add(new Grid());
            grids.get(0).grid.put(new GridPosition(0, 0, 20), Set.of(233737002, 233737003, 233737009));
            grids.get(3).grid.put(new GridPosition(0, 0, 1000), Set.of(233737001, 233737004));
            var previousGrids = cache.put(33737, grids);
            try {
                options.isPreventEntityError = true;
                var ready = new CompletableFuture<Boolean>();
                scene.getScriptManager().whenInitialized(ready::complete);
                assertTrue(ready.get(10, TimeUnit.SECONDS));
                assertFalse(scene.getPlayerActiveGroups(environment.player).contains(233737001),
                        "ordinary world visibility must retain the entity-range protection");
                var manager = new DungeonManager(scene,
                        gson.fromJson("{id:3407,sceneId:33737,passCond:0}", DungeonData.class));
                manager.setTowerDungeon(true);
                scene.setDungeonManager(manager);
                var active = scene.getPlayerActiveGroups(environment.player);
                assertEquals(Set.of(233737001, 233737002, 233737003, 233737004, 233737009), active);
                // The same first pass as Scene.onLoadGroup, before suites and callbacks run.
                for (int id : active)
                    scene.getScriptManager().loadGroupFromScript(scene.getScriptManager().findGroupById(id));
                library.setSceneScriptManager(scene.getScriptManager());
                library.setCurrentGroup(scene.getScriptManager().getLoadedGroupById(233737003));
                assertEquals(0, library.SetGroupVariableValueByGroup("TPL_TIME", 565, 233737001));
                assertEquals(0, library.SetGroupVariableValueByGroup("stage", 1, 233737001));
                assertEquals(565, library.GetGroupVariableValueByGroup("TPL_TIME", 233737001));
                assertEquals(1, library.GetGroupVariableValueByGroup("stage", 233737001));
                assertNotNull(scene.getScriptManager().getLoadedGroupById(233737004));
            } finally {
                library.removeCurrentGroup();
                library.removeSceneScriptManager();
                scene.getScriptManager().onDestroy();
                if (previousGrids == null) cache.remove(33737);
                else cache.put(33737, previousGrids);
                options.isPreventEntityError = previous;
            }
        }
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.ScriptLoader;
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
            try {
                options.isPreventEntityError = true;
                var ready = new CompletableFuture<Boolean>();
                scene.getScriptManager().whenInitialized(ready::complete);
                assertTrue(ready.get(10, TimeUnit.SECONDS));
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
                options.isPreventEntityError = previous;
            }
        }
    }
}

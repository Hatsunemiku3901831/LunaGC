package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.ScriptLib;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.controller.EntityController;
import java.util.Map;
import javax.script.SimpleBindings;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.VarArgFunction;

class EntityLuaContextTest {
    @SuppressWarnings("unchecked")
    private static void install(AbilityTestFixture fixture, SceneGroup group) throws Exception {
        var field = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances"); field.setAccessible(true);
        ((Map<Integer, SceneGroupInstance>)field.get(fixture.scene.getScriptManager())).put(group.id,
                new SceneGroupInstance(group, fixture.player));
        fixture.entity.setGroupId(group.id);
    }

    @Test void monsterCallbacksSeeTheirGroupAndRestoreTheOuterContext() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            if (ScriptLoader.getEngine() == null) ScriptLoader.init();
            var library = ScriptLoader.getScriptLib();
            var group = SceneGroup.of(123);
            install(fixture, group);
            var outer = SceneGroup.of(456);
            library.setCurrentGroup(outer);
            library.setCurrentEntity(null);
            library.removeSceneScriptManager();
            var bindings = new SimpleBindings();
            bindings.put("OnClientExecuteReq", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    assertSame(fixture.entity, library.getCurrentEntity().orElseThrow());
                    assertSame(fixture.scene.getScriptManager(), library.getSceneScriptManagerOrNull());
                    assertEquals(123, library.GetContextGroupId());
                    return LuaValue.ZERO;
                }
            });
            try {
                assertEquals(0, new EntityController(null, bindings).onClientExecuteRequest(fixture.entity, 1, 2, 3));
                assertSame(outer, library.getCurrentGroup().orElseThrow());
                assertTrue(library.getCurrentEntity().isEmpty());
                assertNull(library.getSceneScriptManagerOrNull());
            } finally { library.removeCurrentGroup(); library.removeCurrentEntity(); library.removeSceneScriptManager(); }
        }
    }

    @Test void nestedAndFailingCallbacksRestoreAllThreeContextSlots() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var library = new ScriptLib();
            var group = SceneGroup.of(123);
            install(fixture, group);
            assertThrows(LuaError.class, () -> library.withEntityContext(fixture.entity, () -> {
                assertSame(group, library.getCurrentGroup().orElseThrow());
                var other = new AbilityTestFixture.TestEntity(fixture.scene);
                library.withEntityContext(other, () -> {
                    assertTrue(library.getCurrentGroup().isEmpty());
                    assertSame(other, library.getCurrentEntity().orElseThrow());
                    return null;
                });
                assertSame(fixture.entity, library.getCurrentEntity().orElseThrow());
                assertSame(group, library.getCurrentGroup().orElseThrow());
                throw new LuaError("expected test failure");
            }));
            assertTrue(library.getCurrentGroup().isEmpty());
            assertTrue(library.getCurrentEntity().isEmpty());
            assertNull(library.getSceneScriptManagerOrNull());
        }
    }
}

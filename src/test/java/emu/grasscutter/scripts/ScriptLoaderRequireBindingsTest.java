package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.utils.FileUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Real production compilation/evaluation and require calls, without a server or database. */
final class ScriptLoaderRequireBindingsTest {
    @TempDir Path directory;
    private AtomicReference<Bindings> currentBindings;
    private Bindings previousBindings;
    private Bindings engineBindings;

    @BeforeAll
    static void initialize() throws Exception {
        var expectedWork = System.getProperty("requireBindings.expectedWork");
        if (expectedWork != null) {
            assertEquals(
                    Path.of(expectedWork).resolve("config.json"),
                    Grasscutter.configFile.getCanonicalFile().toPath());
            assertEquals(
                    Path.of(expectedWork).resolve("data"),
                    Path.of(Grasscutter.getConfig().folderStructure.data));
        }
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        assertTrue(Configuration.FAST_REQUIRE, "These regressions exercise production fast require");
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preserveBindings() throws Exception {
        var field = ScriptLoader.class.getDeclaredField("currentBindings");
        field.setAccessible(true);
        currentBindings = (AtomicReference<Bindings>) field.get(null);
        previousBindings = currentBindings.getAndSet(null);
        engineBindings = ScriptLoader.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
    }

    @AfterEach
    void restoreBindings() {
        currentBindings.set(previousBindings);
    }

    @Test
    void secondRequireSeesFirstModulesWritesInEachIndependentGroup() throws Exception {
        var first = module("First", "first_marker=group_marker+7");
        var second = module("Second", "second_marker=first_marker+11");
        var group = script("Group", first + second + "after_requires=true");
        var left = ScriptLoader.getEngine().createBindings();
        var right = ScriptLoader.getEngine().createBindings();
        left.put("group_marker", LuaValue.valueOf(100));
        right.put("group_marker", LuaValue.valueOf(200));

        ScriptLoader.eval(group, left);
        ScriptLoader.eval(group, right);

        assertAll(
                () -> assertEquals(107, ((LuaValue) left.get("first_marker")).checkint()),
                () -> assertEquals(LuaValue.valueOf(118), left.get("second_marker")),
                () -> assertEquals(LuaValue.valueOf(207), right.get("first_marker")),
                () -> assertEquals(LuaValue.valueOf(218), right.get("second_marker")),
                () -> assertEquals(LuaValue.TRUE, left.get("after_requires")),
                () -> assertEquals(LuaValue.TRUE, right.get("after_requires")),
                () -> assertNull(engineBindings.get("first_marker")),
                () -> assertNull(engineBindings.get("second_marker")),
                () -> assertNull(currentBindings.get()));
    }

    @Test
    void nestedModuleAndFollowingSiblingKeepTheCallersBindings() throws Exception {
        var leaf = module("Leaf", "leaf_marker=outer_marker+3");
        var sibling = module("Sibling", "sibling_marker=leaf_marker+5");
        var outer = module("Outer", "outer_marker=7\n" + leaf + sibling);
        var following = module("Following", "following_marker=sibling_marker+11");
        var bindings = ScriptLoader.getEngine().createBindings();

        ScriptLoader.eval(script("NestedGroup", outer + following), bindings);

        assertAll(
                () -> assertEquals(LuaValue.valueOf(7), bindings.get("outer_marker")),
                () -> assertEquals(LuaValue.valueOf(10), bindings.get("leaf_marker")),
                () -> assertEquals(LuaValue.valueOf(15), bindings.get("sibling_marker")),
                () -> assertEquals(LuaValue.valueOf(26), bindings.get("following_marker")),
                () -> assertNull(engineBindings.get("sibling_marker")),
                () -> assertNull(engineBindings.get("following_marker")),
                () -> assertNull(currentBindings.get()));
    }

    @Test
    void failingRequiredModuleStillAllowsTheNextRequireAndClearsTheTopLevelBinding()
            throws Exception {
        var broken = module("Broken", "before_error=7\nerror('expected Common failure')");
        var following = module("AfterFailure", "after_error=before_error+11");
        var bindings = ScriptLoader.getEngine().createBindings();

        assertDoesNotThrow(
                () ->
                        ScriptLoader.eval(
                                script("FailureGroup", broken + following + "continued=true"), bindings));

        assertAll(
                () -> assertEquals(LuaValue.valueOf(7), bindings.get("before_error")),
                () -> assertEquals(LuaValue.valueOf(18), bindings.get("after_error")),
                () -> assertEquals(LuaValue.TRUE, bindings.get("continued")),
                () -> assertNull(engineBindings.get("after_error")),
                () -> assertNull(currentBindings.get()));
    }

    @Test
    void topLevelLuaExceptionRestoresThePreviousEmptyBinding() throws Exception {
        var bindings = ScriptLoader.getEngine().createBindings();
        var failure =
                script("TopLevelFailure", "before_failure=7\nerror('expected top-level failure')");

        var exception =
                assertThrows(ScriptException.class, () -> ScriptLoader.eval(failure, bindings));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("expected top-level failure")),
                () -> assertEquals(LuaValue.valueOf(7), bindings.get("before_failure")),
                () -> assertNull(currentBindings.get()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reentrantEvaluationRestoresDistinctParentBindingAfterSuccessOrFailure(boolean fail)
            throws Exception {
        var parent = ScriptLoader.getEngine().createBindings();
        var child = ScriptLoader.getEngine().createBindings();
        parent.put("owner_marker", LuaValue.valueOf(100));
        child.put("owner_marker", LuaValue.valueOf(200));
        var childScript =
                script(
                        "Child",
                        "child_marker=owner_marker+7\n"
                                + (fail ? "error('expected child failure')" : ""));
        parent.put(
                "evaluate_child",
                new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        if (fail) {
                            var exception =
                                    assertThrows(
                                            ScriptException.class,
                                            () -> ScriptLoader.eval(childScript, child));
                            assertTrue(exception.getMessage().contains("expected child failure"));
                        } else {
                            assertDoesNotThrow(() -> ScriptLoader.eval(childScript, child));
                        }
                        return LuaValue.NONE;
                    }
                });
        var observer = module("ParentObserver", "observed_owner=owner_marker+11");

        ScriptLoader.eval(script("Parent", "evaluate_child()\n" + observer), parent);

        assertAll(
                () -> assertEquals(LuaValue.valueOf(111), parent.get("observed_owner")),
                () -> assertEquals(LuaValue.valueOf(207), child.get("child_marker")),
                () -> assertNull(parent.get("child_marker")),
                () -> assertNull(child.get("observed_owner")),
                () -> assertNull(engineBindings.get("observed_owner")),
                () -> assertNull(currentBindings.get()));
    }

    private CompiledScript script(String name, String source) throws Exception {
        var path = directory.resolve(name + ".lua");
        Files.writeString(path, source);
        var compiled = ScriptLoader.getScript(path.toString(), true);
        assertNotNull(compiled, "Synthetic fixture must compile through the production loader");
        return compiled;
    }

    private String module(String name, String source) throws Exception {
        var path = directory.resolve(name + ".lua");
        Files.writeString(path, source);
        // Keep synthetic Common files in @TempDir while exercising the real require path lookup.
        var relative = FileUtils.getScriptPath("Common").toAbsolutePath().relativize(path);
        var module = relative.toString().replace('\\', '/');
        return "require \"" + module.substring(0, module.length() - 4) + "\"\n";
    }
}

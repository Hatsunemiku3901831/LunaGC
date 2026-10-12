package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.exec.ExecRefreshGroupSuite;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.script.LuaScriptEngineFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Actual quest refresh entry, physical suites, visibility flags and Mongo state. */
class QuestSuiteVisibilityTest extends QuestTestFixture {
    private static final int GROUP = 133003002;
    private SceneGroup group;
    private SceneScriptManager scripts;
    private GameQuest quest;

    @BeforeEach
    void groups() throws Exception {
        quest = active(1100509);
        scripts = player.getScene().getScriptManager();
        player.getWorld().getScenes().put(3, player.getScene());
        replaceField(SceneScriptManager.class, scripts, "isInit", true);
        replaceField(
                ScriptLoader.class, null, "engine", new LuaScriptEngineFactory().getScriptEngine());
        replaceField(ScriptLoader.class, null, "scriptLib", new ScriptLib());
        group = SceneGroup.of(GROUP);
        group.overrideScriptPath = "Scene/3/__quest_visibility_fixture_missing.lua";
        group.variables = new ArrayList<>();
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        group.suites = new ArrayList<>();
        for (int index = 0; index < 3; index++) group.suites.add(new SceneSuite());
        map("localGroups").put(GROUP, group);
        map("sceneGroupsInstances").put(GROUP, new SceneGroupInstance(group, player));
        map("cachedSceneGroupsInstances").put(GROUP, scripts.getGroupInstanceById(GROUP));
    }

    @AfterEach
    void finishEvents() throws Exception {
        SceneScriptManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        drain();
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Integer, T> map(String name) throws Exception {
        var field = SceneScriptManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Integer, T>) field.get(scripts);
    }

    private boolean refresh(int suite) throws Exception {
        boolean result = new ExecRefreshGroupSuite().execute(quest, null, "3", GROUP + "," + suite);
        drain();
        return result;
    }

    @Test
    void successfulQuestOverridePinsAndDefaultResetReleasesIt() throws Exception {
        assertTrue(refresh(2));
        assertTrue(group.dontUnload);
        assertTrue(refresh(0));
        assertEquals(1, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void completedChapterCanReleaseItsExistingQuestPin() throws Exception {
        assertTrue(refresh(2));
        quest.getMainQuest().finish();
        drain();
        assertTrue(quest.getMainQuest().isFinished());
        assertTrue(player.getQuestManager().getSceneGroupSuite(3).isEmpty());
        assertTrue(refresh(0));
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void failedRefreshDoesNotLeakATemporaryPin() throws Exception {
        assertFalse(refresh(9));
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void failedResetPreservesItsPreviousPinAndSavedWave() throws Exception {
        assertTrue(refresh(2));
        group.init_config.suite = 9;
        assertFalse(refresh(0));
        assertTrue(group.dontUnload);
        assertEquals(2, quest.getMainQuest().getQuestGroupSuites().get(0).getSuite());
    }

    @Test
    void failedRefreshRestoresAnExistingIndependentPin() throws Exception {
        group.dontUnload = true;
        assertFalse(refresh(9));
        assertTrue(group.dontUnload);
    }

    @Test
    void anotherActiveChapterStillProtectsTheGroupAfterReset() throws Exception {
        assertTrue(refresh(2));
        var other = active(1909002);
        other.getMainQuest()
                .getQuestGroupSuites()
                .add(QuestGroupSuite.of().scene(3).group(GROUP).suite(3).build());
        assertTrue(refresh(0));
        assertTrue(group.dontUnload);
        assertEquals(3, other.getMainQuest().getQuestGroupSuites().get(0).getSuite());
    }

    @Test
    void anotherPlayerInThisWorldKeepsTheirQuestProtection() throws Exception {
        assertTrue(refresh(2));
        var guest = new TestPlayer();
        guest.setUid(10002);
        guest.server = player.server;
        guest.setWorld(player.getWorld());
        player.getWorld().getPlayers().add(guest);
        var main = guest.getQuestManager().addMainQuest(quest.getQuestData());
        main.getQuestGroupSuites().add(QuestGroupSuite.of().scene(3).group(GROUP).suite(3).build());
        assertTrue(refresh(0));
        assertTrue(group.dontUnload);
        assertEquals(3, main.getQuestGroupSuites().get(0).getSuite());
    }

    @Test
    void playerWhoLeftTheWorldDoesNotKeepThisWorldPinned() throws Exception {
        assertTrue(refresh(2));
        var guest = new TestPlayer();
        guest.setUid(10002);
        guest.server = player.server;
        player.getWorld().getPlayers().add(guest);
        guest.getQuestManager()
                .addMainQuest(quest.getQuestData())
                .getQuestGroupSuites()
                .add(QuestGroupSuite.of().scene(3).group(GROUP).suite(3).build());
        assertTrue(refresh(0));
        assertFalse(group.dontUnload);
    }

    @Test
    void existingFallbackProtectionSurvivesAQuestOverrideAndReset() throws Exception {
        group.dynamic_load = true;
        group.dontUnload = true;
        assertTrue(refresh(2));
        assertTrue(refresh(0));
        assertTrue(group.dontUnload);
        assertTrue(group.dynamic_load);
    }

    @Test
    void resetWithoutQuestOwnershipPreservesAnIndependentPin() throws Exception {
        group.dontUnload = true;
        assertTrue(refresh(0));
        assertTrue(group.dontUnload);
    }

    @Test
    void malformedDefinitionThrowDoesNotLeaveATemporaryPin() throws Exception {
        group.variables = null;
        assertFalse(refresh(2));
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "['3']",
                "[null,'133003002,2']",
                "['3',null]",
                "['0','133003002,2']",
                "['bad','133003002,2']",
                "['3','']",
                "['3','133003002']",
                "['3','133003002,bad']",
                "['3','0,2']",
                "['3','133003002,-1']"
            })
    void invalidParametersReturnFailureWithoutChangingGroups(String json) throws Exception {
        var args = gson.fromJson(json, String[].class);
        assertFalse(new ExecRefreshGroupSuite().execute(quest, null, args));
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void missingGroupReturnsFailureInsteadOfThrowing() {
        assertFalse(new ExecRefreshGroupSuite().execute(quest, null, "3", (GROUP + 99) + ",2"));
        assertFalse(group.dontUnload);
    }

    @Test
    void destroyedSceneDoesNotAcceptANewQuestOverride() throws Exception {
        scripts.onDestroy();
        assertFalse(refresh(2));
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void invalidLaterPairIsRejectedBeforeAnySuiteChanges() throws Exception {
        assertFalse(new ExecRefreshGroupSuite().execute(quest, null, "3", GROUP + ",2;bad,1"));
        drain();
        assertEquals(0, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertFalse(group.dontUnload);
        assertTrue(quest.getMainQuest().getQuestGroupSuites().isEmpty());
    }

    @Test
    void missingTargetDoesNotPreventOtherValidTargetsFromApplying() throws Exception {
        assertFalse(
                new ExecRefreshGroupSuite()
                        .execute(quest, null, "3", (GROUP + 99) + ",1;" + GROUP + ",2"));
        drain();
        assertEquals(2, scripts.getGroupInstanceById(GROUP).getActiveSuiteId());
        assertTrue(group.dontUnload);
        assertEquals(2, quest.getMainQuest().getQuestGroupSuites().get(0).getSuite());
    }

    @Test
    void pinIsVisibleToTheSceneVisibilityThread() throws Exception {
        assertTrue(
                java.lang.reflect.Modifier.isVolatile(
                        SceneGroup.class.getField("dontUnload").getModifiers()));
    }
}

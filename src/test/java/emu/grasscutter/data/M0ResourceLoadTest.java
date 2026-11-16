package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.FightProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M0ResourceLoadTest {
    @TempDir Path directory;

    @Test
    void realPreparedBirthDataRecalculatesAndMissingFileCannotAdmitLogin() throws Exception {
        var config = Grasscutter.getConfig();
        String root = System.getenv("LUNAGC_M0_INSTANCE_ROOT");
        assertNotNull(root, "Prepare D02-M0 resources and set LUNAGC_M0_INSTANCE_ROOT");
        Path resource = Path.of(root).toAbsolutePath().resolve("resources");
        assertTrue(Files.isRegularFile(resource.resolve("D02-M0-MANIFEST.json")));
        var folders = config.folderStructure;
        var previousResources = folders.resources;
        var previousData = folders.data;
        boolean previousMode = Configuration.GAME_INFO.m0.enabled;
        boolean previousQuesting = Configuration.GAME_OPTIONS.questing.enabled;
        try {
            assertFalse(ResourceLoader.isLoadedAll());
            folders.resources = resource.toString();
            folders.data = directory.toString();
            Configuration.GAME_INFO.m0.enabled = true;
            Configuration.GAME_OPTIONS.questing.enabled = true;
            M0Resources.loadBirthResources();
            assertTrue(M0Resources.isBirthReady());
            assertTrue(ResourceLoader.isLoginReady());
            assertFalse(ResourceLoader.isLoadedAll());
            var avatar = new Avatar(M0Resources.AVATAR_ID);
            assertEquals(701, avatar.getSkillDepotId());
            assertEquals(11101, avatar.getAvatarData().getInitialWeapon());
            assertEquals(911.791015625f, avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
            assertEquals(17.808000564575195f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
            assertEquals(57.224998474121094f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE));
            assertEquals(5, avatar.getAvatarData().getAbilities().size());
            assertEquals(50011101, GameData.getItemDataMap().get(11101).getGadgetId());
            assertEquals("Weapon_Sword_Metal", GameData.getGadgetDataMap().get(50011101).getJsonName());
            assertEquals("Level_BigWorld", GameData.getSceneDataMap().get(3).getLevelEntityConfig());
            Configuration.GAME_INFO.m0.enabled = false;
            assertFalse(ResourceLoader.isLoginReady());
            Configuration.GAME_INFO.m0.enabled = true;
            folders.resources = directory.resolve("missing-resources").toString();
            assertThrows(Exception.class, M0Resources::loadBirthResources);
            assertFalse(M0Resources.isBirthReady());
            assertFalse(ResourceLoader.isLoginReady());
            assertFalse(ResourceLoader.isLoadedAll());
        } finally {
            folders.resources = previousResources;
            folders.data = previousData;
            Configuration.GAME_INFO.m0.enabled = previousMode;
            Configuration.GAME_OPTIONS.questing.enabled = previousQuesting;
        }
    }
}

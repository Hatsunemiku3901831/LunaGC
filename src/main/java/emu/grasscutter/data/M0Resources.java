package emu.grasscutter.data;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.weapon.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.FightProperty;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real, finite birth-resource loading for the explicitly enabled D02-M0 instance. */
public final class M0Resources {
    public static final int AVATAR_ID = 10000007;
    public static final int WEAPON_ID = 11101;
    public static final int SCENE_ID = 3;
    private static volatile boolean birthReady;

    private M0Resources() {}

    public static boolean isBirthReady() {
        return birthReady;
    }

    public static int selectedDepotId() {
        return GAME_OPTIONS.questing.enabled ? 701 : 704;
    }

    /** Does not set ResourceLoader.loadedAll or claim that scene wire is ready. */
    public static void loadBirthResources() throws Exception {
        birthReady = false;
        var folders = Grasscutter.getConfig().folderStructure;
        var resources = Path.of(folders.resources);
        var excel = resources.resolve("ExcelBinOutput");
        var avatarConfigs = resources.resolve("BinOutput/Avatar");
        var playerGroup = resources.resolve("BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json");
        require(Files.isRegularFile(avatarConfigs.resolve("ConfigAvatar_PlayerGirl.json")), "PlayerGirl config file");
        require(Files.isRegularFile(playerGroup), "player ability group file");
        GameData.getAbilityEmbryoInfo().clear();
        GameDepot.getPlayerAbilities().clear();
        ResourceLoader.loadAbilityEmbryos(Path.of(folders.data).resolve("AbilityEmbryos.json"),
                avatarConfigs, playerGroup);
        for (var type : new Class<?>[] {AvatarSkillData.class, AvatarSkillDepotData.class,
                AvatarCurveData.class, AvatarPromoteData.class, WeaponCurveData.class,
                WeaponPromoteData.class, GadgetData.class, SceneData.class}) {
            for (var name : type.getAnnotation(ResourceType.class).name())
                ResourceLoader.loadFromResource(type, excel.resolve(name), GameData.getMapByResourceDef(type));
        }
        ResourceLoader.loadFromResource(ItemData.class, excel.resolve("WeaponExcelConfigData.json"),
                GameData.getItemDataMap());
        ResourceLoader.loadFromResource(AvatarData.class, excel.resolve("AvatarExcelConfigData.json"),
                GameData.getAvatarDataMap());
        validateBirthDependencies();
        birthReady = true;
        Grasscutter.getLogger().info(
                "D02-M0 birth resources loaded: avatar {}, depot {}, weapon {}, scene {}. Full resources remain unready.",
                AVATAR_ID, selectedDepotId(), WEAPON_ID, SCENE_ID);
    }

    static void validateBirthDependencies() {
        var data = GameData.getAvatarDataMap().get(AVATAR_ID);
        require(data != null, "avatar " + AVATAR_ID);
        require(data.getInitialWeapon() == WEAPON_ID, "birth weapon reference " + WEAPON_ID);
        require(data.getSkillDepotId() == 701, "base depot reference 701");
        require(GameData.getAvatarCurveDataMap().containsKey(1), "avatar level-1 curve");
        require(GameData.getAvatarPromoteData(data.getAvatarPromoteId(), 0) != null,
                "avatar promote phase 0");
        require(data.getName() != null && !data.getName().isBlank(), "avatar model identity");
        require(GameData.getAbilityEmbryoInfo().containsKey(data.getName())
                && !data.getAbilities().isEmpty(), "base ability embryo " + data.getName());
        for (int id : new int[] {701, selectedDepotId()}) {
            var depot = GameData.getAvatarSkillDepotDataMap().get(id);
            require(depot != null, "depot " + id);
            var group = depot.getSkillDepotAbilityGroup();
            require(group != null && GameDepot.getPlayerAbilities().containsKey(group)
                    && depot.getAbilities() != null && !depot.getAbilities().isEmpty(),
                    "depot ability group " + id);
            depot.getSkillsAndEnergySkill().forEach(skill -> require(
                    GameData.getAvatarSkillDataMap().containsKey(skill), "skill " + skill));
            if (depot.getSubSkills() != null) {
                depot.getSubSkills().stream().filter(skill -> skill > 0).forEach(skill -> require(
                        GameData.getAvatarSkillDataMap().containsKey((int) skill), "subskill " + skill));
            }
        }
        var weapon = GameData.getItemDataMap().get(WEAPON_ID);
        require(weapon != null && weapon.getItemType() == ItemType.ITEM_WEAPON, "weapon " + WEAPON_ID);
        require(GameData.getWeaponCurveDataMap().containsKey(1), "weapon level-1 curve");
        require(GameData.getWeaponPromoteData(weapon.getWeaponPromoteId(), 0) != null,
                "weapon promote phase 0");
        require(GameData.getGadgetDataMap().containsKey(weapon.getGadgetId()),
                "weapon gadget " + weapon.getGadgetId());
        require(GameData.getSceneDataMap().containsKey(SCENE_ID), "scene " + SCENE_ID);
        var avatar = new Avatar(data);
        if (selectedDepotId() != 701)
            avatar.setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(selectedDepotId()));
        for (var property : new FightProperty[] {FightProperty.FIGHT_PROP_MAX_HP,
                FightProperty.FIGHT_PROP_CUR_ATTACK, FightProperty.FIGHT_PROP_CUR_DEFENSE}) {
            float value = avatar.getFightProperty(property);
            require(Float.isFinite(value) && value > 0, "positive finite birth stat " + property);
        }
    }

    private static void require(boolean present, String dependency) {
        if (!present) throw new IllegalStateException("D02-M0 missing dependency: " + dependency);
    }
}

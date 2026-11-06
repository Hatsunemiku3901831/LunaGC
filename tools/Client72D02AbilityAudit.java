import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.ResourceLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Isolated, pinned production resource-loader audit; never starts a server. */
public final class Client72D02AbilityAudit {
    private static final Map<String, String> BLOBS = Map.of(
            "Avatar/ConfigAvatar_PlayerBoy.json", "d9fc78a2436037f5c9125455178078de983bd630d380bda78debcd0b4ccc8d2b",
            "Avatar/ConfigAvatar_PlayerGirl.json", "293175e6089c61f95a9d1caa6e2b2eda25473b72587382cd57f9cc1d14a62ea3",
            "AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json", "47d13068c5138d8190c06ee23c7bdb0859cda760b244641a86986f5bd388b2c7",
            "Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Common.json", "33c70134bd3ee3a6ae5f726072dddecf52de69a4c0cd838eea93e86fd006f7b3",
            "Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Wind.json", "d67449242c38ae7d8f8ef14e7d3c162dcde01a20db91764699cf25a07a51278d",
            "Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_QuestAbilities.json", "ebf9c7cd0b7ba4764379bff65f70bec4f9de14cc9eb02a1da731e17e555183e3",
            "Ability/Temp/AvatarAniEventAbilities/ConfigAbility_Avatar_PlayerBoy_AniEvent_Camera.json", "f6db88e57af642dd13d4991238245412149089a210d46b720110b941ba430238",
            "Ability/Temp/AvatarAniEventAbilities/ConfigAbility_Avatar_PlayerGirl_AniEvent_Camera.json", "043f359582dd8f300f9d92da4ca3084ff80476ea29278b31de62a9d8551fa6d6");
    private static final String WIND = "Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Wind.json";
    private static final Map<String, String> SOURCES = Map.of(
            "src/main/java/emu/grasscutter/data/ResourceLoader.java", "fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015",
            "src/main/java/emu/grasscutter/data/excels/avatar/AvatarSkillDepotData.java", "d09192d2f59e54c81213ea08d7a338b33aa4c09543e96f4a9ed4a37676b4207d",
            "src/main/java/emu/grasscutter/utils/JsonUtils.java", "365283aa208b394ddcb7c3563b7616b71e35cfd3d683dfa1b8caacc0005774b6");

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("repository binOutput mode output required");
        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path bin = Path.of(args[1]).toAbsolutePath().normalize();
        String mode = args[2];
        if (!mode.equals("normal") && !mode.equals("missing-wind")) throw new IllegalArgumentException("invalid mode");
        Path work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (var e : BLOBS.entrySet()) requireHash(bin.resolve(e.getKey()), e.getValue(), false);
        for (var e : SOURCES.entrySet()) requireHash(repo.resolve(e.getKey()), e.getValue(), true);
        Path jar = Path.of(ResourceLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        requireHash(jar, "eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e", false);
        var config = Grasscutter.getConfig();
        if (!Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json"))
                || !Path.of(config.folderStructure.data).toAbsolutePath().normalize().equals(work.resolve("data")))
            throw new IllegalStateException("Isolated configuration and data required");
        if (Files.exists(work.resolve("no-embryo-cache.json"))) throw new IllegalStateException("Unexpected cache");
        var embryos = ResourceLoader.class.getDeclaredMethod("loadAbilityEmbryos", Path.class, Path.class, Path.class);
        embryos.setAccessible(true);
        embryos.invoke(null, work.resolve("no-embryo-cache.json"), bin.resolve("Avatar"),
                bin.resolve("AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json"));
        var load = ResourceLoader.class.getDeclaredMethod("loadAbilityModifiers", Path.class);
        load.setAccessible(true);
        for (String f : new String[]{
                "Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Player_Common.json", WIND,
                "Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_QuestAbilities.json",
                "Ability/Temp/AvatarAniEventAbilities/ConfigAbility_Avatar_PlayerBoy_AniEvent_Camera.json",
                "Ability/Temp/AvatarAniEventAbilities/ConfigAbility_Avatar_PlayerGirl_AniEvent_Camera.json"}) {
            Path path = mode.equals("missing-wind") && f.equals(WIND) ? work.resolve("intentionally-missing-wind.json") : bin.resolve(f);
            if (path.startsWith(work) && Files.exists(path)) throw new IllegalStateException("Negative fixture must be absent");
            load.invoke(null, path);
        }
        int checked = 0;
        var missing = new TreeSet<String>();
        for (String name : new String[]{"PlayerBoy", "PlayerGirl"}) {
            var embryo = GameData.getAbilityEmbryoInfo().get(name);
            if (embryo == null) throw new IllegalStateException("Missing embryo: " + name);
            for (String ability : embryo.getAbilities()) {
                checked++;
                if (!GameData.getAbilityDataMap().containsKey(ability)) missing.add(ability);
            }
        }
        for (String name : new String[]{"Avatar_Player_AbilityGroup_Boy_Common", "Avatar_Player_AbilityGroup_Girl_Common",
                "Avatar_Player_AbilityGroup_Boy_Wind", "Avatar_Player_AbilityGroup_Girl_Wind"}) {
            var group = GameDepot.getPlayerAbilities().get(name);
            if (group == null) throw new IllegalStateException("Missing group: " + name);
            for (var ability : group.abilities) {
                checked++;
                if (!GameData.getAbilityDataMap().containsKey(ability.abilityName)) missing.add(ability.abilityName);
            }
        }
        if (checked != 28 || (mode.equals("normal") ? !missing.isEmpty() : missing.size() != 9))
            throw new IllegalStateException("Unexpected reference result: " + checked + " " + missing);
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", mode);
        result.put("checked", checked);
        result.put("missing", missing);
        result.put("resourceReady", false);
        result.put("scope", "fixed five ability-definition files and selected avatar/group references; no ability execution");
        Files.writeString(Path.of(args[3]), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
    }

    private static void requireHash(Path path, String expected, boolean normalizeNewlines) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        if (normalizeNewlines) bytes = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!actual.equals(expected)) throw new IllegalStateException("Input hash mismatch: " + path.getFileName());
    }
}

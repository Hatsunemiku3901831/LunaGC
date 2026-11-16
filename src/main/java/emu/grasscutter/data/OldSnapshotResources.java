package emu.grasscutter.data;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import com.google.gson.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.data.excels.weapon.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import org.bson.*;
import org.bson.codecs.DocumentCodec;

/** Finite resources and admission for one manifest-bound copied save. Never global/client ready. */
public final class OldSnapshotResources {
    private static volatile boolean ready;
    private static Bundle bundle;
    private static Document player;
    private static List<Document> avatars;
    private static List<Document> items;
    private OldSnapshotResources() {}
    record Bundle(Path root, JsonObject manifest, Path inputRoot) {}

    public static boolean isReady() { return ready; }

    public static void load() throws Exception {
        ready = false;
        if (!GAME_INFO.oldSnapshot.enabled || GAME_INFO.m0.enabled)
            throw new IllegalStateException("Old snapshot requires its own explicit mode");
        if (GAME_INFO.enableScriptInBigWorld)
            throw new IllegalStateException("Old snapshot finite scene requires scripts disabled");
        var folders = Grasscutter.getConfig().folderStructure;
        var root = Path.of(folders.resources);
        byte[] manifestBytes = Files.readAllBytes(root.resolve("D02-OLD-RESOURCE-MANIFEST.json"));
        if (!GAME_INFO.oldSnapshot.manifestSha256.equals(sha(manifestBytes)))
            throw new IllegalStateException("Old snapshot manifest SHA mismatch");
        var manifest = JsonUtils.decode(new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
        bundle = new Bundle(root, manifest, Path.of(GAME_INFO.oldSnapshot.inputRoot));
        for (var output : manifest.getAsJsonObject("outputs").entrySet())
            if (!output.getValue().getAsString().equals(sha(Files.readAllBytes(root.resolve(output.getKey())))))
                throw new IllegalStateException("Old snapshot resource SHA mismatch: " + output.getKey());
        ResourceLoader.loadAbilityEmbryos(Path.of(folders.data).resolve("AbilityEmbryos.json"),
                root.resolve("BinOutput/Avatar"), root.resolve("BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json"));
        ResourceLoader.loadOpenConfig(Path.of(folders.data).resolve("OpenConfig.json"),
                root.resolve("BinOutput/Talent/AvatarTalents"), root.resolve("BinOutput/Talent/EquipTalents"),
                root.resolve("BinOutput/Talent/RelicTalents"));
        for (var type : new Class<?>[] {AvatarSkillData.class, AvatarTalentData.class,
                AvatarCurveData.class, AvatarPromoteData.class, WeaponCurveData.class,
                WeaponPromoteData.class, ProudSkillData.class, EquipAffixData.class,
                ReliquaryMainPropData.class, ReliquaryAffixData.class, ReliquaryLevelData.class,
                ReliquarySetData.class, TpsWeaponData.class, TpsWeaponAccessoryData.class,
                GadgetData.class, AvatarSkillDepotData.class, FetterData.class,
                FetterCharacterCardData.class, RewardData.class, ItemData.class, SceneData.class, AvatarData.class})
            for (String name : type.getAnnotation(ResourceType.class).name())
                ResourceLoader.loadFromResource(type, root.resolve("ExcelBinOutput").resolve(name), GameData.getMapByResourceDef(type));
        try (var files = Files.list(root.resolve("BinOutput/Talent/AvatarTalents"))) {
            for (var file : files.toList()) ResourceLoader.loadTalent(file);
        }
        ResourceLoader.cacheTalentLevelSets();
        M0Resources.loadSceneIdentityResources();
        player = bson(fixedInput(bundle, "selected-0/player.bson")).get(0);
        avatars = bson(fixedInput(bundle, "selected-0/avatars-0.bson"));
        items = bson(fixedInput(bundle, "selected-0/items-0.bson"));
        var account = bson(fixedInput(bundle, "selected-0/account.bson"));
        if (account.size() != 1 || !Objects.equals(account.get(0).get("_id"), player.get("accountId")))
            throw new IllegalStateException("Copied snapshot account/player binding differs");
        admit(bundle);
        if (player.getInteger("sceneId") != 3 || !GameData.getSceneDataMap().containsKey(3))
            throw new IllegalStateException("Old snapshot finite scene is not the admitted Scene3");
        ready = true;
        Grasscutter.getLogger().info("Old snapshot finite resources loaded; network entry remains blocked by required snapshot/scene wire.");
    }

    /** Before AvatarStorage/Inventory can rewrite bindings or supply a default weapon. */
    public static void requireCopiedPlayer(Player selected) {
        if (!ready || selected.getUid() != player.getInteger("_id")
                || !Objects.equals(selected.getAccountId(), player.getString("accountId")))
            throw new IllegalStateException("Old snapshot player does not match the admitted copy");
        try { admit(bundle); }
        catch (Exception missing) { throw new IllegalStateException("Old snapshot actual resource admission failed", missing); }
        var db = DatabaseManager.getGameDatastore().getDatabase();
        checkDocuments(avatars, db.getCollection("avatars").find(new Document("ownerId", selected.getUid())).into(new ArrayList<>()), Set.of("currentHp", "currentEnergy", "currentSpecialEnergy"));
        checkDocuments(items, db.getCollection("items").find(new Document("ownerId", selected.getUid())).into(new ArrayList<>()), Set.of("saveStamp"));
        if (selected.getSceneId() != player.getInteger("sceneId")
                || selected.getMainCharacterId() != player.getInteger("mainCharacterId"))
            throw new IllegalStateException("Old snapshot scene/character changed outside the admitted copy");
    }

    private static void checkDocuments(List<Document> original, List<Document> current, Set<String> generated) {
        var saved = current.stream().collect(Collectors.toMap(d -> d.get("_id"), d -> d));
        if (saved.size() != original.size()) throw new IllegalStateException("Old snapshot document cohort changed");
        for (var before : original) {
            var after = saved.get(before.get("_id"));
            if (after == null) throw new IllegalStateException("Old snapshot saved ObjectId missing");
            for (String key : before.keySet()) {
                if (generated.contains(key)) continue;
                Object a = before.get(key), b = after.get(key);
                if (key.equals("fetters")) {
                    var first = new ArrayList<>(before.getList(key, Integer.class));
                    var last = new ArrayList<>(after.getList(key, Integer.class));
                    Collections.sort(first); Collections.sort(last); a = first; b = last;
                }
                if (!Objects.equals(a,b)) throw new IllegalStateException("Old snapshot saved field differs: " + key);
            }
        }
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    record Snapshot(JsonObject dependencies, JsonObject activeReferences, JsonObject weaponReferences) {}

    private static Snapshot admit(Bundle bundle) throws Exception {
        var dep = fixedJson(bundle, "selected-dependencies.private.json");
        var refs = fixedJson(bundle, "active-item-saved-refs/result.private.json");
        var weaponRefs = fixedJson(bundle, "weapon-saved-affix-refinement.private.json");
        var missing = new ArrayList<String>();
        for (var saved : bson(fixedInput(bundle, "selected-0/avatars-0.bson"))) {
            int id = saved.getInteger("avatarId");
            var data = GameData.getAvatarDataMap().get(id);
            if (data == null) continue;
            var expectedFetters = new ArrayList<>(saved.getList("fetters", Integer.class));
            var actualFetters = data.getFetters() == null ? new ArrayList<Integer>() : new ArrayList<>(data.getFetters());
            Collections.sort(expectedFetters);
            Collections.sort(actualFetters);
            if (!expectedFetters.equals(actualFetters)) missing.add("derived saved fetters " + id);
            for (int fetter : expectedFetters)
                if (!GameData.getMapByResourceDef(FetterData.class).containsKey(fetter)) missing.add("fetter resource " + fetter);
            int rewardId = saved.getInteger("nameCardRewardId", 0);
            int cardId = saved.getInteger("nameCardId", 0);
            if (data.getNameCardRewardId() != rewardId || data.getNameCardId() != cardId) missing.add("derived saved namecard " + id);
            if (rewardId != 0) {
                var card = GameData.getFetterCharacterCardDataMap().get(id);
                var reward = GameData.getRewardDataMap().get(rewardId);
                if (card == null || card.getRewardId() != rewardId || reward == null
                        || reward.getRewardItemList().isEmpty() || reward.getRewardItemList().get(0).getId() != cardId)
                    missing.add("namecard resource " + id);
            }
        }
        int active = 0, equipped = 0;
        var avatars = new HashSet<Integer>();
        for (var entry : dep.getAsJsonArray("avatars")) {
            var saved = entry.getAsJsonObject().getAsJsonObject("fields");
            int id = saved.get("avatarId").getAsInt();
            avatars.add(id);
            var data = GameData.getAvatarDataMap().get(id);
            if (data == null) { missing.add("avatar " + id); continue; }
            int level = saved.get("level").getAsInt();
            int phase = saved.get("promoteLevel").getAsInt();
            if (!GameData.getAvatarCurveDataMap().containsKey(level)
                    || GameData.getAvatarPromoteData(data.getAvatarPromoteId(), phase) == null)
                missing.add("avatar level/phase " + id);
            if (data.getAbilities().isEmpty()) missing.add("base ability embryo " + id);
            int depotId = saved.get("skillDepotId").getAsInt();
            var depot = GameData.getAvatarSkillDepotDataMap().get(depotId);
            if (depot == null) { missing.add("depot " + depotId); continue; }
            depot.getSkillsAndEnergySkill().forEach(skill -> {
                if (!GameData.getAvatarSkillDataMap().containsKey(skill)) missing.add("depot skill " + skill);
            });
            if (depot.getSubSkills() != null) for (int skill : depot.getSubSkills())
                if (skill > 0 && !GameData.getAvatarSkillDataMap().containsKey(skill)) missing.add("subskill " + skill);
            String group = depot.getSkillDepotAbilityGroup();
            if (group != null && !group.isEmpty() && (depot.getAbilities() == null || !GameDepot.getPlayerAbilities().containsKey(group)))
                missing.add("ability group " + depotId);
            for (var skill : saved.getAsJsonObject("skillLevelMap").entrySet()) {
                int skillId = Integer.parseInt(skill.getKey());
                var row = GameData.getAvatarSkillDataMap().get(skillId);
                if (row == null) { missing.add("saved skill " + skillId); continue; }
                if (row.getProudSkillGroupId() > 0) {
                    int proud = row.getProudSkillGroupId() * 100 + skill.getValue().getAsInt();
                    if (!GameData.getProudSkillDataMap().containsKey(proud)) missing.add("derived proud " + proud);
                }
            }
            for (var talent : saved.getAsJsonArray("talentIdList"))
                if (talent.getAsInt() > 0 && !GameData.getAvatarTalentDataMap().containsKey(talent.getAsInt())) missing.add("saved talent " + talent);
            for (var proud : saved.getAsJsonArray("proudSkillList"))
                if (proud.getAsInt() > 0 && !GameData.getProudSkillDataMap().containsKey(proud.getAsInt())) missing.add("saved proud " + proud);
        }
        for (var entry : dep.getAsJsonArray("items")) {
            var saved = entry.getAsJsonObject().getAsJsonObject("fields");
            if (saved.get("deleted").getAsBoolean() || saved.get("count").getAsInt() <= 0) continue;
            active++;
            int id = saved.get("itemId").getAsInt();
            var data = GameData.getItemDataMap().get(id);
            if (data == null) { missing.add("active item " + id); continue; }
            var type = data.getItemType();
            if (type == null || !Set.of(ItemType.ITEM_MATERIAL, ItemType.ITEM_WEAPON, ItemType.ITEM_RELIQUARY,
                    ItemType.ITEM_FURNITURE, ItemType.ITEM_TPS_WEAPON).contains(type)) missing.add("unsupported active item type " + id + ":" + type);
            int avatarId = saved.get("equipCharacter").getAsInt();
            if (avatarId != 0) {
                equipped++;
                if (!avatars.contains(avatarId)) missing.add("equipment owner avatar " + avatarId);
            }
            int level = saved.get("level").getAsInt();
            if (type == ItemType.ITEM_WEAPON && (!GameData.getWeaponCurveDataMap().containsKey(level)
                    || GameData.getWeaponPromoteData(data.getWeaponPromoteId(), saved.get("promoteLevel").getAsInt()) == null))
                missing.add("weapon level/phase " + id);
            if (type == ItemType.ITEM_RELIQUARY && !GameData.getMapByResourceDef(emu.grasscutter.data.excels.reliquary.ReliquaryLevelData.class).containsKey((data.getRankLevel() << 8) + level))
                missing.add("relic level " + id);
        }
        for (var row : GameData.getProudSkillDataMap().values())
            if (row.getOpenConfig() != null && !row.getOpenConfig().isBlank()
                    && !GameData.getOpenConfigEntries().containsKey(row.getOpenConfig())) missing.add("proud openConfig " + row.getOpenConfig());
        for (var row : GameData.getAvatarTalentDataMap().values())
            if (row.getOpenConfig() != null && !row.getOpenConfig().isBlank()
                    && !GameData.getOpenConfigEntries().containsKey(row.getOpenConfig())) missing.add("talent openConfig " + row.getOpenConfig());
        for (var row : GameData.getEquipAffixDataMap().values())
            if (row.getOpenConfig() != null && !row.getOpenConfig().isBlank()
                    && !GameData.getOpenConfigEntries().containsKey(row.getOpenConfig())) missing.add("equipment openConfig " + row.getOpenConfig());
        for (var entry : weaponRefs.getAsJsonArray("items")) {
            var fields = entry.getAsJsonObject().getAsJsonObject("fields");
            int refinement = fields.getAsJsonObject("refinement").get("value").getAsInt();
            for (var affix : fields.getAsJsonObject("affixes").getAsJsonArray("value"))
                if (affix.getAsInt() > 0 && !GameData.getEquipAffixDataMap().containsKey(affix.getAsInt() * 10 + refinement)) missing.add("saved weapon affix " + affix);
        }
        var positive = refs.getAsJsonObject("positiveReferences");
        for (var id : positive.getAsJsonObject("ReliquaryCandidateItems.mainPropId").getAsJsonArray("uniquePositiveIds"))
            if (!GameData.getReliquaryMainPropDataMap().containsKey(id.getAsInt())) missing.add("relic main " + id);
        for (var id : positive.getAsJsonObject("ReliquaryCandidateItems.appendPropIdList").getAsJsonArray("uniquePositiveIds"))
            if (!GameData.getReliquaryAffixDataMap().containsKey(id.getAsInt())) missing.add("relic append " + id);
        for (var entry : refs.getAsJsonArray("items")) {
            var row = entry.getAsJsonObject();
            if (!row.get("candidateCohort").getAsString().equals("TPSCandidateItems")) continue;
            var fields = row.getAsJsonObject("fields");
            int weaponId = fields.getAsJsonObject("itemId").get("value").getAsInt();
            for (var name : List.of("tpsAccessoryIds", "tpsMaterialAccessoryIds")) {
                var field = fields.getAsJsonObject(name);
                if (!field.get("state").getAsString().equals("value")) continue;
                for (var id : field.getAsJsonArray("value")) {
                    int accessoryId = id.getAsJsonObject().get("value").getAsInt();
                    var data = GameData.getTpsWeaponAccessoryDataMap().get(accessoryId);
                    if (data == null || data.getTpsWeaponId() != weaponId) missing.add("TPS accessory owner " + accessoryId);
                }
            }
        }
        if (avatars.size() != 121 || active != 10727 || equipped != 471) missing.add("fixed snapshot cohort changed");
        if (!missing.isEmpty()) throw new IllegalStateException("Old Avatar/Inventory resource admission failed (" + missing.size() + "): " + missing.stream().limit(25).toList());
        return new Snapshot(dep, refs, weaponRefs);
    }

    static JsonObject fixedJson(Bundle bundle, String suffix) throws Exception {
        return JsonUtils.decode(new String(fixedInput(bundle, suffix), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
    }

    static byte[] fixedInput(Bundle bundle, String suffix) throws Exception {
        var matches = bundle.manifest().getAsJsonObject("inputs").entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Fixed input is not unique: " + suffix);
        var input = matches.get(0);
        byte[] data = Files.readAllBytes(bundle.inputRoot().resolve(input.getKey()));
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        if (!input.getValue().getAsString().equals(actual)) throw new IllegalArgumentException("Fixed input SHA mismatch: " + suffix);
        return data;
    }

    static List<Document> bson(byte[] bytes) {
        var documents = new ArrayList<Document>();
        for (int offset = 0; offset < bytes.length;) {
            int size = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            documents.add(new RawBsonDocument(Arrays.copyOfRange(bytes, offset, offset + size)).decode(new DocumentCodec()));
            offset += size;
        }
        return documents;
    }
}

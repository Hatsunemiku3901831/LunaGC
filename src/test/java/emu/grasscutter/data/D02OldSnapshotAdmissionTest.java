package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.data.excels.FetterData;
import emu.grasscutter.utils.JsonUtils;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bson.*;
import org.bson.codecs.DocumentCodec;

/** An actual GameData gate for this snapshot's Avatar/Inventory restoration, never global ready. */
class D02OldSnapshotAdmissionTest {
    @TempDir Path temporary;

    @Test
    void completeReferencesPassAndMissingWeaponStopsBeforeAnyHydration() throws Exception {
        var bundle = D02OldSnapshotResourceLoadTest.loadFixedResources(temporary);
        var snapshot = admit(bundle);
        assertEquals(121, snapshot.dependencies().getAsJsonArray("avatars").size());
        assertEquals(12254, snapshot.dependencies().getAsJsonArray("items").size());
        var weapon = GameData.getItemDataMap().remove(11101);
        assertNotNull(weapon);
        try {
            assertThrows(IllegalStateException.class, () -> admit(bundle));
        } finally {
            GameData.getItemDataMap().put(11101, weapon);
        }
        var reward = GameData.getRewardDataMap().values().iterator().next();
        GameData.getRewardDataMap().remove(reward.getId());
        try {
            assertThrows(IllegalStateException.class, () -> admit(bundle));
        } finally { GameData.getRewardDataMap().put(reward.getId(), reward); }
        assertFalse(ResourceLoader.isLoadedAll());
        System.out.println("OLD_SNAPSHOT_ADMISSION completeReferences=true missingWeaponRejected=true playerHydrated=false fullReady=false");
    }

    record Snapshot(JsonObject dependencies, JsonObject activeReferences, JsonObject weaponReferences) {}

    static Snapshot admit(D02OldSnapshotResourceLoadTest.Bundle bundle) throws Exception {
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

    static JsonObject fixedJson(D02OldSnapshotResourceLoadTest.Bundle bundle, String suffix) throws Exception {
        return JsonUtils.decode(new String(fixedInput(bundle, suffix), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
    }

    static byte[] fixedInput(D02OldSnapshotResourceLoadTest.Bundle bundle, String suffix) throws Exception {
        var matches = bundle.manifest().getAsJsonObject("inputs").entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Fixed input is not unique: " + suffix);
        var input = matches.get(0);
        byte[] data = Files.readAllBytes(Path.of(input.getKey()));
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

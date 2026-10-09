package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.TeamResonances;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import java.util.*;

/**
 * Resolves talents from the caster's skills, equipment and selected party.
 */
public final class AbilityTalentResolver {
    private AbilityTalentResolver() {}

    private record Talent(String name, float[] parameters) {}

    private static List<Talent> activeTalents(Avatar avatar) {
        var result = new ArrayList<Talent>();
        if (avatar == null) return result;
        for (var resonance : TeamResonances.active(avatar))
            result.add(new Talent(resonance.getOpenConfig(), resonance.getParamList()));
        if (avatar.getTalentIdList() != null)
            for (int id : avatar.getTalentIdList()) {
                var data = GameData.getAvatarTalentDataMap().get(id);
                if (data != null) result.add(new Talent(data.getOpenConfig(), data.getParamList()));
            }
        var depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (depot != null) {
            var levels = avatar.getEffectiveSkillLevelMap();
            depot
                    .getSkillsAndEnergySkill()
                    .forEach(
                            id -> {
                                var skill = GameData.getAvatarSkillDataMap().get(id);
                                if (skill == null || skill.getProudSkillGroupId() <= 0) return;
                                int level = levels.getOrDefault(id, 1);
                                addProudSkill(result, skill.getProudSkillGroupId() * 100 + level);
                            });
        }
        if (avatar.getProudSkillList() != null)
            for (int id : avatar.getProudSkillList()) addProudSkill(result, id);

        var weapon = avatar.getWeapon();
        if (weapon != null && weapon.getCount() > 0 && weapon.getAffixes() != null)
            for (int id : weapon.getAffixes()) addAffix(result, id * 10 + weapon.getRefinement());
        for (var item : TpsWeaponSystem.getWornWeapons(avatar))
            for (var affix : TpsWeaponSystem.getAffixLevels(item).int2IntEntrySet())
                addAffix(result, affix.getIntKey() * 10 + affix.getIntValue());

        var sets = new TreeMap<Integer, Integer>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<GameItem, Boolean>());
        for (var item : avatar.getEquips().values()) {
            if (item == null
                    || !seen.add(item)
                    || item.getCount() <= 0
                    || item.getItemData() == null
                    || item.getItemType() != ItemType.ITEM_RELIQUARY) continue;
            int set = item.getItemData().getSetId();
            if (set > 0) sets.merge(set, 1, Integer::sum);
        }
        sets.forEach(
                (id, count) -> {
                    var set = GameData.getReliquarySetDataMap().get((int) id);
                    if (set == null || set.getSetNeedNum() == null) return;
                    for (int i = 0; i < set.getSetNeedNum().length; i++)
                        if (set.getSetNeedNum()[i] > 0 && count >= set.getSetNeedNum()[i])
                            addAffix(result, set.getEquipAffixId() * 10 + i);
                });
        return result;
    }

    private static void addProudSkill(List<Talent> talents, int id) {
        var data = GameData.getProudSkillDataMap().get(id);
        if (data != null) talents.add(new Talent(data.getOpenConfig(), data.getParamList()));
    }

    private static void addAffix(List<Talent> talents, int id) {
        var data = GameData.getEquipAffixDataMap().get(id);
        if (data != null) talents.add(new Talent(data.getOpenConfig(), data.getParamList()));
    }

    public static Map<String, Float> overrides(Avatar avatar, String abilityName) {
        var values = new LinkedHashMap<String, Float>();
        if (abilityName == null || abilityName.isEmpty()) return values;
        for (var talent : activeTalents(avatar)) {
            var config = GameData.getOpenConfigEntries().get(talent.name());
            if (config == null || config.getAbilityVarSetters() == null) continue;
            for (var setter : config.getAbilityVarSetters()) {
                if (setter == null || !abilityName.equals(setter.getAbilityName())) continue;
                Float value = setter.resolve(talent.parameters());
                if (value != null) values.put(setter.getVarName(), value);
            }
        }
        return values;
    }

    public static boolean unlocked(Avatar avatar, String abilityName, String flag) {
        if (abilityName == null || flag == null || flag.isEmpty()) return false;
        for (var talent : activeTalents(avatar)) {
            var config = GameData.getOpenConfigEntries().get(talent.name());
            if (config == null || config.getTalentParamEntries() == null) continue;
            for (var param : config.getTalentParamEntries())
                if (param != null
                        && abilityName.equals(param.getAbilityName())
                        && flag.equals(param.getParamName())) return true;
        }
        return false;
    }

    /** Follow explicit ownership before character-name heuristics; cycles have no caster. */
    public static EntityAvatar caster(Player player, GameEntity owner) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<GameEntity, Boolean>());
        for (int depth = 0; owner != null && depth < 32 && seen.add(owner); depth++) {
            if (owner instanceof EntityAvatar entity) return entity;
            if (owner instanceof EntityClientGadget gadget) {
                int id = gadget.getOriginalOwnerEntityId();
                if (id == 0) id = gadget.getOwnerEntityId();
                owner = gadget.getScene().getEntityById(id);
            } else if (owner instanceof EntityWeapon weapon) {
                Avatar avatar = weapon.getCasterAvatar();
                if (avatar != null && avatar.getPlayer() != null) player = avatar.getPlayer();
                if (player == null) return null;
                // Older weapon entities may have been created before the item was bound.
                for (var entity : player.getTeamManager().getActiveTeam()) {
                    var candidate = entity.getAvatar();
                    if (candidate == avatar
                            || (candidate.getWeapon() != null
                                    && candidate.getWeapon().getWeaponEntity() == weapon)
                            || TpsWeaponSystem.getWornWeapons(candidate).stream()
                                    .anyMatch(item -> item.getWeaponEntity() == weapon)) return entity;
                }
                if (avatar != null)
                    for (var entity : weapon.getScene().getEntities().values())
                        if (entity instanceof EntityAvatar candidate && candidate.getAvatar() == avatar)
                            return candidate;
                return null;
            } else return null;
        }
        return null;
    }

    public static Avatar casterAvatar(Player player, GameEntity owner) {
        if (owner instanceof EntityWeapon weapon && weapon.getCasterAvatar() != null)
            return weapon.getCasterAvatar();
        var entity = caster(player, owner);
        return entity == null ? null : entity.getAvatar();
    }
}

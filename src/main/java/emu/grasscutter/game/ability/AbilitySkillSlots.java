package emu.grasscutter.game.ability;

import com.google.gson.JsonElement;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Read the live skill-slot selection without changing charges or client input flags. */
final class AbilitySkillSlots {
    private static final AtomicLong sourceSequence = new AtomicLong();
    // Values are the current client's NCNELJMLNJP constants, not Java enum ordinals.
    private static final Map<String, Integer> PRIORITIES =
            Map.ofEntries(
                    Map.entry("Base", 0),
                    Map.entry("AvatarCommon", 4),
                    Map.entry("AvatarSpecial", 5),
                    Map.entry("AvatarCustomSkillBase", 10),
                    Map.entry("AvatarCustomSkillCommon", 14),
                    Map.entry("Playmode", 101),
                    Map.entry("Quest", 102),
                    Map.entry("PlaymodeExtra1", 1001),
                    Map.entry("PlaymodeExtra2", 1002),
                    Map.entry("PlaymodeExtra3", 1003),
                    Map.entry("PlaymodeExtra4", 1004));

    private record Source(Ability ability, AbilityMixinData[] mixins, long order) {}

    private AbilitySkillSlots() {}

    static long nextSourceOrder() {
        return sourceSequence.incrementAndGet();
    }

    static int selectedSkill(EntityAvatar entity, long slot) {
        int chosen = defaultSkill(entity.getAvatar(), slot);
        if (slot == 0
                || slot > 6
                || !entity.isAbilityLifecycleCurrent(entity.getAbilityLifecycleVersion()))
            return chosen;
        int priority = 0;
        long order = 0;
        var sources = new ArrayList<Source>();
        synchronized (entity.getInstancedAbilities()) {
            for (var ability : entity.getInstancedAbilities())
                if (ability != null)
                    sources.add(
                            new Source(
                                    ability,
                                    ability.getData().abilityMixins,
                                    ability.getSkillSlotSourceOrder()));
        }
        synchronized (entity.getInstancedModifiers()) {
            for (var controller : entity.getAbilityHpModifier().activeControllers())
                sources.add(
                        new Source(
                                controller.getAbility(),
                                controller.getModifierData().modifierMixins,
                                controller.getSkillSlotSourceOrder()));
        }
        sources.sort(Comparator.comparingLong(Source::order));
        for (var source : sources) {
            if (source.mixins == null
                    || source.ability.getPlayerOwner() != entity.getPlayer()
                    || !AbilityGlobalValues.isCurrent(source.ability, entity)) continue;
            for (var mixin : source.mixins) {
                if (mixin == null || mixin.type != AbilityMixinData.Type.SwitchSkillIDMixin)
                    continue;
                var index =
                        integer(
                                mixin.skillIndex == null
                                        ? mixin.legacySkillIndex
                                        : mixin.skillIndex);
                var id = integer(mixin.skillID);
                var rank = priority(mixin.priority);
                if (index == null
                        || index < 0
                        || index >= 6
                        || index + 1 != slot
                        || id == null
                        || id <= 0
                        || rank == null
                        || !GameData.getAvatarSkillDataMap().containsKey(id)
                        || !AbilitySkillCooldown.ownsSkill(entity.getAvatar(), id)
                        || !AbilityActionSequence.supportedPredicates(mixin.predicates, 0)
                        || !PredicateEvaluator.all(
                                mixin.predicates,
                                source.ability,
                                source.ability.getOwner(),
                                entity,
                                null)) continue;
                if (rank > priority || rank == priority && source.order >= order) {
                    chosen = id;
                    priority = rank;
                    order = source.order;
                }
            }
        }
        return chosen;
    }

    private static Integer integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            return null;
        double number = value.getAsDouble();
        return Double.isFinite(number)
                        && number >= Integer.MIN_VALUE
                        && number <= Integer.MAX_VALUE
                        && number == Math.rint(number)
                ? (int) number
                : null;
    }

    private static Integer priority(JsonElement value) {
        if (value == null) return 4;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
            return PRIORITIES.get(value.getAsString());
        var number = integer(value);
        return number != null && PRIORITIES.containsValue(number) ? number : null;
    }

    private static int defaultSkill(Avatar avatar, long slot) {
        var depot = avatar.getSkillDepot();
        if (depot == null || slot == 0 || slot > 6) return 0;
        if (slot == 5) return depot.getEnergySkill();
        if (slot == 6) return depot.getAttackModeSkill();
        var regular = depot.getSkills();
        if (regular == null || slot > regular.size()) return 0;
        var skill = regular.get((int) slot - 1);
        return skill == null ? 0 : skill;
    }
}

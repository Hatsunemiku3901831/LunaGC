package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AvatarSkillInfoOuterClass.AvatarSkillInfo;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;

import java.util.Arrays;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Resource-backed Qiqi revival control; unknown skill-control profiles remain dormant. */
public final class AbilityRevivalSkillControl {
    private final Map<Ability, NativeUse> nativeUses = new WeakHashMap<>();

    public AbilityRevivalSkillControl() {}

    private static final class NativeUse {
        final Ability burst;
        final AbilityModifierAction action;
        final AbilityExecutionContext context;
        final GameSession session;
        boolean started, requested;

        NativeUse(
                Ability source, Ability burst, AbilityModifierAction action, EntityAvatar caster) {
            this.burst = burst;
            this.action = action;
            context = new AbilityExecutionContext(source, caster);
            session = source.getPlayerOwner().getSession();
        }

        boolean current() {
            return context.isCurrent() && burst.getPlayerOwner().getSession() == session;
        }
    }

    public static boolean nativeAbility(Ability ability) {
        return ability != null && "Avatar_Qiqi_Revive".equals(ability.getData().abilityName);
    }

    /** Pair the secondary ability's start and revival, including reordered native messages. */
    public boolean nativeAction(
            Ability source, AbilityModifierAction received, GameEntity target, boolean start) {
        if (!nativeAbility(source)
                || received == null
                || source.getPlayerOwner() == null
                || target == null
                || !AbilityGlobalValues.isCurrent(source, target)) return false;
        var caster = source.getCasterEntity();
        var burstData = GameData.getAbilityData("Avatar_Qiqi_ElementalBurst");
        var ownActions = source.getData().onAbilityStart;
        if (caster == null
                || caster.getPlayer() != source.getPlayerOwner()
                || burstData == null
                || burstData.onAbilityStart == null
                || ownActions == null
                || Arrays.stream(ownActions).noneMatch(action -> action == received)) return false;
        var original =
                Arrays.stream(burstData.onAbilityStart)
                        .filter(AbilityRevivalSkillControl::controlled)
                        .findFirst()
                        .orElse(null);
        if (original == null
                || !supported(original)
                || start
                        && (received.type != AbilityModifierAction.Type.AvatarSkillStart
                                || received.skillID != original.skillID)
                || !start && received.type != AbilityModifierAction.Type.ReviveAvatar) return false;
        synchronized (caster.getAvatar()) {
            NativeUse use;
            synchronized (nativeUses) {
                nativeUses.values().removeIf(value -> !value.current());
                use = nativeUses.get(source);
                var burst =
                        use == null
                                ? new Ability(burstData, caster, source.getPlayerOwner())
                                : use.burst;
                @SuppressWarnings("unchecked")
                var predicates =
                        (java.util.List<Map<String, Object>>)
                                (java.util.List<?>) original.predicates;
                if (!living(caster)
                        || !AbilityGlobalValues.isCurrent(source, caster)
                        || !AbilityAvatarRevival.eligible(burst, original, caster)
                        || !AbilitySkillCooldown.ownsSkill(caster.getAvatar(), original.skillID)
                        || !PredicateEvaluator.all(
                                predicates, burst, burst.getOwner(), caster, original)) {
                    nativeUses.remove(source);
                    return true;
                }
                if (use == null) {
                    use = new NativeUse(source, burst, original, caster);
                    nativeUses.put(source, use);
                }
                if (start) use.started = true;
                else use.requested = true;
                if (!use.started || !use.requested) return true;
                nativeUses.remove(source);
            }
            if (use.current())
                use.burst
                        .getManager()
                        .executeActionNow(
                                use.burst,
                                use.action,
                                com.google.protobuf.ByteString.EMPTY,
                                caster);
            return true;
        }
    }

    public static boolean controlled(AbilityModifierAction action) {
        return action != null
                && action.type == AbilityModifierAction.Type.ReviveDeadAvatar
                && action.rawReviveSkillControl != null
                && action.rawReviveSkillControl.isJsonPrimitive()
                && action.rawReviveSkillControl.getAsJsonPrimitive().isBoolean()
                && action.rawReviveSkillControl.getAsBoolean();
    }

    public static boolean supported(AbilityModifierAction action) {
        if (action == null) return false;
        var raw = action.rawReviveSkillControl;
        if (raw != null
                && !raw.isJsonNull()
                && (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean())) return false;
        if (raw != null && raw.isJsonPrimitive() && raw.getAsBoolean() && !controlled(action))
            return false;
        if (!controlled(action))
            return action.type != AbilityModifierAction.Type.ReviveDeadAvatar
                    || action.skillID == 0;
        var data = GameData.getAvatarSkillDataMap().get(action.skillID);
        return action.skillID > 0
                && data != null
                && "Avatar_Qiqi_Revive".equals(data.getAbilityName())
                && data.getShareCDID() == 0
                && data.getMaxChargeNum() == 1
                && data.getCostElemVal() == 0
                && data.getCostStamina() == 0
                && Float.isFinite(data.getCdTime())
                && data.getCdTime() > 0;
    }

    public static EntityAvatar predicateCaster(Ability ability, AbilityModifierAction action) {
        return controlled(action) && ability != null ? ability.getCasterEntity() : null;
    }

    /** Serialize a single caster's whole-party revival before recording one skill recovery. */
    public static boolean run(
            Ability ability, AbilityModifierAction action, BooleanSupplier revive) {
        var caster = ability == null ? null : ability.getCasterEntity();
        var player = ability == null ? null : ability.getPlayerOwner();
        if (!supported(action)
                || caster == null
                || player == null
                || caster.getPlayer() != player
                || !"Avatar_Qiqi_ElementalBurst".equals(ability.getData().abilityName)
                || !AbilitySkillCooldown.ownsSkill(caster.getAvatar(), action.skillID))
            return false;
        var data = GameData.getAvatarSkillDataMap().get(action.skillID);
        var ratioValue = action.cdTimeRatio != null ? action.cdTimeRatio : action.legacyCdRatio;
        var ratio =
                ratioValue == null
                        ? OptionalDouble.of(1)
                        : ratioValue.tryGet(AbilityActionHandler.propertiesFor(ability, caster));
        var duration =
                data.isIgnoreCDMinusRatio()
                        ? OptionalDouble.of(data.getCdTime())
                        : AbilityTalentResolver.cooldownSeconds(
                                caster.getAvatar(), action.skillID, data.getCdTime());
        if (ratio.isEmpty() || duration.isEmpty()) return false;
        double seconds = ratio.getAsDouble() * duration.getAsDouble();
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds * 1000 > 0xffffffffL) return false;
        var cooldown = player.getAbilityManager().getSkillCooldown();
        synchronized (caster.getAvatar()) {
            if (!living(caster) || !AbilityAvatarRevival.eligible(ability, action, caster))
                return true;
            int count =
                    caster.getAvatar()
                            .getSkillInfoCharges()
                            .getOrDefault(action.skillID, data.getMaxChargeNum());
            if (count != 1) return false;
            AvatarSkillInfo state = cooldown.state(caster.getAvatar(), action.skillID, count);
            if (state == null) return false;
            if (state.getFullCdTimeListCount() != 0) return true;
            // Check caster conditions before any recipient changes; all recipients share this use.
            @SuppressWarnings("unchecked")
            var predicates =
                    (java.util.List<Map<String, Object>>) (java.util.List<?>) action.predicates;
            if (!PredicateEvaluator.all(predicates, ability, ability.getOwner(), caster, action))
                return true;
            if (!revive.getAsBoolean()) return true;
            if (!cooldown.start(caster.getAvatar(), action.skillID, seconds)) return false;
            var updated = cooldown.state(caster.getAvatar(), action.skillID, count);
            if (updated != null)
                player.sendPacket(
                        new PacketAvatarSkillInfoNotify(
                                caster.getAvatar().getGuid(), Map.of(action.skillID, updated)));
            return true;
        }
    }

    private static boolean living(EntityAvatar caster) {
        float hp = caster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        return caster.isAlive() && Float.isFinite(hp) && hp > 0;
    }
}

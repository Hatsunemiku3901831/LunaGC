package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.ElementType;

import java.util.*;

/** Resource-timed team auras that periodically attach non-elemental reaction listeners. */
public final class AbilityPartyAura {
    private AbilityPartyAura() {}

    private static boolean empty(AbilityModifier.AbilityModifierAction[]... arrays) {
        return Arrays.stream(arrays).allMatch(a -> a == null || a.length == 0);
    }

    static boolean listener(AbilityModifier data) {
        return data != null
                && data.isUnique
                && "RefreshUniqueDurability".equals(data.stacking)
                && data.duration != null
                && (data.duration.isDynamic()
                        || Float.isFinite(data.duration.getConstant())
                                && data.duration.getConstant() > 0)
                && (data.stackingOption == null || data.stackingOption.isEmpty())
                && data.properties == null
                && (data.elementType == null || data.elementType == ElementType.None)
                && data.state == null
                && empty(
                        data.onAdded,
                        data.onRemoved,
                        data.onThinkInterval,
                        data.onBeingHit,
                        data.onAttackLanded,
                        data.onHittingOther,
                        data.onKill,
                        data.onCrash,
                        data.onAvatarIn,
                        data.onAvatarOut,
                        data.onReconnect,
                        data.onChangeAuthority,
                        data.onVehicleIn,
                        data.onVehicleOut,
                        data.onZoneEnter,
                        data.onZoneExit,
                        data.onHeal,
                        data.onBeingHealed)
                && data.modifierMixins != null
                && data.modifierMixins.length > 0
                && Arrays.stream(data.modifierMixins)
                        .allMatch(
                                m ->
                                        m != null
                                                && (m.type
                                                                == AbilityMixinData.Type
                                                                        .DoActionByElementReactionMixin
                                                        || m.type
                                                                == AbilityMixinData.Type
                                                                        .DoActionBySelfElementReactionMixin));
    }

    static boolean window(Ability ability, AbilityModifier data, GameEntity target) {
        if (ability == null
                || !(target instanceof EntityTeam team)
                || team.getPlayer() == null
                || team.getPlayer() != ability.getPlayerOwner()
                || team.getPlayer().getTeamManager().getEntity() != team
                || !windowShape(ability.getData(), data)) return false;
        var properties = AbilityActionHandler.propertiesFor(ability, target);
        float duration = data.duration.get(properties, 0),
                interval = data.thinkInterval.get(properties, 0);
        return Float.isFinite(duration)
                && duration > 0
                && Float.isFinite(interval)
                && interval > 0
                && interval <= duration;
    }

    static boolean windowShape(AbilityData owner, AbilityModifier data) {
        if (owner == null
                || data == null
                || data.duration == null
                || data.thinkInterval == null
                || !data.isUnique
                || !"RefreshUniqueDurability".equals(data.stacking)
                || data.stackingOption != null && !data.stackingOption.isEmpty()
                || data.state != null
                || data.elementType != null && data.elementType != ElementType.None
                || data.properties != null
                || data.modifierMixins != null && data.modifierMixins.length > 0
                || !empty(
                        data.onRemoved,
                        data.onBeingHit,
                        data.onAttackLanded,
                        data.onHittingOther,
                        data.onKill,
                        data.onCrash,
                        data.onAvatarIn,
                        data.onAvatarOut,
                        data.onReconnect,
                        data.onChangeAuthority,
                        data.onVehicleIn,
                        data.onVehicleOut,
                        data.onZoneEnter,
                        data.onZoneExit,
                        data.onHeal,
                        data.onBeingHealed)
                || data.onAdded == null
                || data.onAdded.length != 1
                || data.onThinkInterval == null
                || data.onThinkInterval.length != 1) return false;
        var added = data.onAdded[0];
        var repeated = data.onThinkInterval[0];
        if (added == null
                || repeated == null
                || added.type != AbilityModifier.AbilityModifierAction.Type.AttachModifier
                || repeated.type != added.type
                || !"AllPlayerAvatars".equals(added.target)
                || !Objects.equals(added.target, repeated.target)
                || !Objects.equals(added.modifierName, repeated.modifierName)
                || !Objects.equals(added.predicates, repeated.predicates)
                || !AbilityActionSequence.supportedPredicates(added.predicates, 0)
                || added.otherTargets != null
                || repeated.otherTargets != null
                || !listener(owner.modifiers.get(added.modifierName))) return false;
        return true;
    }

    public static boolean attach(Ability ability, AbilityModifier data, GameEntity target) {
        if (!listener(data) && !window(ability, data, target)) return false;
        AbilityModifierStacking.apply(ability, data, target, () -> true);
        return true;
    }

    static List<GameEntity> targets(Ability ability) {
        var scope = AbilityCooldownInvocation.capture();
        if (scope == null || scope.controller() == null || !scope.controller().ownsAuraDuration())
            return null;
        if (!scope.controller().auraCurrent()) return List.of();
        var player = ability.getPlayerOwner();
        if (player == null
                || player.getScene() == null
                || player.getWorld() != player.getScene().getWorld()) return List.of();
        var scene = player.getScene();
        var result = new LinkedHashSet<GameEntity>();
        for (var member : scene.getPlayers()) {
            if (member.getScene() != scene
                    || member.getWorld() != player.getWorld()
                    || player.getWorld().getPlayers().stream().noneMatch(p -> p == member))
                continue;
            for (var avatar : member.getTeamManager().getActiveTeamSnapshot())
                if (avatar.getScene() == scene && avatar.isInSceneParty() && avatar.isAlive())
                    result.add(avatar);
        }
        return new ArrayList<>(result);
    }

    static void linkCurrent(GameEntity target, AbilityModifierController child) {
        var scope = AbilityCooldownInvocation.capture();
        if (scope != null && scope.controller() != null && scope.controller().ownsAuraDuration())
            scope.controller().attachChild(target, child);
    }

    public static void clearTeam(EntityTeam team) {
        List<AbilityModifierController> windows;
        synchronized (team.getInstancedModifiers()) {
            windows =
                    team.getAbilityHpModifier().activeControllers().stream()
                            .filter(AbilityModifierController::ownsAuraDuration)
                            .toList();
        }
        for (var controller : windows)
            controller.getAbility().removeModifierInstance(team, controller, true);
    }
}

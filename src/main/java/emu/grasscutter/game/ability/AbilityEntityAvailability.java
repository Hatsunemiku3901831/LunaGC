package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;

import java.util.Map;
import java.util.Set;

/** CBHEIAINBAI tests unavailability; its true flag permits otherwise valid off-stage entities. */
public final class AbilityEntityAvailability {
    private static final Set<String> FIELDS =
            Set.of("$type", "target", "PKFBOEPNANB", "forceByCaster");
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Owner",
                    "Caster",
                    "CasterOriginOwner",
                    "OriginOwner",
                    "CurLocalAvatar");

    private AbilityEntityAvailability() {}

    public static boolean supported(Map<?, ?> predicate) {
        return predicate != null
                && "CBHEIAINBAI".equals(predicate.get("$type"))
                && FIELDS.containsAll(predicate.keySet())
                && (!predicate.containsKey("target")
                        || predicate.get("target") instanceof String target
                                && TARGETS.contains(target))
                && (!predicate.containsKey("PKFBOEPNANB")
                        || predicate.get("PKFBOEPNANB") instanceof Boolean)
                && (!predicate.containsKey("forceByCaster")
                        || predicate.get("forceByCaster") instanceof Boolean);
    }

    public static boolean evaluate(
            Map<String, Object> predicate, Ability ability, GameEntity owner, GameEntity target) {
        if (!supported(predicate)) return false;
        GameEntity selected;
        if ("CurLocalAvatar".equals(predicate.get("target"))) {
            var player = ability == null ? null : ability.getPlayerOwner();
            var party = player == null ? null : player.getTeamManager().getActiveTeamSnapshot();
            selected =
                    party == null || party.isEmpty()
                            ? null
                            : player.getTeamManager().getCurrentAvatarEntity();
        } else selected = PredicateEvaluator.resolveTarget(predicate, ability, owner, target);
        if (selected == null
                || !selected.isAlive()
                || !AbilityExecutionContext.canTrigger(
                        selected, ability == null ? null : ability.getPlayerOwner())) return true;
        if (selected instanceof EntityAvatar avatar) {
            var player = avatar.getPlayer();
            if (player == null
                    || player.getScene() != avatar.getScene()
                    || player.getTeamManager().getActiveTeamSnapshot().stream()
                            .noneMatch(member -> member == avatar)) return true;
            if (!Boolean.TRUE.equals(predicate.get("PKFBOEPNANB")))
                return player.getTeamManager().getCurrentAvatarEntity() != avatar;
        }
        return false;
    }
}

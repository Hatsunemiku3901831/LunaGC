package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.props.MonsterType;

import java.util.*;

/** The client's DEOFBICNFHF predicate compares concrete types, then applies reject. */
public final class AbilityEntityTypePredicate {
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Owner",
                    "Caster",
                    "CasterOriginOwner",
                    "OriginOwner",
                    "CurLocalAvatar",
                    "Team");
    private static final Set<String> FIELDS =
            Set.of(
                    "$type",
                    "entityTypes",
                    "target",
                    "reject",
                    "isAuthority",
                    "useEventSource",
                    "MLHNOINPLBG",
                    "NHCLCBDKANP",
                    "value",
                    "amountByCasterMaxHPRatio");

    private AbilityEntityTypePredicate() {}

    public static boolean supported(Map<?, ?> predicate) {
        if (!FIELDS.containsAll(predicate.keySet())
                || !(predicate.get("entityTypes") instanceof List<?> types)
                || types.size() > 256) return false;
        for (var type : types) {
            if (!(type instanceof String name)) return false;
            try {
                EntityType.valueOf(name);
            } catch (IllegalArgumentException unknown) {
                return false;
            }
        }
        if (predicate.containsKey("target")
                && !(predicate.get("target") instanceof String target && TARGETS.contains(target)))
            return false;
        if (predicate.containsKey("reject") && !(predicate.get("reject") instanceof Boolean))
            return false;
        var eventSource =
                predicate.containsKey("useEventSource")
                        ? predicate.get("useEventSource")
                        : predicate.get("NHCLCBDKANP");
        // This path has no event-source entity context; never substitute its ordinary recipient.
        if (eventSource != null && !Boolean.FALSE.equals(eventSource)) return false;
        var authority =
                predicate.containsKey("isAuthority")
                        ? predicate.get("isAuthority")
                        : predicate.get("MLHNOINPLBG");
        if (authority != null
                && (!(authority instanceof Number number)
                        || !Set.of(-1d, 0d, 1d).contains(number.doubleValue()))) return false;
        // Older exports also include a default scalar; its non-default meaning is not established.
        if (predicate.containsKey("value")
                && (!(predicate.get("value") instanceof Number number)
                        || number.doubleValue() != -1d)) return false;
        return true;
    }

    static boolean evaluate(Map<String, Object> predicate, Ability ability, GameEntity target) {
        if (target == null || !supported(predicate)) return false;
        var actual = typeOf(target);
        if (actual == null) return false;
        var authority =
                predicate.containsKey("isAuthority")
                        ? predicate.get("isAuthority")
                        : predicate.get("MLHNOINPLBG");
        boolean matches = ((List<?>) predicate.get("entityTypes")).contains(actual.name());
        if (matches && authority instanceof Number number && number.intValue() != -1) {
            var controller = authorityOwner(target);
            var player = ability == null ? null : ability.getPlayerOwner();
            if (controller == null || player == null) return false;
            matches = (controller == player) == (number.intValue() == 1);
        }
        return Boolean.TRUE.equals(predicate.get("reject")) != matches;
    }

    private static EntityType typeOf(GameEntity entity) {
        if (entity instanceof EntityAvatar) return EntityType.Avatar;
        if (entity instanceof EntityMonster monster
                && monster.getMonsterData() != null
                && monster.getMonsterData().getType() == MonsterType.MONSTER_ENV_ANIMAL)
            return EntityType.EnvAnimal;
        if (entity instanceof EntityVehicle) return EntityType.Vehicle;
        if (entity instanceof EntityBaseGadget gadget) {
            var data = GameData.getGadgetDataMap().get(gadget.getGadgetId());
            if (data != null) return data.getType();
            if (entity instanceof EntityWeapon) return EntityType.Equip;
            return EntityType.Gadget;
        }
        return entity.getEntityType();
    }

    /** Match the authority owner advertised by the existing avatar/gadget/monster protocols. */
    private static Player authorityOwner(GameEntity target) {
        if (target instanceof EntityAvatar avatar) return avatar.getPlayer();
        if (target instanceof EntityClientGadget gadget) return gadget.getOwner();
        if (target instanceof EntityVehicle vehicle) return vehicle.getOwner();
        if ((target instanceof EntityGadget || target instanceof EntityMonster)
                && target.getScene() != null) return target.getScene().getHost();
        return null;
    }
}

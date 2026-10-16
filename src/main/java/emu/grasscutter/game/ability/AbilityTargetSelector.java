package emu.grasscutter.game.ability;

import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Position;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Resolves an ability action's {@code otherTargets} block into the entities it points at.
 *
 * <p>Only {@code SelectTargetsByShape} is understood, which is what avatar summons use to look for
 * something to shoot at. Anything else returns null, meaning "no selector" - the caller then keeps
 * whatever target it already had.
 */
public final class AbilityTargetSelector {
    /** Shape names encode their size, e.g. CircleR20H10 is a radius of 20 and a height of 10. */
    private static final Pattern RADIUS = Pattern.compile("R(\\d+(?:\\.\\d+)?)");

    private static final Pattern HEIGHT = Pattern.compile("H(\\d+(?:\\.\\d+)?)");

    private AbilityTargetSelector() {}

    public static List<GameEntity> select(
            Map<String, Object> otherTargets, Ability ability, GameEntity self) {
        if (otherTargets == null || self == null) return null;
        if (!"SelectTargetsByShape".equals(otherTargets.get("$type"))) return null;

        var scene = self.getScene();
        if (scene == null) return null;

        var wanted = entityTypes(otherTargets);
        var shape = String.valueOf(otherTargets.getOrDefault("shapeName", ""));
        float radius = dimension(RADIUS, shape);
        float height = dimension(HEIGHT, shape);
        var camp = String.valueOf(otherTargets.getOrDefault("campTargetType", "Enemy"));
        var center = otherTargets.get("centerBasedOn");
        GameEntity anchor = self;
        if (center != null) {
            if (!(center instanceof String name)
                    || ability == null
                    || !Set.of(
                                    "Self",
                                    "Target",
                                    "Caster",
                                    "Owner",
                                    "CurLocalAvatar",
                                    "OriginOwner",
                                    "CasterOriginOwner")
                            .contains(name)) return List.of();
            var player = ability.getPlayerOwner();
            if (player != null
                    && (player.getScene() != scene || player.getWorld() != scene.getWorld()))
                return List.of();
            if (Set.of("CurLocalAvatar", "OriginOwner").contains(name)
                    && (player == null
                            || player.getTeamManager().getActiveTeamSnapshot().isEmpty()))
                return List.of();
            anchor = AbilityActionHandler.resolveTarget(ability, self, name);
            if (anchor == null
                    || anchor.getScene() != scene
                    || !anchor.isAlive()
                    || !AbilityExecutionContext.canTrigger(anchor, player)) return List.of();
        }
        var origin = anchor.getPosition();

        var found = new ArrayList<GameEntity>();
        for (var entity : scene.getEntities().values()) {
            // Explicit avatar-centered healing includes its origin. Existing selectors without
            // an explicit center retain their previous exclusion of the invoking entity.
            if (entity == self && center == null || !entity.isAlive()) continue;
            if (!wanted.isEmpty() && !isOfType(entity, wanted)) continue;
            if (!matchesCamp(entity, camp, ability)) continue;
            if (radius > 0 && horizontalDistance(origin, entity.getPosition()) > radius) continue;
            if (height > 0 && Math.abs(origin.getY() - entity.getPosition().getY()) > height)
                continue;

            found.add(entity);
        }

        if ("Nearest".equals(otherTargets.get("sortType"))) {
            found.sort(Comparator.comparingDouble(e -> origin.computeDistance(e.getPosition())));
        }

        int limit = asInt(otherTargets.get("topLimit"));
        return limit > 0 && found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** The names come from either entityTypes or entityTypePriority depending on the selector. */
    private static Set<String> entityTypes(Map<String, Object> otherTargets) {
        var names = new HashSet<String>();
        for (var key : new String[] {"entityTypes", "entityTypePriority"}) {
            if (otherTargets.get(key) instanceof List<?> list) {
                list.forEach(name -> names.add(String.valueOf(name)));
            }
        }
        return names;
    }

    private static boolean isOfType(GameEntity entity, Set<String> wanted) {
        for (var name : wanted) {
            var matches =
                    switch (name) {
                        case "Monster" -> entity instanceof EntityMonster;
                        case "Gadget" -> entity instanceof EntityBaseGadget;
                        case "Avatar", "RemoteAvatar" -> entity instanceof EntityAvatar;
                        case "Vehicle" -> entity instanceof EntityVehicle;
                        default -> false;
                    };
            if (matches) return true;
        }
        return false;
    }

    /**
     * Player-owned avatars and summons are allied when their owners share the caster's world. This
     * includes co-op partners; an absent owner must not compare equal to an absent caster.
     * Non-player camps still use the existing enemy fallback.
     */
    private static boolean matchesCamp(GameEntity entity, String camp, Ability ability) {
        var player = ability != null ? ability.getPlayerOwner() : null;
        var owner =
                entity instanceof EntityAvatar avatar
                        ? avatar.getPlayer()
                        : entity instanceof EntityClientGadget gadget ? gadget.getOwner() : null;
        boolean allied =
                player != null
                        && owner != null
                        && (owner == player
                                || player.getWorld() != null
                                        && player.getWorld() == owner.getWorld());

        return switch (camp) {
            case "Enemy" -> !allied;
            case "Alliance", "SelfCamp" -> allied;
            default -> true;
        };
    }

    private static float horizontalDistance(Position a, Position b) {
        var dx = a.getX() - b.getX();
        var dz = a.getZ() - b.getZ();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** The current no-offset export defaults request no custom selector placement. */
    static boolean defaultSelectorBorn(Object value) {
        if (!(value instanceof Map<?, ?> born)
                || !born.keySet()
                        .equals(
                                Set.of(
                                        "direction",
                                        "EALBHHJEFGH",
                                        "value",
                                        "onGroundRaycastUpDist"))) return false;
        return defaultAngle(born.get("direction"), "maxAngle")
                && defaultAngle(born.get("EALBHHJEFGH"), "BKAPGJCLCIO")
                && number(born.get("value"), 10)
                && number(born.get("onGroundRaycastUpDist"), 2);
    }

    private static boolean defaultAngle(Object value, String key) {
        return value instanceof Map<?, ?> map
                && map.keySet().equals(Set.of(key))
                && number(map.get(key), 180);
    }

    private static boolean number(Object value, double expected) {
        return value instanceof Number number && number.doubleValue() == expected;
    }

    private static float dimension(Pattern pattern, String shapeName) {
        var matcher = pattern.matcher(shapeName);
        return matcher.find() ? Float.parseFloat(matcher.group(1)) : 0f;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}

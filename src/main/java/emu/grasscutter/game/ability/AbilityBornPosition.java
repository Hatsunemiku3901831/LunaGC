package emu.grasscutter.game.ability;

import com.google.gson.*;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Position;

import java.util.Set;

/** Only deterministic birth rules with no terrain, skeleton, or random dependency are local. */
public final class AbilityBornPosition {
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Caster",
                    "Owner",
                    "OriginOwner",
                    "CurLocalAvatar",
                    "CasterOriginOwner");
    private static final Set<String> FIELDS =
            Set.of(
                    "$type",
                    "offset",
                    "useRotation",
                    "positionKey",
                    "onGround",
                    "alongGround",
                    "bornRandom",
                    "direction",
                    "onGroundRaycastUpDist",
                    "APKLAIJMEIL",
                    "value");

    private AbilityBornPosition() {}

    public static GameEntity recipient(
            Ability ability, AbilityModifierAction action, GameEntity target, boolean resolved) {
        if (ability == null
                || action == null
                || target == null
                || action.otherTargets != null
                || action.target != null && !TARGETS.contains(action.target)) return null;
        var result =
                resolved
                        ? target
                        : AbilityActionHandler.resolveTarget(ability, target, action.target);
        return AbilityGlobalPositions.current(ability, result) ? result : null;
    }

    public static boolean supported(JsonElement born) {
        if (born == null || born.isJsonNull()) return true;
        if (!born.isJsonObject()) return false;
        var object = born.getAsJsonObject();
        var type = string(object.get("$type"));
        if (type == null
                || !Set.of("ConfigBornBySelf", "ConfigBornByTarget", "ConfigBornByGlobalValue")
                        .contains(type)
                || !FIELDS.containsAll(object.keySet())
                || vector(object.get("offset")) == null
                || !flag(object, "useRotation")
                || !inactive(object, "onGround")
                || !inactive(object, "alongGround")
                || !inactive(object, "bornRandom")) return false;
        // These inert export defaults do not request a direction or a raycast. Changed values
        // remain unresolved; they must not silently turn into the plain world-offset rule.
        if (object.has("direction") && !defaultObject(object.get("direction"), "maxAngle", 180))
            return false;
        if (object.has("APKLAIJMEIL")
                && !defaultObject(object.get("APKLAIJMEIL"), "HHNLJKNBKPP", 180)) return false;
        if (object.has("value") && !numberEquals(object.get("value"), 10)) return false;
        if (object.has("onGroundRaycastUpDist")
                && number(object.get("onGroundRaycastUpDist")) == null) return false;
        return !"ConfigBornByGlobalValue".equals(type)
                || string(object.get("positionKey")) != null
                        && !string(object.get("positionKey")).isBlank();
    }

    public static Position resolve(JsonElement born, GameEntity self, GameEntity target) {
        if (self == null || target == null || !supported(born)) return null;
        if (born == null || born.isJsonNull()) return snapshot(self.getPosition());
        var object = born.getAsJsonObject();
        var type = string(object.get("$type"));
        var anchor = "ConfigBornBySelf".equals(type) ? self : target;
        var base =
                "ConfigBornByGlobalValue".equals(type)
                        ? target.getAbilityGlobalPositions().get(string(object.get("positionKey")))
                        : snapshot(anchor.getPosition());
        if (base == null) return null;
        var offset = vector(object.get("offset"));
        if (object.has("useRotation")
                && object.get("useRotation").getAsBoolean()
                && (offset.getX() != 0 || offset.getZ() != 0)) {
            var rotation = anchor.getRotation();
            // The server stores Euler angles but has no general client transform hierarchy.
            if (!AbilityGlobalPositions.finite(rotation)
                    || rotation.getX() != 0
                    || rotation.getZ() != 0) return null;
            double yaw = Math.toRadians(rotation.getY());
            double cos = Math.cos(yaw), sin = Math.sin(yaw);
            offset =
                    new Position(
                            (float) (offset.getX() * cos + offset.getZ() * sin),
                            offset.getY(),
                            (float) (offset.getZ() * cos - offset.getX() * sin));
        }
        return snapshot(base.add(offset));
    }

    public static Position snapshot(Position value) {
        return AbilityGlobalPositions.finite(value) ? value.clone() : null;
    }

    public static boolean targetWriteSupported(
            AbilityModifierAction action, Ability ability, GameEntity recipient) {
        var flag = action.setTarget;
        if (flag == null || flag.isJsonNull()) return recipient == ability.getOwner();
        return flag.isJsonPrimitive()
                && flag.getAsJsonPrimitive().isBoolean()
                && (flag.getAsBoolean() || recipient == ability.getOwner());
    }

    private static Position vector(JsonElement value) {
        if (value == null || value.isJsonNull()) return new Position();
        if (!value.isJsonObject()
                || !Set.of("x", "y", "z").containsAll(value.getAsJsonObject().keySet()))
            return null;
        var object = value.getAsJsonObject();
        Float x = object.has("x") ? number(object.get("x")) : Float.valueOf(0);
        Float y = object.has("y") ? number(object.get("y")) : Float.valueOf(0);
        Float z = object.has("z") ? number(object.get("z")) : Float.valueOf(0);
        return x == null || y == null || z == null ? null : new Position(x, y, z);
    }

    private static Float number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            return null;
        try {
            float result = value.getAsFloat();
            return Float.isFinite(result) ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static boolean numberEquals(JsonElement value, float expected) {
        var number = number(value);
        return number != null && number == expected;
    }

    private static boolean defaultObject(JsonElement value, String key, float number) {
        return value != null
                && value.isJsonObject()
                && value.getAsJsonObject().size() == 1
                && numberEquals(value.getAsJsonObject().get(key), number);
    }

    private static boolean flag(JsonObject object, String name) {
        return !object.has(name)
                || object.get(name).isJsonPrimitive()
                        && object.get(name).getAsJsonPrimitive().isBoolean();
    }

    private static boolean inactive(JsonObject object, String name) {
        return !object.has(name) || flag(object, name) && !object.get(name).getAsBoolean();
    }
}

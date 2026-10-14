package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Position;

import java.util.HashMap;
import java.util.Map;

/** Position snapshots belong to an entity lifetime, independently of scalar ability values. */
public final class AbilityGlobalPositions {
    private final GameEntity owner;
    private final Map<String, Position> values = new HashMap<>();

    public AbilityGlobalPositions(GameEntity owner) {
        this.owner = owner;
    }

    public synchronized boolean write(Ability ability, String key, Position position) {
        if (key == null || key.isBlank() || !finite(position) || !current(ability, owner))
            return false;
        values.put(key, position.clone());
        return true;
    }

    public synchronized Position get(String key) {
        if (!owner.isAbilityLifecycleCurrent(owner.getAbilityLifecycleVersion())) return null;
        var value = values.get(key);
        return value == null ? null : value.clone();
    }

    public synchronized void clear() {
        values.clear();
    }

    public static boolean current(Ability ability, GameEntity target) {
        return AbilityGlobalValues.isCurrent(ability, target)
                && ability.getOwner().getScene() != null
                && target.getScene() == ability.getOwner().getScene();
    }

    public static boolean finite(Position position) {
        return position != null
                && Float.isFinite(position.getX())
                && Float.isFinite(position.getY())
                && Float.isFinite(position.getZ());
    }
}

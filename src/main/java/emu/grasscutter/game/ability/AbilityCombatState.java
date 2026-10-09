package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.GameEntity;

/** Combat protection derived from the controllers that are still attached to this entity. */
public record AbilityCombatState(
        boolean invincible, boolean lockHp, boolean limbo, float hpThreshold) {
    public static AbilityCombatState read(GameEntity entity) {
        var state = new Collector();
        var instances = entity.getInstancedModifiers();
        synchronized (instances) {
            // A named replacement does not erase another live client instance of the same name.
            for (var controller : instances.values()) state.add(controller);
            for (var named : entity.getAppliedAbilityModifiers().values()) {
                for (var controller : named.values()) state.add(controller);
            }
        }
        return new AbilityCombatState(state.invincible, state.lockHp, state.limbo, state.hpThreshold);
    }

    private static final class Collector {
        private boolean invincible;
        private boolean lockHp;
        private boolean limbo;
        private float hpThreshold;

        private void add(AbilityModifierController controller) {
            if (controller == null) return;
            var data = controller.getModifierData();
            if (data == null || data.state == null) return;
            switch (data.state) {
                case Invincible -> invincible = true;
                case LockHP -> lockHp = true;
                case Limbo -> {
                    limbo = true;
                    var properties = data.properties;
                    if (properties != null && properties.Actor_HpThresholdRatio != null) {
                        float ratio = properties.Actor_HpThresholdRatio.get(controller.getAbility(), 0f);
                        hpThreshold = Math.max(hpThreshold, Math.max(0f, Math.min(1f, ratio)));
                    }
                }
                default -> {}
            }
        }
    }
}

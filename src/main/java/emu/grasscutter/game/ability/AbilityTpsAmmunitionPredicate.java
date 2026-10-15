package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.tps.TpsWeaponSystem;

import java.math.BigDecimal;
import java.util.Map;

/** FGDONAGOCFP compares the local player's total pool with the scene's resource limit. */
public final class AbilityTpsAmmunitionPredicate {
    private AbilityTpsAmmunitionPredicate() {}

    public static int poolId(Map<?, ?> predicate) {
        Object value = predicate.get("NHCCEJKBOPB");
        if (!(value instanceof Number number)) return 0;
        try {
            int id = new BigDecimal(number.toString()).intValueExact();
            return Math.max(0, id);
        } catch (ArithmeticException | NumberFormatException invalid) {
            return 0;
        }
    }

    public static boolean evaluate(
            Map<String, Object> predicate, Ability ability, GameEntity target) {
        int id = poolId(predicate);
        if (id == 0
                || ability == null
                || ability.getOwner() == null
                || ability.getPlayerOwner() == null
                || !(target instanceof EntityAvatar recipient)
                || recipient.getPlayer() != ability.getPlayerOwner()
                || !AbilityGlobalValues.isCurrent(ability, recipient)) return false;
        var owner = ability.getOwner();
        var scene = ability.getPlayerOwner().getScene();
        if (owner.isDead()
                || scene == null
                || owner.getScene() != scene
                || scene.getEntityById(owner.getId()) != owner
                || !owner.getInstancedAbilities().contains(ability)
                        && owner.getDynamicAbilities().get(ability.getData().abilityName)
                                != ability) return false;
        return TpsWeaponSystem.hasAmmunitionSpace(recipient, id);
    }
}

package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.scripts.constants.ScriptGadgetState;

import java.util.*;

/** Resource end actions for the current chest seals and wooden bramble materials. */
final class EnvironmentalMaterialCallbacks {
    private record Material(String modifier, int state, AbilityModifierAction.Type action) {}

    private static final Map<String, Material> MATERIALS =
            Map.of(
                    "SceneObj_Chest_Bramble_Interact",
                            new Material(
                                    "GrassMaterial",
                                    ScriptGadgetState.ChestBramble,
                                    AbilityModifierAction.Type.ExecuteGadgetLua),
                    "SceneObj_Chest_Frozen_Interact",
                            new Material(
                                    "IceMaterial",
                                    ScriptGadgetState.ChestFrozen,
                                    AbilityModifierAction.Type.ExecuteGadgetLua),
                    "SceneObj_Chest_Rock_Interact",
                            new Material(
                                    "Chest_Rock",
                                    ScriptGadgetState.ChestRock,
                                    AbilityModifierAction.Type.ExecuteGadgetLua),
                    "SceneObj_Environment_WoodenMaterial_Bramble_Interact",
                            new Material(
                                    "Bramble_Burning", -1, AbilityModifierAction.Type.KillSelf),
                    "SceneObj_Environment_WoodenMaterial_BrambleWorld_Interact",
                            new Material("Burning", -1, AbilityModifierAction.Type.KillSelf));

    private EnvironmentalMaterialCallbacks() {}

    static void removed(
            AbilityModifierController controller, GameEntity target, ByteString payload) {
        var ability = controller.getAbility();
        if (!(target instanceof EntityGadget gadget)
                || ability.getOwner() != gadget
                || !controller.isClientInstanceCurrent(target)
                || controller.hasServerCallbacks()
                || controller.hasBindingCallbacks()
                || controller.hasAnimationCallbacks()) return;
        if (gadget.getQuartzOreCycle() != null) {
            gadget.getQuartzOreCycle().removed(controller, payload);
            return;
        }
        var material = MATERIALS.get(ability.getData().abilityName);
        if (material == null
                || !material.modifier.equals(controller.getName())
                || gadget.getScene().getEntities().get(gadget.getId()) != gadget
                || !gadget.isAlive()) return;
        var caster = ability.getPlayerOwner();
        if (caster == null
                || caster.getScene() != gadget.getScene()
                || caster.getWorld() != gadget.getWorld()) return;
        if (material.state >= 0
                && (gadget.getState() != material.state
                        || gadget.getGadgetData() == null
                        || gadget.getGadgetData().getType() != EntityType.Chest)) return;
        var config = gadget.getConfigGadget();
        if (config == null
                || config.getAbilities() == null
                || config.getAbilities().stream()
                        .noneMatch(
                                a ->
                                        a != null
                                                && ability.getData()
                                                        .abilityName
                                                        .equals(a.getAbilityName()))) return;
        var end = controller.getModifierData().onRemoved;
        if (end == null) return;
        // Only the directly selected owner's verified end action bypasses the bookkeeping filter.
        // Other removal callbacks, targets and arbitrary gadget controllers keep their own paths.
        var actions =
                Arrays.stream(end)
                        .filter(
                                a ->
                                        a != null
                                                && a.type == material.action
                                                && (a.target == null || "Self".equals(a.target))
                                                && a.otherTargets == null)
                        .toArray(AbilityModifierAction[]::new);
        AbilityCooldownInvocation.run(
                controller,
                () ->
                        ability.getManager()
                                .executeCallbackActionsNow(
                                        ability, actions, payload, gadget, gadget));
    }
}

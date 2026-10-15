package emu.grasscutter.data.binout;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.utils.JsonAdapters;
import emu.grasscutter.game.ability.AbilityLocalIdGenerator;
import emu.grasscutter.game.ability.AbilityGlobalValueDispatch;
import emu.grasscutter.game.ability.ShieldBarCallbacks;
import emu.grasscutter.game.ability.AbilityLocalIdGenerator.ConfigAbilitySubContainerType;
import java.util.*;

public class AbilityData {
    @SerializedName(value = "abilityName", alternate = {"BEAFNCHOJGD"})
    public String abilityName;

    @SerializedName(value = "modifiers", alternate = {"LEKAENNPGMI"})
    public Map<String, AbilityModifier> modifiers;
    public boolean isDynamicAbility;
    @JsonAdapter(JsonAdapters.AbilitySpecialsAdapter.class)
    public Map<String, Float> abilitySpecials;

    public AbilityModifierAction[] onAdded;
    public AbilityModifierAction[] onRemoved;
    public AbilityModifierAction[] onAbilityStart;
    public AbilityModifierAction[] onKill;
    public AbilityModifierAction[] onFieldEnter;
    public AbilityModifierAction[] onExit;
    public AbilityModifierAction[] onAttach;
    public AbilityModifierAction[] onDetach;
    public AbilityModifierAction[] onAvatarIn;
    public AbilityModifierAction[] onAvatarOut;
    public AbilityModifierAction[] onTriggerAvatarRay;
    public AbilityModifierAction[] onVehicleIn;
    public AbilityModifierAction[] onVehicleOut;

    // abilityMixins
    public AbilityMixinData[] abilityMixins;

    public final Map<Integer, AbilityModifierAction> localIdToAction = new HashMap<>();
    public final Map<Integer, AbilityMixinData> localIdToMixin = new HashMap<>();
    public final transient Map<Integer, ShieldBarCallbacks.Callback> shieldBarCallbacks = new HashMap<>();

    private final transient Set<AbilityModifierAction> globalValueModifierCallbacks =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public boolean isGlobalValueModifierDescendant(AbilityModifierAction action) {
        return globalValueModifierCallbacks.contains(action);
    }

    private boolean _initialized = false;

    public void initialize() {
        if (_initialized) return;
        _initialized = true;

        initializeMixins();
        initializeModifiers();
        initializeActions();
    }

    private void initializeActions() {
        AbilityLocalIdGenerator generator =
                new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.ACTION);
        generator.configIndex = 0;

        generator.initializeActionLocalIds(onAdded, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onRemoved, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAbilityStart, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onKill, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onFieldEnter, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onExit, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAttach, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onDetach, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAvatarIn, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAvatarOut, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onTriggerAvatarRay, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onVehicleIn, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onVehicleOut, localIdToAction);
    }

    private void initializeMixins() {
        if (abilityMixins != null) {
            AbilityLocalIdGenerator generator =
                    new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.MIXIN);
            generator.modifierIndex = 0;
            generator.configIndex = 0;

            generator.initializeMixinsLocalIds(abilityMixins, localIdToMixin);
            ShieldBarCallbacks.index(this, abilityMixins, null, 0);
        }
    }

    private void initializeModifiers() {
        if (modifiers == null) {
            this.modifiers = new HashMap<>();
            return;
        }

        // ModifierChange indexes and action local IDs use the same alphabetical order.
        // Keep the map in that order too: JSON insertion order otherwise picks a different
        // modifier when AbilityManager resolves a client modifierLocalId.
        this.modifiers = new TreeMap<>(this.modifiers);
        var _modifiers =
                modifiers.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .toList();

        var modifierIndex = 0;
        for (AbilityModifier abilityModifier : _modifiers) {
            AbilityGlobalValueDispatch.collectModifierDescendants(abilityModifier.onAdded,globalValueModifierCallbacks);
            AbilityGlobalValueDispatch.collectModifierDescendants(abilityModifier.onRemoved,globalValueModifierCallbacks);
            long configIndex = 0L;
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAdded, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onRemoved, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onBeingHit, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAttackLanded, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onHittingOther, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onThinkInterval, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onKill, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onCrash, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAvatarIn, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAvatarOut, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onReconnect, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onChangeAuthority, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onVehicleIn, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onVehicleOut, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onZoneEnter, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onZoneExit, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onHeal, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onBeingHealed, localIdToAction);

            if (abilityModifier.modifierMixins != null) {
                var generator = new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.MODIFIER_MIXIN);
                generator.modifierIndex = modifierIndex;
                generator.configIndex = 0;

                generator.initializeMixinsLocalIds(abilityModifier.modifierMixins, localIdToMixin);
                ShieldBarCallbacks.index(this, abilityModifier.modifierMixins, abilityModifier, modifierIndex);
            }

            modifierIndex++;
        }
    }

    private void initializeActionSubCategory(
            long modifierIndex,
            long configIndex,
            AbilityModifierAction[] actions,
            Map<Integer, AbilityModifierAction> localIdToAction) {
        if (actions == null) return;

        var generator = new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.MODIFIER_ACTION);
        generator.modifierIndex = modifierIndex;
        generator.configIndex = configIndex;

        generator.initializeActionLocalIds(actions, localIdToAction);
    }
}

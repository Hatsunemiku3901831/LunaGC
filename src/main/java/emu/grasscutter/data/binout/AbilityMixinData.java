package emu.grasscutter.data.binout;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.utils.JsonAdapters;

import java.io.Serializable;
import java.util.*;

@JsonAdapter(AbilityMixinData.EventAdapter.class)
public class AbilityMixinData implements Serializable {
    private static final long serialVersionUID = -2001232313615923575L;

    public enum Type {
        AttachToGadgetStateMixin,
        AttachToStateIDMixin,
        SetGadgetStateV2,
        ShieldBarMixin,
        AvatarCombatMixin,
        DoActionByEventMixin,
        DoActionByKillingMixin,
        DoActionByElementReactionMixin,
        DoActionBySelfElementReactionMixin,
        SkillButtonHoldChargeMixin,
        GlobalSubShieldMixin,
        TileAttackMixin,
        SwitchHealToHPDebtsMixin,
        AttachModifierToGlobalValueMixin,
        DJLJLPAFPGN,
        KENHGCLICPB,
        DoActionOnGlobalValueChangeMixin,
        CurLocalAvatarMixin,
        @SerializedName(value = "NyxCostMixin", alternate = "ChangeNyxValueMixin")
        NyxCostMixin,
        ModifyDamageMixin,
        AvatarChangeSkillMixin,
        SwitchSkillIDMixin,
        KHOENFHDFJE,
        HJKDMEOOBDK,
        FIGCOCJJHCH,
        DMKDPHHJENO,
        LAAJCBLNLDO,
        CameraBlurMixin,
        AttachToNormalizedTimeMixin,
        AttachToMultiNormalizedTimeMixin,
        DLJBCMKDMEK,
        PhlogistonCostMixin,
        FIHACJPNNED,
        JMEOJHGPNMB,
        AttachModifierToSelfGlobalValueMixin,
        AttachActionToModifierMixin,
        AttachModifierToSelfGlobalValueNoInitMixin,
        HPDebtsMixin,
        LimitHpDebtsByTagMixin,
        TileAttackManagerMixin,
        CostStaminaMixin,
        DoActionByEnergyChangeMixin,
        RejectAttackMixin,
        DoActionByTargetsCountMixin,
        AttachToAbilityStateMixin,
        IPIBBIDFDOL,
        EBBCNBHOAIP,
        ReviveElemEnergyMixin,
        HKGPGGJAKGL,
        ENPGGGNLLJG,
        PBOJOFIGPIC,
        IEABBMGDJHC,
        ModifyBeHitDamageMixin,
        DoActionByCreateGadgetMixin,
        MuteHitEffectMixin,
        EntityInVisibleMixin,
        DDCOPGJBHLB,
        IBAMBHPLNNA,
        PCKKGOMJIKL,
        TriggerPostProcessEffectMixin,
        JGOOOFOCJBI,
        OOAMMMJMKPD,
        EFDAMNIDHDC,
        JMJFEPHFFFN,
        AttachToAnimatorStateIDMixin,
        AvatarSteerByCameraMixin,
        ModifyDamageCountMixin,
        AttackCostElementMixin,
        OnAvatarUseSkillMixin,
        CheckSubTagScanEntityMixin,
        TryEnterVehicleMixin,
        DoActionOnVehicleInteractPostMixin,
        TriggerVehicleOff
    }

    public AbilityModifierAction[] idontknowwhattonamethis;
    public AbilityModifierAction[] idontknowwhattonamethis2;

    // Reaction callbacks use actions, while self reactions use actionQueue.
    public AbilityModifierAction[] actions;

    @SerializedName("onAdded")
    public JsonElement addedLifecycleConfig;

    @SerializedName("onRemoved")
    public JsonElement removedLifecycleConfig;

    public AbilityModifierAction[] actionQueue;
    public String onEvent;
    public transient JsonElement structuredOnEvent;
    public String vehicleType;
    public AbilityModifierAction[] onSelectStart;

    @SerializedName(value = "onSelectEnd", alternate = "IMEDGAOLLEM")
    public AbilityModifierAction[] onSelectEnd;

    public AbilityModifierAction[] onVehicleIn;
    public AbilityModifierAction[] onVehicleOut;

    @SerializedName("type")
    public String eventExecutionType;

    public JsonElement skillID;
    public JsonElement skillIndex;

    @SerializedName("actorIceSubHurtDelta")
    public JsonElement legacySkillIndex;

    public JsonElement priority;
    public List<String> reactionTypes = new ArrayList<>();
    // Both occur together with different entries in current resources. Keep them separate.
    @SerializedName("IGAMNNAADJB")
    public List<String> rawReactionTypes = new ArrayList<>();

    @SerializedName("onEnterCombat")
    public AbilityModifierAction[] onEnterCombat;

    @SerializedName("onExitCombat")
    public AbilityModifierAction[] onExitCombat;

    @SerializedName("onTriggerSkill")
    public AbilityModifierAction[] onTriggerSkill;

    @SerializedName("onTriggerUltimateSkill")
    public AbilityModifierAction[] onTriggerUltimateSkill;

    public AbilityModifierAction[] IOKPLLOKGGJ;

    @SerializedName("onKill")
    public AbilityModifierAction[] onKill;

    @SerializedName("successActions")
    public AbilityModifierAction[] successActions;

    @SerializedName("succActions")
    public AbilityModifierAction[] succActions;

    @SerializedName("$type")
    public Type type;

    public JsonElement modifierName;

    public DynamicFloat speed = DynamicFloat.ZERO;
    public DynamicFloat costStaminaDelta = DynamicFloat.ZERO;
    public DynamicFloat ratio = DynamicFloat.ONE;
    public List<Map<String, Object>> predicates;
    public DynamicFloat value;
    public DynamicFloat minValue;
    public DynamicFloat maxValue;
    public DynamicFloat detectWindow = DynamicFloat.ONE;
    public String globalValueKey;
    public List<String> stateIDs = new ArrayList<>();
    public String stateID;
    public DynamicFloat defaultGlobalValueOnCreate = DynamicFloat.ZERO;
    public List<DynamicFloat> ratioSteps = new ArrayList<>();
    public List<DynamicFloat> valueSteps = new ArrayList<>();
    public String globalValueTarget;
    // These coexist in current resources. Do not alias them and lose the canonical value.
    public Boolean removeAppliedModifier;

    @SerializedName("DEFOFMOIAAI")
    public Boolean rawRemoveAppliedModifier;

    public boolean enable = true;

    public boolean removesAppliedModifier() {
        return removeAppliedModifier != null
                ? removeAppliedModifier
                : rawRemoveAppliedModifier == null || rawRemoveAppliedModifier;
    }

    @JsonAdapter(JsonAdapters.ModifierNameStepsAdapter.class)
    public List<String> modifierNameSteps = new ArrayList<>();

    public boolean EJEMBMFPBKF = true;
    public boolean isCheckOnAttach = true;
    public boolean AMFABNCKJNG = true;
    public boolean forceStopWhenRemoved = true;
    public boolean FKAJIEOFOAB = true;

    public List<String> getModifierNames() {
        if (modifierName.isJsonArray()) {
            java.lang.reflect.Type listType = (new TypeToken<List<String>>() {}).getType();
            List<String> list = (new Gson()).fromJson(modifierName, listType);
            return list;
        } else {
            return Arrays.asList(modifierName.getAsString());
        }
    }
    /** Keep scalar event names and structured event callbacks without coercing either form. */
    public static final class EventAdapter implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            var delegate = gson.getDelegateAdapter(this, type);
            var json = gson.getAdapter(JsonElement.class);
            return new TypeAdapter<T>() {
                @Override
                public T read(com.google.gson.stream.JsonReader reader) throws java.io.IOException {
                    var element = json.read(reader);
                    JsonElement structured = null;
                    if (element != null && element.isJsonObject()) {
                        var event = element.getAsJsonObject().get("onEvent");
                        if (event != null
                                && !event.isJsonNull()
                                && !(event.isJsonPrimitive()
                                        && event.getAsJsonPrimitive().isString())) {
                            element = element.deepCopy();
                            structured = element.getAsJsonObject().remove("onEvent");
                        }
                    }
                    T value = delegate.fromJsonTree(element);
                    if (value instanceof AbilityMixinData mixin)
                        mixin.structuredOnEvent = structured;
                    return value;
                }

                @Override
                public void write(com.google.gson.stream.JsonWriter writer, T value)
                        throws java.io.IOException {
                    var element = delegate.toJsonTree(value);
                    if (value instanceof AbilityMixinData mixin
                            && mixin.structuredOnEvent != null
                            && mixin.onEvent == null)
                        element.getAsJsonObject()
                                .add("onEvent", mixin.structuredOnEvent.deepCopy());
                    json.write(writer, element);
                }
            };
        }
    }
}

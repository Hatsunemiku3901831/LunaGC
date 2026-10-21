package emu.grasscutter.data.binout;

import com.google.gson.TypeAdapter;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.utils.JsonAdapters.DynamicFloatAdapter;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import lombok.ToString;

public class AbilityModifier implements Serializable {
    private static final long serialVersionUID = -2001232313615923575L;

    public State state;

    @SerializedName(
            value = "onAdded",
            alternate = {"KCICDEJLIJD", "EHNPMFJPEAI"})
    public AbilityModifierAction[] onAdded;

    @SerializedName(
            value = "onThinkInterval",
            alternate = {"PBDDACFFPOE", "HLCGGKIJAGM"})
    public AbilityModifierAction[] onThinkInterval;

    @SerializedName(value = "onRemoved", alternate = "HCAONLNGFGO")
    public AbilityModifierAction[] onRemoved;

    @SerializedName(value = "onBeingHit", alternate = "MGGKNJPGHPG")
    public AbilityModifierAction[] onBeingHit;

    @SerializedName(value = "onAttackLanded", alternate = "LBABLMBNAJE")
    public AbilityModifierAction[] onAttackLanded;

    @SerializedName(value = "onHittingOther", alternate = "BIGKPCNFDOM")
    public AbilityModifierAction[] onHittingOther;

    @SerializedName(value = "onKill", alternate = "JNPMADJHDPI")
    public AbilityModifierAction[] onKill;

    public AbilityModifierAction[] onCrash;

    @SerializedName(value = "onAvatarIn", alternate = "PCHKGCNLAKL")
    public AbilityModifierAction[] onAvatarIn;

    @SerializedName(value = "onAvatarOut", alternate = "GNNLFAGCOED")
    public AbilityModifierAction[] onAvatarOut;

    @SerializedName(value = "onReconnect", alternate = "OPOMCDOLKFF")
    public AbilityModifierAction[] onReconnect;

    public AbilityModifierAction[] onChangeAuthority;

    @SerializedName(value = "onVehicleIn", alternate = "NNFFLLKFNKN")
    public AbilityModifierAction[] onVehicleIn;

    public AbilityModifierAction[] onVehicleOut;
    public AbilityModifierAction[] onZoneEnter;
    public AbilityModifierAction[] onZoneExit;
    @SerializedName(value = "onHeal", alternate = "JKCBCFOKMCE")
    public AbilityModifierAction[] onHeal;
    @SerializedName(value = "onBeingHealed", alternate = "COJIHOHKDJF")
    public AbilityModifierAction[] onBeingHealed;
    public DynamicFloat duration = DynamicFloat.ZERO;
    public DynamicFloat thinkInterval = DynamicFloat.ZERO;
    public String stacking;
    public boolean isUnique;
    @SerializedName(value = "stackingOption", alternate = "GMGIAGMCGLF")
    public Map<String,Object> stackingOption;

    @SerializedName(value = "modifierMixins", alternate = "IIECHMHLPDM")
    public AbilityMixinData[] modifierMixins;
    public AbilityModifierProperty properties;

    public ElementType elementType;
    public DynamicFloat elementDurability = DynamicFloat.ZERO;

    @ToString
    @JsonAdapter(AbilityActionAdapter.class)
    public static class AbilityModifierAction implements Serializable {
        public enum Type {
            TriggerVehicleOff,
            TriggerVehicleOn,
           PhlogistonCostMixin,
            ActCameraRadialBlur,
            ActCameraShake,
            AddAvatarSkillInfo,
            AddChargeBarValue,
            AddClimateMeter,
            AddElementDurability,
            AddHPDebts,
            @SerializedName(value = "AddSpecialEnergy", alternate = "ReviveSpecialEnergy")
            AddSpecialEnergy,
            GetHPPaidDebts,
            ChangePhlogiston,
            TPSSupplyAmmo,
            AddGlobalValue,
            AddGlobalValueToTarget,
            AddRegionalPlayVarValue,
            ApplyModifier,
            AttachAbilityStateResistance,
            AttachBulletAimPoint,
            AttachEffect,
            AttachEffectFirework,
            AttachElementTypeResistance,
            AttachModifier,
            AttachUIEffect,
            AvatarCameraParam,
            AvatarEnterCameraShot,
            AvatarEnterFocus,
            AvatarEnterViewBias,
            AvatarExitCameraShot,
            AvatarExitClimb,
            AvatarExitFocus,
            AvatarExitViewBias,
            AvatarShareCDSkillStart,
            ActivitySkillShareCDStart,
            ClearShareCD,
            AvatarSkillStart,
        UnlockSkill,
            BroadcastNeuronStimulate,
            ByTargetGlobalValue,
            CalcDvalinS04RebornPoint,
            CallLuaTask,
            ChangeEnviroWeather,
            ChangeFollowDampTime,
            ChangeGadgetUIInteractHint,
            ChangePlayMode,
            ChangeTag,
            ChangeUGCRayTag,
            ClearEndura,
            ClearGlobalPos,
            ClearGlobalValue,
            DoActionByGlobalValue,
            TPSAmmoBoxDispatch,
            ClearLocalGadgets,
            ClearLockTarget,
            ClearPos,
            ConfigAbilityAction,
            ControlEmotion,
            CopyGlobalValue,
            CreateGadget,
            CreateMovingPlatform,
            CreateTile,
            DamageByAttackValue,
            DebugLog,
            @SerializedName(value = "NyxSet", alternate = "SetNyxValue")
            NyxSet,
            @SerializedName(value = "NyxAdd", alternate = "AddNyxValue")
            NyxAdd,
            DestroyTile,
            DoBlink,
            DoTileAction,
            DoWatcherSystemAction,
            DoWidgetSystemAction,
            DropSubfield,
            DummyAction,
            DungeonFogEffects,
            ElementAttachForActivityGacha,
            EnableAIStealthy,
            EnableAfterImage,
            EnableAvatarFlyStateTrail,
            EnableAvatarMoveOnWater,
            EnableBulletCollisionPluginTrigger,
            EnableGadgetIntee,
            EnableHeadControl,
            EnableHitBoxByName,
            EnableMainInterface,
            EnablePartControl,
            EnablePositionSynchronization,
            EnablePushColliderName,
            EnableRocketJump,
            EnableSceneTransformByName,
            EnterCameraLock,
            EntityDoSkill,
            EquipAffixStart,
            ExecuteGadgetLua,
            FireAISoundEvent,
            FireChargeBarEffect,
            FireEffect,
            FireEffectFirework,
            FireEffectForStorm,
            FireFishingEvent,
            FireHitEffect,
            FireSubEmitterEffect,
            FireUIEffect,
            FixedMonsterRushMove,
            ForceAirStateFly,
            ForceEnableShakeOffButton,
            GenerateElemBall,
            GetFightProperty,
            GetInteractIdToGlobalValue,
            GetPos,
            HealHP,
            HideUIBillBoard,
            IgnoreMoveColToRockCol,
            KillGadget,
            KillPlayEntity,
            KillSelf,
            KillServerGadget,
            LoseHP,
            ModifyAvatarSkillCD,
            ModifyVehicleSkillCD,
            PlayEmoSync,
            Predicated,
            PushDvalinS01Process,
            PushInterActionByConfigPath,
            PushPos,
            Randomed,
            RandomAction,
            ReduceHPDebts,
            ReTriggerAISkillInitialCD,
            RefreshUICombatBarLayout,
            RegisterAIActionPoint,
            ReleaseAIActionPoint,
            RemoveAvatarSkillInfo,
            RemoveModifier,
            RemoveModifierByAbilityStateResistanceID,
            RemoveServerBuff,
            RemoveUniqueModifier,
            RemoveVelocityForce,
            Repeated,
            ResetAIAttackTarget,
            ResetAIResistTauntLevel,
            ResetAIThreatBroadcastRange,
            ResetAnimatorTrigger,
            ReviveAvatar,
            ReviveDeadAvatar,
            ReviveElemEnergy,
            ReviveStamina,
            SectorCityManeuver,
            SendEffectTrigger,
            SendEffectTriggerToLineEffect,
            SendEvtElectricCoreMoveEnterP1,
            SendEvtElectricCoreMoveInterrupt,
            ServerLuaCall,
            ServerLuaTriggerEvent,
            ServerMonsterLog,
            SetAIHitFeeling,
            SetAISkillCDAvailableNow,
            SetAISkillCDMultiplier,
            SetAISkillGCD,
            SetAnimatorBool,
            SetAnimatorFloat,
            SetAnimatorInt,
            SetAnimatorTrigger,
            SetAvatarCanShakeOff,
            SetAvatarHitBuckets,
            SetCanDieImmediately,
            SetChargeBarValue,
            SetDvalinS01FlyState,
            SetEmissionScaler,
            SetEntityScale,
            SetExtraAbilityEnable,
            SetExtraAbilityState,
            SetGlobalDir,
            SetGlobalPos,
            SetGlobalValue,
            SetGlobalValueByTargetDistance,
            SetGlobalValueToOverrideMap,
            SetGlobalValueV2,
            SetKeepInAirVelocityForce,
            SetMaterialParamFloatByTransform,
            SetNeuronEnable,
            SetOverrideMapValue,
            SetPartControlTarget,
            SetPoseBool,
            SetPoseFloat,
            SetPoseInt,
            SetRandomOverrideMapValue,
            SetRegionalPlayVarValue,
            SetSelfAttackTarget,
            SetSkillAnchor,
            SetSpecialCamera,
            SetSurroundAnchor,
            SetSystemValueToOverrideMap,
            SetTargetNumToGlobalValue,
            SetUICombatBarAsh,
            SetUICombatBarSpark,
            SetVelocityIgnoreAirGY,
            SetWeaponAttachPointRealName,
            SetWeaponBindState,
            ShowExtraAbility,
            ShowProgressBarAction,
            ShowReminder,
            ShowScreenEffect,
            ShowTextMap,
            ShowUICombatBar,
            StartDither,
            SumTargetWeightToSelfGlobalValue,
            Summon,
            SyncToStageScript,
            TriggerAbility,
            TriggerAttackEvent,
            TriggerAttackTargetMapEvent,
            TriggerAudio,
            TriggerAuxWeaponTrans,
            TriggerBullet,
            TriggerCreateGadgetToEquipPart,
            TriggerDropEquipParts,
            TriggerFaceAnimation,
            TriggerGadgetInteractive,
            TriggerHideWeapon,
            TriggerSetCastShadow,
            TriggerSetPassThrough,
            TriggerSetRenderersEnable,
            TriggerSetShadowRamp,
            TriggerSetVisible,
            TriggerTaunt,
            TriggerThrowEquipPart,
            TriggerUGCGadgetMove,
            TryFindBlinkPoint,
            TryFindBlinkPointByBorn,
            TryTriggerPlatformStartMove,
            TurnDirection,
            TurnDirectionToPos,
            UpdateReactionDamage,
            UseSkillEliteSet,
            WidgetSkillStart;
        }

        @SerializedName("$type")
        public Type type;

        public String target;

        @JsonAdapter(NullSafeStringAdapter.class)
        @SerializedName(value = "animatorParamName", alternate = {"triggerID", "boolID", "intID", "floatID"})
        public String animatorParamName;
        @SerializedName(value = "predicates", alternate = "GLCOHEDMLDM")
        public List<Object> predicates;

        @SerializedName(
                value = "amount",
                alternate = {"LNFMOCKIAGK", "PDLLIFICICJ", "LKDJMGOJHAH", "GHINLKIEMFA"})
        public DynamicFloat amount = DynamicFloat.ZERO;

        @SerializedName(
                value = "amountByCasterAttackRatio",
                alternate = {"GMFELAKANEF", "KECHKKGGAPF"})
        public DynamicFloat amountByCasterAttackRatio = DynamicFloat.ZERO;

        @SerializedName(value = "amountByCasterCurrentHPRatio")
        public DynamicFloat amountByCasterCurrentHPRatio = DynamicFloat.ZERO;

        public DynamicFloat amountByCasterCurrentHPDebtsRatio = DynamicFloat.ZERO;

        @SerializedName(
                value = "amountByCasterMaxHPRatio",
                alternate = {"PKPBLCNMPIG", "HFNJHOGGFKB", "GEJGGCIOLKN", "MEEACMKOLJF", "OLMENJCGGJJ","EJGDELKANMO", "ECOAIFKENNM", "GJBFAJMJFOP"})
        public DynamicFloat amountByCasterMaxHPRatio = DynamicFloat.ZERO;

        public DynamicFloat amountByGetDamage = DynamicFloat.ZERO;

        public DynamicFloat amountByCasterDefRatio = DynamicFloat.ZERO;

        @SerializedName(value = "amountByTargetCurrentHPRatio", alternate = "BJEKIJMNDAA")
        public DynamicFloat amountByTargetCurrentHPRatio = DynamicFloat.ZERO;

        @SerializedName(value = "amountByTargetMaxHPRatio", alternate = {"GGLMMJHNGMO", "FPOCDLCHDPE"})
        public DynamicFloat amountByTargetMaxHPRatio = DynamicFloat.ZERO;

        public DynamicFloat limboByTargetMaxHPRatio = DynamicFloat.ZERO;

        public DynamicFloat healRatio = DynamicFloat.ONE;
        public DynamicFloat speed = DynamicFloat.ONE;

        @SerializedName(value = "ignoreAbilityProperty", alternate = "HHFGADCJJDI")
        public boolean ignoreAbilityProperty;

        public String modifierName;
        public String subfieldName;

        public boolean enableLockHP;
        public boolean disableWhenLoading;
        public boolean doOffStage;
        public transient JsonElement rawDoOffStageConfig;

        /** Empty export placeholders use the default flag; other unknown shapes stay dormant. */
        public boolean hasSupportedOffStageConfig() {
            return rawDoOffStageConfig == null || rawDoOffStageConfig.isJsonNull()
                    || rawDoOffStageConfig.isJsonArray() && rawDoOffStageConfig.getAsJsonArray().size() == 0;
        }
        public boolean lethal = true;

        public boolean muteHealEffect = false;

        public boolean byServer;
        public boolean lifeByOwnerIsAlive;
        public String campTargetType;
        public int campID;
        public int gadgetID;
        public boolean ownerIsTarget;

        /** Keep the full exported birth rule, including types the server cannot yet calculate. */
        public JsonElement born;
        public JsonElement setTarget;
        public JsonElement range;
        @SerializedName("KGHOMBBGBDI")
        public JsonElement rawReviveSkillControl;

        /** KillGadget names its target here rather than in a flat gadgetID. */
        public GadgetInfo gadgetInfo;

        public boolean isFromOwner;
        @SerializedName(value = "healTag", alternate = {"FFNEJGGNAFF", "__exp_healTag"})
        public String healTag;
        public String key;
        public String abilityName;
        public String globalValueKey;
        public String abilityFormula;
        public String srcTarget, dstTarget;
        public String srcKey, dstKey;
        @SerializedName(value = "targetPredicates", alternate = "KNABHPHLAAB")
        public List<Map<String, Object>> targetPredicates;

        public Map<String, Object> otherTargets;

        /** How often a Randomed block takes its success branch, from 0 to 1. */
        public DynamicFloat chance = DynamicFloat.ZERO;

        public DynamicFloat minValue = DynamicFloat.ZERO;
        public DynamicFloat maxValue = DynamicFloat.ZERO;
        public DynamicFloat targetValue = DynamicFloat.ZERO;
        public DynamicFloat costStaminaRatio = DynamicFloat.ZERO;
        // The exported operand is a use multiplier in AvatarSkillStart and a delta ratio in
        // ModifyAvatarSkillCD. Keep the canonical fields separate from the export alias.
        public DynamicFloat cdTimeRatio;
        @SerializedName("BGNJBEPEJBO")
        public DynamicFloat legacyCdRatio;
        public DynamicFloat cdRatio;
        public DynamicFloat cdDelta;
        public com.google.gson.JsonElement cdMin;
        @SerializedName("FBGLBGFMOHC")
        public com.google.gson.JsonElement absoluteCdTime;
        public int[] skillSlot;
        public emu.grasscutter.game.ability.AbilityPlayMode.Type fromPlayMode =
                emu.grasscutter.game.ability.AbilityPlayMode.Type.Normal;
        public emu.grasscutter.game.ability.AbilityPlayMode.Type toPlayMode =
                emu.grasscutter.game.ability.AbilityPlayMode.Type.Normal;
        public DynamicFloat fightProp = DynamicFloat.ZERO;
        // Keep both spellings: canonical source selection must not depend on JSON member order.
        public String fightPropSourceTarget;
        @SerializedName("NKJNADEMKOG")
        public String legacyFightPropSourceTarget;
        @SerializedName("AAFLDOBAJJJ")
        public String fightPropAggregation;
        @SerializedName("LIMIEDCFNAM")
        public String legacyFightPropAggregation;

        public String fightPropertySourceTarget() {
            if (fightPropSourceTarget != null) return fightPropSourceTarget;
            return legacyFightPropSourceTarget == null ? "Caster" : legacyFightPropSourceTarget;
        }

        public String fightPropertyAggregation() {
            if (fightPropAggregation != null && legacyFightPropAggregation != null
                    && !fightPropAggregation.equals(legacyFightPropAggregation)) return "<conflicting>";
            return fightPropAggregation == null ? legacyFightPropAggregation : fightPropAggregation;
        }
        public boolean useLimitRange;
        public boolean randomInRange;

        public int skillID;
        public int shareCDID;
        @JsonAdapter(RandomCountAdapter.class)
        public Integer shareCDType = 0;
        public int resistanceListID;
        public int monsterID;
        @SerializedName(value = "summonTag", alternate = "PCLFAKBGHCI")
        public int summonTag;

        public AbilityModifierAction[] actions;
        @SerializedName(value = "repeatTimes", alternate = "GFJCFFFEDCF")
        public DynamicFloat repeatTimes = DynamicFloat.ZERO;

        @SerializedName(value = "randomCount", alternate = "GJKEHHFNGLP")
        @JsonAdapter(RandomCountAdapter.class)
        public Integer randomCount = 0;
        @SerializedName(value = "randomQueue", alternate = "AHHFBGACEJF")
        public RandomActionGroup[] randomQueue;

        @SerializedName(value = "valueSteps", alternate = "ACDIBKKMCCK")
        public DynamicFloat[] valueSteps;
        @SerializedName(value = "globalValueProvider", alternate = "FKPKBNOINEG")
        public JsonElement globalValueProvider;
        @SerializedName(value = "actionSteps", alternate = "NGHBADANOHL")
        public AbilityModifierAction[][] actionSteps;
        @SerializedName(value = "defaultActions", alternate = "IAMAGJDLMAI")
        public AbilityModifierAction[] defaultActions;

        @SerializedName(value = "tpsAmmoBoxGroups", alternate = "DJGANAOKMHA")
        public TpsAmmoBoxGroup[] tpsAmmoBoxGroups;
        @SerializedName(value = "tpsAmmoBoxTotal", alternate = "OCAMECDEBDO")
        public DynamicFloat tpsAmmoBoxTotal;
        @SerializedName(value = "tpsAmmoBoxAdaptiveKey", alternate = "JACHOCKLPFO")
        public String tpsAmmoBoxAdaptiveKey;

        public static class TpsAmmoBoxGroup implements Serializable {
            @SerializedName(value = "key", alternate = "ADGLOMNJIJB")
            public String key;
            @SerializedName(value = "actionList", alternate = "DKIALEELBPE")
            public AbilityModifierAction[] actionList;
            @SerializedName(value = "weaponType", alternate = "JIMCDBEIAEN")
            public String weaponType;
        }

        @JsonAdapter(AbilityActionAdapter.class)
        public static class RandomActionGroup implements Serializable {
            @SerializedName(value = "randomPredicates", alternate = "EBOIAJJCFAG")
            public List<Map<String,Object>> randomPredicates;
            @SerializedName(value = "actionQueue", alternate = "FMBINFHJBGG")
            public AbilityModifierAction[] actionQueue;
        }

        /** The native count is an integer; malformed numbers must not truncate into one. */
        public static class RandomCountAdapter extends TypeAdapter<Integer> {
            @Override public Integer read(JsonReader reader) throws IOException {
                if (reader.peek()!=JsonToken.NUMBER) { reader.skipValue(); return null; }
                var literal=reader.nextString();
                try { return new java.math.BigDecimal(literal).intValueExact(); }
                catch (ArithmeticException | NumberFormatException invalid) { return null; }
            }
            @Override public void write(JsonWriter writer,Integer count) throws IOException { writer.value(count); }
        }

        /** Which gadget an action is about, where it does not carry a flat gadgetID. */
        public static class GadgetInfo {
            public int configID;
        }

        @SerializedName(value = "successActions", alternate = "NCEBCLBNOFG")
        public AbilityModifierAction[] successActions;

        @SerializedName(value = "failActions", alternate = "JINAOHPNJKC")
        public AbilityModifierAction[] failActions;

        public DropType dropType = DropType.LevelControl;
        public DynamicFloat baseEnergy;
        public DynamicFloat ratio = DynamicFloat.ONE;

        /** Explicit writes may coexist with a ratio, including a deliberately zero ratio. */
        public DynamicFloat value;

        /**
         * The value an action writes, as opposed to the one it multiplies by.
         *
         * <p>An absent write means zero; an absent multiplier still means one. The adapter retains
         * legacy value-only configs for ratio consumers without discarding an explicit ratio.
         */
        public DynamicFloat writtenValue() {
            return this.value != null ? this.value
                    : this.ratio == DynamicFloat.ONE ? DynamicFloat.ZERO : this.ratio;
        }
        public String determineType;
        public String costType;
        /** Fuel pool selection in ChangePhlogiston resource exports, separate from action target. */
        public String targetType;
        @SerializedName("CBOMLBFIPJM")
        public DynamicFloat rawPhlogistonValue;
        @SerializedName("DHDDHBBIHEJ")
        public String rawPhlogistonCostType;
        public Boolean useRatio;
        @SerializedName("JDIMELLBMFO")
        public Boolean rawPhlogistonUseRatio;
        public boolean phlogistonUsesRatio() {
            return useRatio != null ? useRatio : Boolean.TRUE.equals(rawPhlogistonUseRatio);
        }
        public int configID;

        @SerializedName(value = "tpsSupplyAmount", alternate = "EMNMAKMAKLM")
        public DynamicFloat tpsSupplyAmount;
        @SerializedName(value = "tpsSupplyAmmunitionId", alternate = "NHCCEJKBOPB")
        public int tpsSupplyAmmunitionId;
        @SerializedName(value = "tpsSupplyUseExplicitAmmoId", alternate = "FMGHPKANDIA")
        public Boolean tpsSupplyUseExplicitAmmoId;
        @SerializedName(value = "tpsSupplyUseBonus", alternate = "DBPIABFDELD")
        public boolean tpsSupplyUseBonus;

        public DynamicFloat valueRangeMin;
        public DynamicFloat valueRangeMax;
        public String overrideMapKey;

        public int paramNum;
        public DynamicFloat param1 = DynamicFloat.ZERO,
                param2 = DynamicFloat.ZERO,
                param3 = DynamicFloat.ZERO;

        public String funcName;
        public LuaCallType luaCallType;

        @SerializedName("CallParamList")
        public int[] callParamList;

        public String content;

        public enum LuaCallType {
            Gadget,
            @SerializedName(value = "OwnerGadegt", alternate = "OwnerGadget")
            OwnerGadget,
            FromGroup,
            OwnerFromGroup,
            SpecificGroup,
            CurScenePlay,
            CurChallengeGroup,
            CurRogueBossGroup,
            CurGalleryControlGroup,
            AbilityGroupSourceGroup,
            LevelBankZoneContainsGroup
        }

        public enum DropType {
            LevelControl,
            BigWorldOnly,
            ForceDrop
        }
    }

    @JsonAdapter(AbilityModifierPropertyAdapter.class)
    public static class AbilityModifierProperty implements Serializable {
        // Usually a constant, but a config may also name an ability special to read it from.
        public DynamicFloat Actor_HpThresholdRatio = DynamicFloat.ZERO;
        public DynamicFloat Actor_MaxHPRatio = DynamicFloat.ZERO;
        private final Map<String, DynamicFloat> values = new LinkedHashMap<>();

        /** Keep the legacy HP fields writable while retaining other resource properties. */
        public Map<String, DynamicFloat> getValues() {
            var result = new LinkedHashMap<>(values);
            result.put("Actor_HpThresholdRatio", Actor_HpThresholdRatio);
            result.put("Actor_MaxHPRatio", Actor_MaxHPRatio);
            return result;
        }
    }

    public static class AbilityModifierPropertyAdapter extends TypeAdapter<AbilityModifierProperty> {
        private final DynamicFloatAdapter floats = new DynamicFloatAdapter();

        @Override public AbilityModifierProperty read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            var result = new AbilityModifierProperty();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                DynamicFloat value;
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    value = null;
                } else value = floats.read(reader);
                switch (key) {
                    case "Actor_HpThresholdRatio" -> result.Actor_HpThresholdRatio = value;
                    case "Actor_MaxHPRatio" -> result.Actor_MaxHPRatio = value;
                    default -> result.values.put(key, value);
                }
            }
            reader.endObject();
            return result;
        }

        @Override public void write(JsonWriter writer, AbilityModifierProperty value) throws IOException {
            if (value == null) {
                writer.nullValue();
                return;
            }
            writer.beginObject();
            for (var entry : value.getValues().entrySet()) {
                writer.name(entry.getKey());
                floats.write(writer, entry.getValue());
            }
            writer.endObject();
        }
    }

    public enum State {
            LockHP,
            Invincible,
            ElementFreeze,
            ElementPetrifaction,
            DenyLockOn,
            Limbo,
            NoHeal,
            IgnoreAddEnergy,
            IsGhostToEnemy,
            IsGhostToAllied,
            UnlockFrequencyLimit,
            AttackUp,
            DefenseDown,
            ElementDeadTime,
            SpeedUp,
            DefenseUp,
            Struggle,
            OvergrowVariation,
            ElementElectric,
            ElementFire,
            NyxState,
            ElementBurning,
            ElementShock,
            ElementWet,
            ElementIce,
            ElementFrozen,
            ElementRock,
            ElementWind,
            ElementGrass,
            ElementOverdose,
            SpeedDown,
            MuteTaunt
    }

    static class NullSafeStringAdapter extends TypeAdapter<String> {
        @Override
        public void write(JsonWriter out, String value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value);
        }

        @Override
        public String read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.STRING) return in.nextString();
            in.skipValue();
            return null;
        }
    }
}

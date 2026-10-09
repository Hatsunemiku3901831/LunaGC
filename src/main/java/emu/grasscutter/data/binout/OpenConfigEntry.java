package emu.grasscutter.data.binout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import emu.grasscutter.data.ResourceLoader.OpenConfigData;

import java.util.*;

public class OpenConfigEntry {
    private final String name;
    private String[] addAbilities;
    private int extraTalentIndex;
    private ExtraTalentModifier[] extraTalentModifiers;
    private SkillPointModifier[] skillPointModifiers;
    private SkillCooldownModifier[] skillCooldownModifiers;
    private AbilityVarSetter[] abilityVarSetters;
    private TalentParamEntry[] talentParamEntries;

    public OpenConfigEntry(String name, OpenConfigData[] data) {
        this.name = name;

        List<String> abilityList = new ArrayList<>();
        List<ExtraTalentModifier> levelList = new ArrayList<>();
        List<SkillPointModifier> modList = new ArrayList<>();
        List<SkillCooldownModifier> cooldownList = new ArrayList<>();
        List<AbilityVarSetter> varList = new ArrayList<>();
        List<TalentParamEntry> paramList = new ArrayList<>();

        for (OpenConfigData entry : data == null ? new OpenConfigData[0] : data) {
            if (entry == null || entry.$type == null) continue;
            if (entry.$type.equals("AddAbility")
                    || entry.$type.equals("CLMGJOKFOOB")
                    || entry.$type.equals("PHNIGFHBFMD")) {
                if (entry.abilityName != null) abilityList.add(entry.abilityName);
            } else if (entry.$type.equals("AddTalentExtraLevel")
                    || entry.$type.equals("DPACHKADLMK")) {
                if ((entry.talentType == null || entry.talentType.equals("AvatarSkill"))
                        && validTalentIndex(entry.talentIndex)
                        && entry.extraLevel != null
                        && entry.extraLevel > 0) {
                    levelList.add(new ExtraTalentModifier(entry.talentIndex, entry.extraLevel));
                    this.extraTalentIndex = entry.talentIndex;
                }
            } else if (entry.$type.equals("ModifySkillPoint")) {
                if (entry.skillID > 0 && entry.pointDelta != 0)
                    modList.add(new SkillPointModifier(entry.skillID, entry.pointDelta));
            } else if (entry.$type.equals("ModifySkillCD")) {
                var delta = TalentValue.parse(entry.cdDelta);
                var ratio = TalentValue.parse(entry.cdRatio);
                if (entry.skillID > 0 && delta != null && ratio != null)
                    cooldownList.add(
                            new SkillCooldownModifier(
                                    entry.skillID, delta, ratio, entry.talentFilter));
            } else if (entry.$type.equals("ModifyAbility") || entry.$type.equals("GMOELNAHCOH")) {
                if (entry.abilityName != null
                        && entry.varName != null
                        && (entry.varValue != null || entry.paramRatio != null)) {
                    var delta = TalentValue.parse(entry.varValue);
                    var ratio = TalentValue.parse(entry.paramRatio);
                    if (delta != null && ratio != null)
                        varList.add(
                                new AbilityVarSetter(
                                        entry.abilityName,
                                        entry.varName,
                                        delta,
                                        ratio,
                                        entry.talentFilter));
                }
            } else if (entry.$type.equals("NPLMAPHOLOF") || entry.$type.equals("SetAbilityVar")) {

                if (entry.abilityName != null && entry.varName != null && entry.varValue != null) {
                    var setter = parseSetter(entry.abilityName, entry.varName, entry.varValue);
                    if (setter != null) varList.add(setter);
                }
            } else if (entry.$type.equals("UnlockTalentParam")
                    || entry.$type.equals("JNFAEBAAPDM")
                    || entry.$type.equals("CMGFNDNMFFO")) {
                if (entry.abilityName != null && entry.talentParam != null) {
                    paramList.add(new TalentParamEntry(entry.abilityName, entry.talentParam));
                }
            }
        }

        if (!abilityList.isEmpty()) this.addAbilities = abilityList.toArray(new String[0]);
        this.extraTalentModifiers = levelList.toArray(new ExtraTalentModifier[0]);
        this.skillCooldownModifiers = cooldownList.toArray(new SkillCooldownModifier[0]);
        if (!modList.isEmpty())
            this.skillPointModifiers = modList.toArray(new SkillPointModifier[0]);
        if (!varList.isEmpty()) this.abilityVarSetters = varList.toArray(new AbilityVarSetter[0]);
        if (!paramList.isEmpty())
            this.talentParamEntries = paramList.toArray(new TalentParamEntry[0]);
    }

    private static AbilityVarSetter parseSetter(
            String abilityName, String varName, JsonElement el) {
        try {
            // Some exports wrap a parameter reference in a single field. Metadata is not a value.
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                JsonElement candidate = null;
                for (var e : obj.entrySet()) {
                    if (e.getKey().equals("$type")) continue;
                    if (candidate != null) return null;
                    candidate = e.getValue();
                }
                el = candidate;
            }
            if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString())
                return null;
            String val = el.getAsString();
            float multiplier = val.startsWith("-%") ? -1f : 1f;
            String reference = multiplier < 0 ? val.substring(1) : val;
            if (reference.matches("%[1-9][0-9]*")) {
                int index = Integer.parseInt(reference.substring(1)) - 1;
                return new AbilityVarSetter(abilityName, varName, index, multiplier);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String[] getAddAbilities() {
        return addAbilities;
    }

    public int getExtraTalentIndex() {
        return extraTalentIndex;
    }

    private static boolean validTalentIndex(int index) {
        return index == 1 || index == 2 || index == 9;
    }

    public ExtraTalentModifier[] getExtraTalentModifiers() {
        if (extraTalentModifiers != null) return extraTalentModifiers;
        // Older compiled caches recorded only the last constellation slot, always worth three.
        return validTalentIndex(extraTalentIndex)
                ? new ExtraTalentModifier[] {new ExtraTalentModifier(extraTalentIndex, 3)}
                : new ExtraTalentModifier[0];
    }

    public SkillPointModifier[] getSkillPointModifiers() {
        return skillPointModifiers;
    }

    public SkillCooldownModifier[] getSkillCooldownModifiers() {
        return skillCooldownModifiers == null
                ? new SkillCooldownModifier[0]
                : skillCooldownModifiers;
    }

    public static final class SkillCooldownModifier {
        private final int skillId;
        private final TalentValue delta;
        private final TalentValue ratio;
        private final String talentFilter;

        private SkillCooldownModifier(
                int skillId, TalentValue delta, TalentValue ratio, String talentFilter) {
            this.skillId = skillId;
            this.delta = delta;
            this.ratio = ratio;
            this.talentFilter = talentFilter;
        }

        public int skillId() {
            return skillId;
        }

        public boolean isUnconditional() {
            return talentFilter == null || talentFilter.equals("All");
        }

        public Float delta(float[] parameters) {
            return delta == null ? null : delta.resolve(parameters);
        }

        public Float ratio(float[] parameters) {
            return ratio == null ? null : ratio.resolve(parameters);
        }
    }

    public AbilityVarSetter[] getAbilityVarSetters() {
        return abilityVarSetters;
    }

    public TalentParamEntry[] getTalentParamEntries() {
        return talentParamEntries;
    }

    public static final class ExtraTalentModifier {
        private final int talentIndex;
        private final int extraLevel;

        public ExtraTalentModifier(int talentIndex, int extraLevel) {
            this.talentIndex = talentIndex;
            this.extraLevel = extraLevel;
        }

        public int talentIndex() {
            return talentIndex;
        }

        public int extraLevel() {
            return extraLevel;
        }
    }

    public static class SkillPointModifier {
        private final int skillId;
        private final int delta;

        public SkillPointModifier(int skillId, int delta) {
            this.skillId = skillId;
            this.delta = delta;
        }

        public int getSkillId() {
            return skillId;
        }

        public int getDelta() {
            return delta;
        }
    }

    public static class AbilityVarSetter {
        private final String abilityName;
        private final String varName;
        private final int paramIndex;
        private final float multiplier;
        private final boolean modifyAbility;
        private final TalentValue delta;
        private final TalentValue ratio;
        private final String talentFilter;

        public AbilityVarSetter(String abilityName, String varName, int paramIndex) {
            this(abilityName, varName, paramIndex, 1f);
        }

        public AbilityVarSetter(
                String abilityName, String varName, int paramIndex, float multiplier) {
            this.abilityName = abilityName;
            this.varName = varName;
            this.paramIndex = paramIndex;
            this.multiplier = multiplier;
            this.modifyAbility = false;
            this.delta = null;
            this.ratio = null;
            this.talentFilter = null;
        }

        private AbilityVarSetter(
                String abilityName,
                String varName,
                TalentValue delta,
                TalentValue ratio,
                String talentFilter) {
            this.abilityName = abilityName;
            this.varName = varName;
            this.paramIndex = delta.paramIndex;
            this.multiplier = delta.multiplier;
            this.modifyAbility = true;
            this.delta = delta;
            this.ratio = ratio;
            this.talentFilter = talentFilter;
        }

        public boolean isModifyAbility() {
            return modifyAbility;
        }

        public boolean isUnconditional() {
            // Filtered talents require their activation tags; do not activate them unconditionally.
            return talentFilter == null || talentFilter.equals("All");
        }

        public Float resolveRatio(float[] parameters) {
            if (ratio == null) return 0f;
            return ratio.resolve(parameters);
        }

        public String getAbilityName() {
            return abilityName;
        }

        public String getVarName() {
            return varName;
        }

        public int getParamIndex() {
            return paramIndex;
        }

        public float getMultiplier() {
            // Existing generated OpenConfig.json caches do not contain this new field.
            return multiplier == 0f ? 1f : multiplier;
        }

        /** A missing or malformed parameter must not overwrite an ability's resource default. */
        public Float resolve(float[] parameters) {
            if (modifyAbility) return delta == null ? null : delta.resolve(parameters);
            if (parameters == null || paramIndex < 0 || paramIndex >= parameters.length)
                return null;
            float value = parameters[paramIndex] * getMultiplier();
            return Float.isFinite(value) ? value : null;
        }
    }

    /** A talent operand is a finite number or a signed, one-based parameter reference. */
    private static final class TalentValue {
        private final boolean reference;
        private final int paramIndex;
        private final float multiplier;
        private final float constant;

        private TalentValue(boolean reference, int paramIndex, float multiplier, float constant) {
            this.reference = reference;
            this.paramIndex = paramIndex;
            this.multiplier = multiplier;
            this.constant = constant;
        }

        private static TalentValue parse(JsonElement value) {
            if (value == null || value.isJsonNull()) return new TalentValue(false, -1, 1f, 0f);
            try {
                if (value.isJsonObject()) {
                    JsonElement operand = null;
                    for (var entry : value.getAsJsonObject().entrySet()) {
                        if (entry.getKey().equals("$type")) continue;
                        if (operand != null) return null;
                        operand = entry.getValue();
                    }
                    if (operand == null || !operand.isJsonPrimitive()) return null;
                    value = operand;
                }
                if (!value.isJsonPrimitive()) return null;
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    float constant = primitive.getAsFloat();
                    return Float.isFinite(constant)
                            ? new TalentValue(false, -1, 1f, constant)
                            : null;
                }
                if (!primitive.isString()) return null;
                String text = primitive.getAsString();
                float multiplier = text.startsWith("-%") ? -1f : 1f;
                String index = multiplier < 0f ? text.substring(1) : text;
                if (!index.matches("%[1-9][0-9]*")) return null;
                return new TalentValue(
                        true, Integer.parseInt(index.substring(1)) - 1, multiplier, 0f);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private Float resolve(float[] parameters) {
            if (!reference) return Float.isFinite(constant) ? constant : null;
            if (parameters == null || paramIndex < 0 || paramIndex >= parameters.length)
                return null;
            float value = parameters[paramIndex] * multiplier;
            return Float.isFinite(value) ? value : null;
        }
    }

    public static class TalentParamEntry {
        private final String abilityName;
        private final String paramName;

        public TalentParamEntry(String abilityName, String paramName) {
            this.abilityName = abilityName;
            this.paramName = paramName;
        }

        public String getAbilityName() {
            return abilityName;
        }

        public String getParamName() {
            return paramName;
        }
    }
}

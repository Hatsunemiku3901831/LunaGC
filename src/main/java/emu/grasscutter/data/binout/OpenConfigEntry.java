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
    private AbilityVarSetter[] abilityVarSetters;
    private TalentParamEntry[] talentParamEntries;

    public OpenConfigEntry(String name, OpenConfigData[] data) {
        this.name = name;

        List<String> abilityList = new ArrayList<>();
        List<ExtraTalentModifier> levelList = new ArrayList<>();
        List<SkillPointModifier> modList = new ArrayList<>();
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
            } else if (entry.$type.equals("NPLMAPHOLOF")
                    || entry.$type.equals("SetAbilityVar")
                    || entry.$type.equals("ModifyAbility")
                    || entry.$type.equals("GMOELNAHCOH")) {

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

        public AbilityVarSetter(String abilityName, String varName, int paramIndex) {
            this(abilityName, varName, paramIndex, 1f);
        }

        public AbilityVarSetter(
                String abilityName, String varName, int paramIndex, float multiplier) {
            this.abilityName = abilityName;
            this.varName = varName;
            this.paramIndex = paramIndex;
            this.multiplier = multiplier;
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
            if (parameters == null || paramIndex < 0 || paramIndex >= parameters.length)
                return null;
            float value = parameters[paramIndex] * getMultiplier();
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

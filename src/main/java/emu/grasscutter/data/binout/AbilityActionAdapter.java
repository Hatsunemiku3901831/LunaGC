package emu.grasscutter.data.binout;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/** Preserve value-only legacy ratios while keeping separate value and ratio fields intact. */
public final class AbilityActionAdapter implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        var delegate = gson.getDelegateAdapter(this, type);
        var treeAdapter = gson.getAdapter(JsonElement.class);
        return new TypeAdapter<T>() {
            @Override
            public T read(JsonReader reader) throws IOException {
                var tree = treeAdapter.read(reader);
                JsonElement rawOffStage = null;
                if (tree.isJsonObject()) {
                    tree = tree.deepCopy();
                    var object = tree.getAsJsonObject();
                    if (AbilityModifier.AbilityModifierAction.class.isAssignableFrom(
                            type.getRawType())) {
                        var flag = object.get("doOffStage");
                        if (flag != null
                                && !(flag.isJsonPrimitive()
                                        && flag.getAsJsonPrimitive().isBoolean()))
                            rawOffStage = object.remove("doOffStage");
                    }
                    for (var name : new String[] {"value", "ratio", "repeatTimes"}) {
                        if (object.has(name) && object.get(name).isJsonNull()) object.remove(name);
                    }
                    if (object.has("value") && !object.has("ratio")) {
                        object.add("ratio", object.get("value"));
                    }
                    for (var names :
                            new String[][] {
                                {"repeatTimes", "GFJCFFFEDCF"},
                                {"randomCount", "GJKEHHFNGLP"},
                                {"randomQueue", "AHHFBGACEJF"},
                                {"randomPredicates", "EBOIAJJCFAG"},
                                {"actionQueue", "FMBINFHJBGG"}
                            }) if (object.has(names[0])) object.remove(names[1]);
                    var actionType = object.get("$type");
                    if (actionType != null
                            && actionType.isJsonPrimitive()
                            && java.util.Set.of(
                                            "AvatarShareCDSkillStart", "ActivitySkillShareCDStart")
                                    .contains(actionType.getAsString())) {
                        normalize(object, "skillID", "GCOFBECOEFF", "GIBAOPPLKJD");
                        normalize(object, "shareCDType", "ABLJLEAMAGL", "DMGPMFKKGMJ");
                    }
                    if (actionType != null
                            && actionType.isJsonPrimitive()
                            && "ChangePlayMode".equals(actionType.getAsString())) {
                        normalize(object, "fromPlayMode", "FPFLACICBBN", "DLEICBJFEKN");
                        normalize(object, "toPlayMode", "JGNOHILKBNI");
                    }
                }
                T value = delegate.fromJsonTree(tree);
                if (value instanceof AbilityModifier.AbilityModifierAction action)
                    action.rawDoOffStageConfig = rawOffStage;
                return value;
            }

            @Override
            public void write(JsonWriter writer, T value) throws IOException {
                var tree = delegate.toJsonTree(value);
                if (value instanceof AbilityModifier.AbilityModifierAction action
                        && action.rawDoOffStageConfig != null
                        && !action.doOffStage)
                    tree.getAsJsonObject().add("doOffStage", action.rawDoOffStageConfig.deepCopy());
                if (value instanceof AbilityModifier.AbilityModifierAction action
                        && action.value == null
                        && action.ratio == emu.grasscutter.data.common.DynamicFloat.ONE) {
                    tree.getAsJsonObject().remove("ratio");
                }
                if (value instanceof AbilityModifier.AbilityModifierAction action
                        && action.rawDoOffStageConfig != null
                        && action.rawDoOffStageConfig.isJsonNull()
                        && !action.doOffStage) {
                    writer.beginObject();
                    for (var member : tree.getAsJsonObject().entrySet()) {
                        boolean previous = writer.getSerializeNulls();
                        if ("doOffStage".equals(member.getKey())) writer.setSerializeNulls(true);
                        try {
                            writer.name(member.getKey());
                            treeAdapter.write(writer, member.getValue());
                        } finally {
                            writer.setSerializeNulls(previous);
                        }
                    }
                    writer.endObject();
                } else treeAdapter.write(writer, tree);
            }
        }.nullSafe();
    }

    private static void normalize(JsonObject object, String canonical, String... aliases) {
        if (object.has(canonical)) return;
        JsonElement value = null;
        for (var alias : aliases) {
            if (!object.has(alias)) continue;
            var next = object.get(alias);
            if (value != null && !value.equals(next)) {
                object.add(canonical, JsonNull.INSTANCE);
                return;
            }
            value = next;
        }
        if (value != null) object.add(canonical, value);
    }
}

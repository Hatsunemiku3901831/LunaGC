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
            @Override public T read(JsonReader reader) throws IOException {
                var tree = treeAdapter.read(reader);
                if (tree.isJsonObject()) {
                    var object = tree.getAsJsonObject();
                    for (var name : new String[]{"value", "ratio", "repeatTimes"}) {
                        if (object.has(name) && object.get(name).isJsonNull()) object.remove(name);
                    }
                    if (object.has("value") && !object.has("ratio")) {
                        object.add("ratio", object.get("value"));
                    }
                    for (var names : new String[][]{{"repeatTimes","GFJCFFFEDCF"},
                            {"randomCount","GJKEHHFNGLP"},{"randomQueue","AHHFBGACEJF"},
                            {"randomPredicates","EBOIAJJCFAG"},{"actionQueue","FMBINFHJBGG"}})
                        if (object.has(names[0])) object.remove(names[1]);
                    var actionType = object.get("$type");
                    if (actionType != null && actionType.isJsonPrimitive()
                            && java.util.Set.of("AvatarShareCDSkillStart", "ActivitySkillShareCDStart")
                                .contains(actionType.getAsString())) {
                        normalize(object, "skillID", "GCOFBECOEFF", "GIBAOPPLKJD");
                        normalize(object, "shareCDType", "ABLJLEAMAGL", "DMGPMFKKGMJ");
                    }
                    if (actionType != null && actionType.isJsonPrimitive()
                            && "ChangePlayMode".equals(actionType.getAsString())) {
                        normalize(object, "fromPlayMode", "FPFLACICBBN", "DLEICBJFEKN");
                        normalize(object, "toPlayMode", "JGNOHILKBNI");
                    }
                }
                return delegate.fromJsonTree(tree);
            }
            @Override public void write(JsonWriter writer, T value) throws IOException {
                var tree = delegate.toJsonTree(value);
                if (value instanceof AbilityModifier.AbilityModifierAction action
                        && action.value == null && action.ratio == emu.grasscutter.data.common.DynamicFloat.ONE) {
                    tree.getAsJsonObject().remove("ratio");
                }
                treeAdapter.write(writer, tree);
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

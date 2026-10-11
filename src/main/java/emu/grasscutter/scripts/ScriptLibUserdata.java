package emu.grasscutter.scripts;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/** Validate Lua arrays before Java numeric coercion, while preserving ordinary library access. */
final class ScriptLibUserdata extends LuaUserdata {
    private final LuaValue delegate;
    private final LuaValue addTeamValue = teamValueFunction(true);
    private final LuaValue setTeamValue = teamValueFunction(false);
    private final java.util.Map<String, LuaValue> sceneBattleFunctions =
            ScenePlayBattleLua.functions();

    private static LuaValue teamValueFunction(boolean add) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                var receiver = args.arg1().touserdata(ScriptLib.class);
                var list = args.arg(2);
                var key = args.arg(3);
                var amount = args.arg(4);
                if (!(receiver instanceof ScriptLib library)
                        || !list.istable()
                        || key.type() != LuaValue.TSTRING
                        || amount.type() != LuaValue.TNUMBER) return LuaValue.valueOf(-1);
                var table = list.checktable();
                int size = table.length();
                if (table.keys().length != size) return LuaValue.valueOf(-1);
                int[] uids = new int[size];
                for (int i = 0; i < size; i++) {
                    var value = table.rawget(i + 1);
                    double uid = value.todouble();
                    if (value.type() != LuaValue.TNUMBER
                            || !Double.isFinite(uid)
                            || uid <= 0
                            || uid > Integer.MAX_VALUE
                            || uid != Math.rint(uid)) return LuaValue.valueOf(-1);
                    uids[i] = (int) uid;
                }
                return LuaValue.valueOf(
                        add
                                ? library.AddTeamEntityGlobalFloatValue(
                                        uids, key.tojstring(), amount.todouble())
                                : library.SetTeamEntityGlobalFloatValue(
                                        uids, key.tojstring(), amount.todouble()));
            }
        };
    }

    private final LuaValue initTimeAxis =
            new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    var receiver = args.arg1().touserdata(ScriptLib.class);
                    var identifier = args.arg(2);
                    var points = args.arg(3);
                    if (!(receiver instanceof ScriptLib library)
                            || !identifier.isstring()
                            || !points.istable()) return LuaValue.valueOf(-1);
                    return LuaValue.valueOf(
                            library.InitTimeAxis(
                                    identifier.tojstring(),
                                    points.checktable(),
                                    args.arg(4).toboolean()));
                }
            };

    ScriptLibUserdata(ScriptLib library) {
        this(library, CoerceJavaToLua.coerce(library));
    }

    private ScriptLibUserdata(ScriptLib library, LuaValue delegate) {
        super(library, delegate.getmetatable());
        this.delegate = delegate;
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (key.isstring()) {
            var battleFunction = sceneBattleFunctions.get(key.tojstring());
            if (battleFunction != null) return battleFunction;
            switch (key.tojstring()) {
                case "InitTimeAxis":
                    return initTimeAxis;
                case "AddTeamEntityGlobalFloatValue":
                    return addTeamValue;
                case "SetTeamEntityGlobalFloatValue":
                    return setTeamValue;
            }
        }
        return delegate.get(key);
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        delegate.set(key, value);
    }
}

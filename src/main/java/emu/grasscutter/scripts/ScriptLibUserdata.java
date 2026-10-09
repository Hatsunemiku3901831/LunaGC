package emu.grasscutter.scripts;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/** Preserve Java library access while receiving time-axis arrays before LuaJ's numeric coercion. */
final class ScriptLibUserdata extends LuaUserdata {
    private final LuaValue delegate;
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
                                    identifier.tojstring(), points.checktable(), args.arg(4).toboolean()));
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
        return key.isstring() && key.tojstring().equals("InitTimeAxis")
                ? initTimeAxis
                : delegate.get(key);
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        delegate.set(key, value);
    }
}

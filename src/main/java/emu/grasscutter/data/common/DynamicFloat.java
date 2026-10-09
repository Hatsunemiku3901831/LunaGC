package emu.grasscutter.data.common;

import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.ability.Ability;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import lombok.*;

@Getter
public class DynamicFloat {
    public static DynamicFloat ZERO = new DynamicFloat(0f);
    public static DynamicFloat ONE = new DynamicFloat(1f);

    private List<StackOp> ops;
    private boolean dynamic = false;
    private float constant = 0f;

    public DynamicFloat(float constant) {
        this.constant = constant;
    }

    public DynamicFloat(String key) {
        this.dynamic = true;
        this.ops = List.of(new StackOp(key));
    }

    /**
     * A written boolean is just one or zero.
     *
     * <p>This used to hand the String constructor "true", which reads any unrecognised word as the
     * name of a property to look up - so every {@code "value": true} in the ability configs resolved
     * against a map that has no such key and came out as 0. All 429 animator bools that ship with a
     * value were being broadcast false.
     */
    public DynamicFloat(boolean b) {
        this.constant = b ? 1f : 0f;
    }

    public DynamicFloat(List<StackOp> ops) {
        this.dynamic = true;
        this.ops = ops;
    }

    public String toString(boolean nextBoolean) {
        var key = String.valueOf(nextBoolean);
        this.ops = List.of(new StackOp(key));
        return ops.toString();
    }

    public float get() {
        return this.get(new Object2FloatArrayMap<>(), 0);
    }

    public float get(float defaultValue) {
        return this.get(new Object2FloatArrayMap<>(), defaultValue);
    }

    public float get(Ability ability, float defaultValue) {
        return this.get(ability.getAbilitySpecials(), defaultValue);
    }

    public float get(Ability ability) {
        return this.get(ability.getAbilitySpecials(), 0f);
    }

    public int getInt(Ability ability) {
        return (int) this.get(ability.getAbilitySpecials(), 0f);
    }

    public float get(Object2FloatMap<String> props, float defaultValue) {
        float fallback = Float.isFinite(defaultValue) ? defaultValue : 0f;
        return evaluate(props, fallback);
    }

    /** Parameter writes must distinguish invalid expressions from an intentional zero. */
    public OptionalDouble tryGet(Object2FloatMap<String> props) {
        float value = evaluate(props, Float.NaN);
        return Float.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    private float evaluate(Object2FloatMap<String> props, float fallback) {
        if (!this.dynamic) return Float.isFinite(constant) ? constant : fallback;
        if (this.ops == null || this.ops.isEmpty()) return fallback;

        var stack = new FloatArrayList();
        for (var token : this.ops) {
            if (token == null || token.op == null) return fallback;
            float value;
            switch (token.op) {
                case CONSTANT -> value = token.fValue;
                case KEY -> {
                    if (token.sValue == null || props == null) return fallback;
                    value = props.getOrDefault(token.sValue, 0f) * (token.negative ? -1f : 1f);
                }
                case NEXBOOLEAN -> value = token.bValue ? 1f : 0f;
                case ADD, SUB, MUL, DIV -> {
                    if (stack.size() < 2) return fallback;
                    float right = stack.popFloat(), left = stack.popFloat();
                    if (token.op == StackOp.Op.DIV && right == 0f) return fallback;
                    value =
                            switch (token.op) {
                                case ADD -> left + right;
                                case SUB -> left - right;
                                case MUL -> left * right;
                                case DIV -> left / right;
                                default -> fallback;
                            };
                }
                default -> {
                    return fallback;
                }
            }
            if (!Float.isFinite(value)) return fallback;
            stack.push(value);
        }
        return stack.size() == 1 ? stack.getFloat(0) : fallback;
    }

    public float get(ProudSkillData skill) {
        // Construct the map
        return get(skill.getParamListMap(), 0f);
    }

    public float get(ProudSkillData skill, float defaultValue) {
        // Construct the map
        return get(skill.getParamListMap(), defaultValue);
    }

    public static class StackOp {
        public Op op;

        public float fValue;
        public String sValue;
        public boolean bValue;
        public boolean negative = false;

        public StackOp(String s) {
            if (s == null) {
                this.op = Op.KEY;
                return;
            }
            switch (s.toUpperCase(Locale.ROOT)) {
                case "ADD" -> this.op = Op.ADD;
                case "SUB" -> this.op = Op.SUB;
                case "MUL" -> this.op = Op.MUL;
                case "DIV" -> this.op = Op.DIV;
                default -> {
                    if (s.startsWith("%")) {
                        s = s.substring(1);
                    } else if (s.startsWith("-%")) {
                        s = s.substring(2);
                        negative = true;
                    }

                    this.op = Op.KEY;
                    this.sValue = s;
                }
            }
        }

        public StackOp(boolean b) {
            this.op = Op.NEXBOOLEAN;
            this.bValue = Boolean.parseBoolean(String.valueOf(b));
        }

        public StackOp(float f) {
            this.op = Op.CONSTANT;
            this.fValue = f;
        }

        public enum Op {
            CONSTANT,
            KEY,
            ADD,
            SUB,
            MUL,
            DIV,
            NEXBOOLEAN
        }
    }
}

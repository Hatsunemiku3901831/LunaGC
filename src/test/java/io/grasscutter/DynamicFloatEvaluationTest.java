package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionHealHP;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.objects.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DynamicFloatEvaluationTest {
    @Test void strictEvaluationDistinguishesARealZeroFromInvalidArithmeticWithoutChangingFallbacks() {
        var properties=new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<String>();
        assertEquals(0,expression("0").tryGet(properties).orElseThrow());
        assertEquals(0,expression("false").tryGet(properties).orElseThrow());
        var invalid=expression("[1,0,\"DIV\"]");
        assertTrue(invalid.tryGet(properties).isEmpty());
        assertEquals(9,invalid.get(properties,9)); assertEquals(0,invalid.get(properties,Float.NaN));
    }
    @Test void strictEvaluationRejectsNonFiniteOperandsAndPreservesSuccessfulResourceExpressions() {
        var properties=new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<String>();
        properties.put("bad",Float.POSITIVE_INFINITY); properties.put("good",3f);
        assertTrue(expression("\"bad\"").tryGet(properties).isEmpty());
        assertTrue(new emu.grasscutter.data.common.DynamicFloat(Float.NaN).tryGet(properties).isEmpty());
        assertEquals(6,expression("[\"good\",2,\"MUL\"]").tryGet(properties).orElseThrow());
    }
    private static class Row {
        DynamicFloat amount;
        int next;
    }

    private static DynamicFloat expression(String json) {
        return JsonUtils.decode(json, DynamicFloat.class);
    }

    private static Object2FloatMap<String> properties() {
        var values = new Object2FloatOpenHashMap<String>();
        values.put("Power", 12f);
        values.put("1", 7f);
        return values;
    }

    @Test
    void arithmeticPreservesOperandOrderAndSignedReferences() {
        assertEquals(8f, expression("[12,4,\"SUB\"]").get());
        assertEquals(3f, expression("[12,4,\"DIV\"]").get());
        assertEquals(-28f, expression("[\"-%1\",4,\"MUL\"]").get(properties(), 42f));
        assertEquals(6f, expression("[\"Power\",2,\"DIV\"]").get(properties(), 42f));
        assertEquals(13f, expression("[\"Power\",true,\"ADD\"]").get(properties(), 42f));
    }

    @Test
    void missingPropertiesKeepExistingZeroSemantics() {
        assertEquals(0f, expression("\"Missing\"").get(properties(), 42f));
        assertEquals(2f, expression("[\"Missing\",2,\"ADD\"]").get(properties(), 42f));
    }

    @Test
    void binaryOperatorsRejectMissingOperandsWithoutThrowing() {
        for (var op : List.of("ADD", "SUB", "MUL", "DIV")) {
            assertEquals(42f, assertDoesNotThrow(() -> expression("[\"" + op + "\"]").get(42f)));
            assertEquals(42f, assertDoesNotThrow(() -> expression("[2,\"" + op + "\"]").get(42f)));
        }
    }

    @Test
    void extraOperandsCannotSilentlyReturnTheLastValue() {
        assertEquals(42f, expression("[1,2]").get(42f));
        assertEquals(42f, expression("[1,2,3,\"ADD\"]").get(42f));
    }

    @Test
    void emptyExpressionsUseCallerFallback() {
        assertEquals(42f, expression("[]").get(42f));
    }

    @Test
    void divisionByZeroAndNegativeZeroUseCallerFallback() {
        assertEquals(42f, expression("[12,0,\"DIV\"]").get(42f));
        assertEquals(42f, expression("[12,-0.0,\"DIV\"]").get(42f));
        assertEquals(42f, expression("[0,0,\"DIV\"]").get(42f));
        assertEquals(42f, expression("[12,\"Missing\",\"DIV\"]").get(properties(), 42f));
    }

    @Test
    void overflowCannotBecomeAnInfiniteSkillValue() {
        assertEquals(42f, expression("[3.0e38,3.0e38,\"MUL\"]").get(42f));
        assertEquals(42f, expression("[3.0e38,3.0e38,\"ADD\"]").get(42f));
    }

    @Test
    void nonFiniteConstantsAndPropertiesUseCallerFallback() {
        for (float invalid :
                new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(42f, new DynamicFloat(invalid).get(42f));
            var values = properties();
            values.put("Power", invalid);
            assertEquals(42f, expression("\"Power\"").get(values, 42f));
        }
        assertEquals(42f, expression("1e309").get(42f));
    }

    @Test
    void nonFiniteFallbackIsSanitized() {
        assertEquals(0f, expression("[]").get(Float.NaN));
        assertEquals(0f, expression("[]").get(Float.POSITIVE_INFINITY));
    }

    @Test
    void malformedCachedTokensDoNotBreakEvaluation() {
        var missingKind = new DynamicFloat.StackOp(2f);
        missingKind.op = null;
        assertEquals(
                42f,
                assertDoesNotThrow(
                        () -> new DynamicFloat(Arrays.asList((DynamicFloat.StackOp) null)).get(42f)));
        assertEquals(42f, assertDoesNotThrow(() -> new DynamicFloat(List.of(missingKind)).get(42f)));
        assertEquals(
                42f,
                assertDoesNotThrow(() -> new DynamicFloat((List<DynamicFloat.StackOp>) null).get(42f)));
    }

    @Test
    void aNullKeyDoesNotBecomeAValidVariable() {
        assertEquals(42f, assertDoesNotThrow(() -> new DynamicFloat((String) null).get(42f)));
    }

    @Test
    void unknownObjectUsesFallbackAndKeepsReaderAligned() {
        var row = JsonUtils.decode("{\"amount\":{\"unknown\":12},\"next\":7}", Row.class);
        assertEquals(42f, row.amount.get(42f));
        assertEquals(7, row.next);
    }

    @Test
    void proudSkillOverloadUsesItsCallerFallback() {
        var skill = new ProudSkillData();
        assertEquals(42f, expression("[]").get(skill, 42f));
        assertEquals(42f, expression("[\"%1\",0,\"DIV\"]").get(skill, 42f));
    }

    @Test
    void operatorParsingDoesNotDependOnSystemLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(3f, expression("[12,4,\"div\"]").get(42f));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void serializedExpressionsPreserveOperatorsBooleansAndSigns() {
        var original = expression("[\"-%1\",2,\"MUL\",true,\"ADD\"]");
        var restored = expression(JsonUtils.encode(original));
        assertEquals(-13f, restored.get(properties(), 42f));
    }

    @Test
    void malformedHealingExpressionLeavesTheActionUsable() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var ability =
                    new Ability(
                            JsonUtils.decode("{\"abilityName\":\"Test_Healing\"}", AbilityData.class),
                            fixture.entity,
                            fixture.player);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 20f);
            var action = new AbilityModifierAction();
            action.amount = expression("[1,\"MUL\"]");
            assertTrue(
                    assertDoesNotThrow(
                            () -> new ActionHealHP().execute(ability, action, ByteString.EMPTY, fixture.entity)));
            assertEquals(20f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            action.amount = new DynamicFloat(10f);
            assertTrue(new ActionHealHP().execute(ability, action, ByteString.EMPTY, fixture.entity));
            assertEquals(30f, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        }
    }

    @Test
    void shippedPostfixExpressionsRemainCompatible() throws Exception {
        int[] count = {0};
        try (var files = Files.walk(Path.of("resources/BinOutput/Ability"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList())
                checkResourceExpressions(
                        JsonParser.parseString(Files.readString(file)), file.toString(), count);
        }
        assertTrue(
                count[0] >= 7500, "Expected the complete ability resource expression catalog: " + count[0]);
        System.out.println("resource-postfix-expressions=" + count[0]);
    }

    private static boolean operator(JsonElement element) {
        return element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()
                && Set.of("ADD", "SUB", "MUL", "DIV")
                        .contains(element.getAsString().toUpperCase(Locale.ROOT));
    }

    private static void checkResourceExpressions(JsonElement element, String path, int[] count) {
        if (element.isJsonObject()) {
            element
                    .getAsJsonObject()
                    .entrySet()
                    .forEach(e -> checkResourceExpressions(e.getValue(), path + "/" + e.getKey(), count));
        } else if (element.isJsonArray()) {
            var array = element.getAsJsonArray();
            boolean expression = false, scalars = true;
            for (var token : array) {
                expression |= operator(token);
                scalars &= token.isJsonPrimitive();
            }
            if (expression && scalars) {
                var values = new Object2FloatOpenHashMap<String>();
                var stack = new ArrayDeque<Float>();
                float expected = 42f;
                boolean valid = true;
                for (var token : array) {
                    if (operator(token)) {
                        if (stack.size() < 2) {
                            valid = false;
                            break;
                        }
                        float right = stack.pop(), left = stack.pop();
                        float value =
                                switch (token.getAsString().toUpperCase(Locale.ROOT)) {
                                    case "ADD" -> left + right;
                                    case "SUB" -> left - right;
                                    case "MUL" -> left * right;
                                    default -> left / right;
                                };
                        if (!Float.isFinite(value)) {
                            valid = false;
                            break;
                        }
                        stack.push(value);
                    } else if (token.getAsJsonPrimitive().isNumber()) stack.push(token.getAsFloat());
                    else if (token.getAsJsonPrimitive().isBoolean())
                        stack.push(token.getAsBoolean() ? 1f : 0f);
                    else {
                        String key = token.getAsString();
                        boolean negative = key.startsWith("-%");
                        key = key.substring(negative ? 2 : key.startsWith("%") ? 1 : 0);
                        values.put(key, 3f);
                        stack.push(negative ? -3f : 3f);
                    }
                }
                if (valid && stack.size() == 1) expected = stack.pop();
                float actual = DynamicFloatEvaluationTest.expression(array.toString()).get(values, 42f);
                assertEquals(expected, actual, Math.max(1e-5f, Math.abs(expected) * 1e-5f), path);
                count[0]++;
            } else
                for (int i = 0; i < array.size(); i++)
                    checkResourceExpressions(array.get(i), path + "/" + i, count);
        }
    }
}

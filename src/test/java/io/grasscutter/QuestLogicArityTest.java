package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.game.quest.enums.LogicType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestLogicArityTest {
    @Test
    void incompleteMixedExpressionsCannotAcceptOrFinishAQuest() {
        var minimumOperands =
                Map.of(
                        LogicType.LOGIC_A_AND_ETCOR, 2,
                        LogicType.LOGIC_A_AND_B_AND_ETCOR, 3,
                        LogicType.LOGIC_A_OR_ETCAND, 2,
                        LogicType.LOGIC_A_OR_B_OR_ETCAND, 3,
                        LogicType.LOGIC_A_AND_B_OR_ETCAND, 3);
        minimumOperands.forEach(
                (logic, minimum) -> {
                    for (int length = 0; length < minimum; length++) {
                        var progress = new int[length];
                        assertFalse(LogicType.calculate(logic, progress), logic + " missing operands");
                        Arrays.fill(progress, 1);
                        assertFalse(LogicType.calculate(logic, progress), logic + " short-circuit bypass");
                    }
                });
    }

    @Test
    void actualSingleOperandResourceDoesNotAbortLogin() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")))
                        .getAsJsonArray();
        var quest =
                java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                        .map(row -> row.getAsJsonObject())
                        .filter(row -> row.get("subId").getAsInt() == 7902550)
                        .findFirst()
                        .orElseThrow();
        var logic = LogicType.valueOf(quest.get("acceptCondComb").getAsString());
        int count = quest.getAsJsonArray("acceptCond").size();
        assertEquals(LogicType.LOGIC_A_AND_B_AND_ETCOR, logic);
        assertEquals(1, count);
        assertFalse(LogicType.calculate(logic, new int[count]));
        var satisfied = new int[count];
        Arrays.fill(satisfied, 1);
        assertFalse(LogicType.calculate(logic, satisfied));
    }

    @Test
    void completeMixedExpressionsKeepTheirTruthTables() {
        for (int a = 0; a <= 1; a++)
            for (int b = 0; b <= 1; b++)
                for (int c = 0; c <= 1; c++)
                    for (int d = 0; d <= 1; d++) {
                        var values = new int[] {a, b, c, d};
                        boolean aa = a == 1, bb = b == 1, cc = c == 1, dd = d == 1;
                        assertEquals(
                                aa && (bb || cc || dd), LogicType.calculate(LogicType.LOGIC_A_AND_ETCOR, values));
                        assertEquals(
                                aa && bb && (cc || dd),
                                LogicType.calculate(LogicType.LOGIC_A_AND_B_AND_ETCOR, values));
                        assertEquals(
                                aa || (bb && cc && dd), LogicType.calculate(LogicType.LOGIC_A_OR_ETCAND, values));
                        assertEquals(
                                aa || bb || (cc && dd),
                                LogicType.calculate(LogicType.LOGIC_A_OR_B_OR_ETCAND, values));
                        assertEquals(
                                (aa && bb) || (cc && dd),
                                LogicType.calculate(LogicType.LOGIC_A_AND_B_OR_ETCAND, values));
                    }
    }

    @Test
    void unconditionalAndSimpleExpressionsRetainTheirBehavior() {
        for (var logic :
                new LogicType[] {
                    null, LogicType.LOGIC_NONE, LogicType.LOGIC_AND, LogicType.LOGIC_OR, LogicType.LOGIC_NOT
                }) assertTrue(LogicType.calculate(logic, new int[0]));
        assertTrue(LogicType.calculate(LogicType.LOGIC_AND, new int[] {1}));
        assertFalse(LogicType.calculate(LogicType.LOGIC_AND, new int[] {1, 0}));
        assertTrue(LogicType.calculate(LogicType.LOGIC_OR, new int[] {0, 1}));
        assertTrue(LogicType.calculate(LogicType.LOGIC_NOT, new int[] {0, 0}));
    }
}

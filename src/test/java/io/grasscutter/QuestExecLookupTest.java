package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.enums.QuestExec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;

/** Public action lookup must preserve every declared opcode, including NONE and UNKNOWN. */
class QuestExecLookupTest {
    @Test
    void everyDeclaredNumericOpcodeResolvesItsOwnActionWithoutCollisions() {
        var ids = new HashSet<Integer>();
        for (var action : QuestExec.values()) {
            assertTrue(ids.add(action.getValue()), "Duplicate action ID " + action.getValue());
            assertEquals(
                    action, QuestExec.getContentTriggerByValue(action.getValue()), action.name());
        }
    }

    @Test
    void everyDeclaredActionNameResolvesItsOwnAction() {
        for (var action : QuestExec.values())
            assertEquals(action, QuestExec.getContentTriggerByName(action.name()), action.name());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, Integer.MAX_VALUE})
    void unknownNumericValuesRetainTheNoneFallback(int value) {
        assertEquals(QuestExec.QUEST_EXEC_NONE, QuestExec.getContentTriggerByValue(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "QUEST_CONTENT_FINISH_PLOT",
                "QUEST_COND_NONE",
                "missing",
                "quest_exec_notify_group_lua"
            })
    void unknownNamesAndOtherQuestDomainsRetainTheNoneFallback(String name) {
        assertEquals(QuestExec.QUEST_EXEC_NONE, QuestExec.getContentTriggerByName(name));
    }
}

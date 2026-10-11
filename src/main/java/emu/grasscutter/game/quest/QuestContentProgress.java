package emu.grasscutter.game.quest;

import emu.grasscutter.data.excels.quest.QuestData.QuestContentCondition;
import emu.grasscutter.game.quest.enums.LogicType;

import java.util.List;

/** Counts belong to individual content conditions; logic combines their satisfaction flags. */
public final class QuestContentProgress {
    private QuestContentProgress() {}

    public static int target(QuestContentCondition condition) {
        return Math.max(1, condition.getCount());
    }

    public static int current(QuestContentCondition condition, int value) {
        return Math.min(target(condition), Math.max(0, value));
    }

    public static int increment(QuestContentCondition condition, int previous) {
        return (int) Math.min(target(condition), (long) Math.max(0, previous) + 1);
    }

    public static boolean isSatisfied(
            LogicType logic, List<QuestContentCondition> conditions, int[] progress) {
        if (conditions == null || progress == null || conditions.size() != progress.length) {
            return false;
        }
        int[] satisfied = new int[progress.length];
        for (int i = 0; i < progress.length; i++) {
            satisfied[i] = progress[i] >= target(conditions.get(i)) ? 1 : 0;
        }
        return LogicType.calculate(logic, satisfied);
    }
}

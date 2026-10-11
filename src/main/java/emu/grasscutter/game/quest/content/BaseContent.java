package emu.grasscutter.game.quest.content;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.handlers.QuestBaseHandler;

@QuestValueContent(QuestContent.QUEST_CONTENT_NONE)
public class BaseContent extends QuestBaseHandler<QuestData.QuestContentCondition> {

    /** Event predicates retain independent counts; live predicates can become false again. */
    public int getProgress(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            int previous,
            String paramStr,
            int... params) {
        boolean matched = execute(quest, condition, paramStr, params);
        return switch (condition.getType()) {
            case QUEST_CONTENT_QUEST_STATE_EQUAL,
                    QUEST_CONTENT_QUEST_STATE_NOT_EQUAL,
                    QUEST_CONTENT_QUEST_VAR_EQUAL,
                    QUEST_CONTENT_QUEST_VAR_GREATER,
                    QUEST_CONTENT_QUEST_VAR_LESS,
                    QUEST_CONTENT_ITEM_LESS_THAN,
                    QUEST_CONTENT_GAME_TIME_TICK,
                    QUEST_CONTENT_TIME_VAR_GT_EQ,
                    QUEST_CONTENT_TIME_VAR_PASS_DAY,
                    QUEST_CONTENT_TRIGGER_FIRE -> matched
                    ? QuestContentProgress.target(condition)
                    : 0;
            default -> matched ? QuestContentProgress.increment(condition, previous) : previous;
        };
    }

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        // TODO Auto-generated method stub
        return false;
    }
}

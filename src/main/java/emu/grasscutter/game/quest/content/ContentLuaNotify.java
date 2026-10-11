package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_LUA_NOTIFY;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_LUA_NOTIFY)
public class ContentLuaNotify extends BaseContent {

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        return getProgress(quest, condition, 0, paramStr, params)
                >= QuestContentProgress.target(condition);
    }

    @Override
    public int getProgress(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            int previous,
            String paramStr,
            int... params) {
        return QuestContentProgress.current(
                condition,
                quest.getOwner().getPlayerProgress().getCurrentProgress(condition.getParamStr()));
    }
}

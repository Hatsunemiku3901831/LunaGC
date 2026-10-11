package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_OBTAIN_ITEM;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_OBTAIN_ITEM)
public class ContentObtainItem extends BaseContent {
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
                quest.getOwner().getInventory().getItemCountById(condition.getParam()[0]));
    }
}

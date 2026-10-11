package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_CLEAR_GROUP_MONSTER)
public class ContentClearGroupMonster extends BaseContent {

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        if (condition == null
                || condition.getParam() == null
                || condition.getParam().length == 0
                || params == null
                || params.length == 0) return false;
        int groupId = condition.getParam()[0];
        if (groupId <= 0
                || params[0] != groupId
                || quest == null
                || quest.getOwner() == null
                || quest.getOwner().getScene() == null) return false;

        return quest.getOwner().getScene().getScriptManager().isClearedGroupMonsters(groupId);
    }
}

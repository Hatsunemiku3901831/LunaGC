package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_TEAM_DEAD;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueContent;

/** A settled team wipe carries an explicit flag; resource condition parameters are unused. */
@QuestValueContent(QUEST_CONTENT_TEAM_DEAD)
public final class ContentTeamDead extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        return params != null && params.length > 0 && params[0] == 1;
    }
}

package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_COMPLETE_TALK;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

import lombok.val;

@QuestValueContent(QUEST_CONTENT_COMPLETE_TALK)
public class ContentCompleteTalk extends BaseContent {
    @Override
    public int getProgress(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            int previous,
            String paramStr,
            int... params) {
        // Talk history may satisfy a one-time goal. Repeated goals require the matching new talk.
        if (condition.getCount() > 1
                && (params.length == 0 || params[0] != condition.getParam()[0])) {
            return previous;
        }
        return super.getProgress(quest, condition, previous, paramStr, params);
    }

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        val talkId = condition.getParam()[0];
        val checkMainQuest = quest.getOwner().getQuestManager().getMainQuestByTalkId(talkId);
        if (checkMainQuest == null) {
            return talkId == params[0];
        }

        val talkData = checkMainQuest.getTalks().get(talkId);
        return talkData != null;
    }
}

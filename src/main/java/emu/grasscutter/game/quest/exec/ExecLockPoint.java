package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_LOCK_POINT)
public final class ExecLockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (quest == null
                || quest.getMainQuest() == null
                || quest.getOwner() == null
                || params == null
                || params.length < 2
                || params[0] == null
                || params[1] == null) return false;
        try {
            return quest.getOwner()
                    .getProgressManager()
                    .lockTransPoint(
                            Integer.parseInt(params[0].trim()), Integer.parseInt(params[1].trim()));
        } catch (NumberFormatException error) {
            return false;
        }
    }
}

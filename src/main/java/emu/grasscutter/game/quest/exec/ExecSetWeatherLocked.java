package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_SET_IS_WEATHER_LOCKED)
public final class ExecSetWeatherLocked extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (quest == null
                || quest.getMainQuest() == null
                || quest.getOwner() == null
                || params == null
                || params.length == 0
                || params[0] == null) return false;
        final int value;
        try {
            value = Integer.parseInt(params[0].trim());
        } catch (NumberFormatException error) {
            return false;
        }
        if (value != 0 && value != 1) return false;
        return quest.getOwner().setProperty(PlayerProperty.PROP_IS_WEATHER_LOCKED, value == 1);
    }
}

package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.game.world.WeatherAreaState;

@QuestValueExec(QuestExec.QUEST_EXEC_SET_WEATHER_GADGET)
public final class ExecSetWeatherGadget extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... args) {
        if (quest == null || args == null || args.length < 2) return false;
        try {
            int area = Integer.parseInt(args[0]);
            int enabled = Integer.parseInt(args[1]);
            var data = GameData.getWeatherDataMap().get(area);
            if (enabled == 1 && (data == null || data.getGadgetID() <= 0)) return false;
            return WeatherAreaState.apply(quest.getOwner(), area, enabled);
        } catch (NumberFormatException error) {
            return false;
        }
    }
}

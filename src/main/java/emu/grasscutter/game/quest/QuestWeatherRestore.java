package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.world.WeatherAreaState;

import java.util.Collection;

/** Recover only an active begin/finish activation pair with no competing area. */
final class QuestWeatherRestore {
    private QuestWeatherRestore() {}

    static int selectArea(Collection<QuestData> active, int sceneId) {
        int selected = 0;
        for (var quest : active) {
            if (quest == null || quest.getBeginExec() == null || quest.getFinishExec() == null)
                continue;
            for (var begin : quest.getBeginExec()) {
                int area = areaWithFlag(begin, 1);
                if (!WeatherAreaState.canActivate(area, sceneId)) continue;
                if (GameData.getWeatherDataMap().get(area).getGadgetID() <= 0) continue;
                if (quest.getFinishExec().stream().noneMatch(exit -> areaWithFlag(exit, 0) == area))
                    continue;
                if (selected != 0 && selected != area) return 0;
                selected = area;
            }
        }
        return selected;
    }

    private static int areaWithFlag(QuestData.QuestExecParam action, int flag) {
        if (action == null
                || action.getType() != QuestExec.QUEST_EXEC_SET_WEATHER_GADGET
                || action.getParam() == null
                || action.getParam().length < 2) return 0;
        try {
            int area = Integer.parseInt(action.getParam()[0]);
            return area > 0 && Integer.parseInt(action.getParam()[1]) == flag ? area : 0;
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}

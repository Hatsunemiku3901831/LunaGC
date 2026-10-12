package emu.grasscutter.game.quest.exec;

import static emu.grasscutter.utils.Utils.random;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE_RANDOM)
public class ExecRefreshGroupSuiteRandom extends QuestExecHandler {
    private record Choices(int groupId, List<Integer> suites) {}

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (quest == null
                || quest.getMainQuest() == null
                || params == null
                || params.length < 2
                || params[0] == null
                || params[1] == null) return false;
        final int sceneId;
        var choices = new ArrayList<Choices>();
        try {
            sceneId = Integer.parseInt(params[0].trim());
            if (sceneId <= 0) return false;
            for (var entry : params[1].split(";", -1)) {
                var parts = entry.split(",", -1);
                if (parts.length < 2) return false;
                int groupId = Integer.parseInt(parts[0].trim());
                if (groupId <= 0) return false;
                var suites = new ArrayList<Integer>();
                for (int index = 1; index < parts.length; index++) {
                    int suite = Integer.parseInt(parts[index].trim());
                    if (suite < 0) return false;
                    suites.add(suite);
                }
                choices.add(new Choices(groupId, List.copyOf(suites)));
            }
        } catch (NumberFormatException error) {
            return false;
        }
        // Choose once after the entire configuration is valid. Deferred loading retains this
        // choice.
        var selected = new StringJoiner(";");
        for (var choice : choices)
            selected.add(
                    choice.groupId()
                            + ","
                            + choice.suites().get(random.nextInt(choice.suites().size())));
        return new ExecRefreshGroupSuite()
                .execute(quest, condition, Integer.toString(sceneId), selected.toString());
    }
}

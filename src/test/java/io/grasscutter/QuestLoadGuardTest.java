package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.game.quest.QuestManager;
import org.junit.jupiter.api.Test;

public final class QuestLoadGuardTest {

    @Test
    public void savedQuestsAreKeptWhileResourcesAreStillLoading() {
        assumeFalse(ResourceLoader.isLoadedAll());

        assertFalse(QuestManager.resourcesReadyForQuestLoad());
    }
}

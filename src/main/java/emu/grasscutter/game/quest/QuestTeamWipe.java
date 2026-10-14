package emu.grasscutter.game.quest;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.quest.enums.QuestContent;

import java.util.*;

/** One event per party wipe, evaluated after authoritative death callbacks and revival. */
public final class QuestTeamWipe {
    private final TeamManager manager;
    private final Set<EntityAvatar> reported = Collections.newSetFromMap(new IdentityHashMap<>());

    public QuestTeamWipe(TeamManager manager) {
        this.manager = manager;
    }

    public void settled(EntityAvatar defeated) {
        var player = manager.getPlayer();
        var scene = player == null ? null : player.getScene();
        if (defeated == null
                || scene == null
                || defeated.getPlayer() != player
                || defeated.getScene() != scene
                || !defeated.isDead()) return;
        synchronized (this) {
            var members = manager.getActiveTeamSnapshot();
            if (members.isEmpty()
                    || members.stream().noneMatch(member -> member == defeated)
                    || members.stream()
                            .anyMatch(
                                    member ->
                                            member == null
                                                    || member.getPlayer() != player
                                                    || member.getScene() != scene
                                                    || !member.isDead())) return;
            if (reported.size() == members.size() && reported.containsAll(members)) return;
            reported.clear();
            reported.addAll(members);
        }
        player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_TEAM_DEAD, 1);
    }

    /** Revival starts a new life, even if the next wipe occurs without an intervening swap. */
    public synchronized void revived(EntityAvatar member) {
        if (reported.contains(member)) reported.clear();
    }
}

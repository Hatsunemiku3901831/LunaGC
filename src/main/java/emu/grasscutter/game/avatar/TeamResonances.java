package emu.grasscutter.game.avatar;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.TeamResonanceData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;

import java.util.*;

/** Resonance follows the selected four-character roster, including the shared co-op roster. */
public final class TeamResonances {
    private TeamResonances() {}

    public static List<Player> participants(Player player) {
        var world = player.getWorld();
        if (world == null || !world.isMultiplayer()) return List.of(player);
        return world.getPlayers().stream().filter(p -> p.getWorld() == world).toList();
    }

    public static List<TeamResonanceData> active(Player player) {
        var counts = new EnumMap<ElementType, Integer>(ElementType.class);
        int size = 0;
        for (var member : participants(player)) {
            var selected = member.getTeamManager().getSelectedPartySnapshot();
            if (selected.size() > member.getTeamManager().getMaxTeamSize()) return List.of();
            var seen = Collections.newSetFromMap(new IdentityHashMap<Avatar, Boolean>());
            for (var avatar : selected) {
                if (avatar == null || avatar.getPlayer() != member || !seen.add(avatar))
                    return List.of();
                var depot = avatar.getSkillDepot();
                var element = depot == null ? null : depot.getElementType();
                if (element == null
                        || element == ElementType.Default
                        || element.getTeamResonanceId() == 0) return List.of();
                counts.merge(element, 1, Integer::sum);
                size++;
            }
        }
        if (size != 4) return List.of();
        return GameData.getTeamResonanceDataMap().values().stream()
                .filter(
                        d ->
                                d.getId() > 0
                                        && d.getOpenConfig() != null
                                        && !d.getOpenConfig().isBlank())
                .filter(d -> d.matches(counts))
                .sorted(Comparator.comparingInt(TeamResonanceData::getId))
                .toList();
    }

    public static List<TeamResonanceData> active(Avatar avatar) {
        var player = avatar == null ? null : avatar.getPlayer();
        if (player == null
                || player.getTeamManager().getSelectedPartySnapshot().stream()
                        .noneMatch(a -> a == avatar)) return List.of();
        return active(player);
    }
}

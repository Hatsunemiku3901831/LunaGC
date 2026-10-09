package emu.grasscutter.game.avatar;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;

import java.util.*;
import java.util.function.Supplier;

/** Own-party utility talents, kept separate from saved levels and constellation bonuses. */
public final class TeamSkillLevelBonuses {
    private final Set<Avatar> applied = Collections.newSetFromMap(new IdentityHashMap<>());

    public record Change(Avatar avatar, Map<Integer, Integer> previous) {
        public void finish(boolean notify) {
            var player = avatar.getPlayer();
            if (player == null) return;
            if (notify && player.hasSentLoginPackets())
                avatar.notifySkillLevelBonusChanges(previous);
            player.getAbilityManager()
                    .refreshEquipmentTalents(avatar, notify && player.hasSentLoginPackets());
        }
    }

    /** Update bonus maps first so a team announcement precedes skill parameter notifications. */
    public synchronized List<Change> prepare(Player player, Supplier<List<Avatar>> selector) {
        var selected = selector.get();
        var party = Collections.newSetFromMap(new IdentityHashMap<Avatar, Boolean>());
        boolean complete = true;
        for (var avatar : selected) {
            if (avatar == null || avatar.getPlayer() != player) complete = false;
            else party.add(avatar);
        }
        boolean normal = party.stream().anyMatch(a -> unlocked(a, 10000033, 332301));
        boolean skill =
                complete
                        && party.stream().anyMatch(a -> unlocked(a, 10000114, 1142301))
                        && iceWaterParty(party);
        var candidates = Collections.newSetFromMap(new IdentityHashMap<Avatar, Boolean>());
        candidates.addAll(applied);
        candidates.addAll(party);
        var changes = new ArrayList<Change>();
        for (var avatar : candidates) {
            var bonuses = new LinkedHashMap<Integer, Integer>();
            if (party.contains(avatar)) {
                if (normal) add(bonuses, avatar, 0);
                if (skill) add(bonuses, avatar, 1);
            }
            var previous = avatar.getProudSkillBonusMap();
            if (avatar.setTeamSkillLevelBonuses(bonuses)) changes.add(new Change(avatar, previous));
        }
        applied.clear();
        if (normal || skill) applied.addAll(party);
        return changes;
    }

    private static boolean unlocked(Avatar avatar, int avatarId, int proudSkillId) {
        return avatar.getAvatarId() == avatarId
                && avatar.getProudSkillList() != null
                && avatar.getProudSkillList().contains(proudSkillId)
                && GameData.getProudSkillDataMap().containsKey(proudSkillId);
    }

    private static boolean iceWaterParty(Collection<Avatar> party) {
        boolean ice = false, water = false;
        for (var avatar : party) {
            var depot = avatar.getSkillDepot();
            var element = depot == null ? ElementType.None : depot.getElementType();
            if (element == ElementType.Ice) ice = true;
            else if (element == ElementType.Water) water = true;
            else return false;
        }
        return ice && water;
    }

    private static void add(Map<Integer, Integer> bonuses, Avatar avatar, int slot) {
        var depot = avatar.getSkillDepot();
        if (depot == null || depot.getSkills() == null || depot.getSkills().size() <= slot) return;
        var id = depot.getSkills().get(slot);
        var data = id == null ? null : GameData.getAvatarSkillDataMap().get((int) id);
        if (data != null && data.getProudSkillGroupId() > 0)
            bonuses.merge(data.getProudSkillGroupId(), 1, Integer::sum);
    }
}

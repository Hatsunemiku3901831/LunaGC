package emu.grasscutter.game.tower;

import dev.morphia.annotations.Entity;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.*;

import java.util.*;

/** State at entry to a chamber, keyed by owned avatar identity rather than session GUID. */
@Entity
public final class TowerChamberState {
    private int floorId, levelId;
    private List<List<Integer>> parties;
    private Map<Integer, AvatarState> avatars;

    @Entity
    public static final class AvatarState {
        private int depotId, energyProperty;
        private float hp, debt, energy;

        public AvatarState() {}
    }

    public TowerChamberState() {}

    public boolean matches(int floor, int level, List<List<Integer>> selected) {
        return floorId == floor && levelId == level && parties != null && parties.equals(selected);
    }

    public static TowerChamberState capture(
            Player player, int floor, int level, List<List<Integer>> selected) {
        if (player == null || floor <= 0 || level <= 0 || selected == null || selected.isEmpty())
            return null;
        var result = new TowerChamberState();
        result.floorId = floor;
        result.levelId = level;
        result.parties = new ArrayList<>();
        result.avatars = new LinkedHashMap<>();
        for (var team : selected) {
            if (team == null || team.isEmpty()) return null;
            result.parties.add(new ArrayList<>(team));
            for (var id : team) {
                var avatar = id == null ? null : player.getAvatars().getAvatarById(id);
                if (avatar == null
                        || avatar.getPlayer() != player
                        || result.avatars.containsKey(id)) return null;
                var state = new AvatarState();
                state.hp = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                state.debt = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                var depot = avatar.getSkillDepot();
                state.depotId = depot == null ? 0 : depot.getId();
                var property = energyProperty(avatar);
                state.energyProperty = property == null ? 0 : property.getId();
                state.energy = property == null ? 0 : avatar.getFightProperty(property);
                if (!valid(state, avatar)) return null;
                result.avatars.put(id, state);
            }
        }
        return result;
    }

    private static FightProperty energyProperty(Avatar avatar) {
        var depot = avatar.getSkillDepot();
        if (depot == null) return null;
        var skill = depot.getEnergySkillData();
        if (skill != null && skill.getSpecialEnergyMin() > 0)
            return FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY;
        return depot.getElementType() == null ? null : depot.getElementType().getCurEnergyProp();
    }

    /** A new floor starts with a fully recovered roster; later chambers retain their costs. */
    public static TowerChamberState newFloor(
            Player player, int floor, int level, List<List<Integer>> selected) {
        var result = capture(player, floor, level, selected);
        if (result == null) return null;
        result.avatars.forEach((id, state) -> {
            var avatar = player.getAvatars().getAvatarById(id);
            state.hp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            state.debt = 0;
            var energy = energyProperty(avatar);
            state.energy = energy == null ? 0 : maximumEnergy(avatar, energy);
        });
        return result;
    }

    private static float maximumEnergy(Avatar avatar, FightProperty current) {
        return current == FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY
                ? avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY)
                : avatar.getFightProperty(
                        avatar.getSkillDepot().getElementType().getMaxEnergyProp());
    }

    private static boolean valid(AvatarState state, Avatar avatar) {
        if (state == null
                || avatar == null
                || !Float.isFinite(state.hp)
                || state.hp < 0
                || !Float.isFinite(state.debt)
                || state.debt < 0
                || !Float.isFinite(state.energy)
                || state.energy < 0) return false;
        float maximumHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (!Float.isFinite(maximumHp) || maximumHp <= 0) return false;
        var depot = avatar.getSkillDepot();
        if (state.depotId != (depot == null ? 0 : depot.getId())) return false;
        var property = energyProperty(avatar);
        if (state.energyProperty != (property == null ? 0 : property.getId())) return false;
        if (property == null) return state.energy == 0;
        float maximum = maximumEnergy(avatar, property);
        return Float.isFinite(maximum) && maximum > 0;
    }

    private Map<Avatar, AvatarState> targets(
            Player player, int floor, int level, List<List<Integer>> selected) {
        if (player == null || !matches(floor, level, selected) || avatars == null) return null;
        var targets = new LinkedHashMap<Avatar, AvatarState>();
        for (var team : selected) {
            if (team == null || team.isEmpty()) return null;
            for (var id : team) {
                var avatar = id == null ? null : player.getAvatars().getAvatarById(id);
                var state = avatars.get(id);
                if (avatar == null
                        || avatar.getPlayer() != player
                        || !valid(state, avatar)
                        || targets.put(avatar, state) != null) return null;
            }
        }
        return targets.size() == avatars.size() ? targets : null;
    }

    public boolean canRestore(Player player, int floor, int level, List<List<Integer>> selected) {
        return targets(player, floor, level, selected) != null;
    }

    public boolean restore(Player player, int floor, int level, List<List<Integer>> selected) {
        var targets = targets(player, floor, level, selected);
        if (targets == null) return false;
        for (var entry : targets.entrySet()) {
            var avatar = entry.getKey();
            var state = entry.getValue();
            float hp = Math.min(state.hp, avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
            avatar.setCurrentHp(hp);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, state.debt);
            var property = energyProperty(avatar);
            if (property != null)
                avatar.restoreSceneEnergy(
                        property, Math.min(state.energy, maximumEnergy(avatar, property)));
            for (var entity : player.getTeamManager().getActiveTeamSnapshot())
                if (entity.getAvatar() == avatar) entity.restoreChamberHealth(hp, state.debt);
            avatar.save();
            player.sendPacket(new PacketAvatarFightPropNotify(avatar));
            player.sendPacket(
                    new PacketAvatarFightPropUpdateNotify(
                            avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            player.sendPacket(new PacketAvatarLifeStateChangeNotify(avatar));
        }
        return true;
    }
}

package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;

import java.util.*;

/** Temporary skill information belongs to this scene avatar, not its stored talent charges. */
public final class AbilityAvatarSkillInfo {
    private final EntityAvatar entity;
    private final Map<Integer, IdentityHashMap<GameEntity, AbilityExecutionContext>> skills =
            new HashMap<>();
    private long version = -1;

    public AbilityAvatarSkillInfo(EntityAvatar entity) {
        this.entity = entity;
    }

    private boolean current() {
        long now = entity.getAbilityLifecycleVersion();
        if (version != now) {
            skills.clear();
            version = now;
        }
        return entity.isAbilityLifecycleCurrent(now);
    }

    private void pruneSources() {
        skills.values()
                .forEach(sources -> sources.values().removeIf(context -> !context.isCurrent()));
        skills.values().removeIf(Map::isEmpty);
    }

    public synchronized boolean add(Ability ability, int skillId) {
        var data = GameData.getAvatarSkillDataMap().get(skillId);
        if (ability == null || !current() || skillId <= 0 || data == null) return false;
        var context = new AbilityExecutionContext(ability, entity);
        if (!context.isCurrent()) return false;
        pruneSources();
        boolean first = !skills.containsKey(skillId);
        skills.computeIfAbsent(skillId, ignored -> new IdentityHashMap<>())
                .put(ability.getOwner(), context);
        if (first) notifyCharges(skillId, charges(skillId));
        return true;
    }

    public synchronized boolean remove(int skillId) {
        if (!current() || skillId <= 0) return false;
        pruneSources();
        if (skills.remove(skillId) != null) {
            var avatar = entity.getAvatar();
            var depot = avatar.getSkillDepot();
            boolean permanent =
                    avatar.getSkillExtraChargeMap().containsKey(skillId)
                            || depot != null
                                    && (depot.getSkillsAndEnergySkill()
                                                    .anyMatch(id -> id == skillId)
                                            || depot.getSubSkills() != null
                                                    && depot.getSubSkills().contains(skillId));
            if (permanent && GameData.getAvatarSkillDataMap().containsKey(skillId))
                notifyCharges(skillId, charges(skillId));
            // Removing the button is the client's RemoveAvatarSkillInfo action. This protocol
            // carries charge information, not a proved delete flag; do not invent a zero-charge
            // reset.
        }
        return true;
    }

    private int charges(int skillId) {
        var data = GameData.getAvatarSkillDataMap().get(skillId);
        return entity.getAvatar()
                .getSkillExtraChargeMap()
                .getOrDefault(skillId, data == null ? 1 : Math.max(1, data.getMaxChargeNum()));
    }

    private void notifyCharges(int skillId, int count) {
        var player = entity.getPlayer();
        if (player == null) return;
        var map = new Int2IntArrayMap();
        map.put(skillId, count);
        player.sendPacket(new PacketAvatarSkillInfoNotify(entity.getAvatar().getGuid(), map));
    }

    public synchronized Map<Integer, Integer> snapshot() {
        var result = new TreeMap<Integer, Integer>();
        if (current()) {
            pruneSources();
            for (var skill : skills.keySet())
                if (GameData.getAvatarSkillDataMap().containsKey(skill))
                    result.put(skill, charges(skill));
        }
        return result;
    }
}

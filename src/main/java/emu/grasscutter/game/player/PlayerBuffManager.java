package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify.ServerBuffChangeType;
import emu.grasscutter.net.proto.ServerBuffOuterClass.ServerBuff;
import emu.grasscutter.server.packet.send.PacketServerBuffChangeNotify;

import it.unimi.dsi.fastutil.ints.*;

import lombok.Getter;

import java.util.*;
import java.util.function.LongSupplier;

public final class PlayerBuffManager extends BasePlayerManager {
    // UID keys allow two avatars to hold their own instance of the same food group.
    private final Int2ObjectMap<PlayerBuff> buffs = new Int2ObjectOpenHashMap<>();
    private final Map<AbilityData, Set<AbilityModifierAction>> healingClaims =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final LongSupplier clock;
    private int nextBuffUid;

    public PlayerBuffManager(Player player) {
        this(player, System::currentTimeMillis);
    }

    public PlayerBuffManager(Player player, LongSupplier clock) {
        super(player);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized boolean hasBuff(int groupId) {
        return buffs.values().stream().anyMatch(b -> b.getGroupId() == groupId);
    }

    public synchronized void clearBuffs() {
        notify(
                ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_DEL_SERVER_BUFF,
                new ArrayList<>(buffs.values()));
        buffs.clear();
        // Claims survive removal: a late native callback must not recreate an already paid heal.
    }

    public boolean addBuff(int buffId) {
        return addBuff(buffId, -1f);
    }

    public synchronized boolean addBuff(int buffId, float duration) {
        return addBuff(buffId, duration, null);
    }

    /** Negative duration uses the resource; zero applies only immediate effects. */
    public synchronized boolean addBuff(int buffId, float duration, Avatar target) {
        var buffData = GameData.getBuffDataMap().get(buffId);
        if (buffData == null || !Float.isFinite(duration)) return false;
        if (duration < 0) duration = buffData.getTime();
        if (!Float.isFinite(duration)
                || duration < 0
                || target != null && target.getPlayer() != getPlayer()) return false;
        var data = GameData.getAbilityData(buffData.getAbilityName());
        var modifier = data == null ? null : data.modifiers.get(buffData.getModifierName());
        long now = clock.getAsLong();
        FoodBuffHealing healing = null;
        if (FoodBuffHealing.hasHealing(data, modifier)) {
            if (!FoodBuffHealing.supported(data, modifier)) return false;
            healing = new FoodBuffHealing(getPlayer(), target, data, modifier, now);
            if (healing.recipient() == null) return false;
            healingClaims
                    .computeIfAbsent(
                            data, key -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .addAll(healing.actions());
            if (!healing.initial()) return false;
        }
        if (duration == 0) {
            if (healing != null) healing.removed();
            return healing != null;
        }
        long targetGuid = target == null ? 0 : target.getGuid();
        var replaced =
                buffs.values().stream()
                        .filter(
                                b ->
                                        b.getGroupId() == buffData.getGroupId()
                                                && b.getTargetGuid() == targetGuid)
                        .toList();
        remove(replaced);
        var buff = new PlayerBuff(++nextBuffUid, buffData, duration, now, targetGuid, healing);
        buffs.put(buff.getUid(), buff);
        notify(ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_ADD_SERVER_BUFF, List.of(buff));
        return true;
    }

    public boolean ownsHealing(Ability ability, AbilityModifierAction action) {
        var claims =
                ability == null || ability.getPlayerOwner() != getPlayer()
                        ? null
                        : healingClaims.get(ability.getData());
        return claims != null && claims.contains(action);
    }

    public synchronized boolean removeBuff(int groupId) {
        var selected = buffs.values().stream().filter(b -> b.getGroupId() == groupId).toList();
        remove(selected);
        return !selected.isEmpty();
    }

    private void remove(Collection<PlayerBuff> selected) {
        selected.forEach(b -> buffs.remove(b.getUid()));
        for (var buff : selected)
            if (buff.healing != null) {
                try {
                    buff.healing.removed();
                } catch (RuntimeException failed) {
                    emu.grasscutter.Grasscutter.getLogger()
                            .error(
                                    "Food removal callback failed: UID {}, buff {}",
                                    getPlayer().getUid(),
                                    buff.buffData.getId(),
                                    failed);
                }
            }
        notify(ServerBuffChangeType.SERVER_BUFF_CHANGE_TYPE_DEL_SERVER_BUFF, selected);
    }

    private void notify(ServerBuffChangeType type, Collection<PlayerBuff> selected) {
        var groups = new LinkedHashMap<Long, List<PlayerBuff>>();
        for (var buff : selected)
            groups.computeIfAbsent(buff.getTargetGuid(), key -> new ArrayList<>()).add(buff);
        for (var group : groups.values())
            getPlayer().sendPacket(new PacketServerBuffChangeNotify(getPlayer(), type, group));
    }

    public synchronized void onTick() {
        long now = clock.getAsLong();
        var expired = new ArrayList<PlayerBuff>();
        for (var buff : new ArrayList<>(buffs.values())) {
            if (now > buff.getEndTime()) expired.add(buff);
            else if (buff.healing != null) buff.healing.tick(now);
        }
        remove(expired);
    }

    @Getter
    public static class PlayerBuff {
        private final int uid;
        private final BuffData buffData;
        private final long endTime;
        private final long targetGuid;
        private final FoodBuffHealing healing;

        public PlayerBuff(int uid, BuffData buffData, float duration) {
            this(uid, buffData, duration, System.currentTimeMillis(), 0, null);
        }

        private PlayerBuff(
                int uid,
                BuffData buffData,
                float duration,
                long now,
                long targetGuid,
                FoodBuffHealing healing) {
            this.uid = uid;
            this.buffData = buffData;
            this.endTime = now + Math.max(1, Math.round(duration * 1000d));
            this.targetGuid = targetGuid;
            this.healing = healing;
        }

        public int getGroupId() {
            return buffData.getGroupId();
        }

        public ServerBuff toProto() {
            return ServerBuff.newBuilder()
                    .setServerBuffUid(uid)
                    .setServerBuffId(buffData.getId())
                    .setServerBuffType(buffData.getServerBuffType().getValue())
                    .setInstancedModifierId(1)
                    .build();
        }
    }
}

package emu.grasscutter.game.entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.PlayTeamEntityInfoOuterClass.PlayTeamEntityInfo;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

import java.util.*;

/** Scene-play ability carrier, synchronized separately from spatial scene entities. */
public final class EntityPlayTeam extends GameEntity {
    private final Player player;
    private final int playerUid, authorityPeerId, gadgetConfigId;
    private final List<AbilityData> abilities;
    private final Int2FloatMap properties = new Int2FloatOpenHashMap();
    private boolean initialized;

    public EntityPlayTeam(
            Scene scene, Player player, int gadgetConfigId, List<AbilityData> abilities) {
        super(scene);
        this.player = Objects.requireNonNull(player);
        this.playerUid = player.getUid();
        this.authorityPeerId = player.getPeerId();
        this.gadgetConfigId = gadgetConfigId;
        this.abilities = List.copyOf(abilities);
        this.id = scene.getWorld().getNextEntityId(EntityIdType.PLAY_TEAM);
    }

    /** Resolve every configured ability before publishing any participant's carrier. */
    public static List<AbilityData> configuredAbilities(int gadgetConfigId) {
        var gadget = GameData.getGadgetDataMap().get(gadgetConfigId);
        var config =
                gadget == null ? null : GameData.getGadgetConfigData().get(gadget.getJsonName());
        if (config == null) return null;
        var result = new ArrayList<AbilityData>();
        if (config.getAbilities() != null)
            for (var entry : config.getAbilities()) {
                if (entry == null || entry.abilityName == null || entry.abilityName.isBlank())
                    return null;
                // The current ability engine cannot instantiate a named gadget override.
                if (entry.abilityOverride != null && !entry.abilityOverride.isBlank()) return null;
                var data = GameData.getAbilityData(entry.abilityName);
                if (data == null) return null;
                result.add(data);
            }
        return List.copyOf(result);
    }

    @Override
    public void initAbilities() {
        synchronized (getScene()) {
            if (initialized
                    || getScene().getScenePlayBattleManager().getPlayTeamEntity(getId()) != this)
                return;
            initialized = true;
            for (var data : abilities) player.getAbilityManager().addAbilityToEntity(this, data);
        }
    }

    /** Invalidate queued work before removing this carrier's effects from other entities. */
    public void dispose() {
        invalidateAbilityLifecycle();
        var owned = Collections.newSetFromMap(new IdentityHashMap<Ability, Boolean>());
        owned.addAll(getInstancedAbilities());
        owned.addAll(getDynamicAbilities().values());
        for (var ability : owned) {
            if (ability == null) continue;
            try {
                ability.removeInstalledModifiers();
            } catch (RuntimeException failure) {
                Grasscutter.getLogger().error("Scene-play carrier ability cleanup failed", failure);
            }
        }
        getInstancedAbilities().clear();
        getDynamicAbilities().clear();
        getGlobalAbilityValues().clear();
    }

    public Player getPlayer() {
        return player;
    }

    public int getPlayerUid() {
        return playerUid;
    }

    public int getAuthorityPeerId() {
        return authorityPeerId;
    }

    public int getGadgetConfigId() {
        return gadgetConfigId;
    }

    public PlayTeamEntityInfo toPlayTeamProto() {
        return PlayTeamEntityInfo.newBuilder()
                .setEntityId(getId())
                .setPlayerUid(playerUid)
                .setAuthorityPeerId(authorityPeerId)
                .setGadgetConfigId(gadgetConfigId)
                // Keep native initialization enabled while restoring this participant's parameters.
                .setAbilityInfo(getAbilityRoots().toSyncState())
                .build();
    }

    @Override
    public int getEntityTypeId() {
        return EntityIdType.PLAY_TEAM.getId();
    }

    @Override
    public Int2FloatMap getFightProperties() {
        return properties;
    }

    @Override
    public Position getPosition() {
        return new Position();
    }

    @Override
    public Position getRotation() {
        return new Position();
    }

    @Override
    public SceneEntityInfo toProto() {
        return SceneEntityInfo.newBuilder()
                .setEntityId(getId())
                .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_PLAY_TEAM_ENTITY)
                .build();
    }
}

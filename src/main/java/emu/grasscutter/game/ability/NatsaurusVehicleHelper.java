package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;

import java.util.*;
import java.util.regex.Pattern;

/** Adapted from AstaPS c5d57286: selected Saurian replacement, riding and cleanup. */
public final class NatsaurusVehicleHelper {
    private static final Pattern MONSTER = Pattern.compile("^(?:Monster_)?Natsaurus_([^_]+)_.+");
    private static final Pattern VEHICLE = Pattern.compile("^Natsaurus_([^_]+)_Vehicle(?:_.+)?$");
    // ConfigAbility_Scene_V5_0_WhiteBox: Prop_SaurTotem_01..06 effect tribes.
    private static final Map<Integer, String> TOTEMS =
            Map.of(
                    70801015,
                    "Hookwalker",
                    70801016,
                    "Drillhead",
                    70801017,
                    "Mosasaurus",
                    70801018,
                    "Shamansaurus",
                    70801019,
                    "Flamingo",
                    70801020,
                    "Bisonsaurus");

    private NatsaurusVehicleHelper() {}

    public static boolean isNatsaurusVehicleGadget(int id) {
        var data = GameData.getGadgetDataMap().get(id);
        return data != null
                && data.getType() == EntityType.Vehicle
                && data.getJsonName() != null
                && VEHICLE.matcher(data.getJsonName()).matches();
    }

    public static boolean isNatsaurusMount(EntityVehicle vehicle) {
        return vehicle != null && isNatsaurusVehicleGadget(vehicle.getGadgetId());
    }

    private static int forTribe(String tribe) {
        // Read the current resource map; resource reload must not leave a stale cached mapping.
        return GameData.getGadgetDataMap().values().stream()
                .filter(data -> data.getJsonName() != null && data.getType() == EntityType.Vehicle)
                .filter(
                        data -> {
                            var match = VEHICLE.matcher(data.getJsonName());
                            return match.matches() && match.group(1).equals(tribe);
                        })
                .mapToInt(data -> data.getId())
                .min()
                .orElse(0);
    }

    public static int vehicleGadgetId(String name) {
        if (name == null) return 0;
        var match = MONSTER.matcher(name);
        return match.matches() ? forTribe(match.group(1)) : 0;
    }

    public static int vehicleGadgetIdFor(GameEntity source) {
        if (source == null || !source.isAlive()) return 0;
        var props = source.getFightProperties();
        int hpId = emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP.getId();
        if (props != null
                && props.containsKey(hpId)
                && (Float.isNaN(props.get(hpId)) || props.get(hpId) <= 0)) return 0;
        if (source instanceof EntityMonster monster)
            return monster.getMonsterData() == null
                    ? 0
                    : vehicleGadgetId(monster.getMonsterData().getMonsterName());
        if (source instanceof EntityVehicle vehicle)
            return isNatsaurusMount(vehicle) && vehicle.getVehicleMembers().isEmpty()
                    ? vehicle.getGadgetId()
                    : 0;
        if (source instanceof EntityBaseGadget gadget) {
            if (isNatsaurusVehicleGadget(gadget.getGadgetId())) return gadget.getGadgetId();
            var tribe = TOTEMS.get(gadget.getGadgetId());
            return tribe == null ? 0 : forTribe(tribe);
        }
        return 0;
    }

    private static boolean positionValid(Position position) {
        return position != null
                && Float.isFinite(position.getX())
                && Float.isFinite(position.getY())
                && Float.isFinite(position.getZ());
    }

    private static boolean member(Player player, Scene scene) {
        return player.getWorld() == scene.getWorld()
                && scene.getWorld().getScenes().get(scene.getId()) == scene
                && scene.getPlayers().stream().anyMatch(p -> p == player)
                && scene.getWorld().getPlayers().stream().anyMatch(p -> p == player);
    }

    public static EntityVehicle board(Player player, GameEntity source) {
        if (player == null || source == null) return null;
        synchronized (player.getSceneTransferLock()) {
            var scene = player.getScene();
            if (scene == null) return null;
            synchronized (scene) {
                if (!member(player, scene)
                        || source.getScene() != scene
                        || scene.getEntityById(source.getId()) != source
                        || !source.isAlive()
                        || !positionValid(source.getPosition())
                        || !positionValid(source.getRotation())
                        || player.getSession() == null
                        || player.getTeamManager().getCurrentAvatarEntity() == null) return null;
                int gadgetId = vehicleGadgetIdFor(source);
                if (gadgetId == 0) return null;
                var row = GameData.getGadgetDataMap().get(gadgetId);
                var config =
                        row == null ? null : GameData.getGadgetConfigData().get(row.getJsonName());
                if (config == null
                        || config.getCombat() == null
                        || config.getCombat().getProperty() == null
                        || !Float.isFinite(config.getCombat().getProperty().getHP())
                        || config.getCombat().getProperty().getHP() <= 0) return null;
                // Construct first: invalid resources must not consume the selected source.
                var mount =
                        new EntityVehicle(
                                scene,
                                player,
                                gadgetId,
                                0,
                                source.getPosition().clone(),
                                source.getRotation().clone());
                eject(player);
                scene.removeEntity(source, VisionType.VisionType_VISION_REMOVE);
                scene.addEntity(mount);
                player.getStaminaManager()
                        .handleVehicleInteractReq(
                                player.getSession(),
                                mount.getId(),
                                VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN);
                player.sendPacket(
                        new PacketVehicleInteractRsp(
                                player,
                                mount.getId(),
                                VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN));
                scene.updateEntity(mount);
                player.getAbilityManager().setScanTarget(null);
                return mount;
            }
        }
    }

    public static boolean isRiding(Player player) {
        var scene = player == null ? null : player.getScene();
        if (scene == null) return false;
        return scene.getEntities().values().stream()
                .anyMatch(
                        entity ->
                                entity instanceof EntityVehicle vehicle
                                        && isNatsaurusMount(vehicle)
                                        && vehicle.isAlive()
                                        && vehicle.getVehicleMembers().stream()
                                                .anyMatch(m -> m.getUid() == player.getUid()));
    }

    public static void dismount(Player player, EntityVehicle vehicle, VehicleMember member) {
        // The member is consumed once, even if a death and an explicit exit arrive together.
        if (!vehicle.getVehicleMembers().remove(member)) return;
        player.sendPacket(
                new PacketVehicleInteractRsp(
                        vehicle,
                        member,
                        VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT));
        if (player.getScene() == vehicle.getScene())
            player.getStaminaManager().releaseVehicle(vehicle.getId());
        if (vehicle.getScene().getEntities().get(vehicle.getId()) == vehicle)
            vehicle.getScene().updateEntity(vehicle);
    }

    public static void eject(Player player) {
        finish(player, false);
    }

    public static void takeAway(Player player) {
        finish(player, true);
    }

    private static void finish(Player player, boolean remove) {
        if (player == null) return;
        synchronized (player.getSceneTransferLock()) {
            var scene = player.getScene();
            if (scene == null) return;
            synchronized (scene) {
                for (var entity : new ArrayList<>(scene.getEntities().values())) {
                    if (!(entity instanceof EntityVehicle vehicle)
                            || vehicle.getOwner() != player
                            || !isNatsaurusMount(vehicle)
                            || !vehicle.isAlive()) continue;
                    for (var rider : new ArrayList<>(vehicle.getVehicleMembers()))
                        if (rider.getUid() == player.getUid()) dismount(player, vehicle, rider);
                    if (remove && vehicle.getVehicleMembers().isEmpty())
                        scene.removeEntity(vehicle, VisionType.VisionType_VISION_REMOVE);
                }
                player.getAbilityManager().setScanTarget(null);
            }
        }
    }
}

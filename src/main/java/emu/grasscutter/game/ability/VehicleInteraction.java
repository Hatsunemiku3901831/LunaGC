package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;

import java.util.ArrayList;

/** One admission and membership operation for both client requests and soul possession. */
public final class VehicleInteraction {
    private VehicleInteraction() {}

    private static PacketVehicleInteractRsp reject(int id, int type, Retcode error) {
        return new PacketVehicleInteractRsp(id, type, error.getNumber());
    }

    public static PacketVehicleInteractRsp execute(GameSession session, int id, int operation) {
        var player = session == null ? null : session.getPlayer();
        if (player == null) return reject(id, operation, Retcode.RET_GADGET_INTERACT_COND_NOT_MEET);
        synchronized (player.getSceneTransferLock()) {
            var scene = player.getScene();
            if (scene == null || player.getSession() != session)
                return reject(id, operation, Retcode.RET_GADGET_INTERACT_COND_NOT_MEET);
            synchronized (scene) {
                if (player.getWorld() != scene.getWorld()
                        || scene.getWorld().getScenes().get(scene.getId()) != scene
                        || scene.getPlayers().stream().noneMatch(p -> p == player)
                        || scene.getWorld().getPlayers().stream().noneMatch(p -> p == player))
                    return reject(id, operation, Retcode.RET_GADGET_INTERACT_COND_NOT_MEET);
                var type = VehicleInteractType.forNumber(operation);
                if (type != VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN
                        && type != VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT)
                    return reject(id, operation, Retcode.RET_GADGET_INTERACT_PARAM_ERROR);
                var entity = scene.getEntityById(id);
                if (entity == null) return reject(id, operation, Retcode.RET_ENTITY_NOT_EXIST);
                if (!(entity instanceof EntityVehicle vehicle))
                    return reject(id, operation, Retcode.RET_GADGET_NOT_VEHICLE);
                var data = GameData.getGadgetDataMap().get(vehicle.getGadgetId());
                if (data == null || data.getType() != EntityType.Vehicle)
                    return reject(id, operation, Retcode.RET_GADGET_NOT_VEHICLE);
                var member =
                        vehicle.getVehicleMembers().stream()
                                .filter(m -> m.getUid() == player.getUid())
                                .findFirst()
                                .orElse(null);
                if (type == VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT) {
                    if (member == null) return reject(id, operation, Retcode.RET_NOT_IN_VEHICLE);
                    vehicle.getVehicleMembers().removeIf(m -> m.getUid() == player.getUid());
                    player.getStaminaManager().releaseVehicle(id);
                    scene.updateEntity(vehicle);
                    return new PacketVehicleInteractRsp(vehicle, member, type);
                }
                boolean mount = NatsaurusVehicleHelper.isNatsaurusMount(vehicle);
                float hp = vehicle.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                if (!vehicle.isAlive() || mount && (!Float.isFinite(hp) || hp <= 0))
                    return reject(id, operation, Retcode.RET_ENTITY_NOT_EXIST);
                if (mount && vehicle.getOwner() != player)
                    return reject(id, operation, Retcode.RET_GADGET_INTERACT_COND_NOT_MEET);
                if (mount
                        && vehicle.getVehicleMembers().stream()
                                .anyMatch(m -> m.getUid() != player.getUid()))
                    return reject(id, operation, Retcode.RET_VEHICLE_SLOT_OCCUPIED);
                if (member != null) return new PacketVehicleInteractRsp(player, id, type);
                if (player.getTeamManager().getCurrentAvatarEntity() == null)
                    return reject(id, operation, Retcode.RET_GADGET_INTERACT_COND_NOT_MEET);
                for (var other : new ArrayList<>(scene.getEntities().values()))
                    if (other instanceof EntityVehicle previous && previous != vehicle)
                        for (var rider : new ArrayList<>(previous.getVehicleMembers()))
                            if (rider.getUid() == player.getUid())
                                NatsaurusVehicleHelper.dismount(player, previous, rider);
                if (!mount) {
                    vehicle.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
                    vehicle.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
                }
                member =
                        VehicleMember.newBuilder()
                                .setUid(player.getUid())
                                .setAvatarGuid(player.getTeamManager().getCurrentCharacterGuid())
                                .build();
                vehicle.getVehicleMembers().add(member);
                player.getStaminaManager().handleVehicleInteractReq(session, id, type);
                player.getQuestManager()
                        .queueEvent(
                                QuestContent.QUEST_CONTENT_ENTER_VEHICLE, vehicle.getGadgetId());
                scene.updateEntity(vehicle);
                return new PacketVehicleInteractRsp(player, id, type);
            }
        }
    }
}

package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.NatsaurusVehicleHelper;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.VehicleInteractRspOuterClass.VehicleInteractRsp;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;

import java.util.Locale;

/** A snapshot of an interaction; constructing a packet never changes gameplay state. */
public class PacketVehicleInteractRsp extends BasePacket {
    public PacketVehicleInteractRsp(int entityId, int operation, int retcode) {
        super(PacketOpcodes.VehicleInteractRsp);
        setData(
                VehicleInteractRsp.newBuilder()
                        .setEntityId(entityId)
                        .setInteractTypeValue(operation)
                        .setRetcode(retcode)
                        .build());
    }

    public PacketVehicleInteractRsp(Player player, int entityId, VehicleInteractType operation) {
        super(PacketOpcodes.VehicleInteractRsp);
        var entity =
                player == null || player.getScene() == null
                        ? null
                        : player.getScene().getEntityById(entityId);
        if (!(entity instanceof EntityVehicle vehicle)) {
            setData(
                    VehicleInteractRsp.newBuilder()
                            .setEntityId(entityId)
                            .setInteractType(operation)
                            .setRetcode(Retcode.RET_ENTITY_NOT_EXIST.getNumber())
                            .build());
            return;
        }
        var member =
                vehicle.getVehicleMembers().stream()
                        .filter(m -> m.getUid() == player.getUid())
                        .findFirst()
                        .orElseGet(
                                () ->
                                        VehicleMember.newBuilder()
                                                .setUid(player.getUid())
                                                .setAvatarGuid(
                                                        player.getTeamManager()
                                                                .getCurrentCharacterGuid())
                                                .build());
        setData(snapshot(vehicle, member, operation, player));
    }

    public PacketVehicleInteractRsp(
            EntityVehicle vehicle, VehicleMember member, VehicleInteractType operation) {
        super(PacketOpcodes.VehicleInteractRsp);
        setData(snapshot(vehicle, member, operation, null));
    }

    private static VehicleInteractRsp snapshot(
            EntityVehicle vehicle,
            VehicleMember member,
            VehicleInteractType operation,
            Player player) {
        var reply = VehicleInteractRsp.newBuilder().setInteractType(operation);
        if (vehicle == null || member == null)
            return reply.setRetcode(Retcode.RET_ENTITY_NOT_EXIST.getNumber()).build();
        reply.setEntityId(vehicle.getId()).setMember(member);
        if (operation == VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN) {
            var data = GameData.getGadgetDataMap().get(vehicle.getGadgetId());
            var name =
                    data == null || data.getJsonName() == null
                            ? ""
                            : data.getJsonName().toLowerCase(Locale.ROOT);
            int type =
                    name.contains("skiff")
                            ? 2
                            : name.contains("sorush") ? 3 : name.contains("natsaurus") ? 4 : 1;
            boolean mount = NatsaurusVehicleHelper.isNatsaurusMount(vehicle);
            reply.setGadgetId(vehicle.getGadgetId())
                    .setVehicleType(type)
                    .setVehiclePos(
                            (mount || player == null ? vehicle.getPosition() : player.getPosition())
                                    .toProto())
                    .setVehicleRot(
                            (mount || player == null ? vehicle.getRotation() : player.getRotation())
                                    .toProto());
        }
        return reply.build();
    }
}

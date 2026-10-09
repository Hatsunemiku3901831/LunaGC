package emu.grasscutter.game.systems;

import emu.grasscutter.game.CoopRequest;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.World;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultNotifyOuterClass;
import emu.grasscutter.net.proto.ReasonOuterClass;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;

public class MultiplayerSystem extends BaseGameSystem {

    public MultiplayerSystem(GameServer server) {
        super(server);
    }

    public void applyEnterMp(Player player, int targetUid) {
        Player target = getServer().getPlayerByUid(targetUid);
        if (target == null) {
            player.sendPacket(new PacketPlayerApplyEnterMpResultNotify(targetUid, "", false, ReasonOuterClass.Reason.Reason_PLAYER_CANNOT_ENTER_MP));
            return;
        }

        if (getServer().getCrucibleMatchSystem().isReserved(player) || getServer().getCrucibleMatchSystem().isReserved(target)) {
            player.sendPacket(new PacketPlayerApplyEnterMpResultNotify(target, false, ReasonOuterClass.Reason.Reason_HOST_IN_MATCH));
            return;
        }

        if (player.getWorld() == null || player.getWorld().isMultiplayer()) {
            return;
        }

        CoopRequest request = target.getCoopRequests().get(player.getUid());

        if (request != null && !request.isExpired()) {

            return;
        }

        request = new CoopRequest(player);
        target.getCoopRequests().put(player.getUid(), request);

        target.sendPacket(new PacketPlayerApplyEnterMpNotify(player));
    }

    public void applyEnterMpReply(Player hostPlayer, int applyUid, boolean isAgreed) {
        synchronized (getServer().getCrucibleMatchSystem()) {
            applyEnterMpReplyLocked(hostPlayer, applyUid, isAgreed);
        }
    }

    private void applyEnterMpReplyLocked(Player hostPlayer, int applyUid, boolean isAgreed) {

        CoopRequest request = hostPlayer.getCoopRequests().get(applyUid);
        if (request == null || request.isExpired()) {
            return;
        }

        Player requester = request.getRequester();
        hostPlayer.getCoopRequests().remove(applyUid);

        if (getServer().getCrucibleMatchSystem().isReserved(requester) || getServer().getCrucibleMatchSystem().isReserved(hostPlayer)) {
            requester.sendPacket(new PacketPlayerApplyEnterMpResultNotify(hostPlayer, false, ReasonOuterClass.Reason.Reason_HOST_IN_MATCH));
            return;
        }

        if (requester.getWorld() == null || requester.getWorld().isMultiplayer() || hostPlayer.getWorld() == null) {
            request.getRequester().sendPacket(new PacketPlayerApplyEnterMpResultNotify(hostPlayer, false, ReasonOuterClass.Reason.Reason_PLAYER_CANNOT_ENTER_MP));
            return;
        }

        if (!isAgreed) {
            requester.sendPacket(new PacketPlayerApplyEnterMpResultNotify(hostPlayer, false, ReasonOuterClass.Reason.Reason_PLAYER_JUDGE));
            return;
        }

        var target = hostPlayer.getWorld();
        if (!target.isMultiplayer()) {
            var promoted = new World(hostPlayer, true);
            if (!promoted.tryAddPlayer(hostPlayer, target, context -> {
                hostPlayer.sendPacket(new PacketPlayerEnterSceneNotify(hostPlayer, promoted,
                        entry(hostPlayer, EnterType.EnterType_ENTER_OTHER, EnterReason.HostFromSingleToMp)));
                hostPlayer.sendPacket(new PacketEnterScenePeerNotify(hostPlayer));
            })) {
                rejectEntry(requester, hostPlayer);
                return;
            }
            target = promoted;
        }

        var source = requester.getWorld();
        var destination = target;
        var properties = entry(hostPlayer, EnterType.EnterType_ENTER_OTHER, EnterReason.TeamJoin);
        if (!destination.tryAddPlayer(requester, source, properties, context -> {
            requester.sendPacket(new PacketPlayerApplyEnterMpResultNotify(hostPlayer, true, ReasonOuterClass.Reason.Reason_PLAYER_JUDGE));
            requester.sendPacket(new PacketPlayerEnterSceneNotify(requester, destination, properties));
            requester.sendPacket(new PacketEnterScenePeerNotify(requester));
        })) rejectEntry(requester, hostPlayer);
    }

    private static TeleportProperties entry(Player player, EnterType type, EnterReason reason) {
        return TeleportProperties.builder().sceneId(player.getSceneId())
                .teleportTo(player.getPosition().clone()).teleportRot(player.getRotation().clone())
                .enterType(type).enterReason(reason).build();
    }

    private void rejectEntry(Player requester, Player host) {
        requester.sendPacket(new PacketPlayerApplyEnterMpResultNotify(host, false,
                ReasonOuterClass.Reason.Reason_PLAYER_CANNOT_ENTER_MP));
    }

    public boolean leaveCoop(Player player) {
        synchronized (getServer().getCrucibleMatchSystem()) {
            return leaveCoopLocked(player);
        }
    }

    private boolean leaveCoopLocked(Player player) {

        getServer().getCrucibleMatchSystem().cancel(player);

        if (player.getCurHomeWorld() != null && player.getCurHomeWorld().isInHome(player)) {
            return false;
        }

        var source = player.getWorld();
        if (source == null || !source.isMultiplayer()) {
            return false;
        }

        for (Player p : source.getPlayers()) {
            if (p.getSceneLoadState() != SceneLoadState.LOADED) {
                return false;
            }
        }

        World world = new World(player);
        return world.tryAddPlayer(player, source, context -> {
            player.sendPacket(new PacketPlayerEnterSceneNotify(player, world,
                    entry(player, EnterType.EnterType_ENTER_OTHER, EnterReason.TeamBack)));
            player.sendPacket(new PacketEnterScenePeerNotify(player));
        });
    }

    public boolean kickPlayer(Player player, int targetUid) {
        synchronized (getServer().getCrucibleMatchSystem()) {
            return kickPlayerLocked(player, targetUid);
        }
    }

    private boolean kickPlayerLocked(Player player, int targetUid) {

        var source = player.getWorld();
        if (source == null || !source.isMultiplayer() || source.getHost() != player) {
            return false;
        }

        Player victim = player.getServer().getPlayerByUid(targetUid);

        if (victim == null || victim == player || victim.getWorld() != source) {
            return false;
        }

        getServer().getCrucibleMatchSystem().cancel(victim);

        if (victim.getSceneLoadState() != SceneLoadState.LOADED) {
            return false;
        }

        World world = new World(victim);
        return world.tryAddPlayer(victim, source, context -> {
            victim.sendPacket(new PacketPlayerEnterSceneNotify(victim, world,
                    entry(victim, EnterType.EnterType_ENTER_OTHER, EnterReason.TeamKick)));
            victim.sendPacket(new PacketEnterScenePeerNotify(victim));
        });
    }
}

package emu.grasscutter.game.home;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.World;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.event.player.PlayerEnterHomeEvent;
import emu.grasscutter.server.event.player.PlayerLeaveHomeEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.game.BaseGameSystem;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;

public class HomeWorldMPSystem extends BaseGameSystem {
    public HomeWorldMPSystem(GameServer server) {
        super(server);
    }

    public void sendEnterHomeRequest(Player requester, int ownerUid) {
        var owner = getServer().getPlayerByUid(ownerUid);
        if (owner == null) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            ownerUid,
                            "",
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .OPEN_STATE_NOT_OPEN));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        if (owner.getRealmList() == null) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            ownerUid,
                            "",
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .OPEN_STATE_NOT_OPEN));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        var request = owner.getEnterHomeRequests().get(requester.getUid());

        if (request != null && !request.isExpired()) {
            return;
        }

        if (owner.isInEditMode()) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            ownerUid,
                            owner.getNickname(),
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .HOST_IN_EDIT_MODE));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        request = new EnterHomeRequest(requester);
        owner.getEnterHomeRequests().put(requester.getUid(), request);

        owner.sendPacket(new PacketPlayerApplyEnterHomeNotify(requester));
    }

    public void acceptEnterHomeRequest(Player owner, int requesterUid, boolean isAgreed) {
        var request = owner.getEnterHomeRequests().get(requesterUid);
        if (request == null || request.isExpired()) {
            return;
        }

        var requester = request.getRequester();
        owner.getEnterHomeRequests().remove(requesterUid);

        if (requester.getWorld() == null || requester.getWorld().isMultiplayer()) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            owner.getUid(),
                            owner.getNickname(),
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .SYSTEM_JUDGE));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        if (!isAgreed) {
            requester.sendPacket(new PacketPlayerApplyEnterHomeResultNotify(owner.getUid(), owner.getNickname(), false,
                    PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason.PLAYER_JUDGE));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        if (!this.tryEnterHome(requester, owner, 0, false, () -> requester.sendPacket(
                new PacketPlayerApplyEnterHomeResultNotify(owner.getUid(), owner.getNickname(), true,
                        PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason.PLAYER_JUDGE)))) {
            requester.sendPacket(new PacketPlayerApplyEnterHomeResultNotify(owner.getUid(), owner.getNickname(), false,
                    PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason.SYSTEM_JUDGE));
        }
    }

    public void enterHome(Player requester, Player owner, int teleportPoint, boolean toSafe) {
        tryEnterHome(requester, owner, teleportPoint, toSafe, () -> {});
    }

    private boolean tryEnterHome(Player requester, Player owner, int teleportPoint, boolean toSafe, Runnable onEntered) {
        var source = requester.getWorld();
        if (source == null || source.isMultiplayer()) {
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return false;
        }

        if (owner.getRealmList() == null) {

            requester.sendPacket(
                    new PacketTryEnterHomeRsp(
                            RetcodeOuterClass.Retcode.RET_HOME_NOT_FOUND_IN_MEM_VALUE, owner.getUid()));
            return false;
        }

        var world = this.server.getHomeWorldOrCreate(owner);
        var targetHome = world.getHome();

        var event = new PlayerEnterHomeEvent(requester, owner, targetHome);
        event.call();
        if (event.isCanceled()) {
            requester.sendPacket(
                    new PacketTryEnterHomeRsp(
                            RetcodeOuterClass.Retcode.RET_HOME_OWNER_REFUSE_TO_ENTER_HOME_VALUE, owner.getUid()));
            return false;
        }

        if (owner.isInEditMode()) {
            requester.sendPacket(
                    new PacketTryEnterHomeRsp(
                            RetcodeOuterClass.Retcode.RET_HOME_CANT_ENTER_BY_IN_EDIT_MODE_VALUE, owner.getUid()));
            return false;
        }

        int realmId = 2000 + owner.getCurrentRealmId();
        var scene = world.getSceneById(realmId);
        if (scene == null) {
            requester.sendPacket(new PacketTryEnterHomeRsp(
                    RetcodeOuterClass.Retcode.RET_HOME_NOT_FOUND_IN_MEM_VALUE, owner.getUid()));
            return false;
        }
        var item = targetHome.getHomeSceneItem(realmId);

        var config = scene.getScriptManager().getConfig();
        Position pos = toSafe && config != null && config.born_pos != null
                ? config.born_pos : item.getBornPos();

        if (teleportPoint != 0) {
            var target = item.getTeleportPointPos(teleportPoint);
            if (target != null) {
                pos = target;
            }
        }

        if (pos == null) {
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return false;
        }
        var properties = TeleportProperties.builder().sceneId(realmId)
                .enterReason(EnterReason.EnterHome).teleportTo(pos.clone())
                .teleportType(PlayerTeleportEvent.TeleportType.INTERNAL).build();
        boolean entered = world.tryAddPlayer(requester, source, properties, context -> {
            requester.getPrevPosForHome().set(context.previousPosition());
            requester.setCurHomeWorld(world);
            requester.setPrevScene(context.previousSceneId());
            requester.setHasSentInitPacketInHome(false);
            targetHome.save();
            onEntered.run();
            requester.sendPacket(new PacketPlayerEnterSceneNotify(requester, owner.getUid(),
                    properties, !requester.equals(owner)));
            requester.sendPacket(new PacketEnterScenePeerNotify(requester));
            requester.sendPacket(new PacketTryEnterHomeRsp(owner.getUid()));
            world.getPlayers().stream().filter(player -> !player.equals(requester))
                    .forEach(player -> player.sendPacket(new PacketPlayerPreEnterMpNotify(requester)));
        });
        if (!entered) requester.sendPacket(new PacketTryEnterHomeRsp());
        return entered;
    }

    public boolean leaveCoop(Player player, int prevScene) {
        return this.leaveCoop(player, prevScene, player.getPrevPosForHome());
    }

    public boolean leaveCoop(Player player, int prevScene, Position pos) {
        if (!(player.getWorld() instanceof HomeWorld source) || player.getCurHomeWorld() != source)
            return false;
        for (var p : source.getPlayers()) {
            if (p.getSceneLoadState() != Player.SceneLoadState.LOADED) {
                return false;
            }
        }

        var event =
                new PlayerLeaveHomeEvent(
                        player,
                        source.getHost(),
                        source.getHome(),
                        PlayerLeaveHomeEvent.Reason.PLAYER_LEAVE);
        event.call();

        var world = new World(player);
        var properties = TeleportProperties.builder().sceneId(prevScene).teleportTo(pos.clone())
                .enterType(EnterTypeOuterClass.EnterType.EnterType_ENTER_BACK).enterReason(EnterReason.TeamBack).build();
        return world.tryAddPlayer(player, source, properties, context -> {
            source.sendPacketToHostIfOnline(new PacketOtherPlayerEnterOrLeaveHomeNotify(player,
                    OtherPlayerEnterHomeNotifyOuterClass.OtherPlayerEnterHomeNotify.Reason.LEAVE));
            var myHome = this.server.getHomeWorldOrCreate(player);
            player.setCurHomeWorld(myHome);
            myHome.getHome().onOwnerLogin(player);
            player.sendPacket(new PacketPlayerQuitFromHomeNotify(
                    PlayerQuitFromHomeNotifyOuterClass.PlayerQuitFromHomeNotify.QuitReason.BACK_TO_MY_WORLD));
            player.sendPacket(new PacketPlayerEnterSceneNotify(player, world, properties));
            player.sendPacket(new PacketEnterScenePeerNotify(player));
        });
    }

    public boolean kickPlayerFromHome(Player owner, int targetUid) {
        var source = owner.getCurHomeWorld();
        if (source == null || !source.getHost().equals(owner)) {
            return false;
        }

        var victim = owner.getServer().getPlayerByUid(targetUid);
        if (victim == null || owner.equals(victim) || victim.getWorld() != source || victim.getCurHomeWorld() != source) {
            return false;
        }

        if (victim.getSceneLoadState() != Player.SceneLoadState.LOADED) {
            return false;
        }

        var event =
                new PlayerLeaveHomeEvent(
                        victim, owner, source.getHome(), PlayerLeaveHomeEvent.Reason.KICKED);
        event.call();

        var world = new World(victim);
        var properties = TeleportProperties.builder().sceneId(3).teleportTo(victim.getPrevPosForHome().clone())
                .enterType(EnterTypeOuterClass.EnterType.EnterType_ENTER_BACK).enterReason(EnterReason.TeamKick).build();
        return world.tryAddPlayer(victim, source, properties, context -> {
            source.sendPacketToHostIfOnline(new PacketOtherPlayerEnterOrLeaveHomeNotify(victim,
                    OtherPlayerEnterHomeNotifyOuterClass.OtherPlayerEnterHomeNotify.Reason.LEAVE));
            var myHome = this.server.getHomeWorldOrCreate(victim);
            victim.setCurHomeWorld(myHome);
            myHome.getHome().onOwnerLogin(victim);
            victim.sendPacket(new PacketPlayerQuitFromHomeNotify(
                    PlayerQuitFromHomeNotifyOuterClass.PlayerQuitFromHomeNotify.QuitReason.KICK_BY_HOST));
            victim.sendPacket(new PacketPlayerEnterSceneNotify(victim, world, properties));
            victim.sendPacket(new PacketEnterScenePeerNotify(victim));
        });
    }
}

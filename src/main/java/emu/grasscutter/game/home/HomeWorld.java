package emu.grasscutter.game.home;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketDelTeamEntityNotify;
import emu.grasscutter.server.packet.send.PacketPlayerGameTimeNotify;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public class HomeWorld extends World {
    private final GameHome home;
    private HomeModuleManager moduleManager;

    public HomeWorld(GameServer server, Player owner) {
        super(server, owner);

        this.home = owner.isOnline() ? owner.getHome() : GameHome.getByUid(owner.getUid());
        this.refreshModuleManager();
    }

    @Override
    public boolean onTick() {
        if (this.getPlayerCount() == 0) return true;
        if (this.moduleManager == null) {
            return false;
        }
        this.moduleManager.tick();

        if (this.getTickCount() % 10 == 0) {
            this.getPlayers().forEach(p -> p.sendPacket(new PacketPlayerGameTimeNotify(p)));
        }

        if (this.isInHome(this.getHost()) && this.getTickCount() % 60 == 0) {
            this.getHost().updatePlayerGameTime(this.getCurrentWorldTime());
        }

        this.tickCount++;
        return false;
    }

    public void refreshModuleManager() {
        if (this.moduleManager != null) {
            this.moduleManager.onRemovedModule();
        }

        this.moduleManager = new HomeModuleManager(this);
        this.moduleManager.onSetModule();
    }

    public int getActiveOutdoorSceneId() {
        return this.getHost().getCurrentRealmId() + 2000;
    }

    public int getActiveIndoorSceneId() {
        return this.isRealmIdValid()
                ? this.getSceneById(this.getActiveOutdoorSceneId()).getSceneItem().getRoomSceneId()
                : -1;
    }

    public boolean isRealmIdValid() {
        return this.getSceneById(this.getHost().getCurrentRealmId() + 2000) != null;
    }

    @Override
    protected int peerIdForJoiningPlayer(Player player) {
        return player == this.getHost() ? 1 : this.getNextPeerId();
    }

    @Override
    protected boolean disbandsOnHostLeave() {
        return false;
    }

    @Override
    protected void sendDepartureTeamPacket(Player player, int sceneId, List<Integer> teamIds) {
        this.broadcastPacket(new PacketDelTeamEntityNotify(sceneId, teamIds));
    }

    @Override
    @Nullable public HomeScene getSceneById(int sceneId) {
        synchronized (this.getScenes()) {
            var scene = this.getScenes().get(sceneId);
            if (scene instanceof HomeScene homeScene) {
                return homeScene;
            }

            var sceneData = GameData.getSceneDataMap().get(sceneId);
            if (sceneData != null) {
                scene = new HomeScene(this, sceneData);
                this.registerScene(scene);
                return (HomeScene) scene;
            }

            return null;
        }
    }

    @Override
    public int getNextPeerId() {
        // Reserve peer 1 for the owner, even when guests enter before the owner.
        return super.getNextPeerId() + 1;
    }

    @Override
    public synchronized void setHost(Player host) {
        super.setHost(host);
    }

    @Override
    public final boolean isMultiplayer() {
        return true;
    }

    @Override
    public final boolean isPaused() {
        return false;
    }

    @Override
    public final boolean isTimeLocked() {
        return false;
    }

    public int getOwnerUid() {
        return this.getHost().getUid();
    }

    public List<Player> getGuests() {
        return this.getPlayers().stream().filter(player -> !player.equals(this.getHost())).toList();
    }

    public boolean isInHome(Player player) {
        return this.getPlayers().contains(player);
    }

    public void ifHost(Player hostOrGuest, Consumer<Player> ifHost) {
        if (this.getHost().equals(hostOrGuest)) {
            ifHost.accept(hostOrGuest);
        }
    }

    public void sendPacketToHostIfOnline(BasePacket basePacket) {
        if (this.getHost().isOnline()) {
            this.getHost().sendPacket(basePacket);
        }
    }
}

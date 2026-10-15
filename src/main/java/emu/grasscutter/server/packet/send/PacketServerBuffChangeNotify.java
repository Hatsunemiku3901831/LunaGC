package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerBuffManager.PlayerBuff;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify;
import emu.grasscutter.net.proto.ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify.ServerBuffChangeType;

import java.util.Collection;
import java.util.stream.Stream;

public class PacketServerBuffChangeNotify extends BasePacket {

    public PacketServerBuffChangeNotify(
            Player player, ServerBuffChangeType changeType, PlayerBuff buff) {
        this(player, changeType, Stream.of(buff));
    }

    public PacketServerBuffChangeNotify(
            Player player, ServerBuffChangeType changeType, Collection<PlayerBuff> buffs) {
        this(player, changeType, buffs.stream());
    }

    public PacketServerBuffChangeNotify(
            Player player, ServerBuffChangeType changeType, Stream<PlayerBuff> buffs) {
        super(PacketOpcodes.ServerBuffChangeNotify);

        var proto = ServerBuffChangeNotify.newBuilder();

        var selected = buffs.toList();
        long target = selected.isEmpty() ? 0 : selected.get(0).getTargetGuid();
        if (selected.stream().anyMatch(buff -> buff.getTargetGuid() != target))
            throw new IllegalArgumentException("Buff notifications must share a recipient");
        if (target != 0) proto.addAvatarGuidList(target);
        else
            player.getTeamManager().getActiveTeam().stream()
                    .mapToLong(entity -> entity.getAvatar().getGuid())
                    .forEach(proto::addAvatarGuidList);

        proto.setServerBuffChangeType(changeType);
        selected.stream().map(PlayerBuff::toProto).forEach(proto::addServerBuffList);

        this.setData(proto);
    }
}

package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify;

public class PacketProudSkillExtraLevelNotify extends BasePacket {

    public PacketProudSkillExtraLevelNotify(Avatar avatar, int talentIndex) {
        this(avatar, talentIndex, avatar.getSkillLevelBonusForTalentIndex(talentIndex));
    }

    public PacketProudSkillExtraLevelNotify(Avatar avatar, int talentIndex, int extraLevel) {
        super(PacketOpcodes.ProudSkillExtraLevelNotify);

        ProudSkillExtraLevelNotify proto =
                ProudSkillExtraLevelNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .setTalentType(3) // Talent type = 3
                        .setTalentIndex(talentIndex)
                        .setExtraLevel(extraLevel)
                        .build();

        this.setData(proto);
    }
}

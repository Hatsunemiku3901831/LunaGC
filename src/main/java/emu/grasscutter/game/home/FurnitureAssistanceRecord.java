package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.FurnitureMakeBeHelpedDataOuterClass.FurnitureMakeBeHelpedData;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;

import lombok.Getter;

/** Last confirmed assistance identity for one visitor, captured before the home write. */
@Entity
@Getter
public final class FurnitureAssistanceRecord {
    private int uid, time, icon, avatarId, costumeId, pictureId, frameId;
    private String playerName;

    public FurnitureAssistanceRecord() {}

    static FurnitureAssistanceRecord capture(Player player, int time) {
        var record = new FurnitureAssistanceRecord();
        record.uid = player.getUid();
        record.time = time;
        record.icon = player.getHeadImage();
        record.playerName = player.getNickname();
        var picture = player.getProfilePicture();
        if (picture != null) {
            record.avatarId = picture.getAvatarId();
            record.costumeId = picture.getCostumeId();
            record.pictureId = picture.getProfilePictureId();
            record.frameId = picture.getProfileFrameId();
        }
        return record;
    }

    public boolean isValid() {
        return uid > 0
                && time > 0
                && playerName != null
                && icon >= 0
                && avatarId >= 0
                && costumeId >= 0
                && pictureId >= 0
                && frameId >= 0;
    }

    public FurnitureMakeBeHelpedData toProto() {
        return FurnitureMakeBeHelpedData.newBuilder()
                .setUid(uid)
                .setTime(time)
                .setIcon(icon)
                .setPlayerName(playerName)
                .setProfilePicture(
                        ProfilePicture.newBuilder()
                                .setAvatarId(avatarId)
                                .setCostumeId(costumeId)
                                .setProfilePictureId(pictureId)
                                .setProfileFrameId(frameId))
                .build();
    }
}

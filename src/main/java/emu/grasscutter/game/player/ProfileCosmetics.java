package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ProfilePictureData;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;
import java.util.*;

/** Selected portraits and consumed unlocks survive reconnects independently of the item bag. */
@Entity(useDiscriminator = false)
public final class ProfileCosmetics {
    public static final int DEFAULT_FRAME = 100000;
    private int pictureId;
    private int frameId = DEFAULT_FRAME;
    private Set<Integer> unlockedPictures = new HashSet<>();
    private Set<Integer> unlockedFrames = new HashSet<>();

    public synchronized int getPictureId() {
        return pictureId;
    }

    public synchronized int getFrameId() {
        return frameId > 0 ? frameId : DEFAULT_FRAME;
    }

    public synchronized Set<Integer> getUnlockedPictures() {
        return unlockedPictures == null ? Set.of() : Set.copyOf(unlockedPictures);
    }

    public synchronized Set<Integer> getUnlockedFrames() {
        return unlockedFrames == null ? Set.of() : Set.copyOf(unlockedFrames);
    }

    public boolean isPictureUnlocked(Player player, int id) {
        var data = GameData.getProfilePictureDataMap().get(id);
        if (data == null || !data.isValid()) return false;
        return switch (data.getUnlockType()) {
            case PROFILE_PICTURE_UNLOCK_BY_DEFAULT -> true;
            case PROFILE_PICTURE_UNLOCK_BY_ITEM -> getUnlockedPictures().contains(id);
            case PROFILE_PICTURE_UNLOCK_BY_AVATAR -> {
                var avatar = player.getAvatars().getAvatarById(data.getUnlockParam());
                yield avatar != null && avatar.getTrialAvatarId() == 0;
            }
            case PROFILE_PICTURE_UNLOCK_BY_COSTUME -> {
                var costume = GameData.getAvatarCostumeDataMap().get(data.getUnlockParam());
                var avatar =
                        costume == null ? null : player.getAvatars().getAvatarById(costume.getCharacterId());
                yield player.getCostumeList().contains(data.getUnlockParam())
                        || costume != null
                                && costume.isDefault()
                                && avatar != null
                                && avatar.getTrialAvatarId() == 0;
            }
            case PROFILE_PICTURE_UNLOCK_BY_PARENT_QUEST -> {
                var quest = player.getQuestManager().getMainQuestById(data.getUnlockParam());
                yield quest != null && quest.isFinished()
                        || player.getForcedFinishedQuests() != null
                                && player.getForcedFinishedQuests().contains(data.getUnlockParam());
            }
        };
    }

    public List<Integer> unlockedPictureIds(Player player) {
        return GameData.getProfilePictureDataMap()
                .keySet()
                .intStream()
                .filter(id -> isPictureUnlocked(player, id))
                .sorted()
                .boxed()
                .toList();
    }

    public boolean isFrameUnlocked(int id) {
        return id > 0
                && GameData.getProfileFrameDataMap().containsKey(id)
                && (id == DEFAULT_FRAME || getUnlockedFrames().contains(id));
    }

    public List<Integer> unlockedFrameIds() {
        return GameData.getProfileFrameDataMap()
                .keySet()
                .intStream()
                .filter(this::isFrameUnlocked)
                .sorted()
                .boxed()
                .toList();
    }

    public synchronized boolean unlockPicture(int id, int sourceItem) {
        var data = GameData.getProfilePictureDataMap().get(id);
        if (data == null
                || !data.isValid()
                || data.getUnlockType() != ProfilePictureData.UnlockType.PROFILE_PICTURE_UNLOCK_BY_ITEM
                || data.getUnlockParam() != sourceItem) return false;
        if (unlockedPictures == null) unlockedPictures = new HashSet<>();
        return unlockedPictures.add(id);
    }

    /** Reward callers must grant ownership before accepting a frame selection. */
    public synchronized boolean unlockFrame(int id) {
        if (id <= 0 || !GameData.getProfileFrameDataMap().containsKey(id) || id == DEFAULT_FRAME)
            return false;
        if (unlockedFrames == null) unlockedFrames = new HashSet<>();
        return unlockedFrames.add(id);
    }

    public synchronized boolean selectPicture(Player player, int id) {
        if (!isPictureUnlocked(player, id)) return false;
        pictureId = id;
        return true;
    }

    public synchronized boolean selectFrame(int id) {
        if (!isFrameUnlocked(id)) return false;
        frameId = id;
        return true;
    }

    public synchronized void resetPicture() {
        pictureId = 0;
    }

    public synchronized ProfilePicture toProto(int legacyHeadImage) {
        return toProto(legacyHeadImage, pictureId, getFrameId());
    }

    public static ProfilePicture toProto(
            int legacyHeadImage, int selectedPicture, int selectedFrame) {
        var data = GameData.getProfilePictureDataMap().get(selectedPicture);
        if (data == null) data = GameData.getProfilePictureDataMap().get(legacyHeadImage);
        if (data == null && legacyHeadImage > 0) {
            data =
                    GameData.getProfilePictureDataMap().values().stream()
                            .filter(
                                    row ->
                                            row.isValid()
                                                    && row.getUnlockType()
                                                            == ProfilePictureData.UnlockType.PROFILE_PICTURE_UNLOCK_BY_AVATAR
                                                    && row.getUnlockParam() == legacyHeadImage)
                            .min(Comparator.comparingInt(ProfilePictureData::getId))
                            .orElse(null);
        }
        var proto =
                ProfilePicture.newBuilder()
                        .setProfileFrameId(selectedFrame > 0 ? selectedFrame : DEFAULT_FRAME);
        if (data == null || !data.isValid()) {
            if (legacyHeadImage >= 10000000) proto.setAvatarId(legacyHeadImage);
            return proto.build();
        }
        proto.setProfilePictureId(data.getId());
        if (data.getUnlockType() == ProfilePictureData.UnlockType.PROFILE_PICTURE_UNLOCK_BY_AVATAR) {
            proto.setAvatarId(data.getUnlockParam());
        } else if (data.getUnlockType()
                == ProfilePictureData.UnlockType.PROFILE_PICTURE_UNLOCK_BY_COSTUME) {
            proto.setCostumeId(data.getUnlockParam());
            var costume = GameData.getAvatarCostumeDataMap().get(data.getUnlockParam());
            if (costume != null) proto.setAvatarId(costume.getCharacterId());
        }
        return proto.build();
    }
}

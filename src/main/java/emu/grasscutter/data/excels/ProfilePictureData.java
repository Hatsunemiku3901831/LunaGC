package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import lombok.Getter;

@Getter
@ResourceType(name = "ProfilePictureExcelConfigData.json")
public class ProfilePictureData extends GameResource {
    private int id;

    @SerializedName(
            value = "unlockType",
            alternate = {"AJFEJFNMCKP", "NLAPNBMECDF"})
    private UnlockType unlockType;

    @SerializedName(value = "unlockParam", alternate = "HOKCMBCOBAO")
    private int unlockParam;

    public enum UnlockType {
        PROFILE_PICTURE_UNLOCK_BY_AVATAR,
        PROFILE_PICTURE_UNLOCK_BY_COSTUME,
        PROFILE_PICTURE_UNLOCK_BY_ITEM,
        PROFILE_PICTURE_UNLOCK_BY_PARENT_QUEST,
        PROFILE_PICTURE_UNLOCK_BY_DEFAULT
    }

    public boolean isValid() {
        return id > 0
                && unlockType != null
                && (unlockType == UnlockType.PROFILE_PICTURE_UNLOCK_BY_DEFAULT || unlockParam > 0);
    }
}

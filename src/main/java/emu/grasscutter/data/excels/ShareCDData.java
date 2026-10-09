package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

@Getter
@ResourceType(
        name = "ShareCDExcelConfigData.json",
        loadPriority = ResourceType.LoadPriority.HIGHEST)
public final class ShareCDData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    @SerializedName(value = "cdSlots", alternate = "INAFFABEFEK")
    private Slot[] cdSlots;

    private int maxChargeNum;
    private boolean isUseRealTime;

    @Getter
    public static final class Slot {
        private float coolDownTime;
    }

    /** Match the client's float multiplication and truncation, including zero-duration slots. */
    public long durationMs(int index) {
        if (cdSlots == null || index < 0 || index >= cdSlots.length || cdSlots[index] == null)
            return -1;
        float seconds = cdSlots[index].coolDownTime;
        float milliseconds = seconds * 1000f;
        if (!Float.isFinite(seconds)
                || seconds < 0
                || !Float.isFinite(milliseconds)
                || milliseconds >= Long.MAX_VALUE) return -1;
        return (long) milliseconds;
    }
}

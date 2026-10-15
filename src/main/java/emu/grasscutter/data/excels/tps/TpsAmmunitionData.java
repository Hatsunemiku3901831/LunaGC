package emu.grasscutter.data.excels.tps;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

/** TpsAmmunitionExcelConfigData: one total ammunition pool shared by its magazine slots. */
@Getter
@ResourceType(name = "TpsAmmunitionExcelConfigData.json")
public final class TpsAmmunitionData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int tpsAmmoLimit;

    @SerializedName(value = "normalAmmoLimit", alternate = "DFKOLMLANEJ")
    private Integer normalAmmoLimit;

    private List<Integer> ammoSlotIds;
}

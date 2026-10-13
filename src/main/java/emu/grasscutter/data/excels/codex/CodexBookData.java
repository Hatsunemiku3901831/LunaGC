package emu.grasscutter.data.excels.codex;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.data.*;

import lombok.Getter;

@Getter
@ResourceType(name = {"BooksCodexExcelConfigData.json"})
public class CodexBookData extends GameResource {
    @SerializedName(value = "id", alternate = "Id")
    private int id;

    private int materialId;

    @Override
    public void onLoad() {
        if (id > 0 && materialId > 0) GameData.getCodexBookDataItemIdMap().put(materialId, this);
    }
}

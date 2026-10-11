package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "ActivityShopSheetExcelConfigData.json")
public class ActivityShopSheetData extends GameResource {
    private int id;
    private boolean isAheadPreview;
    private String condComb;
    private List<Condition> cond = List.of();

    @Getter
    public static class Condition {
        private String type;
        private List<Integer> param = List.of();
    }
}

package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "AsterMidExcelConfigData.json")
public final class AsterMidData extends GameResource {
    private int id;
    private List<Integer> campVec = List.of();

    @Override
    public int getId() {
        return id;
    }
}

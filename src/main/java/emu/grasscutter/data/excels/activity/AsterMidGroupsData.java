package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

@Getter
@ResourceType(name = "AsterMidGroupsExcelConfigData.json")
public final class AsterMidGroupsData extends GameResource {
    private int id, groupId;
    private List<Integer> battleGroupVec = List.of();

    @Override
    public int getId() {
        return id;
    }
}

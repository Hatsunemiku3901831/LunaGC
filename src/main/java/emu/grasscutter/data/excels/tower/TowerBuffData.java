package emu.grasscutter.data.excels.tower;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "TowerBuffExcelConfigData.json")
@Getter
public class TowerBuffData extends GameResource {
    private int towerBuffId;
    private LastingType lastingType;
    private int buffId;

    public enum LastingType {
        TOWER_BUFF_LASTING_NONE,
        TOWER_BUFF_LASTING_FLOOR,
        TOWER_BUFF_LASTING_LEVEL,
        TOWER_BUFF_LASTING_IMMEDIATE
    }

    @Override
    public int getId() {
        return towerBuffId;
    }
}

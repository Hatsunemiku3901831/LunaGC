package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

@Getter
@ResourceType(name = "ProfileFrameExcelConfigData.json")
public class ProfileFrameData extends GameResource {
    private int id;
    private int groupId;
}

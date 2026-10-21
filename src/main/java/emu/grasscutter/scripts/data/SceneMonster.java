package emu.grasscutter.scripts.data;

import java.util.List;
import lombok.*;

@ToString
@Setter
public class SceneMonster extends SceneObject {
    public int monster_id;
    public int pose_id;
    public int ai_config_id = -1;
    public int drop_id;
    public int kill_score;
    public boolean disableWander;
    public int title_id;
    public int special_name_id;
    public String drop_tag;
    public List<Integer> affix;
    public boolean isElite;
}

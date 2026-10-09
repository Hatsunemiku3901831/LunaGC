package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.game.props.ElementType;

import lombok.Getter;

import java.util.Map;

/** Conditions and talent parameters from the client's resonance table. */
@ResourceType(name = "TeamResonanceExcelConfigData.json")
@Getter
public class TeamResonanceData extends GameResource {
    private int teamResonanceId;
    private String openConfig;
    private float[] paramList;
    private String cond;
    private int fireAvatarCount, waterAvatarCount, windAvatarCount, electricAvatarCount;
    private int grassAvatarCount, iceAvatarCount, rockAvatarCount;

    @Override
    public int getId() {
        return teamResonanceId;
    }

    public boolean matches(Map<ElementType, Integer> counts) {
        if (cond != null && !cond.isEmpty())
            return "TEAM_RESONANCE_COND_ALL_DIFFERENT".equals(cond) && counts.size() == 4;
        int[] required = {
            fireAvatarCount,
            waterAvatarCount,
            windAvatarCount,
            electricAvatarCount,
            grassAvatarCount,
            iceAvatarCount,
            rockAvatarCount
        };
        ElementType[] elements = {
            ElementType.Fire,
            ElementType.Water,
            ElementType.Wind,
            ElementType.Electric,
            ElementType.Grass,
            ElementType.Ice,
            ElementType.Rock
        };
        boolean hasRequirement = false;
        for (int i = 0; i < required.length; i++) {
            if (required[i] < 0 || counts.getOrDefault(elements[i], 0) < required[i]) return false;
            hasRequirement |= required[i] > 0;
        }
        return hasRequirement;
    }
}

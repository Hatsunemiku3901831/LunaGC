package emu.grasscutter.game.world;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;

/** Quest and Lua weather operands are an area id and an activation flag. */
public final class WeatherAreaState {
    private WeatherAreaState() {}

    public static boolean canActivate(int areaId, int sceneId) {
        var data = GameData.getWeatherDataMap().get(areaId);
        return areaId > 0 && sceneId > 0 && data != null && data.getSceneID() == sceneId;
    }

    public static boolean apply(Player player, int areaId, int enabled) {
        if (player == null || areaId <= 0 || (enabled != 0 && enabled != 1)) return false;
        int previous = player.getWeatherId();
        if (enabled == 0) {
            // Cleanup must work after leaving the area, including a removed resource row.
            if (previous == areaId) player.setWeather(0, ClimateType.CLIMATE_SUNNY);
            return true;
        }
        var scene = player.getScene();
        if (scene == null || !canActivate(areaId, scene.getId())) return false;
        if (previous == areaId) return true;
        var climate = GameData.getWeatherDataMap().get(areaId).getDefaultClimate();
        player.setWeather(areaId, climate == null ? ClimateType.CLIMATE_SUNNY : climate);
        return true;
    }
}

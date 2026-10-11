package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneAreaWeatherNotifyOuterClass.SceneAreaWeatherNotify;

public class PacketSceneAreaWeatherNotify extends BasePacket {

    public PacketSceneAreaWeatherNotify(Player player) {
        super(PacketOpcodes.SceneAreaWeatherNotify);

        int area = player.getWeatherId();
        var climate = player.getClimate() == null ? ClimateType.CLIMATE_SUNNY : player.getClimate();
        var data = GameData.getWeatherDataMap().get(area);
        var scene = player.getScene();
        if (area > 0 && (data == null || scene == null || data.getSceneID() != scene.getId())) {
            area = 0;
            climate = ClimateType.CLIMATE_SUNNY;
        }
        var builder =
                SceneAreaWeatherNotify.newBuilder()
                        .setWeatherAreaId(area)
                        .setClimateType(climate.getValue());
        if (area > 0 && data.getGadgetID() > 0) builder.setWeatherGadgetId(data.getGadgetID());
        SceneAreaWeatherNotify proto = builder.build();

        this.setData(proto);
    }
}

package emu.grasscutter.game.expedition;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.data.GameData;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.FileUtils;

import it.unimi.dsi.fastutil.ints.*;

import java.nio.file.Files;
import java.util.List;

public class ExpeditionSystem extends BaseGameSystem {
    private final Int2ObjectMap<List<ExpeditionRewardDataList>> expeditionRewardData;
    private boolean userConfigured;

    public ExpeditionSystem(GameServer server) {
        super(server);
        this.expeditionRewardData = new Int2ObjectOpenHashMap<>();
        this.load();
    }

    public Int2ObjectMap<List<ExpeditionRewardDataList>> getExpeditionRewardDataList() {
        return expeditionRewardData;
    }

    public List<ExpeditionRewardDataList> getResolvedRewardDataList(int id) {
        if (userConfigured && expeditionRewardData.containsKey(id))
            return expeditionRewardData.get(id);
        var data = GameData.getExpeditionDataMap().get(id);
        if (data != null) return ExpeditionCatalog.rewards(data);
        return expeditionRewardData.get(id);
    }

    public synchronized void load() {
        userConfigured = Files.isRegularFile(FileUtils.getDataUserPath("ExpeditionReward.json"));
        getExpeditionRewardDataList().clear();
        try {
            List<ExpeditionRewardInfo> banners =
                    DataLoader.loadList("ExpeditionReward.json", ExpeditionRewardInfo.class);
            if (banners.size() > 0) {
                for (ExpeditionRewardInfo di : banners) {
                    getExpeditionRewardDataList()
                            .put(di.getExpId(), di.getExpeditionRewardDataList());
                }
                Grasscutter.getLogger().debug("Expedition reward successfully loaded.");
            } else {
                Grasscutter.getLogger()
                        .error("Unable to load expedition reward. Expedition reward size is 0.");
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load expedition reward.", e);
        }
    }
}

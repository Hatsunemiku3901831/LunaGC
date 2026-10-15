package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.codex.CodexAnimalData.CountType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QueryCodexMonsterBeKilledNumRspOuterClass.QueryCodexMonsterBeKilledNumRsp;

import java.util.List;

public class PacketQueryCodexMonsterBeKilledNumRsp extends BasePacket {

    public PacketQueryCodexMonsterBeKilledNumRsp(Player player, List<Integer> codexList) {
        super(PacketOpcodes.QueryCodexMonsterBeKilledNumRsp);
        QueryCodexMonsterBeKilledNumRsp.Builder proto =
                QueryCodexMonsterBeKilledNumRsp.newBuilder();

        var unlocked = player.getCodex().getUnlockedAnimal();
        for (int animal : codexList) {
            if (!unlocked.containsKey(animal)) continue;
            var stored = unlocked.get(animal);
            int count = stored == null ? 0 : Math.max(0, stored);
            var definition = GameData.getCodexAnimalDataMap().get(animal);
            boolean capture =
                    definition != null
                            && definition.getCountType() == CountType.CODEX_COUNT_TYPE_CAPTURE;
            // The client indexes both lists with each ID, including zero-valued entries.
            proto.addCodexIdList(animal)
                    .addBeKilledNumList(capture ? 0 : count)
                    .addBeCapturedNumList(capture ? count : 0);
        }

        this.setData(proto);
    }
}

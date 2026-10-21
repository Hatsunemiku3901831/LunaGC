package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.tower.TowerLevelData;
import emu.grasscutter.net.proto.TowerFloorRecordChangeNotifyOuterClass.TowerFloorRecordChangeNotify;
import emu.grasscutter.server.packet.send.PacketTowerFloorRecordChangeNotify;

import org.junit.jupiter.api.Test;

class TowerRecordRegressionTest {
    @Test
    void clearDoesNotPretendStarRewardWasClaimed() throws Exception {
        var record =
                new emu.grasscutter.game.tower.TowerLevelRecord(1053)
                        .setLevelStars(157, 2)
                        .setLevelStars(158, 0);
        var packet = new PacketTowerFloorRecordChangeNotify(record, true);
        var result =
                TowerFloorRecordChangeNotify.parseFrom(packet.getData()).getTowerFloorRecordList(0);
        assertEquals(0, result.getFloorStarRewardProgress());
        assertEquals(2, result.getPassedLevelMapOrThrow(157));
        assertEquals(0, result.getPassedLevelMapOrThrow(158));
        assertEquals(157, result.getPassedLevelRecordList(0).getLevelId());
        assertEquals(
                java.util.List.of(1, 2),
                result.getPassedLevelRecordList(0).getSatisfiedCondListList());
    }

    @Test
    void malformedTimeConditionDoesNotCrashSettlement() {
        var level =
                new Gson()
                        .fromJson(
                                "{conds:[{towerCondType:'TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN',argumentList:[2]}]}",
                                TowerLevelData.class);
        assertNull(level.getTimeCond(0));
    }
}

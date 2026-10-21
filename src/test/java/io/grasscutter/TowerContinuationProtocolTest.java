package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonEndStats;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.dungeons.dungeon_results.TowerResult;
import emu.grasscutter.game.tower.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.TowerCurLevelRecordChangeNotifyOuterClass.TowerCurLevelRecordChangeNotify;
import emu.grasscutter.server.game.GameSession;
import java.util.*;
import org.junit.jupiter.api.Test;

class TowerContinuationProtocolTest {
    @Test
    void threeStarsInTheFirstChamberStillOfferTheNextChamber() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var manager = new TowerManager(environment.player) {
                @Override public boolean hasNextFloor() { return true; }
                @Override public boolean hasNextLevel() { return true; }
                @Override public int getNextFloorId() { return 1045; }
            };
            var challenge = new WorldChallenge(environment.scene, null, 198, 2, List.of(), 580, 1, List.of());
            challenge.setSuccess(true);
            var result = new TowerResult(new Gson().fromJson("{id:3407}", DungeonData.class),
                    new DungeonEndStats(1, 32, 0, DungeonEndReason.COMPLETED), manager, challenge, 3, List.of());
            var outer = UnknownFieldSet.parseFrom(result.getProto().build().toByteArray());
            var detail = UnknownFieldSet.parseFrom(outer.getField(1536).getLengthDelimitedList().get(0));
            // PPFBGNHJIPE reader 0x14f51da20; UI consumes state 1 as next chamber.
            assertEquals(List.of(1L), detail.getField(8).getVarintList());
            assertFalse(detail.hasField(3), "same-floor continuation must not name another floor");
            assertEquals(List.of(1, 2, 3), result.getProto().getTowerLevelEndNotify().getFinishedStarCondListList());
        }
    }

    @Test
    void nextFloorAndContinuationHaveIndependentNativeTags() throws Exception {
        var value = emu.grasscutter.net.proto.TowerLevelEndNotifyOuterClass.TowerLevelEndNotify
                .parseFrom(new byte[] {0x18, (byte) 0x95, 8, 0x40, 2, 0x70, 1});
        assertEquals(1045, value.getNextFloorId());
        assertEquals(2, value.getContinueState());
        assertTrue(value.getIsSuccess());
        assertTrue(value.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void settlementKeepsTheDisplayedChamberWhileServerPreparesTheNext() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var data = new Gson().fromJson("{currentFloorId:1044,currentLevel:0,currentLevelId:133}", TowerData.class);
            var records = new HashMap<Integer, TowerLevelRecord>();
            var manager = new TowerManager(environment.player) {
                @Override public TowerData getTowerData() { return data; }
                @Override public Map<Integer, TowerLevelRecord> getRecordMap() { return records; }
                @Override public int getNextFloorId() { return 1045; }
            };
            environment.player.setSession(new GameSession(null) {
                @Override public void send(BasePacket packet) { environment.player.packets.add(packet); }
            });
            for (int chamber = 1; chamber <= 3; chamber++) {
                environment.player.packets.clear();
                manager.notifyCurLevelRecordChangeWhenDone(3);
                var packet = environment.player.packets.get(0);
                var shown = TowerCurLevelRecordChangeNotify.parseFrom(packet.getData()).getCurLevelRecord();
                assertEquals(1044, shown.getCurFloorId());
                assertEquals(chamber, shown.getCurLevelIndex(), "native results UI adds one to this index");
                assertEquals(chamber + 1, manager.getCurrentLevel(), "server checkpoint advances once");
                assertEquals(chamber * 3, records.get(1044).getStarCount());
            }
        }
    }
}

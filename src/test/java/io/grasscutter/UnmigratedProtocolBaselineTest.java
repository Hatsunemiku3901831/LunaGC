package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.game.dailytask.DailyTaskProto;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityMetaUpdateMoonOvergrowValueOuterClass.AbilityMetaUpdateMoonOvergrowValue;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.net.proto.DailyTaskDataNotifyOuterClass.DailyTaskDataNotify;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.net.proto.WorldOwnerDailyTaskNotifyOuterClass.WorldOwnerDailyTaskNotify;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Diagnostic baselines for unported sources; no old client or CN 7.2 compatibility claim.
 * TokenReq/heartbeat/header have moved to Protocol72LoginWireCompatibilityTest.
 */
public final class UnmigratedProtocolBaselineTest {
    @Test
    public void unportedLoginAndSceneEntriesRemainExplicitLegacyInputs() {
        assertEquals(3713, PacketOpcodes.GetPlayerTokenRsp);
        assertEquals(9282, PacketOpcodes.PlayerLoginReq);
        assertEquals(9582, PacketOpcodes.PlayerEnterSceneNotify);
        assertEquals(21608, PacketOpcodes.EnterSceneReadyReq);
        assertTrue(PacketOpcodes.AddCustomTeamReq < 0);
        assertTrue(PacketOpcodes.UnnamedOpcode2819 < 0);
    }

    @Test
    public void changedResponsesSerialize71Fields() throws Exception {
        var token = GetPlayerTokenRsp.newBuilder().setClientVersion("OSRELWin7.1.0").build();
        assertTrue(UnknownFieldSet.parseFrom(token.toByteArray()).hasField(1856));
        assertFalse(UnknownFieldSet.parseFrom(token.toByteArray()).hasField(1840));

        var gacha = DoGachaRsp.newBuilder().setIsCapturingRadiance(true).build();
        assertTrue(UnknownFieldSet.parseFrom(gacha.toByteArray()).hasField(286));

        var overgrow = AbilityMetaUpdateMoonOvergrowValue.newBuilder().setValue(1.5f).build();
        assertTrue(UnknownFieldSet.parseFrom(overgrow.toByteArray()).hasField(6));
    }

    @Test
    public void dailyTaskNotifiesUse71Schema() throws Exception {
        var board = DailyTaskDataNotify.parseFrom(DailyTaskProto.dataNotify(3, 100, true));
        assertEquals(3, board.getFinishedNum());
        assertEquals(100, board.getScoreRewardId());
        assertTrue(board.getIsTakenScoreReward());

        var owner = WorldOwnerDailyTaskNotify.parseFrom(
                DailyTaskProto.worldOwnerNotify(List.of(), 2, 3));
        assertEquals(2, owner.getFilterCityId());
        assertEquals(3, owner.getFinishedDailyTaskNum());
    }
}

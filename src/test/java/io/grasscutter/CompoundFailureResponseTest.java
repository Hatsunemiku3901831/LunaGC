package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CompoundData;
import emu.grasscutter.game.managers.cooking.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.net.proto.TakeCompoundOutputRspOuterClass.TakeCompoundOutputRsp;
import emu.grasscutter.server.packet.send.PackageTakeCompoundOutputRsp;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;

import java.util.*;

class CompoundFailureResponseTest {
    @Test
    void noOutputFailureCanSerializeWithoutItemList() throws Exception {
        var packet = new PackageTakeCompoundOutputRsp(null, Retcode.RET_COMPOUND_NOT_FINISH_VALUE);
        var reply = TakeCompoundOutputRsp.parseFrom(packet.getData());
        assertEquals(Retcode.RET_COMPOUND_NOT_FINISH_VALUE, reply.getRetcode());
        assertEquals(0, reply.getItemListCount());
    }

    @Test
    void emptyListFailureRetainsTheSameErrorCode() throws Exception {
        var packet =
                new PackageTakeCompoundOutputRsp(List.of(), Retcode.RET_COMPOUND_NOT_FINISH_VALUE);
        var reply = TakeCompoundOutputRsp.parseFrom(packet.getData());
        assertEquals(Retcode.RET_COMPOUND_NOT_FINISH_VALUE, reply.getRetcode());
        assertEquals(0, reply.getItemListCount());
    }

    @Test
    void actualUnfinishedQueueRepliesWithoutConsumingTheQueue() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var map = GameData.getCompoundDataMap();
            var previous = new HashMap<>(map);
            try {
                map.clear();
                map.put(
                        1001,
                        new Gson()
                                .fromJson(
                                        "{id:1001,groupID:1,isDefaultUnlocked:true,costTime:60,queueSize:99,inputVec:[],outputVec:[]}",
                                        CompoundData.class));
                CookingCompoundManager.initialize();
                var player = new InvestigationRewardTest.ClaimPlayer();
                var queue = new ActiveCookCompoundData(1001, 60, 1, Utils.getCurrentSeconds());
                player.getActiveCookCompounds().put(1001, queue);
                player.packets.clear();
                player.getCookingCompoundManager()
                        .handleTakeCompoundOutputReq(
                                TakeCompoundOutputReq.newBuilder().setCompoundGroupId(1).build());
                var response =
                        player.packets.stream()
                                .filter(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp)
                                .findFirst()
                                .orElseThrow();
                assertEquals(
                        Retcode.RET_COMPOUND_NOT_FINISH_VALUE,
                        TakeCompoundOutputRsp.parseFrom(response.getData()).getRetcode());
                assertSame(queue, player.getActiveCookCompounds().get(1001));
                assertEquals(1, queue.getTotalCount());
            } finally {
                map.clear();
                map.putAll(previous);
                CookingCompoundManager.initialize();
            }
        }
    }
}

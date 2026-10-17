package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.net.proto.ForgeQueueDataOuterClass.ForgeQueueData;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketForgeStartRsp;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 7.1 ForgeQueueData uses the tags the client reader stores on the queue object. */
public final class ForgeProtocolTest {

    @Test
    public void startAcknowledgementUsesTheNativePacketId() {
        // Type 33210 posts forge-start event 0x247; its native CmdId getter returns 7643.
        assertEquals(7643, new PacketForgeStartRsp(Retcode.RET_SUCC).getOpcode());
    }

    @Test
    public void startFailureUsesTheNativeRetcodeTag() throws Exception {
        // Reader 0x14ae1a2f0 reads wire tag 0x40 into the sole int32 field at +0x18.
        var packet = new PacketForgeStartRsp(Retcode.RET_FORGE_QUEUE_FULL);
        var fields = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(
                List.of((long) Retcode.RET_FORGE_QUEUE_FULL.getNumber()),
                fields.getField(8).getVarintList());
        assertEquals(1, fields.asMap().size());
    }

    @Test
    public void queueEntryUsesTheNativeFieldTags() {
        // Reader 0x14820f340: forge 0x08, avatar 0x10, unfinished 0x18, total time 0x38,
        // queue 0x48, finished 0x50, next time 0x68.
        byte[] encoded =
                ForgeQueueData.newBuilder()
                        .setForgeId(1)
                        .setAvatarId(2)
                        .setUnfinishCount(3)
                        .setTotalFinishTimestamp(4)
                        .setQueueId(5)
                        .setFinishCount(6)
                        .setNextFinishTimestamp(7)
                        .build()
                        .toByteArray();

        assertArrayEquals(
                new byte[] {0x08, 1, 0x10, 2, 0x18, 3, 0x38, 4, 0x48, 5, 0x50, 6, 0x68, 7},
                encoded);
    }

    @Test
    public void queueIdIsTheDictionaryKey() throws Exception {
        // Page lookup and the notify merger both read field 9 at object +0x30.
        ForgeQueueData data = ForgeQueueData.parseFrom(new byte[] {0x48, 2, 0x08, 14, 0x50, 1});

        assertEquals(2, data.getQueueId());
        assertEquals(14, data.getForgeId());
        assertEquals(1, data.getFinishCount());
        assertEquals(0, data.getUnfinishCount());
    }
}

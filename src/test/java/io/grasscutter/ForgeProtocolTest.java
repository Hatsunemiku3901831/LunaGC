package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.proto.ForgeQueueDataOuterClass.ForgeQueueData;
import org.junit.jupiter.api.Test;

/** 7.1 ForgeQueueData uses the tags the client reader stores on the queue object. */
public final class ForgeProtocolTest {

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
                new byte[] {0x08, 1, 0x10, 2, 0x18, 3, 0x38, 4, 0x48, 5, 0x50, 6, 0x68, 7}, encoded);
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

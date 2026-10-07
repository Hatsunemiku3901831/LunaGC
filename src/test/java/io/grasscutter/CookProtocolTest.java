package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.PlayerCookRspOuterClass.PlayerCookRsp;
import org.junit.jupiter.api.Test;

/** 7.1 cook packets use the tags the client writer and reader actually emit. */
public final class CookProtocolTest {

    @Test
    public void autoCookRequestKeepsTheSliderCount() throws Exception {
        // Writer 0x1510d35e0. Auto cook NDPOBPEMMGM omits quality and sends the slider as field 4.
        // Tags: count 0x20, assist 0x30, controller+0x24c 0x38, controller+0x25c 0x50, recipe 0x70.
        PlayerCookReq req =
                PlayerCookReq.parseFrom(new byte[] {0x20, 5, 0x30, 7, 0x38, 3, 0x50, 9, 0x70, 100});

        assertEquals(5, req.getKLACBPCPCMJ());
        assertEquals(0, req.getJJPABEHGMCH());
        assertEquals(7, req.getAssistAvatar());
        assertEquals(3, req.getDDACKLBMIKL());
        assertEquals(9, req.getOLLOPKLIIAC());
        assertEquals(100, req.getRecipeId());
    }

    @Test
    public void manualCookRequestReadsQualityAndASingleDish() throws Exception {
        // Manual site PDAIJOFMGLL writes quality 1/2/3 on field 1 and count 1 on field 4.
        PlayerCookReq req = PlayerCookReq.parseFrom(new byte[] {0x08, 2, 0x20, 1, 0x70, 14});

        assertEquals(2, req.getJJPABEHGMCH());
        assertEquals(1, req.getKLACBPCPCMJ());
        assertEquals(14, req.getRecipeId());
    }

    @Test
    public void responsesUseTheNativeQualityAndCountTags() {
        // Reader 0x14ca21a80: quality tag 0x30 (field 6), cook count tag 0x40 (field 8).
        byte[] encoded =
                PlayerCookRsp.newBuilder().setQteQuality(3).setCookCount(5).build().toByteArray();

        assertArrayEquals(new byte[] {0x30, 3, 0x40, 5}, encoded);
    }
}

package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto.AvatarExpeditionInfoOuterClass.AvatarExpeditionInfo;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

/** Handwritten samples based on independent native parser branches, not generated writers. */
class ExpeditionTalentProtocolTest {
    @Test
    void nativeStartEntryCarriesGuidDurationAndShortening() throws Exception {
        var response =
                AvatarExpeditionStartRsp.parseFrom(
                        HexFormat.of().parseHex("3a0c1065181428914e550000803e"));
        var entry = response.getExpeditionBasicInfoList(0);
        assertEquals(101, entry.getExpId());
        assertEquals(20, entry.getHourTime());
        assertEquals(10001, entry.getAvatarGuid());
        assertEquals(0.25f, entry.getShortenRatio());
    }

    @Test
    void nativeTripInfoKeepsShorteningInItsOwnFloatField() throws Exception {
        var info =
                AvatarExpeditionInfo.parseFrom(HexFormat.of().parseHex("0801106518142d0000803e"));
        assertEquals(1, info.getStateValue());
        assertEquals(101, info.getExpId());
        assertEquals(20, info.getHourTime());
        assertEquals(0.25f, info.getShortenRatio());
    }

    @Test
    void nativeRewardEntrySeparatesBaseAndExtraItemLists() throws Exception {
        var response =
                AvatarExpeditionGetRewardRsp.parseFrom(
                        HexFormat.of()
                                .parseHex(
                                        "421e2a0c1065181428914e550000803e220608a18d061008520608a18d061002"));
        var reward = response.getExpeditionRewardList(0);
        assertEquals(10001, reward.getBasicInfo().getAvatarGuid());
        assertEquals(100001, reward.getItemList(0).getItemId());
        assertEquals(8, reward.getItemList(0).getCount());
        assertEquals(100001, reward.getExtraItemList(0).getItemId());
        assertEquals(2, reward.getExtraItemList(0).getCount());
    }
}
